package com.project.handongjudge.community.repository;

import com.project.handongjudge.community.entity.Notification;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT DISTINCT n FROM Notification n LEFT JOIN FETCH n.notice LEFT JOIN FETCH n.assignment WHERE n.id = :id")
    java.util.Optional<Notification> findByIdWithNoticeAndAssignment(@Param("id") Long id);

    // 사용자별 알림 목록 조회 (최신순)
    Page<Notification> findByRecipientOrderByCreatedAtDesc(User recipient, Pageable pageable);

    // 사용자별 읽지 않은 알림 목록 조회
    List<Notification> findByRecipientAndIsReadFalseOrderByCreatedAtDesc(User recipient);

    // 사용자별 읽지 않은 알림 수
    Long countByRecipientAndIsReadFalse(User recipient);

    // 알림 일괄 읽음 처리
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient = :recipient AND n.isRead = false")
    void markAllAsReadByRecipient(@Param("recipient") User recipient);

    // 섹션별 알림 조회 (질문의 섹션, 공지사항의 섹션, 과제의 섹션을 확인)
    // LEFT JOIN을 사용하여 한 번의 쿼리로 처리
    @Query("SELECT n FROM Notification n " +
           "LEFT JOIN n.question q " +
           "LEFT JOIN n.notice nt " +
           "LEFT JOIN n.assignment a " +
           "WHERE n.recipient = :recipient " +
           "AND (" +
           "  (q IS NOT NULL AND q.section = :section) OR " +
           "  (nt IS NOT NULL AND nt.section = :section) OR " +
           "  (a IS NOT NULL AND a.section = :section)" +
           ") " +
           "ORDER BY n.createdAt DESC")
    Page<Notification> findByRecipientAndSectionOrderByCreatedAtDesc(
            @Param("recipient") User recipient,
            @Param("section") Section section,
            Pageable pageable);

    /** 해당 과제들을 참조하는 알림 일괄 삭제 (분반 삭제 시 FK 제약 회피용) */
    void deleteByAssignment_IdIn(List<Long> assignmentIds);

    /** 해당 공지들을 참조하는 알림 일괄 삭제 (분반 삭제 시 notice_id FK 제약 회피용) */
    void deleteByNotice_IdIn(List<Long> noticeIds);

    boolean existsByRecipient_IdAndNotice_IdAndType(Long recipientId, Long noticeId, Notification.NotificationType type);

    boolean existsByRecipient_IdAndAssignment_IdAndType(Long recipientId, Long assignmentId, Notification.NotificationType type);
}

