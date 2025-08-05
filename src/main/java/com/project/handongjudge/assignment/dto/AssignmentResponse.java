package com.project.handongjudge.assignment.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AssignmentResponse {
    private Long id;
    private String assignmentNumber;
    private String title;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
