package com.project.handongjudge.auth.service;

import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.service.UserService;
import com.project.handongjudge.user.dto.UserDto;
import com.project.handongjudge.auth.dto.AuthRequestDto;
import com.project.handongjudge.auth.dto.AuthResponseDto;
import com.project.handongjudge.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuthService {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthResponseDto login(AuthRequestDto.LoginRequest loginRequest) throws AuthenticationException {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        String identifier = authentication.getName();
        String accessToken = jwtUtil.generateAccessToken(authentication);
        String refreshToken = jwtUtil.generateRefreshToken(identifier);
        
        // 사용자 정보 조회 (이메일로 조회)
        User user = userService.findByEmail(identifier).orElseThrow(() -> 
            new RuntimeException("사용자를 찾을 수 없습니다."));

        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(UserDto.from(user))
                .build();
    }

    @Transactional
    public AuthResponseDto register(AuthRequestDto.RegisterRequest registerRequest) {
        if (userService.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        }

        User user = userService.createUser(
                registerRequest.getEmail(),
                registerRequest.getPassword(),
                registerRequest.getName()
        );
        
        String accessToken = jwtUtil.generateAccessToken(String.valueOf(user.getId()));
        String refreshToken = jwtUtil.generateRefreshToken(String.valueOf(user.getId()));

        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(UserDto.from(user))
                .build();
    }

    public AuthResponseDto refreshToken(AuthRequestDto.RefreshTokenRequest refreshTokenRequest) {
        String refreshToken = refreshTokenRequest.getRefreshToken();
        
        // Refresh Token 유효성 검사
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new RuntimeException("유효하지 않은 Refresh Token입니다.");
        }
        
        // Refresh Token에서 사용자 ID 추출
        String identifier = jwtUtil.getIDFromToken(refreshToken);
        
        // 새로운 Access Token 생성
        String newAccessToken = jwtUtil.generateAccessToken(identifier);
        
        // 사용자 정보 조회 (ID로 조회)
        User user;
        try {
            Long userId = Long.parseLong(identifier);
            user = userService.findById(userId).orElseThrow(() -> 
                new RuntimeException("사용자를 찾을 수 없습니다."));
        } catch (NumberFormatException e) {
            // ID가 숫자가 아닌 경우 이메일로 시도
            user = userService.findByEmail(identifier).orElseThrow(() -> 
                new RuntimeException("사용자를 찾을 수 없습니다."));
        }
        
        return AuthResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken) // 기존 Refresh Token 유지
                .tokenType("Bearer")
                .user(UserDto.from(user))
                .build();
    }
}