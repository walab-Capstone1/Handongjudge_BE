package com.project.handongjudge.community.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.user.entity.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions", indexes = {
        @Index(name = "idx_question_section", columnList = "section_id"),
        @Index(name = "idx_question_author", columnList = "author_id"),
        @Index(name = "idx_question_status", columnList = "status"),
        @Index(name = "idx_question_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long id;

    // 필수: 어느 섹션(수업)의 질문인가
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    @JsonIgnore
    private Section section;

    // 선택: 특정 과제와 연관된 질문
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    @JsonIgnore
    private Assignment assignment;

    // 선택: 특정 문제와 연관된 질문
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    @JsonIgnore
    private Problem problem;

    // 질문 작성자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    @JsonIgnore
    private User author;

    // 질문 제목
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // 질문 내용 (웹에디터 HTML)
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 익명 여부
    @Column(name = "is_anonymous", nullable = false)
    @Builder.Default
    private Boolean isAnonymous = false;

    // 공개/비공개 (false: 공개, true: 비공개 - 교수만 볼 수 있음)
    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = true;

    // 교수 핀 여부
    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    private Boolean isPinned = false;

    // 해결 상태 (PENDING, RESOLVED)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private QuestionStatus status = QuestionStatus.PENDING;

    // 작성자 표시명 (익명: 별명, 실명: 실제 이름)
    @Column(name = "author_display_name", nullable = false, length = 50)
    private String authorDisplayName;

    // 조회수
    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    // 추천 수 (캐싱용)
    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private Integer likeCount = 0;

    // 댓글 수 (캐싱용)
    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private Integer commentCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 댓글 리스트
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    // 추천 리스트
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QuestionLike> likes = new ArrayList<>();

    // 상태 Enum
    public enum QuestionStatus {
        PENDING,   // 미해결
        RESOLVED   // 해결됨
    }

    // 비즈니스 메서드
    public void incrementViewCount() {
        this.viewCount++;
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void incrementCommentCount() {
        this.commentCount++;
    }

    public void decrementCommentCount() {
        if (this.commentCount > 0) {
            this.commentCount--;
        }
    }

    public void resolve() {
        this.status = QuestionStatus.RESOLVED;
    }

    public void reopen() {
        this.status = QuestionStatus.PENDING;
    }

    public void pin() {
        this.isPinned = true;
    }

    public void unpin() {
        this.isPinned = false;
    }
}

