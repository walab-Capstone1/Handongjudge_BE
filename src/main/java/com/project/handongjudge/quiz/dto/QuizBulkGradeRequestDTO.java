package com.project.handongjudge.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizBulkGradeRequestDTO {
    private Long quizId;
    private List<QuizGradeRequestDTO> grades;
}
