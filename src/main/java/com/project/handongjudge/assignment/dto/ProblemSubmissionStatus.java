package com.project.handongjudge.assignment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProblemSubmissionStatus {
    private Long problemId;
    private String problemTitle;
    private Integer problemOrder;
    private boolean hasSubmitted;
    private boolean hasCorrectSubmission;
}