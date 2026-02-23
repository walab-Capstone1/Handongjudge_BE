package com.project.handongjudge.grade.repository;

import com.project.handongjudge.grade.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    
    // 과제별 모든 성적 조회
    List<Grade> findByAssignmentId(Long assignmentId);
    
    // 학생별 과제 성적 조회
    List<Grade> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);
    
    // 문제별 학생 성적 조회
    Optional<Grade> findByAssignmentIdAndProblemIdAndStudentId(
        Long assignmentId, Long problemId, Long studentId
    );
    
    // 과제의 모든 학생 성적 조회 (문제별 그룹화)
    @Query("SELECT g FROM Grade g WHERE g.assignment.id = :assignmentId " +
           "ORDER BY g.student.id, g.problem.id")
    List<Grade> findAllByAssignmentOrderByStudentAndProblem(@Param("assignmentId") Long assignmentId);
    
    // 학생의 특정 과제 총점 계산
    @Query("SELECT COALESCE(SUM(g.score), 0) FROM Grade g " +
           "WHERE g.assignment.id = :assignmentId AND g.student.id = :studentId")
    Integer calculateTotalScore(@Param("assignmentId") Long assignmentId, 
                                @Param("studentId") Long studentId);
    
    // 과제의 특정 문제 배점 조회
    @Query("SELECT ap.points FROM com.project.handongjudge.assignment.entity.AssignmentProblem ap " +
           "WHERE ap.assignment.id = :assignmentId AND ap.problem.id = :problemId")
    Optional<Integer> findProblemPoints(@Param("assignmentId") Long assignmentId,
                                       @Param("problemId") Long problemId);

    /** 분반 삭제 시 FK 제약 회피: 해당 과제들을 참조하는 성적 삭제 */
    void deleteByAssignment_IdIn(List<Long> assignmentIds);
}

