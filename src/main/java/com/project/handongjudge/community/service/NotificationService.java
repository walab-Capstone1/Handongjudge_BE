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
import com.project.handongjudge.section.entity.SectionUserRole;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.section.repository.SectionUserRoleRepository;
import com.project.handongjudge.section.service.SectionRoleService;
import com.project.handongjudge.user.entity.Enrollment;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
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
    private final SectionUserRoleRepository sectionUserRoleRepository;
    private final NoticeRepository noticeRepository;
    private final AssignmentRepository assignmentRepository;
    private final SectionRoleService sectionRoleService;
    /** 순환 참조 방지 — 알림 읽음 시 대시보드 뱃지용 UserReadStatus와 동기화 */
    private final ObjectProvider<UserService> userServiceProvider;

    /**
     * 공지/과제 알림을 읽으면 내 강의실(/courses) 새 공지·새 과제 카운트에도 반영되도록 UserReadStatus를 맞춤.
     */
    private void syncDashboardReadStatusFromNotification(Notification notification, Long userId) {
        try {
            UserService userService = userServiceProvider.getObject();
            if (notification.getType() == Notification.NotificationType.NOTICE_CREATED
                    && notification.getNotice() != null) {
                userService.markNoticeAsRead(userId, notification.getNotice().getId());
            } else if (notification.getType() == Notification.NotificationType.ASSIGNMENT_CREATED
                    && notification.getAssignment() != null) {
                userService.markAssignmentAsRead(userId, notification.getAssignment().getId());
            }
        } catch (Exception e) {
            log.warn("알림 읽음과 대시보드 읽음 동기화 실패 notificationId={}: {}",
                    notification.getId(), e.getMessage());
        }
    }

    /**
     * 학생에게 비활성 과제 알림을 숨길지 여부. (관리자는 그대로 노출)
     */
    private boolean shouldHideAssignmentNotificationFromUser(Notification n, Long userId) {
        if (n.getType() != Notification.NotificationType.ASSIGNMENT_CREATED || n.getAssignment() == null) {
            return false;
        }
        Assignment a = n.getAssignment();
        if (Boolean.TRUE.equals(a.getActive())) {
            return false;
        }
        return !sectionRoleService.isManager(userId, a.getSection().getId());
    }

    /**
     * 사용자의 알림 목록 조회
     * @param userId 사용자 ID
     * @param sectionId 섹션 ID (선택적, null이면 모든 알림 조회)
     * @param pageable 페이징 정보
     */
    @Transactional(readOnly = false)
    public Page<NotificationResponseDto> getNotifications(Long userId, Long sectionId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        Page<Notification> notifications;
        
        if (sectionId != null) {
            // 섹션별 필터링
            Section section = sectionRepository.findById(sectionId)
                    .orElseThrow(() -> new CustomException("섹션을 찾을 수 없습니다"));
            // 수강생·수업 관리자만: 기존 활성 공지/과제 알림이 없으면 채움 (학생 알림 페이지·대시보드에서 자동 반영)
            boolean canAccess = enrollmentRepository.existsByUserIdAndSectionId(userId, sectionId)
                    || sectionRoleService.isManager(userId, sectionId);
            if (canAccess) {
                runCatchUpForStudentCore(userId, sectionId);
            }
            notifications = notificationRepository.findByRecipientAndSectionOrderByCreatedAtDesc(
                    user, section, pageable);
        } else {
            // 모든 알림 조회
            notifications = notificationRepository.findByRecipientOrderByCreatedAtDesc(
                    user, pageable);
        }

        // 학생에게는 비활성 과제 알림 미노출 (이미 저장된 알림도 조회 시 필터링)
        List<Notification> filtered = notifications.getContent().stream()
                .filter(n -> !shouldHideAssignmentNotificationFromUser(n, userId))
                .collect(Collectors.toList());
        Page<Notification> filteredPage = new PageImpl<>(
                filtered, notifications.getPageable(), notifications.getTotalElements());
        return filteredPage.map(NotificationResponseDto::fromEntity);
    }

    /**
     * 읽지 않은 알림 목록 조회
     */
    public List<NotificationResponseDto> getUnreadNotifications(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        List<Notification> notifications = notificationRepository
                .findByRecipientAndIsReadFalseOrderByCreatedAtDesc(user);

        // 학생에게는 비활성 과제 알림 미노출
        return notifications.stream()
                .filter(n -> !shouldHideAssignmentNotificationFromUser(n, userId))
                .map(NotificationResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 읽지 않은 알림 수
     */
    public Long getUnreadNotificationCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        List<Notification> unread = notificationRepository
                .findByRecipientAndIsReadFalseOrderByCreatedAtDesc(user);
        // 학생에게는 비활성 과제 알림 제외한 개수
        long count = unread.stream()
                .filter(n -> !shouldHideAssignmentNotificationFromUser(n, userId))
                .count();
        return count;
    }

    /**
     * 알림 읽음 처리
     */
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findByIdWithNoticeAndAssignment(notificationId)
                .orElseThrow(() -> new CustomException("알림을 찾을 수 없습니다"));

        // 권한 검증: 본인의 알림만 읽음 처리 가능
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new CustomException("이 알림을 읽을 권한이 없습니다");
        }

        notification.markAsRead();
        notificationRepository.save(notification);
        syncDashboardReadStatusFromNotification(notification, userId);
    }

    /**
     * 모든 알림 읽음 처리
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        List<Notification> unread = notificationRepository
                .findByRecipientAndIsReadFalseOrderByCreatedAtDesc(user);
        List<Notification> toSave = new ArrayList<>();
        for (Notification stub : unread) {
            Notification notification = notificationRepository
                    .findByIdWithNoticeAndAssignment(stub.getId())
                    .orElse(stub);
            notification.markAsRead();
            syncDashboardReadStatusFromNotification(notification, userId);
            toSave.add(notification);
        }
        notificationRepository.saveAll(toSave);
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

    /** 해당 섹션의 관리자(교수·튜터) 목록 조회 */
    private List<User> getSectionManagers(Section section) {
        List<SectionUserRole> roles = sectionUserRoleRepository.findBySectionId(section.getId());
        return roles.stream()
                .filter(sur -> sur.getRole() == SectionUserRole.SectionRole.ADMIN
                        || sur.getRole() == SectionUserRole.SectionRole.TUTOR)
                .map(SectionUserRole::getUser)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 새 공지사항이 작성되었을 때 (섹션의 모든 학생 + 교수·튜터에게 알림)
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

            // 교수·튜터(수업 관리자)에게도 알림
            for (User manager : getSectionManagers(loadedSection)) {
                Notification notification = Notification.builder()
                        .recipient(manager)
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
     * 새 과제가 생성되었을 때 알림 발송.
     * - 학생: 활성화(active=true)된 과제일 때만 알림.
     * - 교수·튜터: 활성/비활성 구분 없이 항상 알림.
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

            // 학생에게는 활성화된 과제만 알림 (비활성화 상태로 생성된 과제는 학생에게 알리지 않음)
            if (Boolean.TRUE.equals(loadedAssignment.getActive())) {
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
            }

            // 교수·튜터(수업 관리자)에게는 과제 생성 시 항상 알림 (활성/비활성 구분 없음)
            for (User manager : getSectionManagers(loadedSection)) {
                Notification notification = Notification.builder()
                        .recipient(manager)
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

    /**
     * 학생이 수업에 추가되었을 때 (교수·튜터에게 알림)
     */
    @Async("taskExecutor")
    @Transactional
    public void notifyStudentEnrolled(Enrollment enrollment, Section section) {
        try {
            // 엔티티를 다시 조회하여 LazyInitializationException 방지
            Enrollment loadedEnrollment = enrollmentRepository.findById(enrollment.getId())
                    .orElse(null);
            Section loadedSection = sectionRepository.findById(section.getId())
                    .orElse(null);
            
            if (loadedEnrollment == null || loadedSection == null) {
                return;
            }

            String message = String.format("새 학생이 수업에 추가되었습니다: %s (%s)",
                    loadedEnrollment.getUser().getName(),
                    loadedSection.getCourse().getTitle() + " - Section " + loadedSection.getSectionNumber());

            for (User manager : getSectionManagers(loadedSection)) {
                Notification notification = Notification.builder()
                        .recipient(manager)
                        .actor(loadedEnrollment.getUser())
                        .type(Notification.NotificationType.STUDENT_ENROLLED)
                        .message(message)
                        .build();
                notificationRepository.save(notification);
            }
        } catch (Exception e) {
            System.err.println("학생 추가 알림 발송 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 한 수강생에 대해, 분반의 활성 공지·활성 과제에 대한 NOTICE/ASSIGNMENT 알림을 없을 때만 생성합니다.
     *
     * @return 새로 저장한 알림 개수
     */
    private int runCatchUpForStudentCore(Long userId, Long sectionId) {
        User student = userRepository.findById(userId).orElse(null);
        Section section = sectionRepository.findById(sectionId).orElse(null);
        if (student == null || section == null) {
            return 0;
        }

        User actor = section.getInstructor();
        if (actor == null) {
            actor = student;
        }

        int created = 0;

        for (Notice notice : noticeRepository.findActiveNoticesBySectionId(sectionId)) {
            Notice loaded = noticeRepository.findById(notice.getId()).orElse(null);
            if (loaded == null) {
                continue;
            }
            if (notificationRepository.existsByRecipient_IdAndNotice_IdAndType(
                    userId, loaded.getId(), Notification.NotificationType.NOTICE_CREATED)) {
                continue;
            }
            notificationRepository.save(Notification.builder()
                    .recipient(student)
                    .actor(actor)
                    .notice(loaded)
                    .type(Notification.NotificationType.NOTICE_CREATED)
                    .message(String.format("새 공지사항: %s", loaded.getTitle()))
                    .build());
            created++;
        }

        for (Assignment assignment : assignmentRepository.findBySectionId(sectionId)) {
            if (!Boolean.TRUE.equals(assignment.getActive())) {
                continue;
            }
            Assignment loaded = assignmentRepository.findById(assignment.getId()).orElse(null);
            if (loaded == null) {
                continue;
            }
            if (notificationRepository.existsByRecipient_IdAndAssignment_IdAndType(
                    userId, loaded.getId(), Notification.NotificationType.ASSIGNMENT_CREATED)) {
                continue;
            }
            String duePart = loaded.getEndDate() != null
                    ? loaded.getEndDate().toLocalDate().toString()
                    : "미정";
            notificationRepository.save(Notification.builder()
                    .recipient(student)
                    .actor(actor)
                    .assignment(loaded)
                    .type(Notification.NotificationType.ASSIGNMENT_CREATED)
                    .message(String.format("새 과제: %s (마감: %s)", loaded.getTitle(), duePart))
                    .build());
            created++;
        }

        return created;
    }

    /**
     * 수강 신청 직후: 분반에 이미 있던 활성 공지·활성 과제에 대해 신규 수강생에게만 알림을 생성합니다.
     * 공지/과제 생성 시점에는 미등록 학생에게 알림이 가지 않으므로 보완합니다.
     */
    @Async("taskExecutor")
    @Transactional
    public void notifyEnrolledStudentCatchUp(Long userId, Long sectionId) {
        try {
            runCatchUpForStudentCore(userId, sectionId);
        } catch (Exception e) {
            System.err.println("수강 신청 후 기존 공지/과제 알림 생성 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 이미 수강 중인 전원에 대해 catch-up 알림을 채웁니다. (수업 관리자 전용, 중복 없음)
     */
    @Transactional
    public Map<String, Object> backfillCatchUpForSection(Long requesterUserId, Long sectionId) {
        if (!sectionRoleService.isManager(requesterUserId, sectionId)) {
            throw new CustomException("수업 관리자만 실행할 수 있습니다.");
        }
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("섹션을 찾을 수 없습니다"));
        List<Enrollment> enrollments = enrollmentRepository.findBySection(section);
        int created = 0;
        for (Enrollment enrollment : enrollments) {
            created += runCatchUpForStudentCore(enrollment.getUser().getId(), sectionId);
        }
        return Map.of(
                "notificationsCreated", created,
                "studentsProcessed", enrollments.size());
    }

    /**
     * 과제 마감 알림 (교수·튜터에게 알림)
     * 마감일이 임박했거나 마감된 과제에 대해 알림 발송
     */
    @Async("taskExecutor")
    @Transactional
    public void notifyAssignmentDeadline(Assignment assignment, Section section, boolean isExpired) {
        try {
            // 엔티티를 다시 조회하여 LazyInitializationException 방지
            Assignment loadedAssignment = assignmentRepository.findById(assignment.getId())
                    .orElse(null);
            Section loadedSection = sectionRepository.findById(section.getId())
                    .orElse(null);
            
            if (loadedAssignment == null || loadedSection == null) {
                return;
            }

            String message;
            if (isExpired) {
                message = String.format("과제가 마감되었습니다: %s (%s)",
                        loadedAssignment.getTitle(),
                        loadedSection.getCourse().getTitle() + " - Section " + loadedSection.getSectionNumber());
            } else {
                message = String.format("과제 마감이 임박했습니다: %s (마감: %s) (%s)",
                        loadedAssignment.getTitle(),
                        loadedAssignment.getEndDate().toLocalDate(),
                        loadedSection.getCourse().getTitle() + " - Section " + loadedSection.getSectionNumber());
            }

            User instructor = loadedSection.getInstructor();
            for (User manager : getSectionManagers(loadedSection)) {
                Notification notification = Notification.builder()
                        .recipient(manager)
                        .actor(instructor != null ? instructor : manager)
                        .assignment(loadedAssignment)
                        .type(Notification.NotificationType.ASSIGNMENT_DEADLINE)
                        .message(message)
                        .build();
                notificationRepository.save(notification);
            }
        } catch (Exception e) {
            System.err.println("과제 마감 알림 발송 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

