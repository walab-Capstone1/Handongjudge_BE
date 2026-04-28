package com.project.handongjudge.community.dto;

import com.project.handongjudge.community.entity.Question;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionResponseDto {

    private Long id;
    private Long sectionId;
    private String sectionName;
    private Long assignmentId;
    private String assignmentTitle;
    private Long problemId;
    private String problemTitle;
    private Long authorId;
    private String authorDisplayName;
    private String title;
    private String content;
    private Boolean isAnonymous;
    /** 익명 질문일 때 별명 표시 여부(null이면 레거시: 별명 사용으로 간주) */
    private Boolean anonymousUseNickname;
    private Boolean isPublic;
    private Boolean isPinned;
    private String status;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isLikedByCurrentUser;  // 현재 사용자가 추천했는지
    private Boolean isAuthor;  // 현재 사용자가 작성자인지

    /** 교수/TA만 수신: 실제 작성자 이름 */
    private String authorRealNameForStaff;

    // Entity -> DTO 변환
    public static QuestionResponseDto fromEntity(Question question) {
        return QuestionResponseDto.builder()
                .id(question.getId())
                .sectionId(question.getSection().getId())
                .sectionName(question.getSection().getCourse().getTitle() + " " + 
                           question.getSection().getSectionNumber() + "분반")
                .assignmentId(question.getAssignment() != null ? question.getAssignment().getId() : null)
                .assignmentTitle(question.getAssignment() != null ? question.getAssignment().getTitle() : null)
                .problemId(question.getProblem() != null ? question.getProblem().getId() : null)
                .problemTitle(question.getProblem() != null ? question.getProblem().getTitle() : null)
                .authorDisplayName(question.getAuthorDisplayName())
                .title(question.getTitle())
                .content(question.getContent())
                .isAnonymous(question.getIsAnonymous())
                .anonymousUseNickname(question.getAnonymousUseNickname())
                .isPublic(question.getIsPublic())
                .isPinned(question.getIsPinned())
                .status(question.getStatus().name())
                .viewCount(question.getViewCount())
                .likeCount(question.getLikeCount())
                .commentCount(question.getCommentCount())
                .createdAt(question.getCreatedAt())
                .updatedAt(question.getUpdatedAt())
                .build();
    }
}


