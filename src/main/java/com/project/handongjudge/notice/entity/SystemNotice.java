package com.project.handongjudge.notice.entity;

import com.project.handongjudge.user.entity.User;
import javax.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 시스템 전체 공지사항 Entity
 * Section과 무관하게 모든 사용자에게 표시되는 공지사항
 */
@Entity
@Table(name = "system_notice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 0; // 우선순위 (숫자가 클수록 우선순위 높음)

    @Column(name = "is_new", nullable = false)
    @Builder.Default
    private boolean isNew = true;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy; // 작성자 (시스템 관리자)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

