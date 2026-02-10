package com.project.handongjudge.problem.repository;

import com.project.handongjudge.problem.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
    @Query("SELECT p FROM Problem p WHERE p.id = :problemId")
    Optional<Problem> findById(Long problemId);

    @Query("SELECT MAX(p.id) FROM Problem p")
    Long findLastProblemId();

    @Query("SELECT DISTINCT p FROM Problem p JOIN p.assignmentProblems ap WHERE ap.assignment.id = :assignmentId")
    List<Problem> findByAssignmentId(Long assignmentId);

    // ✨ 추가: instructor가 만든 문제만 조회
    List<Problem> findByCreatedBy_Id(Long instructorId);
    
    // instructor가 만든 문제 수
    long countByCreatedById(Long instructorId);
}