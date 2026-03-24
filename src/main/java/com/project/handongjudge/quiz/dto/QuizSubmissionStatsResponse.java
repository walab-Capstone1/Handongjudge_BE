package com.project.handongjudge.quiz.dto;

import com.project.handongjudge.assignment.dto.ProblemSubmissionStats;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class QuizSubmissionStatsResponse {
    private Long quizId;
    private String quizTitle;
    private Long sectionId;
    private String sectionName;
    private Integer totalStudents;
    private List<ProblemSubmissionStats> problemStats;
}
