package com.project.handongjudge.notice.repository;

import com.project.handongjudge.notice.entity.SystemGuide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemGuideRepository extends JpaRepository<SystemGuide, Long> {
    
    /**
     * 활성화된 이용안내를 우선순위 내림차순, 생성일 내림차순으로 조회
     */
    List<SystemGuide> findByActiveTrueOrderByPriorityDescCreatedAtDesc();
    
    /**
     * 특정 카테고리의 활성화된 이용안내를 우선순위 내림차순, 생성일 내림차순으로 조회
     */
    List<SystemGuide> findByCategoryAndActiveTrueOrderByPriorityDescCreatedAtDesc(String category);
    
    /**
     * 모든 이용안내를 우선순위 내림차순, 생성일 내림차순으로 조회
     */
    List<SystemGuide> findAllByOrderByPriorityDescCreatedAtDesc();
    
    /**
     * 고유한 카테고리 목록 조회
     */
    @Query("SELECT DISTINCT g.category FROM SystemGuide g WHERE g.category IS NOT NULL")
    List<String> findDistinctCategories();
}

