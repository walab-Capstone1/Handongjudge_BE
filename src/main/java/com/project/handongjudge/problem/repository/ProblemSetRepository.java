package com.project.handongjudge.problem.repository;

import com.project.handongjudge.problem.entity.ProblemSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProblemSetRepository extends JpaRepository<ProblemSet, Long> {
    
    // 작성자별 문제집 목록 조회
    List<ProblemSet> findByCreatedBy_Id(Long instructorId);
    
    // 문제집 상세 조회 (작성자 확인 포함)
    @Query("SELECT ps FROM ProblemSet ps WHERE ps.id = :problemSetId AND ps.createdBy.id = :instructorId")
    Optional<ProblemSet> findByIdAndCreatedBy_Id(Long problemSetId, Long instructorId);
    
    // 문제집 존재 여부 확인 (작성자 확인 포함)
    boolean existsByIdAndCreatedBy_Id(Long problemSetId, Long instructorId);
}

