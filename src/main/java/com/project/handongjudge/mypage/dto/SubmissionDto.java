package com.project.handongjudge.mypage.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionDto {
    private Long id;
    private String problemTitle;
    private String sectionName;
    private String language;
    private String result;
    private String submittedAt;
    private String code; // 코드 내용 (선택적)

    public static SubmissionDto from(com.project.handongjudge.submission.entity.Submission submission) {
        return SubmissionDto.builder()
                .id(submission.getId())
                .problemTitle(submission.getProblem().getTitle())
                .sectionName(submission.getSection().getCourse().getTitle() + " - " + submission.getSection().getSectionNumber() + "분반")
                .language(submission.getLanguage())
                .result(submission.getResult())
                .submittedAt(submission.getSubmittedAt().toString())
                .build();
    }
}