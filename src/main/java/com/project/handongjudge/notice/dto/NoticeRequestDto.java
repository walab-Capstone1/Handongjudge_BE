package com.project.handongjudge.notice.dto;

import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoticeRequestDto {

    @NotNull(message = "분반 ID는 필수입니다")
    private Long sectionId;

    @NotBlank(message = "공지사항 제목은 필수입니다")
    private String title;

    @NotBlank(message = "공지사항 내용은 필수입니다")
    private String content;

    private String difficulty; // EASY, MEDIUM, HARD 등

    @Builder.Default
    private boolean isNew = true;
}