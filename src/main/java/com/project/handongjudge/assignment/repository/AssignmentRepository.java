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


}
