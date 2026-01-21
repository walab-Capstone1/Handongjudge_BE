package com.project.handongjudge.problem.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProblemSetDetailResponse {
    private Long id;
    private String title;
    private String description;
    private String tags;  // JSON string 형식
    private LocalDateTime createdAt;
    private List<ProblemInSetDto> problems;  // 포함된 문제 목록 (순서대로)
}

