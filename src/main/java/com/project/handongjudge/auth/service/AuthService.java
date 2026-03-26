package com.project.handongjudge.auth.service;

import com.project.handongjudge.auth.entity.RefreshToken;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.service.UserService;
import com.project.handongjudge.user.dto.UserDto;
import com.project.handongjudge.auth.dto.AuthRequestDto;
import com.project.handongjudge.auth.dto.AuthResponseDto;
import com.project.handongjudge.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 서비스
 *
 * 토큰 관리 방식:
 * - Access Token: 응답 본문에 포함 (프론트엔드 메모리에 저장)
 * - Refresh Token: HttpOnly 쿠키 + DB에 해시 저장 (중복 로그인 방지, 탈취 감지)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuthService {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    /**
     * 일반 로그인 처리
     * 기존 세션(Refresh Token)을 무효화하고 새로 발급 → 중복 로그인 방지
     */
    @Transactional
    public AuthResponseDto login(AuthRequestDto.LoginRequest loginRequest) throws AuthenticationException {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        User user = userService.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        String accessToken = jwtUtil.generateAccessToken(String.valueOf(user.getId()));
        String refreshToken = jwtUtil.generateRefreshToken(String.valueOf(user.getId()));

        // DB에 저장 (기존 토큰 삭제 후 새 토큰 저장 → 중복 로그인 방지)
        refreshTokenService.saveToken(user.getId(), refreshToken);

        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(UserDto.from(user))
                .build();
    }

    /**
     * 회원가입 처리
     */
    @Transactional
    public AuthResponseDto register(AuthRequestDto.RegisterRequest registerRequest) {
        if (userService.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        }

        User user = userService.createUser(
                registerRequest.getEmail(),
                registerRequest.getPassword(),
                registerRequest.getName(),
                registerRequest.getStudentId()
        );

        String accessToken = jwtUtil.generateAccessToken(String.valueOf(user.getId()));
        String refreshToken = jwtUtil.generateRefreshToken(String.valueOf(user.getId()));

        refreshTokenService.saveToken(user.getId(), refreshToken);

        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(UserDto.from(user))
                .build();
    }

    /**
     * JWT 토큰 갱신 처리
     * DB 검증 + 토큰 로테이션 (Refresh Token Rotation)
     */
    @Transactional
    public AuthResponseDto refreshToken(String rawRefreshToken) {
        // JWT 서명 유효성 검사
        if (!jwtUtil.validateRefreshToken(rawRefreshToken)) {
            throw new RuntimeException("유효하지 않은 Refresh Token입니다.");
        }

        // DB에서 해시 일치 + 만료 여부 검사
        RefreshToken stored = refreshTokenService.findValidToken(rawRefreshToken)
                .orElseThrow(() -> new RuntimeException("세션이 만료되었거나 다른 기기에서 로그인되었습니다."));

        String userId = jwtUtil.getIDFromToken(rawRefreshToken);
        Long userIdLong = validateAndParseUserId(userId);

        String newAccessToken = jwtUtil.generateAccessToken(userId);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId);

        // 기존 토큰 삭제 + 새 토큰 저장 (Rotation)
        refreshTokenService.rotateToken(rawRefreshToken, userIdLong, newRefreshToken);

        User user = userService.findById(userIdLong)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        return AuthResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .user(UserDto.from(user))
                .build();
    }

    /**
     * 로그아웃 처리 - DB에서 Refresh Token 삭제
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.deleteToken(rawRefreshToken);
    }

    private Long validateAndParseUserId(String userId) {
        try {
            Long userIdLong = Long.parseLong(userId);
            if (userIdLong <= 0) {
                throw new RuntimeException("유효하지 않은 사용자 ID입니다.");
            }
            return userIdLong;
        } catch (NumberFormatException e) {
            throw new RuntimeException("유효하지 않은 사용자 ID 형식입니다.");
        }
    }
}
