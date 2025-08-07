package com.project.handongjudge.submission.util;

import com.project.handongjudge.submission.dto.SubmissionRequestDTO;
import com.project.handongjudge.submission.dto.SubmissionResponseDTO;
import com.project.handongjudge.submission.entity.Submission;

import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.section.repository.SectionRepository;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
@Getter
@Setter
@Builder
public class SubmissionMapper {
    // RequestDTO -> Entity
    @Autowired
    private final ProblemRepository problemRepository;
    @Autowired
    private final UserRepository userRepository;
    @Autowired
    private final SectionRepository sectionRepository;
    public Submission toSubmission(SubmissionRequestDTO submissionRequestDTO) {
        return Submission.builder()
            .problem(problemRepository.findById(submissionRequestDTO.getProblemId()).orElseThrow(() -> new RuntimeException("Problem not found")))
            .user(userRepository.findById(submissionRequestDTO.getUserId()).orElseThrow(() -> new RuntimeException("User not found")))
            .language(submissionRequestDTO.getLanguage())
            .code(submissionRequestDTO.getCode())
            .submittedAt(LocalDateTime.now())
            .build();
    }
    // Entity -> ResponseDTO
//     public static SubmissionResponseDTO toSubmissionResponseDTO(Submission submission, ProblemRepository problemRepository, UserRepository userRepository, SectionRepository sectionRepository) {
//         return SubmissionResponseDTO.builder()
//             .submissionId(submission.getSubmissionId())
//             .problemId(submission.getProblem().getProblemId())
//             .userId(submission.getUser().getUserId())
//             .sectionId(submission.getSection().getSectionId())
//             .language(submission.getLanguage())
//             .code(submission.getCode())
//             .submittedAt(submission.getSubmittedAt())
//             .build();

//     }   
}
