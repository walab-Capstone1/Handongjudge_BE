package com.project.handongjudge.notice.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoticeDto {
    private Long id;
    private Long sectionId;
    private String sectionName;
    private String courseTitle;
    private String title;
    private String content;
    private String difficulty;
    private boolean isNew;
    private LocalDateTime createdAt;
}