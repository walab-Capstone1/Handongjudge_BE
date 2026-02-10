package com.project.handongjudge.problem.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProblemSetResponse {
    private Long id;
    private String title;
    private String description;
    private String tags;  // JSON string 형식
    private LocalDateTime createdAt;
    private Long problemCount;  // 포함된 문제 개수
}

