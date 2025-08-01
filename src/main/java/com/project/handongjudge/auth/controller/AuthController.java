package com.project.handongjudge.auth.controller;

import com.project.handongjudge.auth.service.AuthService;
import com.project.handongjudge.auth.util.JwtUtil;
import com.project.handongjudge.auth.dto.AuthRequestDto;
import com.project.handongjudge.auth.dto.AuthResponseDto;
import com.project.handongjudge.user.service.UserService;
import com.project.handongjudge.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 인증 관련 API 컨트롤러
 *
 * 새로운 토큰 관리 방식:
 * - Access Token: 응답 본문에 포함 (프론트엔드 메모리에 저장)
 * - Refresh Token: httpOnly secure 쿠키에 저장
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${FRONTEND_URL:http://localhost:3000}"}, allowCredentials = "true")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @Value("${FRONTEND_URL:http://localhost:3000}")
    private String frontendUrl;

    @Value("${jwt.refresh-token.cookie-name:refreshToken}")
    private String refreshTokenCookieName;

    @Value("${jwt.refresh-token.cookie-domain:localhost}")
    private String refreshTokenCookieDomain;

    /**
     * 일반 로그인 API
     *
     * @param loginRequest 로그인 요청 정보 (이메일, 비밀번호)
     * @param response HTTP 응답 (쿠키 설정용)
     * @return 로그인 성공 시 Access Token과 사용자 정보 반환
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody AuthRequestDto.LoginRequest loginRequest,
                                                     HttpServletResponse response) {
        try {
            AuthResponseDto authResponse = authService.login(loginRequest);

            // Refresh Token을 httpOnly secure 쿠키로 설정
            setRefreshTokenCookie(response, authResponse.getRefreshToken());

            return createSuccessResponse("로그인 성공", authResponse);
        } catch (AuthenticationException e) {
            log.error("Login failed for email: {}", loginRequest.getEmail(), e);
            return createErrorResponse("이메일 또는 비밀번호가 잘못되었습니다.");
        }
    }

    /**
     * 회원가입 API
     *
     * @param registerRequest 회원가입 요청 정보 (이메일, 비밀번호, 이름)
     * @param response HTTP 응답 (쿠키 설정용)
     * @return 회원가입 성공 시 Access Token과 사용자 정보 반환
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody AuthRequestDto.RegisterRequest registerRequest,
                                                        HttpServletResponse response) {
        try {
            AuthResponseDto authResponse = authService.register(registerRequest);

            // Refresh Token을 httpOnly secure 쿠키로 설정
            setRefreshTokenCookie(response, authResponse.getRefreshToken());

            return createSuccessResponse("회원가입 성공", authResponse);
        } catch (RuntimeException e) {
            log.error("Registration failed for email: {}", registerRequest.getEmail(), e);
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * 로그아웃 API
     *
     * @param request HTTP 요청
     * @param response HTTP 응답
     * @param authentication 인증 정보
     * @return 로그아웃 성공 메시지
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request, HttpServletResponse response,
                                                      Authentication authentication) {
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }

        // Refresh Token 쿠키 제거
        clearRefreshTokenCookie(response);

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("success", true);
        responseMap.put("message", "로그아웃 성공");

        return ResponseEntity.ok(responseMap);
    }

    /**
     * JWT 토큰 갱신 API
     *
     * @param request HTTP 요청 (쿠키에서 Refresh Token 추출)
     * @param response HTTP 응답 (새로운 쿠키 설정용)
     * @return 새로운 Access Token과 사용자 정보 반환
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 쿠키에서 Refresh Token 추출
            String refreshToken = getRefreshTokenFromCookie(request);
            if (refreshToken == null) {
                return createErrorResponse("Refresh Token이 없습니다.");
            }

            AuthResponseDto authResponse = authService.refreshToken(refreshToken);

            // 새로운 Refresh Token을 쿠키로 설정
            setRefreshTokenCookie(response, authResponse.getRefreshToken());

            return createSuccessResponse("토큰 갱신 성공", authResponse);
        } catch (RuntimeException e) {
            log.error("Token refresh failed", e);
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * 인증 상태 확인 API
     *
     * @param request HTTP 요청
     * @return 현재 인증된 사용자 정보
     */
    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyAuth(HttpServletRequest request) {
        try {
            String accessToken = getAccessTokenFromRequest(request);
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
     * OAuth2 소셜 로그인 API
     *
     * @param request OAuth2 요청 정보 (provider, token)
     * @param response HTTP 응답 (쿠키 설정용)
     * @return OAuth2 로그인 URL 또는 인증된 사용자 정보
     */
    @PostMapping("/social-login")
    public ResponseEntity<Map<String, Object>> socialLogin(@RequestBody Map<String, String> request,
                                                           HttpServletResponse response) {
        String provider = request.get("provider");
        String token = request.get("token");

        if (token != null && !token.isEmpty()) {
            return handleOAuthCallback(provider, token, response);
        } else {
            return provideOAuthLoginUrl(provider);
        }
    }

    /**
     * OAuth2 콜백 처리
     */
    private ResponseEntity<Map<String, Object>> handleOAuthCallback(String provider, String token, HttpServletResponse response) {
        try {
            if (!jwtUtil.validateToken(token)) {
                return createErrorResponse("유효하지 않은 토큰입니다.");
            }

            String userId = jwtUtil.getIDFromToken(token);
            log.info("OAuth callback processing - provider: {}, userId: {}", provider, userId);

            UserDto user = userService.getUserInfoById(Long.parseLong(userId));

            // OAuth의 경우 Refresh Token도 생성하여 쿠키에 설정
            String refreshToken = jwtUtil.generateRefreshToken(userId);
            setRefreshTokenCookie(response, refreshToken);

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

    /**
     * OAuth2 로그인 URL 제공
     */
    private ResponseEntity<Map<String, Object>> provideOAuthLoginUrl(String provider) {
        String loginUrl = "/oauth2/authorization/" + provider.toLowerCase();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "소셜 로그인 URL");
        response.put("loginUrl", loginUrl);

        return ResponseEntity.ok(response);
    }

    /**
     * 비밀번호 재설정 요청 API (구현 예정)
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> request) {
        return createErrorResponse("비밀번호 재설정 기능이 아직 구현되지 않았습니다.");
    }

    /**
     * 비밀번호 재설정 API (구현 예정)
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        return createErrorResponse("비밀번호 재설정 기능이 아직 구현되지 않았습니다.");
    }

    /**
     * Google OAuth2 로그인 URL 제공
     */
    @GetMapping("/oauth2/google")
    public ResponseEntity<Map<String, Object>> getGoogleLoginUrl() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("loginUrl", "/oauth2/authorization/google"));
        return ResponseEntity.ok(response);
    }

    /**
     * GitHub OAuth2 로그인 URL 제공
     */
    @GetMapping("/oauth2/github")
    public ResponseEntity<Map<String, Object>> getGithubLoginUrl() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("loginUrl", "/oauth2/authorization/github"));
        return ResponseEntity.ok(response);
    }

    // ===== 헬퍼 메서드들 =====

    /**
     * Refresh Token을 httpOnly secure 쿠키로 설정
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(refreshTokenCookieName, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // HTTPS에서만 전송
        cookie.setPath("/");
        cookie.setDomain(refreshTokenCookieDomain);
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7일
        response.addCookie(cookie);
    }

    /**
     * Refresh Token 쿠키 제거
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(refreshTokenCookieName, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setDomain(refreshTokenCookieDomain);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    /**
     * 쿠키에서 Refresh Token 추출
     */
    private String getRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (refreshTokenCookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * HTTP 요청에서 Access Token 추출
     */
    private String getAccessTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 성공 응답 생성 헬퍼 메서드
     */
    private ResponseEntity<Map<String, Object>> createSuccessResponse(String message, AuthResponseDto authResponse) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("accessToken", authResponse.getAccessToken());
        response.put("tokenType", authResponse.getTokenType());
        response.put("user", authResponse.getUser());
        // Refresh Token은 쿠키에만 설정하므로 응답에 포함하지 않음
        return ResponseEntity.ok(response);
    }

    /**
     * 에러 응답 생성 헬퍼 메서드
     */
    private ResponseEntity<Map<String, Object>> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.badRequest().body(response);
    }
}