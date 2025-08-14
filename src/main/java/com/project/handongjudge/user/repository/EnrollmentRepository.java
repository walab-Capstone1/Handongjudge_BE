package com.project.handongjudge.user.repository;

import com.project.handongjudge.user.dto.DashboardCourseDto;
import com.project.handongjudge.user.dto.StudentDto;
import com.project.handongjudge.user.entity.Enrollment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EnrollmentRepository extends CrudRepository<Enrollment, Long> {

    @Query("SELECT new com.project.handongjudge.user.dto.DashboardCourseDto(" +
            "c.id, c.title, s.id, s.sectionNumber, u.name, " +
            "COALESCE(SUM(CASE WHEN n.isNew = true AND urs.id IS NULL THEN 1 ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN a.isNew = true THEN 1 ELSE 0 END), 0)) " +
            "FROM Enrollment e " +
            "JOIN Section s ON e.section.id = s.id " +
            "JOIN Course c ON s.course.id = c.id " +
            "JOIN User u ON s.instructor.id = u.id " +
            "LEFT JOIN Notice n ON n.section.id = s.id " +
            "LEFT JOIN UserReadStatus urs ON urs.notice.id = n.id AND urs.user.id = :userId " +
            "LEFT JOIN Assignment a ON a.section.id = s.id " +
            "WHERE e.user.id = :userId " +
            "GROUP BY c.id, c.title, s.id, s.sectionNumber, u.name")
    List<DashboardCourseDto> findDashboardCoursesByUserId(@Param("userId") Long userId);
    @Query("SELECT e.teamId FROM Enrollment e WHERE e.user.id = :userId AND e.section.id = :sectionId")
    String findTeamIdByUserIdAndSectionId(@Param("userId") Long userId, @Param("sectionId") Long sectionId);

    // 사용자가 특정 분반에 수강 등록되어 있는지 확인
    boolean existsByUserIdAndSectionId(Long userId, Long sectionId);

    // 교수가 담당하는 분반들 조회 (교수용 대시보드)
    @Query("SELECT new com.project.handongjudge.user.dto.DashboardCourseDto(" +
            "c.id, c.title, s.id, s.sectionNumber, u.name, " +
            "COALESCE(SUM(CASE WHEN n.isNew = true THEN 1 ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN a.isNew = true THEN 1 ELSE 0 END), 0)) " +
            "FROM Section s " +
            "JOIN Course c ON s.course.id = c.id " +
            "JOIN User u ON s.instructor.id = u.id " +
            "LEFT JOIN Notice n ON n.section.id = s.id " +
            "LEFT JOIN Assignment a ON a.section.id = s.id " +
            "WHERE s.instructor.id = :instructorId " +
            "GROUP BY c.id, c.title, s.id, s.sectionNumber, u.name")
    List<DashboardCourseDto> findDashboardCoursesByInstructorId(@Param("instructorId") Long instructorId);

    // EnrollmentRepository.java에 추가
    @Query("SELECT new com.project.handongjudge.user.dto.StudentDto(" +
            "u.id, u.name, u.email, '', e.teamId, s.id, " +
            "CONCAT(c.title, ' - Section ', s.sectionNumber), c.title, s.sectionNumber, " +
            "e.joinedAt, u.updatedAt) " +
            "FROM Enrollment e " +
            "JOIN User u ON e.user.id = u.id " +
            "JOIN Section s ON e.section.id = s.id " +
            "JOIN Course c ON s.course.id = c.id " +
            "WHERE s.id = :sectionId")
    List<StudentDto> findStudentsBySectionId(@Param("sectionId") Long sectionId);

    // 교수가 담당하는 모든 분반의 학생들 조회
    @Query("SELECT new com.project.handongjudge.user.dto.StudentDto(" +
            "u.id, u.name, u.email, '', e.teamId, s.id, " +
            "CONCAT(c.title, ' - Section ', s.sectionNumber), c.title, s.sectionNumber, " +
            "e.joinedAt, u.updatedAt) " +
            "FROM Enrollment e " +
            "JOIN User u ON e.user.id = u.id " +
            "JOIN Section s ON e.section.id = s.id " +
            "JOIN Course c ON s.course.id = c.id " +
            "WHERE s.instructor.id = :instructorId " +
            "ORDER BY c.title, s.sectionNumber, u.name")
    List<StudentDto> findStudentsByInstructorId(@Param("instructorId") Long instructorId);
}
