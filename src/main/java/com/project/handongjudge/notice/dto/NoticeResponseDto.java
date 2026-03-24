package com.project.handongjudge.notice.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NoticeResponseDto {
    private Long id;
    private Long sectionId;
    private String sectionName; // 분반 정보 (예: "컴퓨터과학개론 - 16분반")
    private String instructorName; // 작성자(해당 수업 담당 교수)
    private String title;
    private String content;
    private String difficulty;
    private boolean isNew;
    private Boolean active;

    /** 알림 API와 동일한 Jackson 기본 ISO 직렬화(UTC+Z 가짜 포맷 제거로 프론트 날짜 어긋남 방지) */
    private LocalDateTime createdAt;
}
