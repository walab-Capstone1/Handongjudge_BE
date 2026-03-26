package com.project.handongjudge.auth.controller;

import com.project.handongjudge.auth.service.AuthService;
import com.project.handongjudge.auth.util.AuthCookieUtil;
import com.project.handongjudge.auth.util.JwtUtil;
import com.project.handongjudge.auth.dto.AuthRequestDto;
import com.project.handongjudge.auth.dto.AuthResponseDto;
import com.project.handongjudge.user.service.UserService;
import com.project.handongjudge.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 인증 관련 API 컨트롤러
 *
 * 토큰 관리 방식:
 * - Access Token: 응답 본문에 포함 (프론트엔드 메모리에 저장)
 * - Refresh Token: HttpOnly 쿠키 (AuthCookieUtil 경유) + DB 해시 저장
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final AuthCookieUtil authCookieUtil;

    /**
     * 일반 로그인 API
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody AuthRequestDto.LoginRequest loginRequest,
                                                     HttpServletResponse response) {
        try {
            AuthResponseDto authResponse = authService.login(loginRequest);
            authCookieUtil.setRefreshTokenCookie(response, authResponse.getRefreshToken());
            return createSuccessResponse("로그인 성공", authResponse);
        } catch (org.springframework.security.core.AuthenticationException e) {
            log.error("Login failed for email: {}", loginRequest.getEmail(), e);
            return createErrorResponse("이메일 또는 비밀번호가 잘못되었습니다.");
        }
    }

    /**
     * 회원가입 API
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody AuthRequestDto.RegisterRequest registerRequest,
                                                        HttpServletResponse response) {
        try {
            AuthResponseDto authResponse = authService.register(registerRequest);
            authCookieUtil.setRefreshTokenCookie(response, authResponse.getRefreshToken());
            return createSuccessResponse("회원가입 성공", authResponse);
        } catch (RuntimeException e) {
            log.error("Registration failed for email: {}", registerRequest.getEmail(), e);
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * 로그아웃 API - DB에서 Refresh Token 삭제 후 쿠키 제거
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request, HttpServletResponse response,
                                                      Authentication authentication) {
        String rawRefreshToken = authCookieUtil.extractRefreshToken(request);
        authService.logout(rawRefreshToken);

        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }

        authCookieUtil.clearRefreshTokenCookie(response);

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("success", true);
        responseMap.put("message", "로그아웃 성공");
        return ResponseEntity.ok(responseMap);
    }

    /**
     * JWT 토큰 갱신 API (Refresh Token Rotation)
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        try {
            String rawRefreshToken = authCookieUtil.extractRefreshToken(request);
            if (rawRefreshToken == null) {
                return createErrorResponse("Refresh Token이 없습니다.");
            }

            AuthResponseDto authResponse = authService.refreshToken(rawRefreshToken);
            authCookieUtil.setRefreshTokenCookie(response, authResponse.getRefreshToken());
            return createSuccessResponse("토큰 갱신 성공", authResponse);
        } catch (RuntimeException e) {
            log.error("Token refresh failed", e);
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * 인증 상태 확인 API
     */
    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyAuth(HttpServletRequest request) {
        try {
            String bearerToken = request.getHeader("Authorization");
            String accessToken = (bearerToken != null && bearerToken.startsWith("Bearer "))
                    ? bearerToken.substring(7) : null;

            if (accessToken == null || !jwtUtil.validateToken(accessToken)) {
                return createErrorResponse("유효하지 않은 Access Token입니다.");
            }

            String userId = jwtUtil.getIDFromToken(accessToken);
            UserDto user = userService.getUserInfoById(Long.parseLong(userId));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "인증 확인 성공");
            response.put("user", user);
            response.put("accessToken", accessToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Auth verification failed", e);
            return createErrorResponse("인증 확인에 실패했습니다.");
        }
    }

    /**
     * OAuth2 소셜 로그인 처리 (프론트엔드 콜백에서 호출)
     */
    @PostMapping("/social-login")
    public ResponseEntity<Map<String, Object>> socialLogin(@RequestBody Map<String, String> body,
                                                           HttpServletResponse response) {
        String provider = body.get("provider");
        String token = body.get("token");

        if (token != null && !token.isEmpty()) {
            return handleOAuthCallback(provider, token, response);
        } else {
            String loginUrl = "/oauth2/authorization/" + (provider != null ? provider.toLowerCase() : "google");
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("message", "소셜 로그인 URL");
            res.put("loginUrl", loginUrl);
            return ResponseEntity.ok(res);
        }
    }

    private ResponseEntity<Map<String, Object>> handleOAuthCallback(String provider, String token,
                                                                     HttpServletResponse response) {
        try {
            if (!jwtUtil.validateToken(token)) {
                return createErrorResponse("유효하지 않은 토큰입니다.");
            }

            String userId = jwtUtil.getIDFromToken(token);
            log.info("OAuth callback - provider: {}, userId: {}", provider, userId);

            UserDto user = userService.getUserInfoById(Long.parseLong(userId));

            // OAuth2 성공 핸들러(SecurityConfig)에서 이미 DB 저장이 완료됨
            // 여기서는 쿠키에 기존 설정된 refreshToken이 그대로 유효함
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("success", true);
            responseMap.put("message", "소셜 로그인 성공");
            responseMap.put("user", user);
            responseMap.put("accessToken", token);
            return ResponseEntity.ok(responseMap);

        } catch (Exception e) {
            log.error("OAuth callback processing failed for provider: {}", provider, e);
            return createErrorResponse("소셜 로그인 처리 중 오류가 발생했습니다.");
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> request) {
        return createErrorResponse("비밀번호 재설정 기능이 아직 구현되지 않았습니다.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        return createErrorResponse("비밀번호 재설정 기능이 아직 구현되지 않았습니다.");
    }

    @GetMapping("/oauth2/google")
    public ResponseEntity<Map<String, Object>> getGoogleLoginUrl() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("loginUrl", "/oauth2/authorization/google"));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/oauth2/github")
    public ResponseEntity<Map<String, Object>> getGithubLoginUrl() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("loginUrl", "/oauth2/authorization/github"));
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> createSuccessResponse(String message, AuthResponseDto authResponse) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("accessToken", authResponse.getAccessToken());
        response.put("tokenType", authResponse.getTokenType());
        response.put("user", authResponse.getUser());
        // Refresh Token은 쿠키에만 설정하므로 응답 바디에 포함하지 않음
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.badRequest().body(response);
    }
}
