package com.project.handongjudge.user.entity;

import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.notice.entity.Notice;
import com.project.handongjudge.user.entity.User;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_read_status", 
       indexes = {
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

    // 공지사항 읽음 상태 (nullable, 공지사항인 경우에만 값 존재)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id")
    private Notice notice;

    // 과제 읽음 상태 (nullable, 과제인 경우에만 값 존재)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    // 읽음 상태 타입 (NOTICE 또는 ASSIGNMENT)
    @Enumerated(EnumType.STRING)
    @Column(name = "read_type", nullable = false)
    private ReadType readType;

    public enum ReadType {
        NOTICE, ASSIGNMENT
    }
}
