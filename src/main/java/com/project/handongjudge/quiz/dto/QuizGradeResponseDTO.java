package com.project.handongjudge.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizGradeResponseDTO {
    private Long id;
    private Long quizId;
    private String quizTitle;
    private Long problemId;
    private String problemTitle;
    private Long userId;
    private String studentName;
    private String studentId;
    private Integer points;
    private Integer score;
    private String comment;
    private LocalDateTime gradedAt;
    private String gradedByName;

    private Boolean submitted;
    private LocalDateTime submittedAt;
    private Boolean isOnTime;
    private String result;
}
