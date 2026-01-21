package com.project.handongjudge.assignment.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class StudentAcceptedCodeResponse {
    private Long submissionId;
    private Long userId;
    private String studentId;
    private String studentName;
    private Long problemId;
    private String problemTitle;
    private String code;
    private String language;
    private LocalDateTime submittedAt;
    private String result;
}

