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
    private String domjudgeProblemId;
    private LocalDateTime createdAt;
    private Integer points; // 배점 (AssignmentProblem에서 가져옴)
    private Integer problemOrder; // 문제 순서 (AssignmentProblem에서 가져옴)

}