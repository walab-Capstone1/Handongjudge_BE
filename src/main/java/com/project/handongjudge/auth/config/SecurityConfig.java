package com.project.handongjudge.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.handongjudge.auth.entity.AuthUser;
import com.project.handongjudge.auth.service.CustomOAuth2UserService;
import com.project.handongjudge.auth.service.CustomUserDetailsService;
import com.project.handongjudge.auth.util.JwtAuthenticationFilter;
import com.project.handongjudge.auth.util.JwtUtil;
import com.project.handongjudge.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Cookie;
import java.io.IOException;
import java.util.Arrays;

/**
 * Spring Security 설정
 *
 * 새로운 토큰 관리 방식:
 * - Access Token: 응답 본문에 포함 (프론트엔드 메모리에 저장)
 * - Refresh Token: httpOnly secure 쿠키에 저장
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtUtil jwtUtil;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * PasswordEncoder 빈 생성
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    /**
     * HTTP 보안 설정
     *
     * @param http HttpSecurity 객체
     * @throws Exception 설정 오류 시
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                // CORS 설정
                .cors().configurationSource(corsConfigurationSource())
                .and()
                // CSRF 비활성화 (JWT 사용으로 인해)
                .csrf().disable()
                // 세션 정책 설정 (NEVER - 세션 생성 완전 방지)
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.NEVER)
                .and()
                // 요청 권한 설정
                .authorizeRequests()
              //  .antMatchers("/api/user/dashboard").authenticated()
                .antMatchers("/api/**", "/oauth2/**", "/h2-console/**").permitAll()
                .antMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                .anyRequest().authenticated()
                .and()
                // OAuth2 로그인 설정
                .oauth2Login()
                    .authorizationEndpoint()
                        .baseUri("/api/oauth2/authorization")   // ✅ 이 한 줄 추가 (Spring이 /api/oauth2/* 도 인식하게 함)
                    .and()
                // added
                .redirectionEndpoint()
                .baseUri("/api/login/oauth2/code/*")
                .and()
                // added

                .userInfoEndpoint()
                .userService(customOAuth2UserService)
                .and()
                // OAuth2 로그인 성공 핸들러
                .successHandler((request, response, authentication) -> {
                    handleOAuth2Success(request, response, authentication);
                })
                // OAuth2 로그인 실패 핸들러
                .failureHandler((request, response, exception) -> {
                    log.error("OAuth2 login failed", exception);
                    response.sendRedirect("https://hj.walab.info/handongjudge/login?error=oauth_failed");
                })
                .and()
                // JWT 인증 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // H2 Console을 위한 설정 (개발환경에서만)
        http.headers().frameOptions().disable();
    }

    /**
     * CORS 설정
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 개발 환경에서는 localhost 허용, 프로덕션에서는 실제 도메인으로 변경
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:3000",
                "https://localhost:3000",
                "http://localhost:3001",
                "https://localhost:3001",
                "https://hj.walab.info"
        ));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(Arrays.asList("Authorization"));

        // 프리플라이트 요청 캐시 시간 설정
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * OAuth2 로그인 성공 처리
     *
     * @param request HTTP 요청
     * @param response HTTP 응답
     * @param authentication 인증 정보
     * @throws IOException 리다이렉트 실패 시
     */
    private void handleOAuth2Success(HttpServletRequest request, HttpServletResponse response,
                                     org.springframework.security.core.Authentication authentication) throws IOException {
        try {
            AuthUser authUser = (AuthUser) authentication.getPrincipal();
            User user = authUser.getUser();

            // ID 기반으로 JWT 토큰 생성 (OAuth 사용자용)
            String accessToken = jwtUtil.generateAccessToken(String.valueOf(user.getId()));
            String refreshToken = jwtUtil.generateRefreshToken(String.valueOf(user.getId()));

            // 세션 완전 무효화 (JSESSIONID 제거)
            try {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                    log.info("OAuth2 세션 무효화 완료");
                }
            } catch (Exception e) {
                log.warn("세션 무효화 중 오류:", e);
            }

            // JSESSIONID 쿠키 제거
            Cookie jsessionCookie = new Cookie("JSESSIONID", "");
            jsessionCookie.setMaxAge(0);
            jsessionCookie.setPath("/");
            response.addCookie(jsessionCookie);

            // Refresh Token을 httpOnly secure 쿠키로 설정
            setRefreshTokenCookie(response, refreshToken);

            // 프론트엔드 콜백 페이지로 리다이렉트 (Access Token만 포함)
            String redirectUrl = String.format("https://hj.walab.info/handongjudge/auth/callback?accessToken=%s", accessToken);
            log.info("OAuth2 success - redirecting to: {}", redirectUrl);
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.error("OAuth2 login success handler failed", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "OAuth2 login failed");
        }
    }

    /**
     * Refresh Token을 httpOnly secure 쿠키로 설정
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // 개발환경에서는 HTTP 사용
        cookie.setPath("/");
        cookie.setDomain("hj.walab.info");
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7일
        response.addCookie(cookie);
    }

    /**
     * 인증 매니저 빈 생성
     *
     * @return 인증 매니저
     * @throws Exception 빈 생성 실패 시
     */
    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    /**
     * 인증 매니저 설정
     *
     * @param auth AuthenticationManagerBuilder
     * @throws Exception 설정 실패 시
     */
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(customUserDetailsService).passwordEncoder(passwordEncoder());
    }
}