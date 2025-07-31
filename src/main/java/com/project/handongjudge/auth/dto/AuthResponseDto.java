package com.project.handongjudge.auth.dto;

import com.project.handongjudge.user.dto.UserDto;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDto {
    private String accessToken;
    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";
    
    // 사용자 정보 (선택적)
    private UserDto user;
}