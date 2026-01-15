package com.project.handongjudge.notice.dto;

import lombok.Data;

@Data
public class SystemGuideRequestDto {
    private String title;
    private String content;
    private String category; // 카테고리 (선택사항)
    private Integer priority; // 우선순위 (선택사항, 기본값 0)
}

