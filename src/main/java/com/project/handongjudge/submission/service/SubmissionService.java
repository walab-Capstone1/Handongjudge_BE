package com.project.handongjudge.submission.service;

import com.project.handongjudge.section.entity.Contest;
import com.project.handongjudge.section.repository.ContestRepository;
import com.project.handongjudge.submission.dto.SubmissionOutputResponseDTO;
import com.project.handongjudge.submission.dto.SubmissionRequestDTO;
import com.project.handongjudge.submission.dto.SubmissionResponseDTO;           
import com.project.handongjudge.submission.dto.SubmissionAuthDTO;
import com.project.handongjudge.submission.entity.Output;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
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
import com.project.handongjudge.submission.repository.SubmissionMetricRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

import com.project.handongjudge.domjudge.service.DomjudgeService;
import lombok.AllArgsConstructor;
import lombok.Getter;



@RequiredArgsConstructor
@Service
public class SubmissionService {
    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);
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
                .code(submissionRequestDTO.getCode().getOriginalFilename())                .submittedAt(LocalDateTime.now())
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

    public SubmissionResponseDTO submitAndGetResult(Authentication authentication, SubmissionAuthDTO submissionRequestDTO) {
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
                    .submittedAt(LocalDateTime.now())
                    .build();
            submission.setSection(section);
            submission.setSubmissionId(domjudgeSubmissionId);
            submissionRepository.save(submission);

            Submission savedSubmission = submissionRepository.findById(submission.getId())
                    .orElseThrow(() -> new RuntimeException("Submission not found"));

            String cid = String.valueOf(savedSubmission.getSection().getId());

            long judgingStart = System.currentTimeMillis();
            PollingResult<String> pollingResult = pollForResult(cid, savedSubmission.getSubmissionId(), 30);
            long judgingDuration = System.currentTimeMillis() - judgingStart;
            metric.setJudgingDurationMs(judgingDuration);
            metric.setPollingAttempts(pollingResult.getAttempts());
            log.info("[METRIC] judging_duration_ms={} polling_attempts={}", judgingDuration, pollingResult.getAttempts());

            savedSubmission.setResult(pollingResult.getResult());
            submissionRepository.save(savedSubmission);

            metric.setSubmission(savedSubmission);
            long e2eDuration = System.currentTimeMillis() - e2eStart;
            metric.setE2eDurationMs(e2eDuration);
            log.info("[METRIC] e2e_duration_ms={} language={} problemId={} flow=submitAndGetResult",
                     e2eDuration, submissionRequestDTO.getLanguage(), submissionRequestDTO.getProblemId());

            return toSubmissionResponseDTO(savedSubmission);

        } catch (RuntimeException e) {
            metric.setTimedOut(e.getMessage() != null && e.getMessage().contains("not available after"));
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

    private PollingResult<String> pollForResult(String cid, String submissionId, int maxWaitSeconds) {
        int maxAttempts = maxWaitSeconds * 2;
        int attempts = 0;

        while (attempts < maxAttempts) {
            long apiStart = System.currentTimeMillis();
            try {
                String result = domjudgeService.getResult(cid, submissionId);
                long apiDuration = System.currentTimeMillis() - apiStart;
                log.debug("[METRIC] getResult api_ms={} attempt={}/{} submissionId={}",
                          apiDuration, attempts + 1, maxAttempts, submissionId);
                if (result != null && !result.isEmpty()) {
                    return new PollingResult<>(result, attempts + 1);
                }
            } catch (Exception e) {
                long apiDuration = System.currentTimeMillis() - apiStart;
                log.debug("[METRIC] getResult_failed api_ms={} attempt={}/{} error={}",
                          apiDuration, attempts + 1, maxAttempts, e.getMessage());
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("채점이 중단되었습니다. 잠시 후 다시 시도해 주세요.", e);
            }
            attempts++;
        }

        throw new RuntimeException("채점 결과를 가져오는 데 시간이 초과되었습니다. 제출은 완료되었을 수 있으니 제출 목록에서 확인해 주세요.");
    }


    // for with output.

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
            PollingResult<SubmissionOutputResponseDTO> pollingResult = pollForResultOutput(cid, domjudgeSubmissionId, 30);
            long judgingDuration = System.currentTimeMillis() - judgingStart;
            metric.setJudgingDurationMs(judgingDuration);
            metric.setPollingAttempts(pollingResult.getAttempts());
            log.info("[METRIC] judging_duration_ms={} polling_attempts={}", judgingDuration, pollingResult.getAttempts());

            SubmissionOutputResponseDTO responseDTO = pollingResult.getResult();

            long e2eDuration = System.currentTimeMillis() - e2eStart;
            metric.setE2eDurationMs(e2eDuration);
            log.info("[METRIC] e2e_duration_ms={} language={} problemId={} flow=submitAndGetResultOutput",
                     e2eDuration, submissionRequestDTO.getLanguage(), submissionRequestDTO.getProblemId());

            return SubmissionOutputResponseDTO.builder()
                    .problemId(problem.getId())
                    .submissionId(domjudgeSubmissionId)
                    .language(submissionRequestDTO.getLanguage())
                    .submittedAt(LocalDateTime.now())
                    .result(responseDTO.getResult())
                    .outputList(responseDTO.getOutputList())
                    .sectionId(section.getId())
                    .build();

        } catch (RuntimeException e) {
            metric.setTimedOut(e.getMessage() != null && e.getMessage().contains("not available after"));
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


    private PollingResult<SubmissionOutputResponseDTO> pollForResultOutput(String cid, String submissionId, int maxWaitSeconds) {
        int maxAttempts = maxWaitSeconds * 2;
        int attempts = 0;

        while (attempts < maxAttempts) {
            long apiStart = System.currentTimeMillis();
            try {
                SubmissionOutputResponseDTO result = domjudgeService.getResultOutput(cid, submissionId);
                long apiDuration = System.currentTimeMillis() - apiStart;
                log.debug("[METRIC] getResultOutput api_ms={} attempt={}/{} submissionId={}",
                          apiDuration, attempts + 1, maxAttempts, submissionId);

                if (result != null && !result.getResult().isEmpty()) {
                    if (result.getOutputList() != null && !result.getOutputList().isEmpty()) {
                        boolean allCompleted = result.getOutputList().stream()
                            .allMatch(output -> output.getResult() != null && !output.getResult().isEmpty());

                        if (allCompleted) {
                            return new PollingResult<>(result, attempts + 1);
                        }
                    } else {
                        if ("CE".equals(result.getResult())) {
                            return new PollingResult<>(result, attempts + 1);
                        }
                    }
                }
            } catch (Exception e) {
                long apiDuration = System.currentTimeMillis() - apiStart;
                log.debug("[METRIC] getResultOutput_failed api_ms={} attempt={}/{} error={}",
                          apiDuration, attempts + 1, maxAttempts, e.getMessage());
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("채점이 중단되었습니다. 잠시 후 다시 시도해 주세요.", e);
            }
            attempts++;
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
     * - 관리자/튜터는 퀴즈 종료 후에도 제출 가능 (과제는 마감일 체크)
     */
    private void validateSubmission(Long problemId, Long sectionId, Long userId) {
        // 관리자인지 확인
        boolean isManager = sectionRoleService.isManager(userId, sectionId);
        
        // 문제가 속한 과제 목록 조회
        List<AssignmentProblem> assignmentProblems = assignmentProblemRepository.findByProblemId(problemId);
        
        // 문제가 속한 퀴즈 목록 조회
        List<QuizProblem> quizProblems = quizProblemRepository.findByProblemId(problemId);
        
        // 과제와 퀴즈 모두에 속하지 않은 경우
        if (assignmentProblems.isEmpty() && quizProblems.isEmpty()) {
            throw new IllegalArgumentException("해당 문제는 과제나 코딩 테스트에 속해있지 않습니다");
        }
        
        LocalDateTime now = LocalDateTime.now();
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
            Quiz targetQuiz = null;
            for (QuizProblem qp : quizProblems) {
                Quiz quiz = qp.getQuiz();
                if (quiz.getSection().getId().equals(sectionId)) {
                    targetQuiz = quiz;
                    break;
                }
            }
            
            if (targetQuiz != null) {
                // 학생이고 퀴즈가 비활성화되어 있으면 제출 불가
                if (!isManager && !targetQuiz.getActive()) {
                    throw new IllegalArgumentException("해당 코딩 테스트는 비활성화되어 있어 제출할 수 없습니다");
                }
                
                // 퀴즈 종료 시간 체크
                if (targetQuiz.getEndTime() != null && now.isAfter(targetQuiz.getEndTime())) {
                    // 학생은 퀴즈 종료 후 제출 불가
                    if (!isManager) {
                        throw new IllegalArgumentException("코딩 테스트 시간이 종료되었습니다");
                    }
                    // 관리자/튜터는 퀴즈 종료 후에도 제출 가능 (예외 처리 없음)
                }
                
                quizValid = true;
            }
        }
        
        // 과제와 퀴즈 모두에 속하지 않은 경우 (sectionId가 다른 경우)
        if (!assignmentValid && !quizValid) {
            throw new IllegalArgumentException("해당 문제는 이 분반의 과제나 코딩 테스트에 속해있지 않습니다");
        }
    }

}
