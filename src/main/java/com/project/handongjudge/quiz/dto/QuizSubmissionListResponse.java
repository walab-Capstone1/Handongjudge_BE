package com.project.handongjudge.quiz.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class QuizSubmissionListResponse {
    private List<QuizSubmissionRecordDto> content;
    private long totalElements;
    private int totalPages;
    private int number;
    private int size;
}
