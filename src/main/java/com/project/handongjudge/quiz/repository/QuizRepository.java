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
}

