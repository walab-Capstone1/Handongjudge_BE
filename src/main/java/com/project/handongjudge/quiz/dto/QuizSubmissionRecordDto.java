package com.project.handongjudge.quiz.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class QuizSubmissionRecordDto {
    private Long submissionId;
    private Long userId;
    private String studentId;
    private String studentName;
    private Long problemId;
    private String problemTitle;
    private LocalDateTime submittedAt;
    private String result;
    private String language;
}
