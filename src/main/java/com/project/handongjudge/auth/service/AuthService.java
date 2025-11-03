package com.project.handongjudge.auth.service;

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
 * 새로운 토큰 관리 방식:
 * - Access Token: 응답 본문에 포함 (프론트엔드 메모리에 저장)
 * - Refresh Token: httpOnly secure 쿠키에 저장
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuthService {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    /**
     * 일반 로그인 처리
     *
     * @param loginRequest 로그인 요청 정보
     * @return 인증 응답 (JWT 토큰 + 사용자 정보)
     * @throws AuthenticationException 인증 실패 시
     */
    public AuthResponseDto login(AuthRequestDto.LoginRequest loginRequest) throws AuthenticationException {
        // Spring Security를 통한 사용자 인증
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        // 인증 성공 후 사용자 정보 조회 (이메일로 조회)
        User user = userService.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // JWT 토큰 생성 (사용자 ID 사용)
        String accessToken = jwtUtil.generateAccessToken(String.valueOf(user.getId()));
        String refreshToken = jwtUtil.generateRefreshToken(String.valueOf(user.getId()));

        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(UserDto.from(user))
                .build();
    }

    /**
     * 회원가입 처리
     *
     * @param registerRequest 회원가입 요청 정보
     * @return 인증 응답 (JWT 토큰 + 사용자 정보)
     */
    @Transactional
    public AuthResponseDto register(AuthRequestDto.RegisterRequest registerRequest) {
        // 이메일 중복 확인
        if (userService.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        }

        // 새 사용자 생성
        User user = userService.createUser(
                registerRequest.getEmail(),
                registerRequest.getPassword(),
                registerRequest.getName(),
                registerRequest.getStudentId()
        );

        // JWT 토큰 생성 (사용자 ID 사용)
        String accessToken = jwtUtil.generateAccessToken(String.valueOf(user.getId()));
        String refreshToken = jwtUtil.generateRefreshToken(String.valueOf(user.getId()));

        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(UserDto.from(user))
                .build();
    }

    /**
     * JWT 토큰 갱신 처리
     *
     * @param refreshToken 쿠키에서 추출한 Refresh Token
     * @return 새로운 액세스 토큰과 사용자 정보
     */
    public AuthResponseDto refreshToken(String refreshToken) {
        // Refresh Token 유효성 검사
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new RuntimeException("유효하지 않은 Refresh Token입니다.");
        }

        // Refresh Token에서 사용자 ID 추출
        String userId = jwtUtil.getIDFromToken(refreshToken);

        // 사용자 ID 유효성 검사
        Long userIdLong = validateAndParseUserId(userId);

        // 새로운 Access Token 생성 (사용자 ID 사용)
        String newAccessToken = jwtUtil.generateAccessToken(userId);

        // 새로운 Refresh Token 생성 (보안 강화)
        String newRefreshToken = jwtUtil.generateRefreshToken(userId);

        // 사용자 정보 조회 (ID로만 조회)
        User user = userService.findById(userIdLong)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        return AuthResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken) // 새로운 Refresh Token 반환
                .tokenType("Bearer")
                .user(UserDto.from(user))
                .build();
    }

    /**
     * 사용자 ID 유효성 검사 및 파싱
     *
     * @param userId 사용자 ID 문자열
     * @return 파싱된 사용자 ID (Long)
     * @throws RuntimeException 유효하지 않은 ID인 경우
     */
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