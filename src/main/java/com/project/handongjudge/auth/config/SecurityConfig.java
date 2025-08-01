package com.project.handongjudge.auth.config;

import com.project.handongjudge.auth.entity.AuthUser;
import com.project.handongjudge.auth.service.CustomOAuth2UserService;
import com.project.handongjudge.auth.util.JwtUtil;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;

/**
 * Spring Security 설정
 * 
 * 주요 기능:
 * - JWT 기반 인증 설정
 * - OAuth2 소셜 로그인 설정
 * - CORS 및 CSRF 설정
 * - 인증 필터 설정
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

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
                .cors().and()
                // CSRF 비활성화 (JWT 사용으로 인해)
                .csrf().disable()
                // 세션 정책 설정 (STATELESS - JWT 사용)
                .sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                // 요청 권한 설정
                .authorizeRequests()
                    .antMatchers("/api/**", "/oauth2/**", "/h2-console/**").permitAll()
                    .anyRequest().authenticated()
                .and()
                // OAuth2 로그인 설정
                .oauth2Login()
                .userInfoEndpoint()
                .userService(customOAuth2UserService)
                .and()
                // OAuth2 로그인 성공 핸들러
                .successHandler((request, response, authentication) -> {
                    handleOAuth2Success(request, response, authentication);
                })
                .and()
                // JWT 인증 필터 추가
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        // H2 Console을 위한 설정 (개발환경에서만)
        http.headers().frameOptions().disable();
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

            // 프론트엔드 콜백 페이지로 리다이렉트 (토큰 포함)
            String redirectUrl = String.format("http://localhost:3000/auth/callback?accessToken=%s&refreshToken=%s", 
                                             accessToken, refreshToken);
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            //log.error("OAuth2 login success handler failed", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "OAuth2 login failed");
        }
    }

    /**
     * JWT 인증 필터 빈 생성
     * 
     * @return JWT 인증 필터
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, userService);
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
        auth.userDetailsService(userDetailsService()).passwordEncoder(passwordEncoder);
    }

    /**
     * 사용자 상세 서비스 빈 생성
     * 
     * @return 사용자 상세 서비스
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return email -> {
            var user = userService.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

            return new org.springframework.security.core.userdetails.User(
                    user.getEmail(),
                    user.getPassword() != null ? user.getPassword() : "",
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
            );
        };
    }

    /**
     * JWT 인증 필터
     * 
     * 모든 요청에 대해 JWT 토큰을 검증하고 인증 정보를 설정
     */
    @Component
    @RequiredArgsConstructor
    public static class JwtAuthenticationFilter extends OncePerRequestFilter {

        private final JwtUtil jwtUtil;
        private final UserService userService;

        /**
         * 필터 내부 처리
         * 
         * @param request HTTP 요청
         * @param response HTTP 응답
         * @param filterChain 필터 체인
         * @throws ServletException 서블릿 예외
         * @throws IOException IO 예외
         */
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            try {
                // 요청에서 JWT 토큰 추출
                String jwt = getJwtFromRequest(request);

                // JWT 토큰이 있고 유효한 경우
                if (StringUtils.hasText(jwt) && jwtUtil.validateToken(jwt)) {
                    // 토큰에서 사용자 ID 추출
                    String userId = jwtUtil.getIDFromToken(jwt);

                    // 사용자 ID 유효성 검사 및 파싱
                    Long userIdLong = validateAndParseUserId(userId);
                    
                    // 사용자 정보 조회 (ID로만 조회)
                    var userOptional = userService.findById(userIdLong);
                    
                    if (userOptional.isPresent()) {
                        var user = userOptional.get();

                        // Spring Security 인증 토큰 생성
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userId, // 사용자 ID를 principal로 사용
                                        null,
                                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                                );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        // SecurityContext에 인증 정보 설정
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception ex) {
                logger.error("Could not set user authentication in security context", ex);
            }

            // 다음 필터로 요청 전달
            filterChain.doFilter(request, response);
        }

        /**
         * HTTP 요청에서 JWT 토큰 추출
         * 
         * @param request HTTP 요청
         * @return JWT 토큰 (없으면 null)
         */
        private String getJwtFromRequest(HttpServletRequest request) {
            String bearerToken = request.getHeader("Authorization");
            if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
            return null;
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
}