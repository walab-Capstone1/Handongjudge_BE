package com.project.handongjudge.community.service;

import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.common.exception.CustomException;
import com.project.handongjudge.community.dto.NotificationResponseDto;
import com.project.handongjudge.community.entity.Comment;
import com.project.handongjudge.community.entity.Notification;
import com.project.handongjudge.community.entity.Question;
import com.project.handongjudge.community.repository.CommentRepository;
import com.project.handongjudge.community.repository.NotificationRepository;
import com.project.handongjudge.community.repository.QuestionRepository;
import com.project.handongjudge.notice.entity.Notice;
import com.project.handongjudge.notice.repository.NoticeRepository;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.user.entity.Enrollment;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuestionRepository questionRepository;
    private final CommentRepository commentRepository;
    private final SectionRepository sectionRepository;
    private final NoticeRepository noticeRepository;
    private final AssignmentRepository assignmentRepository;

    /**
     * 사용자의 알림 목록 조회
     * @param userId 사용자 ID
     * @param sectionId 섹션 ID (선택적, null이면 모든 알림 조회)
     * @param pageable 페이징 정보
     */
    public Page<NotificationResponseDto> getNotifications(Long userId, Long sectionId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        Page<Notification> notifications;
        
        if (sectionId != null) {
            // 섹션별 필터링
            Section section = sectionRepository.findById(sectionId)
                    .orElseThrow(() -> new CustomException("섹션을 찾을 수 없습니다"));
            notifications = notificationRepository.findByRecipientAndSectionOrderByCreatedAtDesc(
                    user, section, pageable);
        } else {
            // 모든 알림 조회
            notifications = notificationRepository.findByRecipientOrderByCreatedAtDesc(
                    user, pageable);
        }

        return notifications.map(NotificationResponseDto::fromEntity);
    }

    /**
     * 읽지 않은 알림 목록 조회
     */
    public List<NotificationResponseDto> getUnreadNotifications(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        List<Notification> notifications = notificationRepository
                .findByRecipientAndIsReadFalseOrderByCreatedAtDesc(user);

        return notifications.stream()
                .map(NotificationResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 읽지 않은 알림 수
     */
    public Long getUnreadNotificationCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        return notificationRepository.countByRecipientAndIsReadFalse(user);
    }

    /**
     * 알림 읽음 처리
     */
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException("알림을 찾을 수 없습니다"));

        // 권한 검증: 본인의 알림만 읽음 처리 가능
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new CustomException("이 알림을 읽을 권한이 없습니다");
        }

        notification.markAsRead();
        notificationRepository.save(notification);
    }

    /**
     * 모든 알림 읽음 처리
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        notificationRepository.markAllAsReadByRecipient(user);
    }

    // ========== 알림 생성 메서드 ==========

    /**
     * 질문에 댓글이 달렸을 때
     */
    @Async("taskExecutor")
    @Transactional
    public void notifyQuestionComment(Question question, Comment comment) {
        try {
            // 엔티티를 다시 조회하여 LazyInitializationException 방지
            Question loadedQuestion = questionRepository.findById(question.getId())
                    .orElse(null);
            Comment loadedComment = commentRepository.findById(comment.getId())
                    .orElse(null);

            if (loadedQuestion == null || loadedComment == null) {
                return;
            }

            // 질문 작성자와 댓글 작성자가 다른 경우에만 알림 발송
            if (!loadedQuestion.getAuthor().getId().equals(loadedComment.getAuthor().getId())) {
                Notification notification = Notification.builder()
                        .recipient(loadedQuestion.getAuthor())
                        .actor(loadedComment.getAuthor())
                        .question(loadedQuestion)
                        .comment(loadedComment)
                        .type(Notification.NotificationType.QUESTION_COMMENT)
                        .message(String.format("%s님이 회원님의 질문에 댓글을 달았습니다: %s", 
                                loadedComment.getAuthorDisplayName(), loadedQuestion.getTitle()))
                        .build();

                notificationRepository.save(notification);
            }
        } catch (Exception e) {
            System.err.println("질문 댓글 알림 발송 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 질문이 핀되었을 때
     */
    @Async("taskExecutor")
    @Transactional
    public void notifyQuestionPinned(Question question) {
        try {
            // 엔티티를 다시 조회하여 LazyInitializationException 방지
            Question loadedQuestion = questionRepository.findById(question.getId())
                    .orElse(null);
            if (loadedQuestion == null) return;

            Notification notification = Notification.builder()
                    .recipient(loadedQuestion.getAuthor())
                    .actor(loadedQuestion.getSection().getInstructor())
                    .question(loadedQuestion)
                    .type(Notification.NotificationType.QUESTION_PINNED)
                    .message(String.format("회원님의 질문이 고정되었습니다: %s", loadedQuestion.getTitle()))
                    .build();

            notificationRepository.save(notification);
        } catch (Exception e) {
            System.err.println("질문 핀 알림 발송 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 질문이 해결되었을 때
     */
    @Async("taskExecutor")
    @Transactional
    public void notifyQuestionResolved(Question question) {
        try {
            // 엔티티를 다시 조회하여 LazyInitializationException 방지
            Question loadedQuestion = questionRepository.findById(question.getId())
                    .orElse(null);
            if (loadedQuestion == null) return;

            // 댓글 목록 조회
            List<Comment> comments = commentRepository.findByQuestionOrderByCreatedAtAsc(loadedQuestion);
            
            // 댓글 작성자들에게 알림
            for (Comment comment : comments) {
                if (!comment.getAuthor().getId().equals(loadedQuestion.getAuthor().getId())) {
                    Notification notification = Notification.builder()
                            .recipient(comment.getAuthor())
                            .actor(loadedQuestion.getAuthor())
                            .question(loadedQuestion)
                            .type(Notification.NotificationType.QUESTION_RESOLVED)
                            .message(String.format("참여한 질문이 해결되었습니다: %s", loadedQuestion.getTitle()))
                            .build();

                    notificationRepository.save(notification);
                }
            }
        } catch (Exception e) {
            System.err.println("질문 해결 알림 발송 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 댓글이 채택되었을 때
     */
    @Async("taskExecutor")
    @Transactional
    public void notifyCommentAccepted(Comment comment) {
        try {
            // 엔티티를 다시 조회하여 LazyInitializationException 방지
            Comment loadedComment = commentRepository.findById(comment.getId())
                    .orElse(null);
            if (loadedComment == null) return;

            Notification notification = Notification.builder()
                    .recipient(loadedComment.getAuthor())
                    .actor(loadedComment.getQuestion().getAuthor())
                    .question(loadedComment.getQuestion())
                    .comment(loadedComment)
                    .type(Notification.NotificationType.COMMENT_ACCEPTED)
                    .message(String.format("회원님의 댓글이 채택되었습니다: %s", 
                            loadedComment.getQuestion().getTitle()))
                    .build();

            notificationRepository.save(notification);
        } catch (Exception e) {
            System.err.println("댓글 채택 알림 발송 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 질문에 추천이 눌렸을 때
     */
    @Async("taskExecutor")
    @Transactional
    public void notifyQuestionLiked(Question question, User liker) {
        try {
            // 엔티티를 다시 조회하여 LazyInitializationException 방지
            Question loadedQuestion = questionRepository.findById(question.getId())
                    .orElse(null);
            User loadedLiker = userRepository.findById(liker.getId())
                    .orElse(null);
            
            if (loadedQuestion == null || loadedLiker == null) return;

            // 본인이 추천한 경우 알림 발송 안 함
            if (!loadedQuestion.getAuthor().getId().equals(loadedLiker.getId())) {
                Notification notification = Notification.builder()
                        .recipient(loadedQuestion.getAuthor())
                        .actor(loadedLiker)
                        .question(loadedQuestion)
                        .type(Notification.NotificationType.QUESTION_LIKED)
                        .message(String.format("%s님이 회원님의 질문을 추천했습니다: %s", 
                                loadedLiker.getName(), loadedQuestion.getTitle()))
                        .build();

                notificationRepository.save(notification);
            }
        } catch (Exception e) {
            System.err.println("질문 추천 알림 발송 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 댓글에 추천이 눌렸을 때
     */
    @Async("taskExecutor")
    @Transactional
    public void notifyCommentLiked(Comment comment, User liker) {
        try {
            // 엔티티를 다시 조회하여 LazyInitializationException 방지
            Comment loadedComment = commentRepository.findById(comment.getId())
                    .orElse(null);
            User loadedLiker = userRepository.findById(liker.getId())
                    .orElse(null);
            
            if (loadedComment == null || loadedLiker == null) return;

            // 본인이 추천한 경우 알림 발송 안 함
            if (!loadedComment.getAuthor().getId().equals(loadedLiker.getId())) {
                Notification notification = Notification.builder()
                        .recipient(loadedComment.getAuthor())
                        .actor(loadedLiker)
                        .question(loadedComment.getQuestion())
                        .comment(loadedComment)
                        .type(Notification.NotificationType.COMMENT_LIKED)
                        .message(String.format("%s님이 회원님의 댓글을 추천했습니다", loadedLiker.getName()))
                        .build();

                notificationRepository.save(notification);
            }
        } catch (Exception e) {
            System.err.println("댓글 추천 알림 발송 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 새 공지사항이 작성되었을 때 (섹션의 모든 학생에게 알림)
     */
    @Async("taskExecutor")
    @Transactional
    public void notifyNoticeCreated(Notice notice, Section section) {
        try {
            // 엔티티를 다시 조회하여 LazyInitializationException 방지
            Notice loadedNotice = noticeRepository.findById(notice.getId())
                    .orElse(null);
            Section loadedSection = sectionRepository.findById(section.getId())
                    .orElse(null);
            
            if (loadedNotice == null || loadedSection == null) {
                return;
            }

            // 해당 섹션에 등록된 모든 학생 조회
            List<Enrollment> enrollments = enrollmentRepository.findBySection(loadedSection);
            
            for (Enrollment enrollment : enrollments) {
                Notification notification = Notification.builder()
                        .recipient(enrollment.getUser())
                        .actor(loadedSection.getInstructor())
                        .notice(loadedNotice)
                        .type(Notification.NotificationType.NOTICE_CREATED)
                        .message(String.format("새 공지사항: %s", loadedNotice.getTitle()))
                        .build();

                notificationRepository.save(notification);
            }
        } catch (Exception e) {
            System.err.println("공지사항 알림 발송 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 새 과제가 생성되었을 때 (섹션의 모든 학생에게 알림)
     */
    @Async("taskExecutor")
    @Transactional
    public void notifyAssignmentCreated(Assignment assignment, Section section) {
        try {
            // 엔티티를 다시 조회하여 LazyInitializationException 방지
            Assignment loadedAssignment = assignmentRepository.findById(assignment.getId())
                    .orElse(null);
            Section loadedSection = sectionRepository.findById(section.getId())
                    .orElse(null);
            
            if (loadedAssignment == null || loadedSection == null) {
                return;
            }

            // 해당 섹션에 등록된 모든 학생 조회
            List<Enrollment> enrollments = enrollmentRepository.findBySection(loadedSection);
            
            for (Enrollment enrollment : enrollments) {
                Notification notification = Notification.builder()
                        .recipient(enrollment.getUser())
                        .actor(loadedSection.getInstructor())
                        .assignment(loadedAssignment)
                        .type(Notification.NotificationType.ASSIGNMENT_CREATED)
                        .message(String.format("새 과제: %s (마감: %s)", 
                                loadedAssignment.getTitle(), 
                                loadedAssignment.getEndDate().toLocalDate()))
                        .build();

                notificationRepository.save(notification);
            }
        } catch (Exception e) {
            System.err.println("과제 알림 발송 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

