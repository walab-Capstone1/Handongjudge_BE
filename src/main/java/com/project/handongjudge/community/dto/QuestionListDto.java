package com.project.handongjudge.community.dto;

import com.project.handongjudge.community.entity.Question;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionListDto {

    private Long id;
    private String title;
    private String authorDisplayName;
    private Boolean isAnonymous;
    private Boolean isPinned;
    private String status;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private LocalDateTime createdAt;
    private String assignmentTitle;
    private String problemTitle;
    private Boolean hasAcceptedAnswer;
    private Boolean isPublic;

    // Entity -> DTO 변환 (목록용 축약)
    public static QuestionListDto fromEntity(Question question) {
        return QuestionListDto.builder()
                .id(question.getId())
                .title(question.getTitle())
                .authorDisplayName(question.getAuthorDisplayName())
                .isAnonymous(question.getIsAnonymous())
                .isPinned(question.getIsPinned())
                .status(question.getStatus().name())
                .viewCount(question.getViewCount())
                .likeCount(question.getLikeCount())
                .commentCount(question.getCommentCount())
                .createdAt(question.getCreatedAt())
                .assignmentTitle(question.getAssignment() != null ? question.getAssignment().getTitle() : null)
                .problemTitle(question.getProblem() != null ? question.getProblem().getTitle() : null)
                .isPublic(question.getIsPublic())
                .build();
    }
}


