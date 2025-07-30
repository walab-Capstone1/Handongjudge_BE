package com.project.handongjudge.auth.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDto {
    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";
}