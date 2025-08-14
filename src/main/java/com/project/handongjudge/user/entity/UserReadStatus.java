package com.project.handongjudge.user.entity;

import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.notice.entity.Notice;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_read_status", indexes = {
        @Index(name = "idx_user_notice", columnList = "user_id, notice_id"),
        @Index(name = "idx_user_assignment", columnList = "user_id, assignment_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserReadStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id")
    private Notice notice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @Enumerated(EnumType.STRING)
    @Column(name = "read_type", nullable = false)
    private ReadType readType;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    public enum ReadType {
        NOTICE, ASSIGNMENT
    }
}