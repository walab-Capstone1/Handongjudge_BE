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
}
