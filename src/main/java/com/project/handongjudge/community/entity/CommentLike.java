package com.project.handongjudge.community.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.handongjudge.user.entity.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comment_likes", 
       uniqueConstraints = @UniqueConstraint(
           name = "uk_comment_user_like", 
           columnNames = {"comment_id", "user_id"}
       ),
       indexes = {
           @Index(name = "idx_comment_like_comment", columnList = "comment_id"),
           @Index(name = "idx_comment_like_user", columnList = "user_id")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "like_id")
    private Long id;

    // 어느 댓글에 대한 추천인가
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    @JsonIgnore
    private Comment comment;

    // 누가 추천했는가
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

