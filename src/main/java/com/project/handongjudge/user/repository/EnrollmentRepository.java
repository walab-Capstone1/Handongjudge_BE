package com.project.handongjudge.user.repository;

import com.project.handongjudge.user.dto.DashboardCourseDto;
import com.project.handongjudge.user.dto.StudentDto;
import com.project.handongjudge.user.entity.Enrollment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.CrudRepository;
import com.project.handongjudge.user.entity.User;
import java.util.List;

public interface EnrollmentRepository extends CrudRepository<Enrollment, Long> {

    @Query("SELECT new com.project.handongjudge.user.dto.DashboardCourseDto(" +
            "c.id, c.title, s.id, s.sectionNumber, u.name, " +
            "CAST(COALESCE(SUM(CASE WHEN n.isNew = true AND urs.id IS NULL THEN 1 ELSE 0 END), 0) AS long), " +
            "CAST(COALESCE(SUM(CASE WHEN a.isNew = true THEN 1 ELSE 0 END), 0) AS long), " +
            "CAST(COUNT(DISTINCT n.id) AS long), " +
            "CAST((SELECT COUNT(e2.id) FROM Enrollment e2 WHERE e2.section.id = s.id) AS long), " +
            "s.createdAt) " +
            "FROM Enrollment e " +
            "JOIN Section s ON e.section.id = s.id " +
            "JOIN Course c ON s.course.id = c.id " +
            "JOIN User u ON s.instructor.id = u.id " +
            "LEFT JOIN Notice n ON n.section.id = s.id " +
            "LEFT JOIN UserReadStatus urs ON urs.notice.id = n.id AND urs.user.id = :userId " +
            "LEFT JOIN Assignment a ON a.section.id = s.id " +
            "WHERE e.user.id = :userId " +
            "GROUP BY c.id, c.title, s.id, s.sectionNumber, u.name, s.createdAt")
    List<DashboardCourseDto> findDashboardCoursesByUserId(@Param("userId") Long userId);
    @Query("SELECT e.teamId FROM Enrollment e WHERE e.user.id = :userId AND e.section.id = :sectionId")
    String findTeamIdByUserIdAndSectionId(@Param("userId") Long userId, @Param("sectionId") Long sectionId);

    // 사용자가 특정 분반에 수강 등록되어 있는지 확인
    boolean existsByUserIdAndSectionId(Long userId, Long sectionId);

    // 교수가 담당하는 분반들 조회 (교수용 대시보드)
    @Query("SELECT new com.project.handongjudge.user.dto.DashboardCourseDto(" +
            "c.id, c.title, s.id, s.sectionNumber, u.name, " +
            "CAST(COALESCE(SUM(CASE WHEN n.isNew = true THEN 1 ELSE 0 END), 0) AS long), " +
            "CAST(COALESCE(SUM(CASE WHEN a.isNew = true THEN 1 ELSE 0 END), 0) AS long), " +
            "CAST(COUNT(DISTINCT n.id) AS long), " +
            "CAST((SELECT COUNT(e2.id) FROM Enrollment e2 WHERE e2.section.id = s.id) AS long), " +
            "s.createdAt) " +
            "FROM Section s " +
            "JOIN Course c ON s.course.id = c.id " +
            "JOIN User u ON s.instructor.id = u.id " +
            "LEFT JOIN Notice n ON n.section.id = s.id " +
            "LEFT JOIN Assignment a ON a.section.id = s.id " +
            "WHERE s.instructor.id = :instructorId " +
            "GROUP BY c.id, c.title, s.id, s.sectionNumber, u.name, s.createdAt")
    List<DashboardCourseDto> findDashboardCoursesByInstructorId(@Param("instructorId") Long instructorId);

    @Query("SELECT new com.project.handongjudge.user.dto.StudentDto(" +
            "u.id, u.name, u.email, '', e.teamId, s.id, " +
            "CONCAT(c.title, ' - Section ', s.sectionNumber), c.title, s.sectionNumber, " +
            "e.joinedAt, u.updatedAt, " +
            "CAST(0.0 AS double), CAST(0 AS int), CAST(0 AS int)) " +
            "FROM Enrollment e " +
            "JOIN e.user u " +
            "JOIN e.section s " +
            "JOIN s.course c " +
            "WHERE s.id = :sectionId")
    List<StudentDto> findStudentsBySectionId(@Param("sectionId") Long sectionId);

    @Query("SELECT new com.project.handongjudge.user.dto.StudentDto(" +
            "u.id, u.name, u.email, '', e.teamId, s.id, " +
            "CONCAT(c.title, ' - Section ', s.sectionNumber), c.title, s.sectionNumber, " +
            "e.joinedAt, u.updatedAt, " +
            "CAST(0.0 AS double), CAST(0 AS int), CAST(0 AS int)) " +
            "FROM Enrollment e " +
            "JOIN e.user u " +
            "JOIN e.section s " +
            "JOIN s.course c " +
            "WHERE s.instructor.id = :instructorId " +
            "ORDER BY c.title, s.sectionNumber, u.name")
    List<StudentDto> findStudentsByInstructorId(@Param("instructorId") Long instructorId);
    // 분반의 모든 학생 조회 (학생별 문제 풀이 현황용)
    @Query("SELECT e.user FROM Enrollment e " +
            "WHERE e.section.id = :sectionId " +
            "ORDER BY e.user.email")
    List<User> findUsersBySectionId(@Param("sectionId") Long sectionId);
}
