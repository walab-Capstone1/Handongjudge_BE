package com.project.handongjudge.submission.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmissionRequestDTO {
    private Long id;    // submissionId 추가 필요       
    private Long problemId;
    private Long userId;
    private Long sectionId;
    private String language;
    private String code;
}
