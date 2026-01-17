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
    private Double timeLimit;  // 초 단위
    private Integer memoryLimit;  // MB 단위
    private Boolean isUsed;  // 사용 중인지 여부
    private Integer assignmentCount;  // 사용 중인 과제 개수
}
