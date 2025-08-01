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
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 인증 관련 API 컨트롤러
 * 
 * 주요 기능:
 * - 일반 로그인/회원가입
 * - OAuth2 소셜 로그인
 * - JWT 토큰 갱신
 * - 로그아웃
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${FRONTEND_URL:http://localhost:3000}"})
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @Value("${FRONTEND_URL:http://localhost:3000}")
    private String frontendUrl;

    /**
     * 일반 로그인 API
     * 
     * @param loginRequest 로그인 요청 정보 (이메일, 비밀번호)
     * @return 로그인 성공 시 JWT 토큰과 사용자 정보 반환
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody AuthRequestDto.LoginRequest loginRequest) {
        try {
            AuthResponseDto authResponse = authService.login(loginRequest);
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
     * @return 회원가입 성공 시 JWT 토큰과 사용자 정보 반환
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody AuthRequestDto.RegisterRequest registerRequest) {
        try {
            AuthResponseDto authResponse = authService.register(registerRequest);
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

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("success", true);
        responseMap.put("message", "로그아웃 성공");

        return ResponseEntity.ok(responseMap);
    }

    /**
     * JWT 토큰 갱신 API
     * 
     * @param refreshTokenRequest 리프레시 토큰 요청
     * @return 새로운 액세스 토큰과 사용자 정보 반환
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@Valid @RequestBody AuthRequestDto.RefreshTokenRequest refreshTokenRequest) {
        try {
            AuthResponseDto authResponse = authService.refreshToken(refreshTokenRequest);
            return createSuccessResponse("토큰 갱신 성공", authResponse);
        } catch (RuntimeException e) {
            log.error("Token refresh failed", e);
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * OAuth2 소셜 로그인 API
     * 
     * 토큰이 제공된 경우: OAuth2 콜백 처리 (사용자 인증 완료)
     * 토큰이 없는 경우: OAuth2 로그인 URL 제공
     * 
     * @param request OAuth2 요청 정보 (provider, token)
     * @return OAuth2 로그인 URL 또는 인증된 사용자 정보
     */
    @PostMapping("/social-login")
    public ResponseEntity<Map<String, Object>> socialLogin(@RequestBody Map<String, String> request) {
        String provider = request.get("provider");
        String token = request.get("token");

        // 토큰이 있으면 OAuth 콜백 처리, 없으면 로그인 URL 제공
        if (token != null && !token.isEmpty()) {
            return handleOAuthCallback(provider, token);
        } else {
            return provideOAuthLoginUrl(provider);
        }
    }

    /**
     * OAuth2 콜백 처리
     * 
     * @param provider OAuth 제공자 (google, github 등)
     * @param token OAuth2 액세스 토큰
     * @return 인증된 사용자 정보와 JWT 토큰
     */
    private ResponseEntity<Map<String, Object>> handleOAuthCallback(String provider, String token) {
        try {
            // 토큰 유효성 검사
            if (!jwtUtil.validateToken(token)) {
                return createErrorResponse("유효하지 않은 토큰입니다.");
            }

            // 토큰에서 사용자 ID 추출
            String userId = jwtUtil.getIDFromToken(token);
            log.info("OAuth callback processing - provider: {}, userId: {}", provider, userId);
            
            // 사용자 정보 조회
            UserDto user = userService.getUserInfoById(userId);

            // 성공 응답 (프론트엔드 APIService 형식에 맞춤)
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "소셜 로그인 성공");
            response.put("user", user);
            response.put("accessToken", token);
            response.put("refreshToken", null); // OAuth는 보통 refresh token 없음
            response.put("tokenType", "Bearer");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("OAuth callback processing failed for provider: {}", provider, e);
            return createErrorResponse("소셜 로그인 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * OAuth2 로그인 URL 제공
     * 
     * @param provider OAuth 제공자 (google, github 등)
     * @return OAuth2 로그인 URL
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
     * 
     * @param request 비밀번호 재설정 요청 정보
     * @return 구현 예정 메시지
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> request) {
        return createErrorResponse("비밀번호 재설정 기능이 아직 구현되지 않았습니다.");
    }

    /**
     * 비밀번호 재설정 API (구현 예정)
     * 
     * @param request 비밀번호 재설정 정보
     * @return 구현 예정 메시지
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        return createErrorResponse("비밀번호 재설정 기능이 아직 구현되지 않았습니다.");
    }

    /**
     * Google OAuth2 로그인 URL 제공
     * 
     * @return Google OAuth2 로그인 URL
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
     * 
     * @return GitHub OAuth2 로그인 URL
     */
    @GetMapping("/oauth2/github")
    public ResponseEntity<Map<String, Object>> getGithubLoginUrl() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("loginUrl", "/oauth2/authorization/github"));
        return ResponseEntity.ok(response);
    }

    /**
     * 성공 응답 생성 헬퍼 메서드
     * 
     * @param message 성공 메시지
     * @param authResponse 인증 응답 데이터
     * @return 성공 응답
     */
    private ResponseEntity<Map<String, Object>> createSuccessResponse(String message, AuthResponseDto authResponse) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("accessToken", authResponse.getAccessToken());
        response.put("refreshToken", authResponse.getRefreshToken());
        response.put("tokenType", authResponse.getTokenType());
        response.put("user", authResponse.getUser());
        return ResponseEntity.ok(response);
    }

    /**
     * 에러 응답 생성 헬퍼 메서드
     * 
     * @param message 에러 메시지
     * @return 에러 응답
     */
    private ResponseEntity<Map<String, Object>> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.badRequest().body(response);
    }
}