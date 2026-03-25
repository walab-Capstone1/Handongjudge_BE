package com.project.handongjudge.auth.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;
import java.util.Optional;

/**
 * JWT 토큰 생성 및 검증 유틸리티
 *
 * 주요 기능:
 * - Access Token 생성 및 검증
 * - Refresh Token 생성 및 검증
 * - 토큰에서 사용자 정보 추출
 * - 토큰 타입 구분 (access/refresh)
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token.expiration}")
    private Long accessTokenExpirationMs;

    @Value("${jwt.refresh-token.expiration}")
    private Long refreshTokenExpirationMs;

    /**
     * JWT 서명 키 생성
     *
     * @return HMAC-SHA512 서명 키
     */
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Authentication 객체로부터 Access Token 생성
     *
     * @param authentication Spring Security 인증 객체
     * @return JWT Access Token
     */
    public String generateAccessToken(Authentication authentication) {
        String identifier = authentication.getName();
        return generateAccessToken(identifier);
    }

    /**
     * 사용자 식별자로부터 Access Token 생성
     *
     * @param identifier 사용자 ID 또는 이메일
     * @return JWT Access Token
     */
    public String generateAccessToken(String identifier) {
        Date expiryDate = new Date(System.currentTimeMillis() + accessTokenExpirationMs);

        return Jwts.builder()
                .setSubject(identifier)           // 사용자 식별자 (ID 또는 이메일)
                .setIssuedAt(new Date())         // 토큰 발행일
                .setExpiration(expiryDate)       // 토큰 만료일
                .claim("type", "access")         // 토큰 타입 구분
                .claim("role", "USER")           // 기본 역할 (나중에 사용자별로 설정 가능)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * 사용자 식별자로부터 Refresh Token 생성
     *
     * @param identifier 사용자 ID 또는 이메일
     * @return JWT Refresh Token
     */
    public String generateRefreshToken(String identifier) {
        Date expiryDate = new Date(System.currentTimeMillis() + refreshTokenExpirationMs);

        return Jwts.builder()
                .setSubject(identifier)           // 사용자 식별자 (ID 또는 이메일)
                .setIssuedAt(new Date())         // 토큰 발행일
                .setExpiration(expiryDate)       // 토큰 만료일
                .claim("type", "refresh")        // 토큰 타입 구분
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * JWT 토큰에서 사용자 ID 추출
     *
     * @param token JWT 토큰
     * @return 사용자 ID (문자열)
     */
    public String getIDFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    /**
     * JWT 토큰에서 사용자 역할 추출
     *
     * @param token JWT 토큰
     * @return 사용자 역할 (문자열)
     */
    public String getRoleFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.get("role", String.class);
        } catch (Exception e) {
            log.warn("Failed to extract role from token: {}", e.getMessage());
            return "USER"; // 기본 역할
        }
    }

    /**
     * JWT 토큰에서 사용자 ID 추출 (Long 타입)
     *
     * @param token JWT 토큰
     * @return 사용자 ID (Long)
     * @throws NumberFormatException ID가 숫자가 아닌 경우
     */
    public Long getUserIdFromToken(String token) {
        String userIdStr = getIDFromToken(token);
        return Long.parseLong(userIdStr);
    }

    /**
     * JWT 토큰 유효성 검사
     *
     * @param authToken JWT 토큰
     * @return 토큰 유효성 여부
     */
    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT token validation failed: ", e);
            return false;
        }
    }

    /**
     * 토큰이 만료되었는지 여부만 체크 (서명은 유효하지만 만료된 경우)
     * 필터에서 만료 여부를 401로 분기할 때 사용
     *
     * @param authToken JWT 토큰
     * @return 만료 여부 (true = 만료됨)
     */
    public boolean isTokenExpired(String authToken) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(authToken);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Refresh Token 유효성 검사
     *
     * @param refreshToken JWT Refresh Token
     * @return Refresh Token 유효성 여부
     */
    public boolean validateRefreshToken(String refreshToken) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(refreshToken)
                    .getBody();

            // refresh 토큰인지 확인
            String tokenType = claims.get("type", String.class);
            return "refresh".equals(tokenType);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Refresh token validation failed: ", e);
            return false;
        }
    }

    /**
     * JWT 토큰에서 토큰 타입 추출
     *
     * @param token JWT 토큰
     * @return 토큰 타입 (access/refresh)
     */
    public String getTokenType(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.get("type", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Failed to get token type: ", e);
            return null;
        }
    }

    // ===== 하위 호환성을 위한 메서드들 =====

    /**
     * 하위 호환성을 위한 메서드 - Access Token 생성으로 위임
     *
     * @param authentication Spring Security 인증 객체
     * @return JWT Access Token
     * @deprecated generateAccessToken() 사용 권장
     */
    @Deprecated
    public String generateToken(Authentication authentication) {
        return generateAccessToken(authentication);
    }

    /**
     * 하위 호환성을 위한 메서드 - Access Token 생성으로 위임
     *
     * @param email 사용자 이메일
     * @return JWT Access Token
     * @deprecated generateAccessToken() 사용 권장
     */
    @Deprecated
    public String generateToken(String email) {
        return generateAccessToken(email);
    }
}