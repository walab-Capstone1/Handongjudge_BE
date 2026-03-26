package com.project.handongjudge.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizProblemWorkStatusDto {
    private Long problemId;
    private boolean submitted;
    private String result;
    private boolean saved;
}
