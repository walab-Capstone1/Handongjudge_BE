package com.project.handongjudge.notice.entity;

import com.project.handongjudge.user.entity.User;
import javax.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 시스템 이용안내 Entity
 * 시스템 사용 방법, 가이드 등을 제공하는 문서
 */
@Entity
@Table(name = "system_guide")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemGuide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "category", length = 50)
    private String category; // 카테고리 (예: "사용법", "FAQ", "업데이트", "문의사항" 등)

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 0; // 우선순위 (숫자가 클수록 우선순위 높음)

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

