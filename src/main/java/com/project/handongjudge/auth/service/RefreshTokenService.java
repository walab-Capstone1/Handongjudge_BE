package com.project.handongjudge.auth.service;

import com.project.handongjudge.auth.entity.RefreshToken;
import com.project.handongjudge.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Refresh Token 서버 저장/검증/삭제.
 * - 신규 로그인 시 기존 토큰 삭제 후 새 토큰 저장 → 사용자당 항상 1개만 유지
 * - 같은 크롬 프로필 내에서는 쿠키 공유로 자동 로그인 복구 가능
 * - Rotation: 검증 성공 시 기존 삭제 + 새 토큰 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token.expiration}")
    private Long refreshTokenExpirationMs;

    /** 신규 로그인: 기존 토큰 삭제 후 새 토큰 저장 (사용자당 1개만 유지) */
    @Transactional
    public void saveToken(Long userId, String rawToken) {
        refreshTokenRepository.deleteByUserId(userId);
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000))
                .createdAt(LocalDateTime.now())
                .build());
        log.debug("Refresh token saved for userId={}", userId);
    }

    /** 토큰 검증: 저장된 해시와 일치해야 유효 */
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findValidToken(String rawToken) {
        String hash = hash(rawToken);
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(hash);
        if (found.isPresent() && found.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Refresh token expired in DB: userId={}", found.get().getUserId());
            return Optional.empty();
        }
        return found;
    }

    /** Rotation: 기존 토큰 삭제 + 새 토큰 저장 */
    @Transactional
    public void rotateToken(String oldRawToken, Long userId, String newRawToken) {
        refreshTokenRepository.deleteByTokenHash(hash(oldRawToken));
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(newRawToken))
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000))
                .createdAt(LocalDateTime.now())
                .build());
        log.debug("Refresh token rotated for userId={}", userId);
    }

    /** 로그아웃: 해당 토큰 삭제 */
    @Transactional
    public void deleteToken(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokenRepository.deleteByTokenHash(hash(rawToken));
        }
    }

    /** 만료된 토큰 정리 (주기적 호출 가능) */
    @Transactional
    public void deleteExpired() {
        refreshTokenRepository.deleteExpired(LocalDateTime.now());
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
