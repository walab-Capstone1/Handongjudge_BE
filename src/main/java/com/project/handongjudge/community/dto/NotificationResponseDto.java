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
    private Long sectionId;
    private String courseTitle;
    private String type;
    private String message;
    private Boolean isRead;
    private LocalDateTime createdAt;

    // Entity -> DTO 변환
    public static NotificationResponseDto fromEntity(Notification notification) {
        // Section 정보 추출
        Long sectionId = null;
        String courseTitle = null;
        
        if (notification.getNotice() != null && notification.getNotice().getSection() != null) {
            sectionId = notification.getNotice().getSection().getId();
            courseTitle = notification.getNotice().getSection().getCourse().getTitle();
        } else if (notification.getAssignment() != null && notification.getAssignment().getSection() != null) {
            sectionId = notification.getAssignment().getSection().getId();
            courseTitle = notification.getAssignment().getSection().getCourse().getTitle();
        } else if (notification.getQuestion() != null && notification.getQuestion().getSection() != null) {
            sectionId = notification.getQuestion().getSection().getId();
            courseTitle = notification.getQuestion().getSection().getCourse().getTitle();
        }
        
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
                .sectionId(sectionId)
                .courseTitle(courseTitle)
                .type(notification.getType().name())
                .message(notification.getMessage())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}

