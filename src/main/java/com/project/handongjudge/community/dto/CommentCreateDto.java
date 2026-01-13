package com.project.handongjudge.community.dto;

import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentCreateDto {

    @NotNull(message = "질문 ID는 필수입니다")
    private Long questionId;

    @NotBlank(message = "내용은 필수입니다")
    private String content;

    @NotNull(message = "익명 여부는 필수입니다")
    private Boolean isAnonymous;
}

