package com.project.handongjudge.user.repository;

import com.project.handongjudge.user.dto.DashboardCourseDto;
import com.project.handongjudge.user.entity.Enrollment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EnrollmentRepository extends CrudRepository<Enrollment, Long> {

    @Query("SELECT new com.project.handongjudge.user.dto.DashboardCourseDto(" +
            "c.id, c.title, s.id, u.name, COUNT(n.id)) " +
            "FROM Enrollment e " +
            "JOIN Section s ON e.section.id = s.id " +
            "JOIN Course c ON s.course.id = c.id " +
            "JOIN User u ON s.instructor.id = u.id " +
            "LEFT JOIN Notice n ON n.section.id = s.id AND n.isNew = true " +
            "WHERE e.user.id = :userId " +
            "GROUP BY c.id, s.id, u.name")
    List<DashboardCourseDto> findDashboardCoursesByUserId(@Param("userId") Long userId);
}
