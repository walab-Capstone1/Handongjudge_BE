package com.project.handongjudge.notice.repository;

import com.project.handongjudge.notice.entity.SystemNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemNoticeRepository extends JpaRepository<SystemNotice, Long> {
    
    /**
     * 활성화된 공지사항을 우선순위 내림차순, 생성일 내림차순으로 조회
     */
    List<SystemNotice> findByActiveTrueOrderByPriorityDescCreatedAtDesc();
    
    /**
     * 모든 공지사항을 우선순위 내림차순, 생성일 내림차순으로 조회
     */
    List<SystemNotice> findAllByOrderByPriorityDescCreatedAtDesc();
}

