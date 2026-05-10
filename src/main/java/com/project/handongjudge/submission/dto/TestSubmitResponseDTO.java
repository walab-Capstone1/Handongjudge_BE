package com.project.handongjudge.submission.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 테스트하기 비동기 제출 응답 DTO.
 * sessionKey(= DOMjudge submissionId)로 /test/stream/{sessionKey} SSE 연결에 사용한다.
 */
@Getter
@Builder
public class TestSubmitResponseDTO {

    /** SSE 스트림 연결 키 (= DOMjudge submissionId) */
    private String sessionKey;

    private Long sectionId;
    private Long problemId;
    private String language;
    private LocalDateTime submittedAt;
}
