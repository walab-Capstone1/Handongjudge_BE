package com.project.handongjudge.submission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.project.handongjudge.submission.entity.Submission;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByUserId(Long userId);

    // 과제별 제출 통계
    @Query("SELECT COUNT(DISTINCT s.user.id) FROM Submission s " +
            "WHERE s.problem.id IN " +
            "(SELECT ap.problem.id FROM AssignmentProblem ap WHERE ap.assignment.id = :assignmentId) " +
            "AND s.section.id = :sectionId")
    Integer countSubmittedStudentsByAssignment(@Param("assignmentId") Long assignmentId,
                                               @Param("sectionId") Long sectionId);

    // 문제별 제출 통계
    @Query("SELECT COUNT(DISTINCT s.user.id) FROM Submission s " +
            "WHERE s.problem.id = :problemId AND s.section.id = :sectionId")
    Integer countSubmittedStudentsByProblem(@Param("problemId") Long problemId,
                                            @Param("sectionId") Long sectionId);

    // 문제별 정답 제출 수
    @Query("SELECT COUNT(DISTINCT s.user.id) FROM Submission s " +
            "WHERE s.problem.id = :problemId AND s.section.id = :sectionId " +
            "AND s.result = 'correct'")
    Integer countCorrectSubmissionsByProblem(@Param("problemId") Long problemId,
                                             @Param("sectionId") Long sectionId);

    // 분반별 학생 수 (수강생 수)
    @Query("SELECT COUNT(DISTINCT e.user.id) FROM Enrollment e WHERE e.section.id = :sectionId")
    Integer countStudentsBySection(@Param("sectionId") Long sectionId);
    @Query("SELECT COUNT(DISTINCT s.user.id) FROM Submission s " +
            "WHERE s.section.id = :sectionId " +
            "AND s.problem.id IN " +
            "(SELECT ap.problem.id FROM AssignmentProblem ap WHERE ap.assignment.id = :assignmentId) " +
            "GROUP BY s.user.id " +
            "HAVING COUNT(DISTINCT s.problem.id) = " +
            "(SELECT COUNT(ap) FROM AssignmentProblem ap WHERE ap.assignment.id = :assignmentId)")
    Integer countAllProblemsSubmittedStudents(@Param("assignmentId") Long assignmentId, @Param("sectionId") Long sectionId);
}
