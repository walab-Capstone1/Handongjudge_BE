package com.project.handongjudge.submission.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.project.handongjudge.submission.entity.Submission;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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



    // 분반별 학생 수 (수강생 수)
    @Query("SELECT COUNT(DISTINCT e.user.id) FROM Enrollment e WHERE e.section.id = :sectionId")
    Integer countStudentsBySection(@Param("sectionId") Long sectionId);
    /** 과제의 모든 문제를 1번 이상 제출한 사용자 ID 목록 (제출 현황 인원 수 계산용) */
    @Query("SELECT s.user.id FROM Submission s " +
            "WHERE s.section.id = :sectionId " +
            "AND s.problem.id IN " +
            "(SELECT ap.problem.id FROM AssignmentProblem ap WHERE ap.assignment.id = :assignmentId) " +
            "GROUP BY s.user.id " +
            "HAVING COUNT(DISTINCT s.problem.id) = " +
            "(SELECT COUNT(ap) FROM AssignmentProblem ap WHERE ap.assignment.id = :assignmentId)")
    List<Long> findUserIdsWhoSubmittedAllProblems(@Param("assignmentId") Long assignmentId, @Param("sectionId") Long sectionId);

    // 사용자가 특정 문제를 제출했는지 확인
    @Query("SELECT COUNT(s) > 0 FROM Submission s " +
            "WHERE s.user.id = :userId AND s.problem.id = :problemId AND s.section.id = :sectionId")
    boolean existsByUserIdAndProblemIdAndSectionId(@Param("userId") Long userId,
                                                   @Param("problemId") Long problemId,
                                                   @Param("sectionId") Long sectionId);

    // 사용자가 특정 문제를 정답으로 제출했는지 확인
    @Query("SELECT COUNT(s) > 0 FROM Submission s " +
            "WHERE s.user.id = :userId AND s.problem.id = :problemId AND s.section.id = :sectionId " +
            "AND s.result = 'AC'")
    boolean existsCorrectSubmissionByUserIdAndProblemIdAndSectionId(@Param("userId") Long userId,
                                                                    @Param("problemId") Long problemId,
                                                                    @Param("sectionId") Long sectionId);
    // user last submission code
    @Query(value = "SELECT s.code FROM submissions s WHERE s.user_id = :userId AND s.problem_id = :problemId AND s.section_id = :sectionId AND s.language = :language ORDER BY s.submitted_at DESC LIMIT 1", nativeQuery = true)
    Optional<String> getUserLastSubmission(@Param("userId") Long userId,
                                           @Param("problemId") Long problemId,@Param("sectionId") Long sectionId, @Param("language") String language);

    // 문제별 정답 제출 수 - "AC"로 수정
    @Query("SELECT COUNT(DISTINCT s.user.id) FROM Submission s " +
            "WHERE s.problem.id = :problemId AND s.section.id = :sectionId " +
            "AND s.result = 'AC'")
    Integer countCorrectSubmissionsByProblem(@Param("problemId") Long problemId,
                                             @Param("sectionId") Long sectionId);

    // 특정 학생이 특정 문제들에 대해 ACCEPTED를 받은 문제 ID 목록 - "AC"로 수정
    @Query("SELECT DISTINCT s.problem.id FROM Submission s " +
            "WHERE s.user.id = :userId " +
            "AND s.problem.id IN :problemIds " +
            "AND s.result = 'AC'")
    List<Long> findAcceptedProblemIdsByUserAndProblems(@Param("userId") Long userId,
                                                       @Param("problemIds") List<Long> problemIds);



    // 특정 학생이 특정 문제에 대해 ACCEPTED를 받았는지 확인 - 파라미터 제거하고 직접 "AC" 사용
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Submission s " +
            "WHERE s.user.id = :userId AND s.problem.id = :problemId AND s.result = 'AC'")
    Boolean existsByUserIdAndProblemIdAndAccepted(@Param("userId") Long userId,
                                                  @Param("problemId") Long problemId);

    // 특정 학생의 특정 문제에 대한 제출 횟수
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.user.id = :userId AND s.problem.id = :problemId")
    Integer countByUserIdAndProblemId(@Param("userId") Long userId, @Param("problemId") Long problemId);

    // 특정 학생의 특정 문제에 대한 ACCEPTED 제출 횟수
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.user.id = :userId AND s.problem.id = :problemId AND s.result = 'AC'")
    Integer countAcceptedByUserIdAndProblemId(@Param("userId") Long userId, @Param("problemId") Long problemId);

    // 학생별 특정 과제에서 푼 문제 조회 (정답 처리된 문제만) - "AC"로 수정
    @Query("SELECT DISTINCT s.problem.id FROM Submission s " +
            "WHERE s.user.id = :userId " +
            "AND s.problem.id IN (SELECT ap.problem.id FROM AssignmentProblem ap WHERE ap.assignment.id = :assignmentId) " +
            "AND s.section.id = :sectionId " +
            "AND s.result = 'AC'")
    List<Long> findSolvedProblemIdsByUserAndAssignment(@Param("userId") Long userId,
                                                       @Param("assignmentId") Long assignmentId,
                                                       @Param("sectionId") Long sectionId);
    // 특정 학생의 특정 문제에 대한 첫 정답 제출 시간 조회 - "AC"로 수정
    @Query("SELECT MIN(s.submittedAt) FROM Submission s " +
            "WHERE s.user.id = :userId AND s.problem.id = :problemId " +
            "AND s.section.id = :sectionId AND s.result = 'AC'")
    Optional<LocalDateTime> findFirstAcceptedSubmissionTime(@Param("userId") Long userId,
                                                            @Param("problemId") Long problemId,
                                                            @Param("sectionId") Long sectionId);

    // 특정 학생의 특정 문제에 대한 가장 최근 제출 시간 (지각 여부 판단용)
    @Query("SELECT MAX(s.submittedAt) FROM Submission s " +
            "WHERE s.user.id = :userId AND s.problem.id = :problemId " +
            "AND s.section.id = :sectionId")
    Optional<LocalDateTime> findLatestSubmissionTime(@Param("userId") Long userId,
                                                     @Param("problemId") Long problemId,
                                                     @Param("sectionId") Long sectionId);

    /** 분반·문제별 submitted_at 기준 가장 늦은 제출 1건 (성적·표시용) */
    Optional<Submission> findTopByUser_IdAndProblem_IdAndSection_IdOrderBySubmittedAtDesc(
            Long user_id, Long problem_id, Long section_id);

    // 특정 학생의 특정 문제에 대한 첫 번째 accept된 제출 조회
    @Query("SELECT s FROM Submission s " +
            "WHERE s.user.id = :userId AND s.problem.id = :problemId " +
            "AND s.section.id = :sectionId AND s.result = 'AC' " +
            "ORDER BY s.submittedAt ASC")
    List<Submission> findAcceptedSubmissionsByUserAndProblem(@Param("userId") Long userId,
                                                              @Param("problemId") Long problemId,
                                                              @Param("sectionId") Long sectionId);

    /** 성적·표시용: 해당 분반·문제의 가장 최근 제출 1건 (결과 무관) */
    @Query("SELECT s FROM Submission s " +
            "WHERE s.user.id = :userId AND s.problem.id = :problemId AND s.section.id = :sectionId " +
            "ORDER BY s.submittedAt DESC")
    List<Submission> findLatestSubmissionsByUserAndProblem(
            @Param("userId") Long userId,
            @Param("problemId") Long problemId,
            @Param("sectionId") Long sectionId,
            Pageable pageable);

    // 시스템 관리자용: 모든 제출 조회 (관계 엔티티 포함)
    @Query("SELECT s FROM Submission s " +
            "JOIN FETCH s.user u " +
            "JOIN FETCH s.problem p " +
            "JOIN FETCH s.section sec " +
            "JOIN FETCH sec.course c " +
            "ORDER BY s.submittedAt DESC")
    List<Submission> findAllWithDetails();
    
    // 특정 날짜 이후 제출 수
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.submittedAt >= :date")
    long countBySubmittedAtAfter(@Param("date") LocalDateTime date);
    
    // 특정 문제들에 제출한 고유 사용자 수
    @Query("SELECT COUNT(DISTINCT s.user.id) FROM Submission s " +
            "WHERE s.problem.id IN :problemIds AND s.section.id = :sectionId")
    long countDistinctUsersByProblemIdsAndSectionId(@Param("problemIds") List<Long> problemIds, 
                                                     @Param("sectionId") Long sectionId);
    
    // 특정 문제에 대한 모든 제출 조회
    @Query("SELECT s FROM Submission s WHERE s.problem.id = :problemId")
    List<Submission> findByProblemId(@Param("problemId") Long problemId);
    
}
