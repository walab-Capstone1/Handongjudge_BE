package com.project.handongjudge.submission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import com.project.handongjudge.submission.dto.SubmissionOutputResponseDTO;
import com.project.handongjudge.submission.entity.Output;
import com.project.handongjudge.submission.entity.Submission;
import com.project.handongjudge.submission.entity.SubmissionMetric;
import com.project.handongjudge.submission.repository.SubmissionMetricRepository;
import com.project.handongjudge.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SSE 채점 결과 스트리밍 전용 비동기 폴링 태스크.
 * sseExecutor 스레드 풀에서 실행되며, DOMjudge를 폴링해 테스트케이스 결과를
 * SseEmitter로 순차 push한다.
 */
@Component
@RequiredArgsConstructor
public class SubmissionPollingTask {

    private static final Logger log = LoggerFactory.getLogger(SubmissionPollingTask.class);
    private static final long POLL_INITIAL_BACKOFF_MS = 500L;
    private static final long POLL_MAX_BACKOFF_MS = 3_000L;
    private static final long SSE_POLL_TIMEOUT_MS = 50_000L;

    private final SubmissionRepository submissionRepository;
    private final DomjudgeService domjudgeService;
    private final SubmissionSseService submissionSseService;
    private final SubmissionMetricRepository submissionMetricRepository;

    /**
     * submissionDbId에 해당하는 제출을 DOMjudge에서 폴링해 SSE 이벤트를 push한다.
     * - testcase 이벤트: 테스트케이스 결과가 순차적으로 push됨
     * - complete  이벤트: 모든 테스트케이스 완료 시 최종 결과 push
     * - ce        이벤트: CE 판정 즉시 push
     * - error     이벤트: 타임아웃 또는 예외 발생 시 push
     */
    @Async("sseExecutor")
    public void pollAndStream(Long submissionDbId) {
        // section 정보는 JOIN FETCH로 eager 로드해 LazyLoading 문제를 방지한다.
        Submission submission = submissionRepository.findByIdWithSection(submissionDbId).orElse(null);
        if (submission == null) {
            log.warn("[SSE] submission not found submissionDbId={}", submissionDbId);
            submissionSseService.sendError(submissionDbId, "제출 정보를 불러올 수 없습니다.");
            submissionSseService.complete(submissionDbId);
            return;
        }

        String cid = String.valueOf(submission.getSection().getId());
        String domjudgeSubmissionId = submission.getSubmissionId();
        log.info("[SSE] polling started submissionDbId={} domjudgeId={}", submissionDbId, domjudgeSubmissionId);

        long deadlineMs = System.currentTimeMillis() + SSE_POLL_TIMEOUT_MS;
        long backoffMs = POLL_INITIAL_BACKOFF_MS;
        Set<Integer> sentIndices = new HashSet<>();

        while (System.currentTimeMillis() < deadlineMs) {
            try {
                SubmissionOutputResponseDTO result =
                        domjudgeService.getResultOutput(cid, domjudgeSubmissionId);

                if (result != null && result.getResult() != null && !result.getResult().isEmpty()) {

                    // CE: outputList 없이 즉시 완료 처리
                    if ("CE".equals(result.getResult())) {
                        submissionSseService.sendCe(submissionDbId);
                        submissionRepository.updateResultIfPending(submissionDbId, "CE", 0, 0);
                        tryFinalizeMetric(submissionDbId);
                        submissionSseService.complete(submissionDbId);
                        log.info("[SSE] CE submissionDbId={}", submissionDbId);
                        return;
                    }

                    List<Output> outputList = result.getOutputList();
                    if (outputList != null && !outputList.isEmpty()) {

                        // 새로 완료된 테스트케이스 이벤트 push (중복 방지)
                        for (int i = 0; i < outputList.size(); i++) {
                            Output output = outputList.get(i);
                            if (output.getResult() != null
                                    && !output.getResult().isEmpty()
                                    && !sentIndices.contains(i)) {
                                sentIndices.add(i);
                                submissionSseService.sendTestcase(submissionDbId, i + 1, output.getResult());
                            }
                        }

                        // 전체 테스트케이스 완료 여부 확인
                        boolean allDone = outputList.stream()
                                .allMatch(o -> o.getResult() != null && !o.getResult().isEmpty());

                        if (allDone) {
                            int total = outputList.size();
                            int passed = (int) outputList.stream()
                                    .filter(o -> "correct".equals(o.getResult()))
                                    .count();
                            submissionSseService.sendComplete(submissionDbId, result.getResult(), passed, total);
                            submissionRepository.updateResultIfPending(
                                    submissionDbId, result.getResult(), passed, total);
                            tryFinalizeMetric(submissionDbId);
                            submissionSseService.complete(submissionDbId);
                            log.info("[SSE] complete submissionDbId={} result={} passed={}/{}",
                                    submissionDbId, result.getResult(), passed, total);
                            return;
                        }
                    }
                }

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() != 404) {
                    log.warn("[SSE] DOMjudge client error status={} submissionDbId={}",
                            e.getStatusCode(), submissionDbId);
                }
            } catch (ResourceAccessException e) {
                log.warn("[SSE] DOMjudge network error submissionDbId={}: {}", submissionDbId, e.getMessage());
            } catch (JsonProcessingException e) {
                log.warn("[SSE] JSON parse error submissionDbId={}: {}", submissionDbId, e.getMessage());
                submissionSseService.sendError(submissionDbId, "채점 결과를 해석하는 중 오류가 발생했습니다.");
                submissionSseService.complete(submissionDbId);
                return;
            } catch (Exception e) {
                log.warn("[SSE] unexpected error submissionDbId={}: {}", submissionDbId, e.getMessage());
            }

            long remaining = deadlineMs - System.currentTimeMillis();
            if (remaining <= 0) break;

            try {
                Thread.sleep(Math.min(backoffMs, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            backoffMs = Math.min(backoffMs * 2, POLL_MAX_BACKOFF_MS);
        }

        // 타임아웃
        log.warn("[SSE] timeout submissionDbId={}", submissionDbId);
        submissionSseService.sendError(submissionDbId,
                "채점 결과를 가져오는 데 시간이 초과되었습니다. 제출은 완료되었을 수 있으니 제출 목록에서 확인해 주세요.");
        submissionSseService.complete(submissionDbId);
    }

    /** 채점 완료 후 e2e 메트릭을 최초 1회 확정한다. */
    private void tryFinalizeMetric(Long submissionDbId) {
        try {
            submissionMetricRepository.findBySubmission_Id(submissionDbId).ifPresent(metric -> {
                if (metric.getE2eDurationMs() != null) return;
                SubmissionMetric fresh = submissionMetricRepository.findById(metric.getId()).orElse(metric);
                long handler = fresh.getSubmitHandlerDurationMs() != null ? fresh.getSubmitHandlerDurationMs() : 0L;
                long judging = fresh.getJudgingDurationMs() != null ? fresh.getJudgingDurationMs() : 0L;
                fresh.setE2eDurationMs(handler + judging);
                submissionMetricRepository.save(fresh);
            });
        } catch (Exception e) {
            log.warn("[SSE] finalize metric failed submissionDbId={}: {}", submissionDbId, e.getMessage());
        }
    }
}
