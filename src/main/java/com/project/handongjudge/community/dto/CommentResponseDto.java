package com.project.handongjudge.community.dto;

import com.project.handongjudge.community.entity.Comment;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentResponseDto {

    private Long id;
    private Long questionId;
    private Long authorId;
    private String authorDisplayName;
    private String content;
    private Boolean isAnonymous;
    private Boolean isInstructorAnswer;
    private Boolean isAccepted;
    private Integer likeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isLikedByCurrentUser;  // 현재 사용자가 추천했는지
    private Boolean isAuthor;  // 현재 사용자가 작성자인지

    // Entity -> DTO 변환
    public static CommentResponseDto fromEntity(Comment comment) {
        return CommentResponseDto.builder()
                .id(comment.getId())
                .questionId(comment.getQuestion().getId())
                .authorId(comment.getAuthor().getId())
                .authorDisplayName(comment.getAuthorDisplayName())
                .content(comment.getContent())
                .isAnonymous(comment.getIsAnonymous())
                .isInstructorAnswer(comment.getIsInstructorAnswer())
                .isAccepted(comment.getIsAccepted())
                .likeCount(comment.getLikeCount())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}


