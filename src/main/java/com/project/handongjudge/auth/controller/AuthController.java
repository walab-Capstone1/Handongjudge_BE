package com.project.handongjudge.auth.controller;

import com.project.handongjudge.auth.service.AuthService;
import com.project.handongjudge.common.util.JwtUtil;
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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody AuthRequestDto.LoginRequest loginRequest) {
        Map<String, Object> response = new HashMap<>();

        try {
            AuthResponseDto authResponse = authService.login(loginRequest);
            response.put("success", true);
            response.put("message", "로그인 성공");
            response.put("accessToken", authResponse.getAccessToken());
            response.put("refreshToken", authResponse.getRefreshToken());
            response.put("tokenType", authResponse.getTokenType());
            response.put("user", authResponse.getUser());
            log.info("Login successful for email: {}", loginRequest.getEmail());
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            log.error("Login failed for email: {}", loginRequest.getEmail(), e);
            response.put("success", false);
            response.put("message", "이메일 또는 비밀번호가 잘못되었습니다.");
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody AuthRequestDto.RegisterRequest registerRequest) {
        Map<String, Object> response = new HashMap<>();

        try {
            AuthResponseDto authResponse = authService.register(registerRequest);
            response.put("success", true);
            response.put("message", "회원가입 성공");
            response.put("accessToken", authResponse.getAccessToken());
            response.put("refreshToken", authResponse.getRefreshToken());
            response.put("tokenType", authResponse.getTokenType());
            response.put("user", authResponse.getUser());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Registration failed for email: {}", registerRequest.getEmail(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }




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



    // 토큰 갱신 API
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@Valid @RequestBody AuthRequestDto.RefreshTokenRequest refreshTokenRequest) {
        Map<String, Object> response = new HashMap<>();

        try {
            AuthResponseDto authResponse = authService.refreshToken(refreshTokenRequest);
            response.put("success", true);
            response.put("message", "토큰 갱신 성공");
            response.put("accessToken", authResponse.getAccessToken());
            response.put("refreshToken", authResponse.getRefreshToken());
            response.put("tokenType", authResponse.getTokenType());
            response.put("user", authResponse.getUser());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Token refresh failed", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 소셜 로그인 정보 제공 API


    // 기존 AuthController의 /social-login 메서드를 다음과 같이 수정하세요:

    @PostMapping("/social-login")
    public ResponseEntity<Map<String, Object>> socialLogin(@RequestBody Map<String, String> request) {

        Map<String, Object> response = new HashMap<>();

        String provider = request.get("provider");
        String token = request.get("token");

        //log.info("Social login request received - provider: {}, token present: {}", provider, token != null);

        // 토큰이 있으면 OAuth 콜백 처리, 없으면 로그인 URL 제공
        if (token != null && !token.isEmpty()) {
            try {
                // 토큰 검증
                if (!jwtUtil.validateToken(token)) {
                    response.put("success", false);
                    response.put("message", "유효하지 않은 토큰입니다.");
                    return ResponseEntity.badRequest().body(response);
                }

                // 토큰에서 이메일 추출
                String Id = jwtUtil.getIDFromToken(token);
                log.info("Social login request received - provider: {}, token: {}", provider, token);
                // 사용자 정보 조회
                UserDto user = userService.getUserInfoById(Id);

                // 성공 응답 (프론트엔드 APIService 형식에 맞춤)
                response.put("success", true);
                response.put("message", "소셜 로그인 성공");
                response.put("user", user);
                response.put("accessToken", token);
                response.put("refreshToken", null); // OAuth는 보통 refresh token 없음
                response.put("tokenType", "Bearer");

                return ResponseEntity.ok(response);

            } catch (Exception e) {
                log.error("Social login token validation failed for provider: {}", provider, e);
                response.put("success", false);
                response.put("message", "소셜 로그인 처리 중 오류가 발생했습니다.");
                return ResponseEntity.badRequest().body(response);
            }
        } else {
            // 기존 로직 - OAuth2 플로우 URL 제공
            String loginUrl = "/oauth2/authorization/" + provider.toLowerCase();

            response.put("success", true);
            response.put("message", "소셜 로그인 URL");
            response.put("loginUrl", loginUrl);

            return ResponseEntity.ok(response);
        }
    }
    // 비밀번호 재설정 요청 (구현 예정)
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "비밀번호 재설정 기능이 아직 구현되지 않았습니다.");
        return ResponseEntity.badRequest().body(response);
    }

    // 비밀번호 재설정 (구현 예정)
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "비밀번호 재설정 기능이 아직 구현되지 않았습니다.");
        return ResponseEntity.badRequest().body(response);
    }

    // OAuth2Success는 SecurityConfig의 successHandler에서 직접 처리하므로 제거

    // OAuth 로그인 URL 제공 (프론트엔드에서 사용)
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
}