package com.project.handongjudge.community.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.handongjudge.notice.entity.Notice;
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.user.entity.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_recipient", columnList = "recipient_id"),
        @Index(name = "idx_notification_read", columnList = "is_read"),
        @Index(name = "idx_notification_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    // 알림을 받을 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    @JsonIgnore
    private User recipient;

    // 알림을 발생시킨 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    @JsonIgnore
    private User actor;

    // 관련된 질문
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    @JsonIgnore
    private Question question;

    // 관련된 댓글 (선택적)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    @JsonIgnore
    private Comment comment;

    // 관련된 공지사항 (선택적)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id")
    @JsonIgnore
    private Notice notice;

    // 관련된 과제 (선택적)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    @JsonIgnore
    private Assignment assignment;

    // 알림 타입
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    // 알림 메시지
    @Column(name = "message", nullable = false, length = 500)
    private String message;

    // 읽음 여부
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 알림 타입 Enum
    public enum NotificationType {
        QUESTION_COMMENT,      // 내 질문에 댓글 달림
        COMMENT_REPLY,         // 내 댓글에 답글 달림
        QUESTION_PINNED,       // 내 질문이 핀됨
        QUESTION_RESOLVED,     // 내 질문이 해결됨
        COMMENT_ACCEPTED,      // 내 댓글이 채택됨
        QUESTION_LIKED,        // 내 질문에 추천
        COMMENT_LIKED,         // 내 댓글에 추천
        NOTICE_CREATED,        // 새 공지사항 작성됨
        ASSIGNMENT_CREATED     // 새 과제 생성됨
    }

    // 비즈니스 메서드
    public void markAsRead() {
        this.isRead = true;
    }

    public void markAsUnread() {
        this.isRead = false;
    }
}

