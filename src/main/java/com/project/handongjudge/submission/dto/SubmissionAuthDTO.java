package com.project.handongjudge.submission.dto;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class SubmissionAuthDTO {
    private Long problemId;
    private Long sectionId;
    private String language;
    private String codeString; // 코드 문자열
}
