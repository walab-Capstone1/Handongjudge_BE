package com.project.handongjudge.quiz.repository;

import com.project.handongjudge.quiz.entity.QuizGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuizGradeRepository extends JpaRepository<QuizGrade, Long> {

    List<QuizGrade> findByQuizId(Long quizId);

    Optional<QuizGrade> findByQuizIdAndProblemIdAndStudentId(
            Long quizId, Long problemId, Long studentId
    );

    List<QuizGrade> findByQuizIdAndStudentId(Long quizId, Long studentId);

    /** 해당 퀴즈들에 대한 성적 일괄 삭제 (분반 삭제 시 quiz_id FK 제약 회피용) */
    void deleteByQuiz_IdIn(List<Long> quizIds);
}
