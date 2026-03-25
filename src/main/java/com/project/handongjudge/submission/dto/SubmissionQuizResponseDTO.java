package com.project.handongjudge.submission.dto;

import com.project.handongjudge.submission.entity.Output;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 퀴즈 제출 응답 DTO - 테스트케이스별 scoring 정보 포함
 * SubmissionOutputResponseDTO + passedCount, totalCount, points, score
 */
@Getter
@Setter
@Builder
public class SubmissionQuizResponseDTO {

    private Long problemId;
    private Long sectionId;
    private String language;
    private String submissionId;
    private String result;
    private List<Output> outputList;
    private LocalDateTime submittedAt;

    // 퀴즈 scoring 필드
    private Integer passedCount;
    private Integer totalCount;
    private Integer points;
    private Double score;
}
