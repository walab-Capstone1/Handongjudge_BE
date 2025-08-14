package com.project.handongjudge.notice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NoticeResponseDto {
    private Long id;
    private Long sectionId;
    private String sectionName; // 분반 정보 (예: "컴퓨터과학개론 - 16분반")
    private String title;
    private String content;
    private String difficulty;
    private boolean isNew;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime createdAt;
}
