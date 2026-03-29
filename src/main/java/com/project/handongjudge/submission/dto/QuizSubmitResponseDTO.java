package com.project.handongjudge.submission.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Phase 2: POST /api/quiz/submit 응답 DTO
 * DOMjudge 제출 직후 즉시 반환. 클라이언트는 submissionDbId로 결과를 폴링.
 */
@Getter
@Builder
public class QuizSubmitResponseDTO {
    /** DB 기본 키 (GET /api/quiz/result/{submissionDbId} 폴링에 사용) */
    private Long submissionDbId;

    /** DOMjudge submission ID */
    private String submissionId;

    private Long problemId;
    private Long sectionId;
    private String language;
    private LocalDateTime submittedAt;
}
