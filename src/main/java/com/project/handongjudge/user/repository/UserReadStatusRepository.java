package com.project.handongjudge.user.repository;

import com.project.handongjudge.user.entity.UserReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserReadStatusRepository extends JpaRepository<UserReadStatus, Long> {

    // 특정 사용자가 특정 공지사항을 읽었는지 확인
    boolean existsByUserIdAndNoticeId(Long userId, Long noticeId);

    // 특정 사용자가 특정 과제를 읽었는지 확인
    boolean existsByUserIdAndAssignmentId(Long userId, Long assignmentId);

    // 특정 사용자가 읽은 공지사항 수 조회
    @Query("SELECT COUNT(urs) FROM UserReadStatus urs WHERE urs.user.id = :userId AND urs.readType = 'NOTICE'")
    Long countReadNoticesByUserId(@Param("userId") Long userId);

    // 특정 사용자가 읽은 과제 수 조회
    @Query("SELECT COUNT(urs) FROM UserReadStatus urs WHERE urs.user.id = :userId AND urs.readType = 'ASSIGNMENT'")
    Long countReadAssignmentsByUserId(@Param("userId") Long userId);
}
