package com.project.handongjudge.quiz.repository;

import com.project.handongjudge.quiz.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    List<Quiz> findBySectionId(Long sectionId);
    
    @Query("SELECT q FROM Quiz q WHERE q.section.id = :sectionId ORDER BY q.startTime DESC")
    List<Quiz> findBySectionIdOrderByStartTimeDesc(@Param("sectionId") Long sectionId);
    
    @Query("SELECT q FROM Quiz q WHERE q.section.id = :sectionId AND q.active = true ORDER BY q.startTime DESC")
    List<Quiz> findActiveQuizzesBySectionId(@Param("sectionId") Long sectionId);
    
    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.section.id = :sectionId")
    Integer countBySectionId(@Param("sectionId") Long sectionId);

    /** 분반 삭제 시 해당 분반 퀴즈 ID 목록 조회 */
    @Query("SELECT q.id FROM Quiz q WHERE q.section.id = :sectionId")
    List<Long> findQuizIdsBySectionId(@Param("sectionId") Long sectionId);

    /** 분반 삭제 시 해당 분반 퀴즈 일괄 삭제 (section_id FK 제약 회피용) */
    void deleteBySection_Id(Long sectionId);
}


