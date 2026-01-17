package com.project.handongjudge.problem.repository;

import com.project.handongjudge.problem.entity.ProblemSetProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProblemSetProblemRepository extends JpaRepository<ProblemSetProblem, Long> {
    
    // 문제집의 모든 문제 조회 (순서대로)
    @Query("SELECT psp FROM ProblemSetProblem psp WHERE psp.problemSet.id = :problemSetId ORDER BY psp.problemOrder ASC")
    List<ProblemSetProblem> findByProblemSetIdOrderByProblemOrder(Long problemSetId);
    
    // 문제집에서 특정 문제 조회
    Optional<ProblemSetProblem> findByProblemSetIdAndProblemId(Long problemSetId, Long problemId);
    
    // 문제집의 문제 개수
    long countByProblemSetId(Long problemSetId);
    
    // 문제집의 모든 문제 삭제
    void deleteByProblemSetId(Long problemSetId);
    
    // 문제집에서 특정 문제 삭제
    void deleteByProblemSetIdAndProblemId(Long problemSetId, Long problemId);
}

