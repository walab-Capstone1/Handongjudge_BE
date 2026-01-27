package com.project.handongjudge.quiz.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuizProblemDto {
    private Long id;
    private Long problemId;
    private String title;
    private Integer problemOrder;
}


