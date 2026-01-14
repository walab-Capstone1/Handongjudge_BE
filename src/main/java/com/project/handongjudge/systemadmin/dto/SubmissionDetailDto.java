package com.project.handongjudge.systemadmin.dto;

import com.project.handongjudge.submission.entity.Submission;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionDetailDto {
    private Long id;
    private String submissionId;
    private Long userId;
    private String userName;
    private String userEmail;
    private Long problemId;
    private String problemTitle;
    private Long sectionId;
    private String sectionName;
    private String language;
    private String result;
    private String submittedAt;
    private String code;

    public static SubmissionDetailDto from(Submission submission) {
        return SubmissionDetailDto.builder()
                .id(submission.getId())
                .submissionId(submission.getSubmissionId())
                .userId(submission.getUser().getId())
                .userName(submission.getUser().getName())
                .userEmail(submission.getUser().getEmail())
                .problemId(submission.getProblem().getId())
                .problemTitle(submission.getProblem().getTitle())
                .sectionId(submission.getSection().getId())
                .sectionName(submission.getSection().getCourse().getTitle() + 
                            " - " + submission.getSection().getSectionNumber() + "분반")
                .language(submission.getLanguage())
                .result(submission.getResult())
                .submittedAt(submission.getSubmittedAt() != null ? submission.getSubmittedAt().toString() : null)
                .code(submission.getCode())
                .build();
    }
}

