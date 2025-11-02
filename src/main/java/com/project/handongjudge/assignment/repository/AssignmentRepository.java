package com.project.handongjudge.assignment.repository;

import com.project.handongjudge.assignment.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findBySectionId(Long sectionId);
    @Query("SELECT COUNT(a) FROM Assignment a WHERE a.section.id = :sectionId")
    Integer countBySectionId(@Param("sectionId") Long sectionId);

    // 특정 분반의 과제 ID 목록
    @Query("SELECT a.id FROM Assignment a WHERE a.section.id = :sectionId")
    List<Long> findAssignmentIdsBySectionId(@Param("sectionId") Long sectionId);

    // 학생용: active=true인 과제만 조회
    @Query("SELECT a FROM Assignment a WHERE a.section.id = :sectionId AND a.active = true ORDER BY a.startDate DESC")
    List<Assignment> findActiveAssignmentsBySectionId(@Param("sectionId") Long sectionId);

    // 교수용: 모든 과제 조회 (active 여부와 관계없이)
    @Query("SELECT a FROM Assignment a WHERE a.section.id = :sectionId ORDER BY a.startDate DESC")
    List<Assignment> findAllAssignmentsBySectionId(@Param("sectionId") Long sectionId);
}