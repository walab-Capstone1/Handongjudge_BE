package com.project.handongjudge.assignment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// dto/AssignmentInfoDto.java
@Getter
@AllArgsConstructor
@Builder
public class AssignmentInfoDto {
    private Long id;
    private String title;
    private String assignmentNumber;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}