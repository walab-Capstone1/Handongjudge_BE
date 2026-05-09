package com.project.handongjudge.submission.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 비동기 제출 직후 응답 (과제 / 퀴즈 공통).
 * DOMjudge 제출 후 즉시 반환하며, 클라이언트는 submissionDbId로 결과 API를 폴링한다.
 */
@Getter
@Builder
public class AsyncSubmitResponseDTO {
    /** DB 기본 키 (GET …/result/{submissionDbId} 폴링에 사용) */
    private Long submissionDbId;

    /** DOMjudge submission ID */
    private String submissionId;

    private Long problemId;
    private Long sectionId;
    private String language;
    private LocalDateTime submittedAt;
}
