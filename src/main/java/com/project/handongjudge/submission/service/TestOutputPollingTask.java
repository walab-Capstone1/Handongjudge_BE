package com.project.handongjudge.submission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import com.project.handongjudge.submission.dto.SubmissionOutputResponseDTO;
import com.project.handongjudge.submission.entity.Output;
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
 * 테스트하기 전용 비동기 폴링 태스크.
 * DB에 Submission을 저장하지 않는 일회성 테스트 제출에서 사용.
 * DOMjudge를 폴링하며 테스트케이스 결과(output 상세 포함)를 TestOutputSseService로 순차 push한다.
 */
@Component
@RequiredArgsConstructor
public class TestOutputPollingTask {

    private static final Logger log = LoggerFactory.getLogger(TestOutputPollingTask.class);
    private static final long POLL_INITIAL_BACKOFF_MS = 500L;
    private static final long POLL_MAX_BACKOFF_MS = 3_000L;
    private static final long SSE_POLL_TIMEOUT_MS = 50_000L;

    private final DomjudgeService domjudgeService;
    private final TestOutputSseService testOutputSseService;

    /**
     * @param sessionKey  DOMjudge submission ID (SSE emitter 키로 사용)
     * @param cid         DOMjudge contest ID (= sectionId)
     */
    @Async("sseExecutor")
    public void pollAndStream(String sessionKey, String cid) {
        log.info("[TEST-SSE] polling started sessionKey={} cid={}", sessionKey, cid);

        long deadlineMs = System.currentTimeMillis() + SSE_POLL_TIMEOUT_MS;
        long backoffMs = POLL_INITIAL_BACKOFF_MS;
        Set<Integer> sentIndices = new HashSet<>();
        boolean totalSent = false;

        while (System.currentTimeMillis() < deadlineMs) {
            try {
                SubmissionOutputResponseDTO result =
                        domjudgeService.getResultOutput(cid, sessionKey);

                if (result != null && result.getResult() != null && !result.getResult().isEmpty()) {

                    // CE: 즉시 완료
                    if ("CE".equals(result.getResult())) {
                        testOutputSseService.sendCe(sessionKey);
                        testOutputSseService.complete(sessionKey);
                        log.info("[TEST-SSE] CE sessionKey={}", sessionKey);
                        return;
                    }

                    List<Output> outputList = result.getOutputList();
                    if (outputList != null && !outputList.isEmpty()) {

                        // 전체 테스트케이스 수를 최초 1회만 push
                        if (!totalSent) {
                            testOutputSseService.sendTotal(sessionKey, outputList.size());
                            totalSent = true;
                            log.info("[TEST-SSE] total={} sessionKey={}", outputList.size(), sessionKey);
                        }

                        // 새로 완료된 테스트케이스 이벤트 push (output 상세 포함)
                        for (int i = 0; i < outputList.size(); i++) {
                            Output output = outputList.get(i);
                            if (output.getResult() != null
                                    && !output.getResult().isEmpty()
                                    && !sentIndices.contains(i)) {
                                sentIndices.add(i);
                                testOutputSseService.sendTestcase(
                                        sessionKey,
                                        i + 1,
                                        output.getResult(),
                                        output.getOutput(),
                                        output.getOutputError(),
                                        output.getOutputDiff(),
                                        output.getTestcase_input(),
                                        output.getTestcase_output(),
                                        output.getRuntime(),
                                        output.getMemoryUsed()
                                );
                                //Debug for SSE  ,to check if the SSE is working in web browser testcase by testcase
                                // try {
                                //     Thread.sleep(500); // 0.5초 간격
                                // } catch (InterruptedException e) {
                                //     Thread.currentThread().interrupt();
                                //     return;
                                // }

                            }
                        }

                        // 전체 완료 여부
                        boolean allDone = outputList.stream()
                                .allMatch(o -> o.getResult() != null && !o.getResult().isEmpty());

                        if (allDone) {
                            testOutputSseService.sendComplete(sessionKey, result.getResult());
                            testOutputSseService.complete(sessionKey);
                            log.info("[TEST-SSE] complete sessionKey={} result={}", sessionKey, result.getResult());
                            return;
                        }
                    }
                }

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() != 404) {
                    log.warn("[TEST-SSE] DOMjudge client error status={} sessionKey={}",
                            e.getStatusCode(), sessionKey);
                }
            } catch (ResourceAccessException e) {
                log.warn("[TEST-SSE] DOMjudge network error sessionKey={}: {}", sessionKey, e.getMessage());
            } catch (JsonProcessingException e) {
                log.warn("[TEST-SSE] JSON parse error sessionKey={}: {}", sessionKey, e.getMessage());
                testOutputSseService.sendError(sessionKey, "채점 결과를 해석하는 중 오류가 발생했습니다.");
                testOutputSseService.complete(sessionKey);
                return;
            } catch (Exception e) {
                log.warn("[TEST-SSE] unexpected error sessionKey={}: {}", sessionKey, e.getMessage());
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
        log.warn("[TEST-SSE] timeout sessionKey={}", sessionKey);
        testOutputSseService.sendError(sessionKey,
                "채점 결과를 가져오는 데 시간이 초과되었습니다. 다시 시도해 주세요.");
        testOutputSseService.complete(sessionKey);
    }
}
