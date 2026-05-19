package com.project.handongjudge.submission.service;

import com.project.handongjudge.section.entity.Contest;
import com.project.handongjudge.section.repository.ContestRepository;
import com.project.handongjudge.submission.dto.AsyncSubmitResponseDTO;
import com.project.handongjudge.submission.dto.SubmissionOutputResponseDTO;
import com.project.handongjudge.submission.dto.SubmissionQuizResponseDTO;
import com.project.handongjudge.submission.dto.SubmissionRequestDTO;
import com.project.handongjudge.submission.dto.SubmissionResponseDTO;           
import com.project.handongjudge.submission.dto.SubmissionAuthDTO;
import com.project.handongjudge.submission.entity.Output;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;

import com.project.handongjudge.submission.repository.SubmissionRepository;
import com.project.handongjudge.submission.util.CodeExtenstion;
import com.project.handongjudge.submission.entity.Submission;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.assignment.repository.AssignmentProblemRepository;
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.entity.AssignmentProblem;
import com.project.handongjudge.section.service.SectionRoleService;
import com.project.handongjudge.quiz.repository.QuizRepository;
import com.project.handongjudge.quiz.repository.QuizProblemRepository;
import com.project.handongjudge.quiz.entity.Quiz;
import com.project.handongjudge.quiz.entity.QuizProblem;
import com.project.handongjudge.submission.entity.SubmissionMetric;
import com.project.handongjudge.submission.dto.TestSubmitResponseDTO;
import com.project.handongjudge.submission.repository.SubmissionMetricRepository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import com.project.handongjudge.grade.service.GradeService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;



@RequiredArgsConstructor
@Service
public class SubmissionService {
    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");
    private static final long POLL_INITIAL_BACKOFF_MS = 500L;
    private static final long POLL_MAX_BACKOFF_MS = 3_000L;
    private static final String FLOW_ASSIGNMENT_ASYNC = "assignmentAsyncSubmit";
    private static final String FLOW_QUIZ_ASYNC = "quizAsyncSubmit";
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final ProblemRepository problemRepository;
    private final SectionRepository sectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final DomjudgeService domjudgeService;
    private final ContestRepository contestRepository; // 추가해줘야 함
    private final AssignmentRepository assignmentRepository;
    private final AssignmentProblemRepository assignmentProblemRepository;
    private final SectionRoleService sectionRoleService;
    private final QuizRepository quizRepository;
    private final QuizProblemRepository quizProblemRepository;
    private final SubmissionMetricRepository submissionMetricRepository;
    private final ObjectProvider<GradeService> gradeServiceProvider;
    private final SubmissionSseService submissionSseService;
    private final SubmissionPollingTask submissionPollingTask;
    private final TestOutputSseService testOutputSseService;
    private final TestOutputPollingTask testOutputPollingTask;

    private void maybeClearAssignmentRejectIfAc(Submission submission) {
        if (submission == null || submission.getResult() == null) {
            return;
        }
        if (!"AC".equals(submission.getResult())) {
            return;
        }
        gradeServiceProvider.getObject().clearRejectedOnAcForAssignmentProblem(
                submission.getUser().getId(),
                submission.getSection().getId(),
                submission.getProblem().getId());
    }

    /** 비동기 제출 직후: submit 핸들러 시간·DOMjudge submitCode 구간을 submission_metrics에 저장 */
    private void persistAsyncSubmitMetric(Submission saved, SubmissionAuthDTO dto, long handlerStartedAtMs,
                                          long submitCodeStartedAtMs, long submitCodeEndedAtMs, String flowType) {
        long submitDurationMs = submitCodeEndedAtMs - submitCodeStartedAtMs;
        long submitHandlerDurationMs = System.currentTimeMillis() - handlerStartedAtMs;
        try {
            SubmissionMetric metric = SubmissionMetric.builder()
                    .measuredAt(LocalDateTime.now(KST_ZONE))
                    .submission(saved)
                    .flowType(flowType)
                    .language(dto.getLanguage())
                    .problemId(dto.getProblemId())
                    .sectionId(dto.getSectionId())
                    .domjudgeSubmissionId(saved.getSubmissionId())
                    .submitDurationMs(submitDurationMs)
                    .submitHandlerDurationMs(submitHandlerDurationMs)
                    .timedOut(false)
                    .build();
            submissionMetricRepository.save(metric);
        } catch (Exception ex) {
            log.error("[METRIC] Failed to save async submit metric: {}", ex.getMessage());
        }
    }

    /** GET result 1회당: getResultOutput 소요시간 누적 + polling_attempts (단일 UPDATE). */
    private void recordAsyncPollSample(long submissionDbId, long getResultDurationMs) {
        try {
            submissionMetricRepository.addAsyncPollSampleBySubmissionId(submissionDbId, getResultDurationMs);
        } catch (Exception ex) {
            log.warn("[METRIC] record async poll sample failed submissionId={}: {}", submissionDbId, ex.getMessage());
        }
    }

    /** 채점 결과가 DB에 확정된 뒤: e2e = submit_handler_duration_ms + judging_duration_ms (최초 1회만). */
    private void finalizeAsyncMetricE2eIfPending(long submissionDbId) {
        try {
            submissionMetricRepository.findBySubmission_Id(submissionDbId).ifPresent(metric -> {
                if (metric.getE2eDurationMs() != null) {
                    return;
                }
                SubmissionMetric fresh = submissionMetricRepository.findById(metric.getId()).orElse(metric);
                long handler = fresh.getSubmitHandlerDurationMs() != null ? fresh.getSubmitHandlerDurationMs() : 0L;
                long judging = fresh.getJudgingDurationMs() != null ? fresh.getJudgingDurationMs() : 0L;
                fresh.setE2eDurationMs(handler + judging);
                submissionMetricRepository.save(fresh);
            });
        } catch (Exception ex) {
            log.warn("[METRIC] finalize async e2e failed submissionId={}: {}", submissionDbId, ex.getMessage());
        }
    }

    @Getter
    @AllArgsConstructor
    private static class PollingResult<T> {
        private final T result;
        private final int attempts;
    }

    public SubmissionResponseDTO submitCode(SubmissionRequestDTO submissionRequestDTO) {
        User user = userRepository.findById(submissionRequestDTO.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Problem problem = problemRepository.findById(submissionRequestDTO.getProblemId())
                .orElseThrow(() -> new RuntimeException("Problem not found"));
        Section section = sectionRepository.findById(submissionRequestDTO.getSectionId())
                .orElseThrow(() -> new RuntimeException("Section not found"));

        String contestId = String.valueOf(section.getId());
        String teamId = enrollmentRepository.findTeamIdByUserIdAndSectionId(user.getId(), section.getId());
        String domjudgeProblemId = problem.getDomjudgeProblemId();

        File codeFile = CodeExtenstion.multipartToFile(
                submissionRequestDTO.getCode(),
                submissionRequestDTO.getLanguage()
        );

        String domjudgeSubmissionId = domjudgeService.submitCode(
                contestId,
                teamId,
                domjudgeProblemId,
                submissionRequestDTO.getLanguage(),
                codeFile
        );

        // Delete TmpFile immediately after submission (before DB save)
        if(codeFile != null) {
            try {
                Files.deleteIfExists(codeFile.toPath());
                log.debug("TmpFile deleted: {}", codeFile.getName());
            } catch (IOException e) {
                log.error("Failed to delete TmpFile: {}", e.getMessage());
            }
        }

        Submission submission = toSubmission(submissionRequestDTO);
        submission.setSection(section);
        submission.setSubmissionId(domjudgeSubmissionId);

        submissionRepository.save(submission);
        return toSubmissionResponseDTO(submission);
    }


    public Submission toSubmission(SubmissionRequestDTO submissionRequestDTO) {
        return Submission.builder()
                .problem(problemRepository.findById(submissionRequestDTO.getProblemId()).orElseThrow(() -> new RuntimeException("Problem not found")))
                .user(userRepository.findById(submissionRequestDTO.getUserId()).orElseThrow(() -> new RuntimeException("User not found")))
                .language(submissionRequestDTO.getLanguage())
                .code(submissionRequestDTO.getCode().getOriginalFilename())                .submittedAt(LocalDateTime.now(KST_ZONE))
                .build();
    }
    public SubmissionResponseDTO toSubmissionResponseDTO(Submission submission) {
        return SubmissionResponseDTO.builder()
                .submissionId(submission.getSubmissionId())
                .result(submission.getResult())
                .submittedAt(submission.getSubmittedAt())
                .language(submission.getLanguage())
                .problemId(submission.getProblem().getId())
                .sectionId(submission.getSection().getId())
                .build();
    }

    public SubmissionOutputResponseDTO toSubmissionOutputResponseDTO(Submission submission, List<Output> outputLists) {
        return SubmissionOutputResponseDTO.builder()
                .submissionId(submission.getSubmissionId())
                .result(submission.getResult())
                .submittedAt(submission.getSubmittedAt())
                .language(submission.getLanguage())
                .problemId(submission.getProblem().getId())
                .sectionId(submission.getSection().getId())
                .outputList(outputLists)
                .build();
    }


    public String getResult(Long sectionId, String submissionId) {
        String cid = String.valueOf(sectionId);
        return domjudgeService.getResult(cid, submissionId);
    }

    public SubmissionOutputResponseDTO getResultOutput(Long sectionId, String submissionId) {
        String cid = String.valueOf(sectionId);
        try {
            SubmissionOutputResponseDTO result = domjudgeService.getResultOutput(cid, submissionId);
            if (result != null && !result.getResult().isEmpty()) {
                return result;
            }
        } catch (Exception e) {
            log.warn("[METRIC] getResultOutput failed: sectionId={} submissionId={} error={}",
                     sectionId, submissionId, e.getMessage());
        }
        return null;
    }

    /**
     * @deprecated SSE 스트리밍 방식(POST /submit → GET /stream/{id})으로 교체되었습니다.
     *             이 API는 하위 호환 유지 목적으로만 남아있으며 추후 제거될 예정입니다.
     */
    @Deprecated
    public SubmissionResponseDTO submitAndGetResult(Authentication authentication, SubmissionAuthDTO submissionRequestDTO) {
        log.warn("[DEPRECATED] submitAndGetResult called — please migrate to /submit + /stream/{submissionDbId}");
        long e2eStart = System.currentTimeMillis();
        SubmissionMetric metric = SubmissionMetric.builder()
                .measuredAt(LocalDateTime.now())
                .language(submissionRequestDTO.getLanguage())
                .problemId(submissionRequestDTO.getProblemId())
                .sectionId(submissionRequestDTO.getSectionId())
                .flowType("submitAndGetResult")
                .timedOut(false)
                .build();

        try {
            Long userId = Long.parseLong(authentication.getName());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Problem problem = problemRepository.findById(submissionRequestDTO.getProblemId())
                    .orElseThrow(() -> new RuntimeException("Problem not found"));
            Section section = sectionRepository.findById(submissionRequestDTO.getSectionId())
                    .orElseThrow(() -> new RuntimeException("Section not found"));

            validateSubmission(
                    submissionRequestDTO.getProblemId(),
                    submissionRequestDTO.getSectionId(),
                    userId
            );

            String contestId = String.valueOf(section.getId());
            String teamId = enrollmentRepository.findTeamIdByUserIdAndSectionId(user.getId(), section.getId());
            String domjudgeProblemId = problem.getDomjudgeProblemId();

            log.debug("=== Submission Debug Info === userId={} sectionId={} contestId={} teamId={} domjudgeProblemId={}",
                      userId, section.getId(), contestId, teamId, domjudgeProblemId);

            if (teamId == null || teamId.isEmpty()) {
                throw new RuntimeException("현재 수업에 수강신청되어 있지 않습니다. 수강 신청 후 제출해 주세요.");
            }

            File codeFile = CodeExtenstion.StringToFile(
                    submissionRequestDTO.getLanguage(),
                    submissionRequestDTO.getCodeString()
            );

            long submitStart = System.currentTimeMillis();
            String domjudgeSubmissionId = domjudgeService.submitCode(
                    contestId, teamId, domjudgeProblemId,
                    submissionRequestDTO.getLanguage(), codeFile
            );
            long submitDuration = System.currentTimeMillis() - submitStart;
            metric.setSubmitDurationMs(submitDuration);
            metric.setDomjudgeSubmissionId(domjudgeSubmissionId);
            log.info("[METRIC] submit_duration_ms={} domjudge_submission_id={}", submitDuration, domjudgeSubmissionId);

            if (codeFile != null) {
                try {
                    Files.deleteIfExists(codeFile.toPath());
                    log.debug("TmpFile deleted: {}", codeFile.getName());
                } catch (IOException e) {
                    log.error("Failed to delete TmpFile: {}", e.getMessage());
                }
            }

            Submission submission = Submission.builder()
                    .problem(problem)
                    .user(user)
                    .language(submissionRequestDTO.getLanguage())
                    .code(submissionRequestDTO.getCodeString())
                    .submittedAt(LocalDateTime.now(KST_ZONE))
                    .build();
            submission.setSection(section);
            submission.setSubmissionId(domjudgeSubmissionId);
            submissionRepository.save(submission);

            Submission savedSubmission = submissionRepository.findById(submission.getId())
                    .orElseThrow(() -> new RuntimeException("Submission not found"));

            String cid = String.valueOf(savedSubmission.getSection().getId());

            long judgingStart = System.currentTimeMillis();
            PollingResult<SubmissionOutputResponseDTO> pollingResult =
                    pollForResultOutput(cid, savedSubmission.getSubmissionId(), 30);
            long judgingDuration = System.currentTimeMillis() - judgingStart;
            metric.setJudgingDurationMs(judgingDuration);
            metric.setPollingAttempts(pollingResult.getAttempts());
            log.info("[METRIC] judging_duration_ms={} polling_attempts={}", judgingDuration, pollingResult.getAttempts());

            SubmissionOutputResponseDTO judged = pollingResult.getResult();
            savedSubmission.setResult(judged.getResult());
            applyTestCaseCountsFromOutput(savedSubmission, judged);
            submissionRepository.save(savedSubmission);
            maybeClearAssignmentRejectIfAc(savedSubmission);

            metric.setSubmission(savedSubmission);
            long e2eDuration = System.currentTimeMillis() - e2eStart;
            metric.setE2eDurationMs(e2eDuration);
            log.info("[METRIC] e2e_duration_ms={} language={} problemId={} flow=submitAndGetResult",
                     e2eDuration, submissionRequestDTO.getLanguage(), submissionRequestDTO.getProblemId());

            return toSubmissionResponseDTO(savedSubmission);

        } catch (RuntimeException e) {
            metric.setTimedOut(isJudgingTimeoutMessage(e.getMessage()));
            metric.setErrorMessage(e.getMessage() != null
                    ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "unknown");
            metric.setE2eDurationMs(System.currentTimeMillis() - e2eStart);
            throw e;
        } finally {
            try {
                submissionMetricRepository.save(metric);
            } catch (Exception ex) {
                log.error("[METRIC] Failed to save metric: {}", ex.getMessage());
            }
        }
    }

    private void applyTestCaseCountsFromOutput(Submission submission, SubmissionOutputResponseDTO out) {
        if (out == null || out.getOutputList() == null || out.getOutputList().isEmpty()) {
            submission.setPassedTestCases(0);
            submission.setTotalTestCases(0);
            return;
        }
        int total = out.getOutputList().size();
        int passed = (int) out.getOutputList().stream()
                .filter(o -> "correct".equals(o.getResult()))
                .count();
        submission.setPassedTestCases(passed);
        submission.setTotalTestCases(total);
    }

    private static boolean isBlankResult(String result) {
        return result == null || result.trim().isEmpty();
    }

    /**
     * DomJudge 채점이 완료된 output인지 (getQuizResult / 비동기 폴링과 동일 기준)
     */
    private boolean isDomjudgeOutputReady(SubmissionOutputResponseDTO outputDTO) {
        if (outputDTO == null || isBlankResult(outputDTO.getResult())) {
            return false;
        }
        if ("CE".equals(outputDTO.getResult())) {
            return true;
        }
        if (outputDTO.getOutputList() != null && !outputDTO.getOutputList().isEmpty()) {
            return outputDTO.getOutputList().stream()
                    .allMatch(o -> o.getResult() != null && !o.getResult().isEmpty());
        }
        return false;
    }

    private int[] computeTestCaseCounts(SubmissionOutputResponseDTO outputDTO) {
        int passedCount = 0;
        int totalCount = 0;
        if (outputDTO.getOutputList() != null && !outputDTO.getOutputList().isEmpty()) {
            totalCount = outputDTO.getOutputList().size();
            passedCount = (int) outputDTO.getOutputList().stream()
                    .filter(o -> "correct".equals(o.getResult()))
                    .count();
        }
        return new int[]{passedCount, totalCount};
    }

    /**
     * DB에 result 또는 TC 집계가 비어 있으면 DomJudge에서 조회해 보충.
     * 채점이 아직이면 false (기존 데이터는 변경하지 않음).
     *
     * @return DB가 갱신되었으면 true
     */
    public boolean syncSubmissionFromDomjudgeIfNeeded(Submission submission) {
        if (submission == null) {
            return false;
        }
        if (submission.getSubmissionId() == null || submission.getSubmissionId().isBlank()) {
            return false;
        }
        if (submission.getSection() == null) {
            log.warn("sync from DomJudge skipped: submission id={} has no section", submission.getId());
            return false;
        }

        boolean needsResult = isBlankResult(submission.getResult());
        boolean needsTc = submission.getTotalTestCases() == null;
        if (!needsResult && !needsTc) {
            return false;
        }

        String cid = String.valueOf(submission.getSection().getId());
        try {
            SubmissionOutputResponseDTO out =
                    domjudgeService.getResultOutput(cid, submission.getSubmissionId());

            // DomJudge가 아직 채점하지 않은 경우
            if (out == null || isBlankResult(out.getResult())) {
                return false;
            }

            boolean outputReady = isDomjudgeOutputReady(out);

            boolean updated = false;
            if (needsResult) {
                int rows;
                if (!needsTc) {
                    // TC가 이미 DB에 있음: 기존 TC 값 유지하고 result만 저장
                    int passedCount = submission.getPassedTestCases() != null ? submission.getPassedTestCases() : 0;
                    int totalCount = submission.getTotalTestCases();
                    rows = submissionRepository.updateResultIfPending(
                            submission.getId(), out.getResult(), passedCount, totalCount);
                } else if (outputReady) {
                    // TC도 없고 outputList 완전히 준비됨: result + TC 함께 저장
                    int[] counts = computeTestCaseCounts(out);
                    rows = submissionRepository.updateResultIfPending(
                            submission.getId(), out.getResult(), counts[0], counts[1]);
                } else {
                    // TC도 없고 outputList도 없음: result만 저장 (TC는 NULL 유지)
                    rows = submissionRepository.updateResultOnlyIfPending(
                            submission.getId(), out.getResult());
                }
                if (rows > 0) {
                    updated = true;
                    Submission latest = submissionRepository.findById(submission.getId()).orElse(null);
                    if (latest != null) {
                        maybeClearAssignmentRejectIfAc(latest);
                        if (!isBlankResult(latest.getResult())) {
                            finalizeAsyncMetricE2eIfPending(submission.getId());
                        }
                    }
                }
            }

            if (needsTc && outputReady) {
                Submission fresh = submissionRepository.findById(submission.getId()).orElse(submission);
                if (fresh.getTotalTestCases() == null) {
                    applyTestCaseCountsFromOutput(fresh, out);
                    submissionRepository.save(fresh);
                    updated = true;
                }
            }

            if (updated) {
                Submission latest = submissionRepository.findById(submission.getId()).orElse(submission);
                submission.setResult(latest.getResult());
                submission.setPassedTestCases(latest.getPassedTestCases());
                submission.setTotalTestCases(latest.getTotalTestCases());
            }
            return updated;
        } catch (Exception e) {
            log.warn("sync submission from DomJudge failed id={} domjudgeId={}: {}",
                    submission.getId(), submission.getSubmissionId(), e.getMessage());
            return false;
        }
    }

    /**
     * DB에 total_test_cases / result가 없는 경우 DomJudge에서 조회해 저장.
     * 성적 API 조회 시 호출 — 이후에는 DB 값을 사용.
     */
    public void backfillTestCaseCountsIfMissing(Submission submission) {
        syncSubmissionFromDomjudgeIfNeeded(submission);
    }

    // for with output.

    /**
     * DomJudge 제출 + 테스트케이스별 output 폴링만 수행 (DB에 Submission 행을 만들지 않음).
     * 퀴즈 제출({@link #submitQuizCode}) 및 과제 「테스트하기」({@link #submitAndGetResultOutput})에서 공통 사용.
     */
    private SubmissionOutputResponseDTO submitAndPollOutputOnly(
            Authentication authentication,
            SubmissionAuthDTO submissionRequestDTO,
            SubmissionMetric metric) {
        long e2eStart = System.currentTimeMillis();
        Long userId = Long.parseLong(authentication.getName());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Problem problem = problemRepository.findById(submissionRequestDTO.getProblemId())
                .orElseThrow(() -> new RuntimeException("Problem not found"));
        Section section = sectionRepository.findById(submissionRequestDTO.getSectionId())
                .orElseThrow(() -> new RuntimeException("Section not found"));

        validateSubmission(
                submissionRequestDTO.getProblemId(),
                submissionRequestDTO.getSectionId(),
                userId
        );

        String contestId = String.valueOf(section.getId());
        String teamId = enrollmentRepository.findTeamIdByUserIdAndSectionId(user.getId(), section.getId());
        String domjudgeProblemId = problem.getDomjudgeProblemId();

        if (teamId == null || teamId.isEmpty()) {
            throw new RuntimeException("현재 수업에 수강신청되어 있지 않습니다. 수강 신청 후 제출해 주세요.");
        }

        File codeFile = CodeExtenstion.StringToFile(
                submissionRequestDTO.getLanguage(),
                submissionRequestDTO.getCodeString()
        );

        long submitStart = System.currentTimeMillis();
        String domjudgeSubmissionId = domjudgeService.submitCode(
                contestId, teamId, domjudgeProblemId,
                submissionRequestDTO.getLanguage(), codeFile
        );
        long submitDuration = System.currentTimeMillis() - submitStart;
        metric.setSubmitDurationMs(submitDuration);
        metric.setDomjudgeSubmissionId(domjudgeSubmissionId);
        log.info("[METRIC] submit_duration_ms={} domjudge_submission_id={}", submitDuration, domjudgeSubmissionId);

        if (codeFile != null) {
            try {
                Files.deleteIfExists(codeFile.toPath());
                log.debug("TmpFile deleted: {}", codeFile.getName());
            } catch (IOException e) {
                log.error("Failed to delete TmpFile: {}", e.getMessage());
            }
        }

        String cid = String.valueOf(section.getId());

        long judgingStart = System.currentTimeMillis();
        PollingResult<SubmissionOutputResponseDTO> pollingResult =
                pollForResultOutput(cid, domjudgeSubmissionId, 30);
        long judgingDuration = System.currentTimeMillis() - judgingStart;
        metric.setJudgingDurationMs(judgingDuration);
        metric.setPollingAttempts(pollingResult.getAttempts());
        log.info("[METRIC] judging_duration_ms={} polling_attempts={}", judgingDuration, pollingResult.getAttempts());

        SubmissionOutputResponseDTO responseDTO = pollingResult.getResult();

        long e2eDuration = System.currentTimeMillis() - e2eStart;
        metric.setE2eDurationMs(e2eDuration);
        log.info("[METRIC] e2e_duration_ms={} language={} problemId={} flow=submitAndPollOutputOnly",
                e2eDuration, submissionRequestDTO.getLanguage(), submissionRequestDTO.getProblemId());

        return SubmissionOutputResponseDTO.builder()
                .problemId(problem.getId())
                .submissionId(domjudgeSubmissionId)
                .language(submissionRequestDTO.getLanguage())
                .submittedAt(LocalDateTime.now(KST_ZONE))
                .result(responseDTO.getResult())
                .outputList(responseDTO.getOutputList())
                .outputCompile(responseDTO.getOutputCompile())
                .sectionId(section.getId())
                .build();
    }

    /**
     * 과제 「테스트하기」: DomJudge 채점 + 테스트케이스별 output 반환.
     * {@link Submission} 행은 만들지 않음 — 성적·최신 제출 집계는 {@link #submitAndGetResult}만 사용.
     */
    public SubmissionOutputResponseDTO submitAndGetResultOutput(Authentication authentication, SubmissionAuthDTO submissionRequestDTO) {
        long e2eStart = System.currentTimeMillis();
        SubmissionMetric metric = SubmissionMetric.builder()
                .measuredAt(LocalDateTime.now())
                .language(submissionRequestDTO.getLanguage())
                .problemId(submissionRequestDTO.getProblemId())
                .sectionId(submissionRequestDTO.getSectionId())
                .flowType("submitAndGetResultOutput")
                .timedOut(false)
                .build();

        try {
            return submitAndPollOutputOnly(authentication, submissionRequestDTO, metric);
        } catch (RuntimeException e) {
            metric.setTimedOut(isJudgingTimeoutMessage(e.getMessage()));
            metric.setErrorMessage(e.getMessage() != null
                    ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "unknown");
            metric.setE2eDurationMs(System.currentTimeMillis() - e2eStart);
            throw e;
        } finally {
            try {
                submissionMetricRepository.save(metric);
            } catch (Exception ex) {
                log.error("[METRIC] Failed to save metric: {}", ex.getMessage());
            }
        }
    }

    /**
     * 퀴즈 전용 제출 — 폴링만 공유하고 DB에는 퀴즈용 Submission 한 번만 저장
     */
    public SubmissionQuizResponseDTO submitQuizCode(Authentication authentication, SubmissionAuthDTO submissionRequestDTO) {
        validateQuizLanguage(submissionRequestDTO.getLanguage());
        long e2eStart = System.currentTimeMillis();
        SubmissionMetric metric = SubmissionMetric.builder()
                .measuredAt(LocalDateTime.now())
                .language(submissionRequestDTO.getLanguage())
                .problemId(submissionRequestDTO.getProblemId())
                .sectionId(submissionRequestDTO.getSectionId())
                .flowType("submitQuizCode")
                .timedOut(false)
                .build();
        try {
            SubmissionOutputResponseDTO outputDTO =
                    submitAndPollOutputOnly(authentication, submissionRequestDTO, metric);

            Long userId = Long.parseLong(authentication.getName());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Problem problem = problemRepository.findById(submissionRequestDTO.getProblemId())
                    .orElseThrow(() -> new RuntimeException("Problem not found"));
            Section section = sectionRepository.findById(submissionRequestDTO.getSectionId())
                    .orElseThrow(() -> new RuntimeException("Section not found"));

            Submission submission = Submission.builder()
                .problem(problem)
                .user(user)
                .language(submissionRequestDTO.getLanguage())
                .code(submissionRequestDTO.getCodeString())
                .submittedAt(LocalDateTime.now(KST_ZONE))
                .result(outputDTO.getResult())
                .build();
            submission.setSection(section);
            submission.setSubmissionId(outputDTO.getSubmissionId());

            int passedCount = 0;
            int totalCount = 0;
            int points = 1;
            double score = 0.0;

            if (outputDTO.getOutputList() != null && !outputDTO.getOutputList().isEmpty()) {
                totalCount = outputDTO.getOutputList().size();
                passedCount = (int) outputDTO.getOutputList().stream()
                        .filter(o -> "correct".equals(o.getResult()))
                        .count();
            }

            Optional<QuizProblem> quizProblemOpt = quizProblemRepository.findByProblemId(problem.getId())
                    .stream()
                    .filter(qp -> qp.getQuiz().getSection().getId().equals(section.getId()))
                    .findFirst();
            if (quizProblemOpt.isPresent() && quizProblemOpt.get().getPoints() != null && quizProblemOpt.get().getPoints() > 0) {
                points = quizProblemOpt.get().getPoints();
            }

            if (totalCount > 0) {
                score = (passedCount * 1.0 / totalCount) * points;
            }

            submission.setPassedTestCases(passedCount);
            submission.setTotalTestCases(totalCount);
            submissionRepository.save(submission);

            return SubmissionQuizResponseDTO.builder()
                    .problemId(outputDTO.getProblemId())
                    .sectionId(outputDTO.getSectionId())
                    .language(outputDTO.getLanguage())
                    .submissionId(outputDTO.getSubmissionId())
                    .result(outputDTO.getResult())
                    .outputList(outputDTO.getOutputList())
                    .outputCompile(outputDTO.getOutputCompile())
                    .submittedAt(outputDTO.getSubmittedAt())
                    .passedCount(passedCount)
                    .totalCount(totalCount)
                    .points(points)
                    .score(score)
                    .build();
        } catch (RuntimeException e) {
            metric.setTimedOut(isJudgingTimeoutMessage(e.getMessage()));
            metric.setErrorMessage(e.getMessage() != null
                    ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "unknown");
            metric.setE2eDurationMs(System.currentTimeMillis() - e2eStart);
            throw e;
        } finally {
            try {
                submissionMetricRepository.save(metric);
            } catch (Exception ex) {
                log.error("[METRIC] Failed to save metric: {}", ex.getMessage());
            }
        }
    }

    // =========================================================================
    // 과제 비동기 제출 — Submit / Result 분리 API (클라이언트 폴링)
    // =========================================================================

    /**
     * 과제 비동기 제출: DOMjudge에 코드를 제출하고 pending Submission을 DB에 저장한 뒤 즉시 반환.
     * 채점 결과는 GET /api/submissions/result/{submissionDbId} 로 클라이언트가 폴링.
     */
    public AsyncSubmitResponseDTO submitAssignmentAsync(Authentication authentication, SubmissionAuthDTO submissionRequestDTO) {
        long handlerStartMs = System.currentTimeMillis();
        Long userId = Long.parseLong(authentication.getName());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Problem problem = problemRepository.findById(submissionRequestDTO.getProblemId())
                .orElseThrow(() -> new RuntimeException("Problem not found"));
        Section section = sectionRepository.findById(submissionRequestDTO.getSectionId())
                .orElseThrow(() -> new RuntimeException("Section not found"));

        validateSubmission(
                submissionRequestDTO.getProblemId(),
                submissionRequestDTO.getSectionId(),
                userId
        );

        String contestId = String.valueOf(section.getId());
        String teamId = enrollmentRepository.findTeamIdByUserIdAndSectionId(user.getId(), section.getId());
        String domjudgeProblemId = problem.getDomjudgeProblemId();

        if (teamId == null || teamId.isEmpty()) {
            throw new RuntimeException("현재 수업에 수강신청되어 있지 않습니다. 수강 신청 후 제출해 주세요.");
        }

        File codeFile = CodeExtenstion.StringToFile(
                submissionRequestDTO.getLanguage(),
                submissionRequestDTO.getCodeString()
        );

        long submitCodeStartMs = System.currentTimeMillis();
        String domjudgeSubmissionId = domjudgeService.submitCode(
                contestId, teamId, domjudgeProblemId,
                submissionRequestDTO.getLanguage(), codeFile
        );
        long submitCodeEndMs = System.currentTimeMillis();
        log.info("[ASYNC] Assignment DOMjudge submission created: {}", domjudgeSubmissionId);

        if (codeFile != null) {
            try {
                Files.deleteIfExists(codeFile.toPath());
                log.debug("TmpFile deleted: {}", codeFile.getName());
            } catch (IOException e) {
                log.error("Failed to delete TmpFile: {}", e.getMessage());
            }
        }

        Submission submission = Submission.builder()
                .problem(problem)
                .user(user)
                .language(submissionRequestDTO.getLanguage())
                .code(submissionRequestDTO.getCodeString())
                .submittedAt(LocalDateTime.now(KST_ZONE))
                .build();
        submission.setSection(section);
        submission.setSubmissionId(domjudgeSubmissionId);
        Submission saved = submissionRepository.save(submission);

        persistAsyncSubmitMetric(saved, submissionRequestDTO, handlerStartMs, submitCodeStartMs, submitCodeEndMs,
                FLOW_ASSIGNMENT_ASYNC);

        return AsyncSubmitResponseDTO.builder()
                .submissionDbId(saved.getId())
                .submissionId(domjudgeSubmissionId)
                .problemId(problem.getId())
                .sectionId(section.getId())
                .language(submissionRequestDTO.getLanguage())
                .submittedAt(saved.getSubmittedAt())
                .build();
    }

    /**
     * SSE 스트리밍 세션 생성.
     * 소유권 검증 후 SseEmitter를 등록하고 @Async 폴링 태스크를 시작한 뒤 emitter를 반환한다.
     * 클라이언트는 text/event-stream 응답으로 채점 결과를 실시간 수신한다.
     */
    @Transactional(readOnly = true)
    public SseEmitter createSseStream(Authentication authentication, Long submissionDbId) {
        Long userId = Long.parseLong(authentication.getName());

        Submission submission = submissionRepository.findById(submissionDbId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        if (!submission.getUser().getId().equals(userId) &&
                !sectionRoleService.isManager(userId, submission.getSection().getId())) {
            throw new IllegalArgumentException("해당 제출에 접근할 권한이 없습니다.");
        }

        SseEmitter emitter = submissionSseService.register(submissionDbId);
        submissionPollingTask.pollAndStream(submissionDbId);
        log.info("[SSE] stream created submissionDbId={} userId={}", submissionDbId, userId);
        return emitter;
    }

    /**
     * 과제 채점 결과 조회 — DB에 결과가 있으면 즉시 반환, 없으면 DOMjudge에 1회 조회.
     * 채점 중이면 {@code null} 반환 (컨트롤러에서 204 No Content 처리).
     */
    @Transactional
    public SubmissionResponseDTO getAssignmentResult(Authentication authentication, Long submissionDbId) {
        Long userId = Long.parseLong(authentication.getName());

        Submission submission = submissionRepository.findById(submissionDbId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        if (!submission.getUser().getId().equals(userId) &&
                !sectionRoleService.isManager(userId, submission.getSection().getId())) {
            throw new IllegalArgumentException("해당 제출에 접근할 권한이 없습니다.");
        }

        // 이미 결과가 저장된 경우 즉시 반환 (DOMjudge 호출 없음)
        if (submission.getResult() != null && !submission.getResult().isEmpty()) {
            maybeClearAssignmentRejectIfAc(submission);
            return toSubmissionResponseDTO(submission);
        }

        // DOMjudge에 1회 조회
        String cid = String.valueOf(submission.getSection().getId());
        String domjudgeSubmissionId = submission.getSubmissionId();

        long pollStartMs = System.currentTimeMillis();
        SubmissionOutputResponseDTO outputDTO = null;
        try {
            outputDTO = domjudgeService.getResultOutput(cid, domjudgeSubmissionId);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return null;
            }
            log.warn("[ASYNC] getResultOutput client error status={} submissionId={}",
                    e.getStatusCode(), domjudgeSubmissionId);
            throw new RuntimeException("채점 서버에서 오류가 발생했습니다.", e);
        } catch (ResourceAccessException e) {
            log.warn("[ASYNC] getResultOutput network error submissionId={} error={}",
                    domjudgeSubmissionId, e.getMessage());
            return null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("채점 결과를 해석하는 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            log.warn("[ASYNC] getResultOutput unexpected error submissionId={} error={}",
                    domjudgeSubmissionId, e.getMessage());
            return null;
        } finally {
            recordAsyncPollSample(submissionDbId, System.currentTimeMillis() - pollStartMs);
        }

        if (outputDTO == null || outputDTO.getResult() == null || outputDTO.getResult().isEmpty()) {
            return null;
        }

        // CE는 outputList 없이도 완료 판정
        boolean isReady;
        if ("CE".equals(outputDTO.getResult())) {
            isReady = true;
        } else if (outputDTO.getOutputList() != null && !outputDTO.getOutputList().isEmpty()) {
            isReady = outputDTO.getOutputList().stream()
                    .allMatch(o -> o.getResult() != null && !o.getResult().isEmpty());
        } else {
            return null;
        }

        if (!isReady) {
            return null;
        }

        // 테스트케이스 수·정답 수 계산
        int passedCount = 0;
        int totalCount = 0;
        if (outputDTO.getOutputList() != null && !outputDTO.getOutputList().isEmpty()) {
            totalCount = outputDTO.getOutputList().size();
            passedCount = (int) outputDTO.getOutputList().stream()
                    .filter(o -> "correct".equals(o.getResult()))
                    .count();
        }

        submissionRepository.updateResultIfPending(
                submissionDbId, outputDTO.getResult(), passedCount, totalCount);

        Submission latest = submissionRepository.findById(submissionDbId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));
        if (latest.getResult() != null && !latest.getResult().isEmpty()) {
            finalizeAsyncMetricE2eIfPending(submissionDbId);
        }
        maybeClearAssignmentRejectIfAc(latest);

        return toSubmissionResponseDTO(latest);
    }

    // =========================================================================
    // Phase 2: Submit / Result 분리 API (클라이언트 폴링) — 퀴즈 전용
    // =========================================================================

    /**
     * Phase 2: DOMjudge에 코드 제출 후 pending Submission을 DB에 저장하고 즉시 반환.
     * 채점 결과는 GET /result/{submissionDbId} 로 클라이언트가 폴링.
     */
    public AsyncSubmitResponseDTO submitQuizCodeAsync(Authentication authentication, SubmissionAuthDTO submissionRequestDTO) {
        validateQuizLanguage(submissionRequestDTO.getLanguage());
        long handlerStartMs = System.currentTimeMillis();

        Long userId = Long.parseLong(authentication.getName());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Problem problem = problemRepository.findById(submissionRequestDTO.getProblemId())
                .orElseThrow(() -> new RuntimeException("Problem not found"));
        Section section = sectionRepository.findById(submissionRequestDTO.getSectionId())
                .orElseThrow(() -> new RuntimeException("Section not found"));

        validateSubmission(
                submissionRequestDTO.getProblemId(),
                submissionRequestDTO.getSectionId(),
                userId
        );

        String contestId = String.valueOf(section.getId());
        String teamId = enrollmentRepository.findTeamIdByUserIdAndSectionId(user.getId(), section.getId());
        String domjudgeProblemId = problem.getDomjudgeProblemId();

        if (teamId == null || teamId.isEmpty()) {
            throw new RuntimeException("현재 수업에 수강신청되어 있지 않습니다. 수강 신청 후 제출해 주세요.");
        }

        File codeFile = CodeExtenstion.StringToFile(
                submissionRequestDTO.getLanguage(),
                submissionRequestDTO.getCodeString()
        );

        long submitCodeStartMs = System.currentTimeMillis();
        String domjudgeSubmissionId = domjudgeService.submitCode(
                contestId, teamId, domjudgeProblemId,
                submissionRequestDTO.getLanguage(), codeFile
        );
        long submitCodeEndMs = System.currentTimeMillis();
        log.info("[PHASE2] DOMjudge submission created: {}", domjudgeSubmissionId);

        if (codeFile != null) {
            try {
                Files.deleteIfExists(codeFile.toPath());
                log.debug("TmpFile deleted: {}", codeFile.getName());
            } catch (IOException e) {
                log.error("Failed to delete TmpFile: {}", e.getMessage());
            }
        }

        Submission submission = Submission.builder()
                .problem(problem)
                .user(user)
                .language(submissionRequestDTO.getLanguage())
                .code(submissionRequestDTO.getCodeString())
                .submittedAt(LocalDateTime.now(KST_ZONE))
                .build();
        submission.setSection(section);
        submission.setSubmissionId(domjudgeSubmissionId);
        Submission saved = submissionRepository.save(submission);

        persistAsyncSubmitMetric(saved, submissionRequestDTO, handlerStartMs, submitCodeStartMs, submitCodeEndMs,
                FLOW_QUIZ_ASYNC);

        return AsyncSubmitResponseDTO.builder()
                .submissionDbId(saved.getId())
                .submissionId(domjudgeSubmissionId)
                .problemId(problem.getId())
                .sectionId(section.getId())
                .language(submissionRequestDTO.getLanguage())
                .submittedAt(saved.getSubmittedAt())
                .build();
    }

    /**
     * Phase 2: 채점 결과 조회 — DB에 결과가 있으면 즉시 반환, 없으면 DOMjudge에 1회 조회.
     * 채점 중이면 {@code null} 반환 (컨트롤러에서 204 No Content 처리).
     */
    @Transactional
    public SubmissionQuizResponseDTO getQuizResult(Authentication authentication, Long submissionDbId) {
        Long userId = Long.parseLong(authentication.getName());

        Submission submission = submissionRepository.findById(submissionDbId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        // 본인 제출 또는 관리자/튜터만 조회 가능
        if (!submission.getUser().getId().equals(userId) &&
                !sectionRoleService.isManager(userId, submission.getSection().getId())) {
            throw new IllegalArgumentException("해당 제출에 접근할 권한이 없습니다.");
        }

        // 이미 결과가 저장된 경우 즉시 반환 (DOMjudge 호출 없음)
        if (submission.getResult() != null && !submission.getResult().isEmpty()) {
            maybeClearAssignmentRejectIfAc(submission);
            return buildQuizResponseFromSubmission(submission, null);
        }

        // DOMjudge에 1회 조회
        String cid = String.valueOf(submission.getSection().getId());
        String domjudgeSubmissionId = submission.getSubmissionId();

        long pollStartMs = System.currentTimeMillis();
        SubmissionOutputResponseDTO outputDTO = null;
        try {
            outputDTO = domjudgeService.getResultOutput(cid, domjudgeSubmissionId);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return null; // 아직 채점 큐에 없음 → 채점 중
            }
            log.warn("[PHASE2] getResultOutput client error status={} submissionId={}",
                    e.getStatusCode(), domjudgeSubmissionId);
            throw new RuntimeException("채점 서버에서 오류가 발생했습니다.", e);
        } catch (ResourceAccessException e) {
            log.warn("[PHASE2] getResultOutput network error submissionId={} error={}",
                    domjudgeSubmissionId, e.getMessage());
            return null; // 일시적 네트워크 오류 → 채점 중으로 처리
        } catch (JsonProcessingException e) {
            throw new RuntimeException("채점 결과를 해석하는 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            log.warn("[PHASE2] getResultOutput unexpected error submissionId={} error={}",
                    domjudgeSubmissionId, e.getMessage());
            return null;
        } finally {
            recordAsyncPollSample(submissionDbId, System.currentTimeMillis() - pollStartMs);
        }

        if (!isDomjudgeOutputReady(outputDTO)) {
            return null; // 아직 채점 중
        }

        int[] counts = computeTestCaseCounts(outputDTO);
        int passedCount = counts[0];
        int totalCount = counts[1];

        // 조건부 UPDATE (레이스 컨디션 방지: result IS NULL인 경우만 업데이트)
        submissionRepository.updateResultIfPending(
                submissionDbId, outputDTO.getResult(), passedCount, totalCount);

        // 최신 Submission 재조회 (다른 요청이 먼저 저장했을 수도 있음)
        Submission latest = submissionRepository.findById(submissionDbId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));
        if (latest.getResult() != null && !latest.getResult().isEmpty()) {
            finalizeAsyncMetricE2eIfPending(submissionDbId);
        }
        maybeClearAssignmentRejectIfAc(latest);

        return buildQuizResponseFromSubmission(latest, outputDTO.getOutputList());
    }

    private SubmissionQuizResponseDTO buildQuizResponseFromSubmission(
            Submission submission, List<Output> outputList) {
        int passedCount = submission.getPassedTestCases() != null ? submission.getPassedTestCases() : 0;
        int totalCount = submission.getTotalTestCases() != null ? submission.getTotalTestCases() : 0;

        int points = 1;
        double score = 0.0;

        Optional<QuizProblem> quizProblemOpt = quizProblemRepository
                .findByProblemId(submission.getProblem().getId())
                .stream()
                .filter(qp -> qp.getQuiz().getSection().getId().equals(submission.getSection().getId()))
                .findFirst();
        if (quizProblemOpt.isPresent() && quizProblemOpt.get().getPoints() != null
                && quizProblemOpt.get().getPoints() > 0) {
            points = quizProblemOpt.get().getPoints();
        }
        if (totalCount > 0) {
            score = (passedCount * 1.0 / totalCount) * points;
        }

        return SubmissionQuizResponseDTO.builder()
                .problemId(submission.getProblem().getId())
                .sectionId(submission.getSection().getId())
                .language(submission.getLanguage())
                .submissionId(submission.getSubmissionId())
                .result(submission.getResult())
                .outputList(outputList)
                .submittedAt(submission.getSubmittedAt())
                .passedCount(passedCount)
                .totalCount(totalCount)
                .points(points)
                .score(score)
                .build();
    }

    private void validateQuizLanguage(String language) {
        if (language == null || !"c".equalsIgnoreCase(language.trim())) {
            throw new IllegalArgumentException("코딩테스트는 C 언어만 제출할 수 있습니다.");
        }
    }

    private static boolean isJudgingTimeoutMessage(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("not available after") || message.contains("시간이 초과");
    }

    private PollingResult<SubmissionOutputResponseDTO> pollForResultOutput(String cid, String submissionId, int maxWaitSeconds) {
        long deadlineMs = System.currentTimeMillis() + maxWaitSeconds * 1000L;
        int attempts = 0;
        long backoffMs = POLL_INITIAL_BACKOFF_MS;

        while (System.currentTimeMillis() < deadlineMs) {
            attempts++;
            long apiStart = System.currentTimeMillis();
            try {
                SubmissionOutputResponseDTO result = domjudgeService.getResultOutput(cid, submissionId);
                long apiDuration = System.currentTimeMillis() - apiStart;
                log.debug("[METRIC] getResultOutput api_ms={} attempt={} submissionId={}",
                          apiDuration, attempts, submissionId);

                if (result != null && !result.getResult().isEmpty()) {
                    if (result.getOutputList() != null && !result.getOutputList().isEmpty()) {
                        boolean allCompleted = result.getOutputList().stream()
                            .allMatch(output -> output.getResult() != null && !output.getResult().isEmpty());

                        if (allCompleted) {
                            return new PollingResult<>(result, attempts);
                        }
                    } else {
                        if ("CE".equals(result.getResult())) {
                            return new PollingResult<>(result, attempts);
                        }
                    }
                }
            } catch (HttpClientErrorException e) {
                long apiDuration = System.currentTimeMillis() - apiStart;
                if (e.getStatusCode().value() == 404) {
                    log.debug("[METRIC] getResultOutput not ready (404) api_ms={} attempt={} submissionId={}",
                              apiDuration, attempts, submissionId);
                } else {
                    log.warn("[METRIC] getResultOutput client error api_ms={} attempt={} status={} submissionId={}",
                             apiDuration, attempts, e.getStatusCode(), submissionId);
                    throw new RuntimeException("채점 서버에서 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", e);
                }
            } catch (ResourceAccessException e) {
                long apiDuration = System.currentTimeMillis() - apiStart;
                log.warn("[METRIC] getResultOutput network error api_ms={} attempt={} submissionId={} error={}",
                         apiDuration, attempts, submissionId, e.getMessage());
            } catch (HttpServerErrorException e) {
                long apiDuration = System.currentTimeMillis() - apiStart;
                log.warn("[METRIC] getResultOutput server error api_ms={} attempt={} status={} submissionId={}",
                         apiDuration, attempts, e.getStatusCode(), submissionId);
            } catch (JsonProcessingException e) {
                log.warn("[METRIC] getResultOutput parse error attempt={} submissionId={} error={}",
                         attempts, submissionId, e.getMessage());
                throw new RuntimeException("채점 결과를 해석하는 중 오류가 발생했습니다.", e);
            } catch (Exception e) {
                long apiDuration = System.currentTimeMillis() - apiStart;
                log.warn("[METRIC] getResultOutput unexpected error api_ms={} attempt={} submissionId={} error={}",
                         apiDuration, attempts, submissionId, e.getMessage());
            }

            long remaining = deadlineMs - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            long sleepMs = Math.min(backoffMs, remaining);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("채점이 중단되었습니다. 잠시 후 다시 시도해 주세요.", e);
            }
            backoffMs = Math.min(backoffMs * 2, POLL_MAX_BACKOFF_MS);
        }

        throw new RuntimeException("채점 결과를 가져오는 데 시간이 초과되었습니다. 제출은 완료되었을 수 있으니 제출 목록에서 확인해 주세요.");
    }

    public String getUserLatestSubmission(Authentication authentication, Long problemId,Long sectionId, String language ) {
        Long userId = Long.parseLong(authentication.getName());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new RuntimeException("Problem not found"));

        String result = submissionRepository.getUserLastSubmission(userId, problemId,sectionId,language)
                .orElse(null);

        return result;
    }

    // =========================================================================
    // 테스트하기 비동기 — SSE 스트리밍 (DB 저장 없음)
    // =========================================================================

    /**
     * 테스트하기 비동기 제출: DOMjudge에만 제출하고 sessionKey(= domjudgeSubmissionId)를 반환.
     * DB에 Submission을 저장하지 않으며, 클라이언트는
     * GET /api/submissions/test/stream/{sessionKey} 로 SSE 연결해 output 결과를 수신한다.
     */
    public TestSubmitResponseDTO submitCodeForTestAsync(Authentication authentication, SubmissionAuthDTO dto) {
        Long userId = Long.parseLong(authentication.getName());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Problem problem = problemRepository.findById(dto.getProblemId())
                .orElseThrow(() -> new RuntimeException("Problem not found"));
        Section section = sectionRepository.findById(dto.getSectionId())
                .orElseThrow(() -> new RuntimeException("Section not found"));

        validateSubmission(dto.getProblemId(), dto.getSectionId(), userId);

        String contestId = String.valueOf(section.getId());
        String teamId = enrollmentRepository.findTeamIdByUserIdAndSectionId(user.getId(), section.getId());
        String domjudgeProblemId = problem.getDomjudgeProblemId();

        if (teamId == null || teamId.isEmpty()) {
            throw new RuntimeException("현재 수업에 수강신청되어 있지 않습니다. 수강 신청 후 제출해 주세요.");
        }

        File codeFile = CodeExtenstion.StringToFile(dto.getLanguage(), dto.getCodeString());

        String domjudgeSubmissionId = domjudgeService.submitCode(
                contestId, teamId, domjudgeProblemId, dto.getLanguage(), codeFile);
        log.info("[TEST-SSE] DOMjudge submission created: {}", domjudgeSubmissionId);

        if (codeFile != null) {
            try {
                java.nio.file.Files.deleteIfExists(codeFile.toPath());
            } catch (java.io.IOException e) {
                log.error("Failed to delete TmpFile: {}", e.getMessage());
            }
        }

        return TestSubmitResponseDTO.builder()
                .sessionKey(domjudgeSubmissionId)
                .sectionId(section.getId())
                .problemId(problem.getId())
                .language(dto.getLanguage())
                .submittedAt(java.time.LocalDateTime.now(KST_ZONE))
                .build();
    }

    /**
     * 테스트하기 SSE 스트림 생성.
     * 인증 확인 후 emitter를 등록하고 @Async 폴링 태스크를 시작한다.
     */
    public SseEmitter createTestOutputStream(Authentication authentication, String sessionKey, Long sectionId) {
        Long.parseLong(authentication.getName()); // 인증 확인 (파싱 실패 시 예외)
        SseEmitter emitter = testOutputSseService.register(sessionKey);
        testOutputPollingTask.pollAndStream(sessionKey, String.valueOf(sectionId));
        log.info("[TEST-SSE] stream created sessionKey={}", sessionKey);
        return emitter;
    }

    /**
     * 문제가 속한 과제/퀴즈를 찾고, 마감일 및 비활성화 상태를 체크
     * @param problemId 문제 ID
     * @param sectionId 분반 ID
     * @param userId 사용자 ID
     * @throws IllegalArgumentException 마감일이 지났거나 비활성화된 경우 (학생만)
     * 
     * 비고:
     * - 관리자/튜터는 비활성화된 과제/퀴즈도 제출 가능
     * - 학생은 마감일이 지나면 제출 불가
     * - 학생은 퀴즈 상태가 PAUSED·ENDED이거나 종료 시각이 지나면 제출 불가
     * - 관리자/튜터는 퀴즈 종료 후에도 제출 가능 (과제는 마감일 체크)
     */
    private void validateSubmission(Long problemId, Long sectionId, Long userId) {
        // 관리자인지 확인
        boolean isManager = sectionRoleService.isManager(userId, sectionId);
        
        // 섹션 기준으로 매핑을 조회해 문제-분반 연결 검증을 안정화
        List<AssignmentProblem> assignmentProblems =
                assignmentProblemRepository.findByProblemIdAndSectionId(problemId, sectionId);
        List<QuizProblem> quizProblems =
                quizProblemRepository.findByProblemIdAndSectionId(problemId, sectionId);
        
        // 과제와 퀴즈 모두에 속하지 않은 경우
        if (assignmentProblems.isEmpty() && quizProblems.isEmpty()) {
            throw new IllegalArgumentException("해당 문제는 과제나 코딩 테스트에 속해있지 않습니다");
        }
        
        LocalDateTime now = LocalDateTime.now(UTC_ZONE);
        boolean assignmentValid = false;
        boolean quizValid = false;
        
        // 과제 검증
        if (!assignmentProblems.isEmpty()) {
            Assignment targetAssignment = null;
            for (AssignmentProblem ap : assignmentProblems) {
                Assignment assignment = ap.getAssignment();
                if (assignment.getSection().getId().equals(sectionId)) {
                    targetAssignment = assignment;
                    break;
                }
            }
            
            if (targetAssignment != null) {
                // 학생이고 과제가 비활성화되어 있으면 제출 불가
                if (!isManager && !targetAssignment.getActive()) {
                    throw new IllegalArgumentException("해당 과제는 비활성화되어 있어 제출할 수 없습니다");
                }
                // 마감일 지나도 제출 허용 → 지각 제출로 처리 (isOnTime=false, 성적 화면에서 △ 표시)
                assignmentValid = true;
            }
        }
        
        // 퀴즈 검증
        if (!quizProblems.isEmpty()) {
            List<Quiz> quizzesInSection = quizProblems.stream()
                    .map(QuizProblem::getQuiz)
                    .filter(q -> q.getSection().getId().equals(sectionId))
                    .collect(Collectors.toList());

            if (!quizzesInSection.isEmpty()) {
                if (isManager) {
                    quizValid = true;
                } else {
                    boolean hasActiveQuiz = false;
                    boolean hasRunningOrWaitingQuiz = false;

                    for (Quiz quiz : quizzesInSection) {
                        if (!Boolean.TRUE.equals(quiz.getActive())) {
                            continue;
                        }
                        hasActiveQuiz = true;

                        if (quiz.getStatus() == Quiz.QuizStatus.PAUSED) {
                            continue;
                        }

                        if (quiz.getStatus() == Quiz.QuizStatus.ENDED) {
                            continue;
                        }

                        if (quiz.getEndTime() == null || !now.isAfter(quiz.getEndTime())) {
                            quizValid = true;
                            hasRunningOrWaitingQuiz = true;
                            break;
                        }
                    }

                    if (!quizValid) {
                        if (!hasActiveQuiz) {
                            throw new IllegalArgumentException("해당 코딩 테스트는 비활성화되어 있어 제출할 수 없습니다");
                        }
                        if (!hasRunningOrWaitingQuiz && quizzesInSection.stream().anyMatch(q -> q.getStatus() == Quiz.QuizStatus.PAUSED)) {
                            throw new IllegalArgumentException("코딩 테스트가 일시정지 상태입니다. 진행이 재개될 때까지 제출할 수 없습니다");
                        }
                        if (!hasRunningOrWaitingQuiz && quizzesInSection.stream().anyMatch(q ->
                                Boolean.TRUE.equals(q.getActive()) && q.getStatus() == Quiz.QuizStatus.ENDED)) {
                            throw new IllegalArgumentException("코딩 테스트가 종료되어 제출할 수 없습니다");
                        }
                        throw new IllegalArgumentException("코딩 테스트 시간이 종료되었습니다");
                    }
                }
            }
        }
        
        // 과제와 퀴즈 모두에 속하지 않은 경우
        if (!assignmentValid && !quizValid) {
            throw new IllegalArgumentException("해당 문제는 이 분반의 과제나 코딩 테스트에 속해있지 않습니다");
        }
    }

}
