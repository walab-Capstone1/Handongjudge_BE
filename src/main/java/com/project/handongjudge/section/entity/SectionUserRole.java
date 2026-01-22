package com.project.handongjudge.section.entity;

import com.project.handongjudge.user.entity.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 수업별 사용자 역할 관리 엔티티
 * Enrollment는 수강 관계만 관리하고, 역할은 이 엔티티에서 통합 관리
 */
@Entity
@Table(name = "section_user_role", indexes = {
        @Index(name = "idx_section_user", columnList = "section_id, user_id"),
        @Index(name = "idx_user_section", columnList = "user_id, section_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionUserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private SectionRole role;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 수업별 역할 타입
     * - ADMIN: 수업 생성자, 수업 관리 권한
     * - TUTOR: 교수가 지정한 조교, 제한적 관리 권한
     * - STUDENT: 수강생, 기본 권한
     */
    public enum SectionRole {
        ADMIN,      // 수업 관리자 (생성자)
        TUTOR,      // 조교
        STUDENT     // 수강생
    }
}

