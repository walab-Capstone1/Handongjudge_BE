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

import java.awt.print.Pageable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

import com.project.handongjudge.domjudge.service.DomjudgeService;
import org.springframework.web.multipart.MultipartFile;



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
            // 결과가 아직 준비되지 않은 경우 무시하고 계속 시도
            System.out.println(e);
        }
        return null;
    }

    public SubmissionResponseDTO submitAndGetResult(Authentication authentication, SubmissionAuthDTO submissionRequestDTO) {
        Long userId = Long.parseLong(authentication.getName());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Problem problem = problemRepository.findById(submissionRequestDTO.getProblemId())
                .orElseThrow(() -> new RuntimeException("Problem not found"));
        Section section = sectionRepository.findById(submissionRequestDTO.getSectionId())
                .orElseThrow(() -> new RuntimeException("Section not found"));

        String contestId = String.valueOf(section.getId());
        String teamId = enrollmentRepository.findTeamIdByUserIdAndSectionId(user.getId(), section.getId());
        String domjudgeProblemId = problem.getDomjudgeProblemId();

        log.info("=== Submission Debug Info ===");
        log.info("User ID: {}", userId);
        log.info("Section ID: {}", section.getId());
        log.info("Contest ID: {}", contestId);
        log.info("Team ID: {}", teamId);
        log.info("Problem DomJudge ID: {}", domjudgeProblemId);
        log.info("===========================");

        if (teamId == null || teamId.isEmpty()) {
            throw new RuntimeException("팀 ID를 찾을 수 없습니다. 수강신청이 되어있는지 확인하세요.");
        }

        File codeFile = CodeExtenstion.StringToFile(
                submissionRequestDTO.getLanguage(),
                submissionRequestDTO.getCodeString()
        );

        String domjudgeSubmissionId = domjudgeService.submitCode(
                contestId,
                teamId,
                domjudgeProblemId,
                submissionRequestDTO.getLanguage(),
                codeFile
        );

        // Delete TmpFile after submission
        if(codeFile != null) {
            try {
                Files.deleteIfExists(codeFile.toPath());
                log.debug("TmpFile deleted: {}", codeFile.getName());
            } catch (IOException e) {
                log.error("Failed to delete TmpFile: {}", e.getMessage());
            }       
        }
        
        //Submission submission = toSubmission(submissionRequestDTO);
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

        // 폴링 방식으로 결과 조회 (최대 30초 대기)
        String result = pollForResult(cid, savedSubmission.getSubmissionId(), 30);

        savedSubmission.setResult(result);
        submissionRepository.save(savedSubmission);


        return toSubmissionResponseDTO(savedSubmission);
    }

    private String pollForResult(String cid, String submissionId, int maxWaitSeconds) {
        int maxAttempts = maxWaitSeconds * 2; // 0.5초마다 시도
        int attempts = 0;
        
        while (attempts < maxAttempts) {
            try {
                String result = domjudgeService.getResult(cid, submissionId);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            } catch (Exception e) {
                // 결과가 아직 준비되지 않은 경우 무시하고 계속 시도
                System.out.println("Result not ready yet, attempt " + (attempts + 1) + "/" + maxAttempts);
            }
            
            try {
                Thread.sleep(100); // 0.1초 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted", e);
            }
            attempts++;
        }
        
        throw new RuntimeException("Result not available after " + maxWaitSeconds + " seconds");
    }


    // for with output.

    public SubmissionOutputResponseDTO submitAndGetResultOutput(Authentication authentication, SubmissionAuthDTO submissionRequestDTO) {
        Long userId = Long.parseLong(authentication.getName());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Problem problem = problemRepository.findById(submissionRequestDTO.getProblemId())
                .orElseThrow(() -> new RuntimeException("Problem not found"));
        Section section = sectionRepository.findById(submissionRequestDTO.getSectionId())
                .orElseThrow(() -> new RuntimeException("Section not found"));

        String contestId = String.valueOf(section.getId());
        String teamId = enrollmentRepository.findTeamIdByUserIdAndSectionId(user.getId(), section.getId());
        String domjudgeProblemId = problem.getDomjudgeProblemId();

        File codeFile = CodeExtenstion.StringToFile(
                submissionRequestDTO.getLanguage(),
                submissionRequestDTO.getCodeString()
        );

        String domjudgeSubmissionId = domjudgeService.submitCode(
                contestId,
                teamId,
                domjudgeProblemId,
                submissionRequestDTO.getLanguage(),
                codeFile
        );

        // Delete TmpFile after submission
        if(codeFile != null) {
            try {
                Files.deleteIfExists(codeFile.toPath());
                log.debug("TmpFile deleted: {}", codeFile.getName());
            } catch (IOException e) {
                log.error("Failed to delete TmpFile: {}", e.getMessage());
            }
        }

        // ⭐⭐⭐ 테스트하기는 DB에 저장하지 않음!
        String cid = String.valueOf(section.getId());

        // 폴링 방식으로 결과 조회 (최대 30초 대기)
        SubmissionOutputResponseDTO responseDTO = pollForResultOutput(cid, domjudgeSubmissionId, 30);

        // DB에 저장하지 않고 바로 반환
        return SubmissionOutputResponseDTO.builder()
                .problemId(problem.getId())
                .submissionId(domjudgeSubmissionId)
                .language(submissionRequestDTO.getLanguage())
                .submittedAt(LocalDateTime.now())
                .result(responseDTO.getResult())
                .outputList(responseDTO.getOutputList())
                .sectionId(section.getId())
                .build();
    }


    private SubmissionOutputResponseDTO pollForResultOutput(String cid, String submissionId, int maxWaitSeconds) {
        int maxAttempts = maxWaitSeconds * 2; // 0.5초마다 시도
        int attempts = 0;

        while (attempts < maxAttempts) {
            try {
                SubmissionOutputResponseDTO result = domjudgeService.getResultOutput(cid, submissionId);
                if (result != null && !result.getResult().isEmpty()) {
                    return result;
                }
            } catch (Exception e) {
                // 결과가 아직 준비되지 않은 경우 무시하고 계속 시도
                System.out.println("Result not ready yet, attempt " + (attempts + 1) + "/" + maxAttempts);
            }

            try {
                Thread.sleep(2000); // 0.1초 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted", e);
            }
            attempts++;
        }

        throw new RuntimeException("Result not available after " + maxWaitSeconds + " seconds");
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

}
