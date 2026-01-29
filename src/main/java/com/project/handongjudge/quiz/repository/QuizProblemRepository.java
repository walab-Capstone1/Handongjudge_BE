package com.project.handongjudge.quiz.repository;

import com.project.handongjudge.quiz.entity.QuizProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizProblemRepository extends JpaRepository<QuizProblem, Long> {
    List<QuizProblem> findByQuizId(Long quizId);
    
    @Query("SELECT qp FROM QuizProblem qp WHERE qp.quiz.id = :quizId ORDER BY qp.problemOrder ASC")
    List<QuizProblem> findByQuizIdOrderByProblemOrderAsc(@Param("quizId") Long quizId);
    
    @Query("SELECT qp.problem.id FROM QuizProblem qp WHERE qp.quiz.id = :quizId ORDER BY qp.problemOrder ASC")
    List<Long> findProblemIdsByQuizId(@Param("quizId") Long quizId);
    
    @Modifying
    @Query("DELETE FROM QuizProblem qp WHERE qp.quiz.id = :quizId")
    void deleteByQuizId(@Param("quizId") Long quizId);
    
    // 문제 ID로 QuizProblem 목록 조회
    List<QuizProblem> findByProblemId(Long problemId);
}

