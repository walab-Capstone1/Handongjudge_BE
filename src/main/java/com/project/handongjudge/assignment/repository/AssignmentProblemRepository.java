package com.project.handongjudge.assignment.repository;

import com.project.handongjudge.assignment.entity.AssignmentProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssignmentProblemRepository extends JpaRepository<AssignmentProblem, Long> {
    // 기존 메서드들
    List<AssignmentProblem> findByAssignmentIdOrderByProblemOrderAsc(Long assignmentId);
    Optional<AssignmentProblem> findByAssignmentIdAndProblemId(Long assignmentId, Long problemId);
    List<AssignmentProblem> findAllByAssignmentIdAndProblemId(Long assignmentId, Long problemId);

    // 새로 추가할 메서드
    List<AssignmentProblem> findByAssignmentId(Long assignmentId);
    @Modifying
    @Query("DELETE FROM AssignmentProblem ap WHERE ap.assignment.id = :assignmentId")
    void deleteByAssignmentId(@Param("assignmentId") Long assignmentId);
    @Query("SELECT ap.problem.id FROM AssignmentProblem ap WHERE ap.assignment.id = :assignmentId")
    List<Long> findProblemIdsByAssignmentId(@Param("assignmentId") Long assignmentId);
    
    // 문제 ID로 AssignmentProblem 목록 조회
    List<AssignmentProblem> findByProblemId(Long problemId);
}