package com.project.handongjudge.submission.service;

import com.project.handongjudge.section.entity.Contest;
import com.project.handongjudge.section.repository.ContestRepository;
import com.project.handongjudge.submission.dto.SubmissionRequestDTO;
import com.project.handongjudge.submission.dto.SubmissionResponseDTO;
import org.springframework.stereotype.Service; 
import lombok.RequiredArgsConstructor;

import com.project.handongjudge.submission.repository.SubmissionRepository;
import com.project.handongjudge.submission.entity.Submission;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import com.project.handongjudge.problem.util.CodeExtenstion;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;

import com.project.handongjudge.domjudge.service.DomjudgeService;
import org.springframework.web.multipart.MultipartFile;


@RequiredArgsConstructor
@Service
public class SubmissionService {
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
                .problemId(submission.getProblem().getId())
                .userId(submission.getUser().getId())
                .sectionId(submission.getSection().getId())
                .language(submission.getLanguage())
                .code(submission.getCode())
                .submittedAt(submission.getSubmittedAt())
                .build();
    }
    public String getResult(Long sectionId, String submissionId) {
        String cid = String.valueOf(sectionId);
        return domjudgeService.getResult(cid, submissionId);
    }


}
