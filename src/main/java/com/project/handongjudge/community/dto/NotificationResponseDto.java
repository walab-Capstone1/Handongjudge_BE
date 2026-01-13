package com.project.handongjudge.community.dto;

import com.project.handongjudge.community.entity.Notification;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponseDto {

    private Long id;
    private Long actorId;
    private String actorName;
    private Long questionId;
    private String questionTitle;
    private Long commentId;
    private Long noticeId;
    private String noticeTitle;
    private Long assignmentId;
    private String assignmentTitle;
    private String type;
    private String message;
    private Boolean isRead;
    private LocalDateTime createdAt;

    // Entity -> DTO 변환
    public static NotificationResponseDto fromEntity(Notification notification) {
        return NotificationResponseDto.builder()
                .id(notification.getId())
                .actorId(notification.getActor().getId())
                .actorName(notification.getActor().getName())
                .questionId(notification.getQuestion() != null ? notification.getQuestion().getId() : null)
                .questionTitle(notification.getQuestion() != null ? notification.getQuestion().getTitle() : null)
                .commentId(notification.getComment() != null ? notification.getComment().getId() : null)
                .noticeId(notification.getNotice() != null ? notification.getNotice().getId() : null)
                .noticeTitle(notification.getNotice() != null ? notification.getNotice().getTitle() : null)
                .assignmentId(notification.getAssignment() != null ? notification.getAssignment().getId() : null)
                .assignmentTitle(notification.getAssignment() != null ? notification.getAssignment().getTitle() : null)
                .type(notification.getType().name())
                .message(notification.getMessage())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}

