package com.project.handongjudge.quiz.repository;

import com.project.handongjudge.quiz.entity.QuizProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizProblemRepository extends JpaRepository<QuizProblem, Long> {
    List<QuizProblem> findByQuizId(Long quizId);

    Optional<QuizProblem> findByQuizIdAndProblemId(Long quizId, Long problemId);
    
    @Query("SELECT qp FROM QuizProblem qp WHERE qp.quiz.id = :quizId ORDER BY qp.problemOrder ASC")
    List<QuizProblem> findByQuizIdOrderByProblemOrderAsc(@Param("quizId") Long quizId);
    
    @Query("SELECT qp.problem.id FROM QuizProblem qp WHERE qp.quiz.id = :quizId ORDER BY qp.problemOrder ASC")
    List<Long> findProblemIdsByQuizId(@Param("quizId") Long quizId);
    
    @Modifying
    @Query("DELETE FROM QuizProblem qp WHERE qp.quiz.id = :quizId")
    void deleteByQuizId(@Param("quizId") Long quizId);

    @Modifying
    @Query("DELETE FROM QuizProblem qp WHERE qp.quiz.id = :quizId AND qp.problem.id = :problemId")
    void deleteByQuizIdAndProblemId(@Param("quizId") Long quizId, @Param("problemId") Long problemId);

    /** 해당 퀴즈들에 대한 QuizProblem 일괄 삭제 (분반 삭제 시 quiz_id FK 제약 회피용) */
    @Modifying
    @Query("DELETE FROM QuizProblem qp WHERE qp.quiz.id IN :quizIds")
    void deleteByQuiz_IdIn(@Param("quizIds") List<Long> quizIds);

    // 문제 ID로 QuizProblem 목록 조회
    List<QuizProblem> findByProblemId(Long problemId);
}

