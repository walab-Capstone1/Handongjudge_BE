package com.project.handongjudge.auth.entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 서버 저장 Refresh Token.
 * - 중복 로그인 방지: 신규 로그인 시 기존 토큰 모두 삭제
 * - Rotation: /refresh 성공 시 기존 토큰 삭제 + 새 토큰 저장
 * - token_hash: SHA-256 해시값 저장 (원문은 클라이언트 쿠키에만)
 */
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_token_user_id", columnList = "user_id"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
