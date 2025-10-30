package com.project.handongjudge.section.repository;

import com.project.handongjudge.section.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SectionRepository extends JpaRepository<Section, Long> {
    // 새로 추가할 메서드
    @Query("SELECT s FROM Section s WHERE s.instructor.id = :instructorId")
    List<Section> findByInstructorId(@Param("instructorId") Long instructorId);

    Optional<Section> findByEnrollmentCode(String enrollmentCode);

}