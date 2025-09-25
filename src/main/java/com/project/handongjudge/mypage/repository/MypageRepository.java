package com.project.handongjudge.mypage.repository;

import com.project.handongjudge.submission.entity.Submission;
import com.project.handongjudge.user.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface MypageRepository extends JpaRepository<Submission, Long> {

    // ==================== 기본 통계 쿼리 ====================

    // 총 제출 수
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.user.id = :userId")
    int countTotalSubmissionsByUserId(@Param("userId") Long userId);

    // 정답 제출 수
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.user.id = :userId AND s.result = 'correct'")
    int countCorrectSubmissionsByUserId(@Param("userId") Long userId);

    // 해결한 문제 수 (중복 제거)
    @Query("SELECT COUNT(DISTINCT s.problem.id) FROM Submission s WHERE s.user.id = :userId AND s.result = 'correct'")
    int countSolvedProblemsByUserId(@Param("userId") Long userId);

    // 수강 중인 분반 수
    @Query("SELECT COUNT(DISTINCT e.section.id) FROM Enrollment e WHERE e.user.id = :userId")
    int countEnrolledSectionsByUserId(@Param("userId") Long userId);

    // 언어별 통계 쿼리 수정
    @Query("SELECT s.language, COUNT(s) FROM Submission s " +
            "WHERE s.user.id = :userId AND s.language IS NOT NULL AND s.language != '' " +
            "GROUP BY s.language")
    Map<String, Integer> getLanguageStatsByUserId(@Param("userId") Long userId);
    // ==================== 상세 통계 쿼리 ====================

    // 일별 제출 통계 (최근 N일) - 간단한 버전
    @Query("SELECT s.submittedAt, COUNT(s) FROM Submission s " +
            "WHERE s.user.id = :userId AND s.submittedAt >= :startDate " +
            "GROUP BY s.submittedAt ORDER BY s.submittedAt DESC")
    List<Object[]> getDailySubmissionStatsRaw(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);

    // 정답률 트렌드 (최근 N일) - 간단한 버전
    @Query("SELECT s.submittedAt, " +
            "COUNT(CASE WHEN s.result = 'correct' THEN 1 END) * 100.0 / COUNT(s) as accuracy " +
            "FROM Submission s " +
            "WHERE s.user.id = :userId AND s.submittedAt >= :startDate " +
            "GROUP BY s.submittedAt ORDER BY s.submittedAt DESC")
    List<Object[]> getAccuracyTrendRaw(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);

    // 전체 과제 수 - 간단한 버전
    @Query("SELECT COUNT(DISTINCT a.id) FROM Assignment a " +
            "JOIN a.section s " +
            "JOIN Enrollment e ON e.section.id = s.id " +
            "WHERE e.user.id = :userId")
    int countTotalAssignmentsByUserId(@Param("userId") Long userId);

    // 완료된 과제 수 - 간단한 버전
    @Query("SELECT COUNT(DISTINCT a.id) FROM Assignment a " +
            "JOIN a.section s " +
            "JOIN Enrollment e ON e.section.id = s.id " +
            "WHERE e.user.id = :userId " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM AssignmentProblem ap " +
            "  WHERE ap.assignment.id = a.id " +
            "  AND NOT EXISTS (" +
            "    SELECT 1 FROM Submission sub " +
            "    WHERE sub.user.id = :userId " +
            "    AND sub.problem.id = ap.problem.id " +
            "    AND sub.result = 'correct'" +
            "  )" +
            ")")
    int countCompletedAssignmentsByUserId(@Param("userId") Long userId);

    // ==================== 데이터 조회 쿼리 ====================

    // 최근 제출 기록 (limit 파라미터 제거)
    @Query("SELECT sub FROM Submission sub " +
            "JOIN FETCH sub.problem p " +
            "JOIN FETCH sub.section s " +
            "JOIN FETCH s.course c " +
            "WHERE sub.user.id = :userId " +
            "ORDER BY sub.submittedAt DESC")
    List<Submission> getRecentSubmissionsByUserId(@Param("userId") Long userId);

    // 수강 중인 과목 현황
    @Query("SELECT e FROM Enrollment e " +
            "JOIN FETCH e.section s " +
            "JOIN FETCH s.course c " +
            "JOIN FETCH s.instructor i " +
            "WHERE e.user.id = :userId")
    List<Enrollment> getEnrolledSectionsByUserId(@Param("userId") Long userId);

    /**
     * 제출 ID로 상세 정보 조회 (코드 포함)
     */
    @Query("SELECT s FROM Submission s " +
            "JOIN FETCH s.problem p " +
            "JOIN FETCH s.section sec " +
            "JOIN FETCH sec.course c " +
            "JOIN FETCH s.user u " +
            "WHERE s.id = :submissionId")
    Optional<Submission> findSubmissionWithDetailsById(@Param("submissionId") Long submissionId);
}