package com.project.handongjudge.assignment.repository;

import com.project.handongjudge.assignment.entity.AssignmentProblem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentProblemRepository extends JpaRepository<AssignmentProblem, Long> {
    List<AssignmentProblem> findByAssignmentIdOrderByProblemOrderAsc(Long assignmentId);
    Optional<AssignmentProblem> findByAssignmentIdAndProblemId(Long assignmentId, Long problemId);
}
