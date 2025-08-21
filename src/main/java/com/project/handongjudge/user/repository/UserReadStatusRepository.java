package com.project.handongjudge.user.repository;

import com.project.handongjudge.user.entity.UserReadStatus;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserReadStatusRepository extends CrudRepository<UserReadStatus, Long> {
    boolean existsByUserIdAndNoticeId(Long userId, Long noticeId);
    boolean existsByUserIdAndAssignmentId(Long userId, Long assignmentId);
}