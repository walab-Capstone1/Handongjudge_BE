package com.project.handongjudge.problem.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProblemResponse {
    private Long id;
    private String title;
    private String description;
    private String difficulty;
    private LocalDateTime createdAt;
    private Integer order;
}
