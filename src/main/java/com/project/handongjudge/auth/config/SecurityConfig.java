package com.project.handongjudge.auth.config;

import com.project.handongjudge.auth.entity.AuthUser;
import com.project.handongjudge.auth.service.CustomOAuth2UserService;
import com.project.handongjudge.auth.service.CustomUserDetailsService;
import com.project.handongjudge.auth.service.RefreshTokenService;
import com.project.handongjudge.auth.util.AuthCookieUtil;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 설정
 *
 * 토큰 관리 방식:
 * - Access Token: 응답 본문에 포함 (프론트엔드 메모리에 저장)
 * - Refresh Token: HttpOnly Secure 쿠키에 저장 + DB에 해시 보관 (중복 로그인 방지)
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
    private final AuthCookieUtil authCookieUtil;
    private final CorsProperties corsProperties;
    private final RefreshTokenService refreshTokenService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .cors().configurationSource(corsConfigurationSource())
                .and()
                .csrf().disable()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers("/api/**", "/oauth2/**", "/h2-console/**").permitAll()
                .antMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
                .and()
                .oauth2Login()
                    .authorizationEndpoint()
                        .baseUri("/api/oauth2/authorization")
                    .and()
                    .redirectionEndpoint()
                        .baseUri("/api/login/oauth2/code/*")
                    .and()
                    .userInfoEndpoint()
                        .userService(customOAuth2UserService)
                    .and()
                    .successHandler((request, response, authentication) ->
                            handleOAuth2Success(request, response, authentication))
                    .failureHandler((request, response, exception) -> {
                        log.error("OAuth2 login failed", exception);
                        response.sendRedirect("https://hcl.walab.info/login?error=oauth_failed");
                    })
                .and()
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        http.headers().frameOptions().disable();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = corsProperties.getAllowedOrigins();
        if (origins == null || origins.isEmpty()) {
            // 프로파일 설정이 없을 경우 로컬 fallback
            origins = Arrays.asList(
                    "http://localhost:3000", "https://localhost:3000",
                    "http://localhost:3001", "https://localhost:3001"
            );
        }
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void handleOAuth2Success(HttpServletRequest request, HttpServletResponse response,
                                     org.springframework.security.core.Authentication authentication) throws IOException {
        try {
            AuthUser authUser = (AuthUser) authentication.getPrincipal();
            User user = authUser.getUser();

            String accessToken = jwtUtil.generateAccessToken(String.valueOf(user.getId()));
            String refreshToken = jwtUtil.generateRefreshToken(String.valueOf(user.getId()));

            // 세션 무효화
            try {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
            } catch (Exception e) {
                log.warn("세션 무효화 중 오류:", e);
            }

            // JSESSIONID 쿠키 제거
            Cookie jsessionCookie = new Cookie("JSESSIONID", "");
            jsessionCookie.setMaxAge(0);
            jsessionCookie.setHttpOnly(true);
            jsessionCookie.setPath("/");
            response.addCookie(jsessionCookie);

            // Refresh Token DB 저장 (중복 로그인 방지: 기존 토큰 삭제 후 저장)
            refreshTokenService.saveToken(user.getId(), refreshToken);

            // Refresh Token을 HttpOnly 쿠키로 설정
            authCookieUtil.setRefreshTokenCookie(response, refreshToken);

            String redirectUrl = String.format(
                    "https://hcl.walab.info/auth/callback?accessToken=%s", accessToken);
            log.info("OAuth2 success - redirecting to callback");
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.error("OAuth2 login success handler failed", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "OAuth2 login failed");
        }
    }

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(customUserDetailsService).passwordEncoder(passwordEncoder());
    }
}
