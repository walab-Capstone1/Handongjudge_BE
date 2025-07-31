package com.project.handongjudge.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;

@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token.expiration}")
    private Long accessTokenExpirationMs;

    @Value("${jwt.refresh-token.expiration}")
    private Long refreshTokenExpirationMs;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateAccessToken(Authentication authentication) {
        String identifier = authentication.getName();
        Date expiryDate = new Date(System.currentTimeMillis() + accessTokenExpirationMs);

        return Jwts.builder()
                .setSubject(identifier)
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .claim("type", "access")
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String generateAccessToken(String identifier) {
        Date expiryDate = new Date(System.currentTimeMillis() + accessTokenExpirationMs);

        return Jwts.builder()
                .setSubject(identifier)
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .claim("type", "access")
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String generateRefreshToken(String identifier) {
        Date expiryDate = new Date(System.currentTimeMillis() + refreshTokenExpirationMs);

        return Jwts.builder()
                .setSubject(identifier)
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .claim("type", "refresh")
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }



    // 기존 메서드들 (하위 호환성을 위해 유지)
    public String generateToken(Authentication authentication) {
        return generateAccessToken(authentication);
    }

    public String generateToken(String email) {
        return generateAccessToken(email);
    }



    public String getIDFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }



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
}