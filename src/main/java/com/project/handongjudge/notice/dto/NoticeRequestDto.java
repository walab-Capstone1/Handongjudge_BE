package com.project.handongjudge.notice.dto;

import lombok.Data;

@Data
public class NoticeRequestDto {
    private Long sectionId;
    private String title;
    private String content;
    private String difficulty;
}
