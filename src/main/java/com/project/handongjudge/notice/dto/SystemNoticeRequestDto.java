package com.project.handongjudge.notice.dto;

import lombok.Data;

@Data
public class SystemNoticeRequestDto {
    private String title;
    private String content;
    private Integer priority; // 우선순위 (선택사항, 기본값 0)
}

