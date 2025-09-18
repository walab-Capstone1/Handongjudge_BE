package com.project.handongjudge.assignment.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class UserSubmissionStatusResponse {
    private Long assignmentId;
    private String assignmentTitle;
    private Long sectionId;
    private Long userId;
    private List<ProblemSubmissionStatus> problemStatuses;
}