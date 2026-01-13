package com.project.handongjudge.community.dto;

import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNicknameDto {

    @NotNull(message = "섹션 ID는 필수입니다")
    private Long sectionId;

    @NotBlank(message = "별명은 필수입니다")
    @Size(min = 2, max = 50, message = "별명은 2-50자 사이여야 합니다")
    private String nickname;
}

