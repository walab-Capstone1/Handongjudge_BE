package com.project.handongjudge.user.repository;

import com.project.handongjudge.user.dto.DashboardCourseDto;
import com.project.handongjudge.user.dto.StudentDto;
import com.project.handongjudge.user.entity.Enrollment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EnrollmentRepository extends CrudRepository<Enrollment, Long> {

    // 학생용: 수강 중인 분반 조회 (읽지 않은 공지/과제만 카운트)
    @Query("SELECT new com.project.handongjudge.user.dto.DashboardCourseDto(" +
            "c.id, c.title, s.id, u.name, COUNT(DISTINCT n.id), COUNT(DISTINCT a.id)) " +
            "FROM Enrollment e " +
            "JOIN Section s ON e.section.id = s.id " +
            "JOIN Course c ON s.course.id = c.id " +
            "JOIN User u ON s.instructor.id = u.id " +
            "LEFT JOIN Notice n ON n.section.id = s.id AND n.isNew = true " +
            "    AND NOT EXISTS (SELECT 1 FROM UserReadStatus urs WHERE urs.user.id = :userId AND urs.notice.id = n.id) " +
            "LEFT JOIN Assignment a ON a.section.id = s.id AND a.isNew = true " +
            "    AND NOT EXISTS (SELECT 1 FROM UserReadStatus urs2 WHERE urs2.user.id = :userId AND urs2.assignment.id = a.id) " +
            "WHERE e.user.id = :userId " +
            "GROUP BY c.id, s.id, u.name")
    List<DashboardCourseDto> findDashboardCoursesByUserId(@Param("userId") Long userId);

    // 교수용: 담당하고 있는 분반 조회 (모든 새로운 공지/과제 카운트)
    @Query("SELECT new com.project.handongjudge.user.dto.DashboardCourseDto(" +
            "c.id, c.title, s.id, u.name, COUNT(DISTINCT n.id), COUNT(DISTINCT a.id)) " +
            "FROM Section s " +
            "JOIN Course c ON s.course.id = c.id " +
            "JOIN User u ON s.instructor.id = u.id " +
            "LEFT JOIN Notice n ON n.section.id = s.id AND n.isNew = true " +
            "LEFT JOIN Assignment a ON a.section.id = s.id AND a.isNew = true " +
            "WHERE s.instructor.id = :instructorId " +
            "GROUP BY c.id, s.id, u.name")
    List<DashboardCourseDto> findDashboardCoursesByInstructorId(@Param("instructorId") Long instructorId);

    @Query("SELECT e.teamId FROM Enrollment e WHERE e.user.id = :userId AND e.section.id = :sectionId")
    String findTeamIdByUserIdAndSectionId(@Param("userId") Long userId, @Param("sectionId") Long sectionId);

    // 특정 사용자가 특정 분반을 수강하고 있는지 확인
    boolean existsByUserIdAndSectionId(Long userId, Long sectionId);

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
