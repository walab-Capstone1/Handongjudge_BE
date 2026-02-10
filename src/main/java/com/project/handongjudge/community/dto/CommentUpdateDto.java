package com.project.handongjudge.community.dto;

import lombok.*;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentUpdateDto {

    @NotBlank(message = "내용은 필수입니다")
    private String content;
}


