package com.project.handongjudge.problem.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProblemDto {
    private Long id;
    private String title;
    private String description;
    private String difficulty;
    private Long domjudgeProblemId;
    private LocalDateTime createdAt;
} 