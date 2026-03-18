package com.project.handongjudge.problem.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProblemInSetDto {
    private Long id;
    private String title;
    private String difficulty;
    private Integer order;  // 문제집 내 순서
    private LocalDateTime createdAt;
}

