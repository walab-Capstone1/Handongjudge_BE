package com.project.handongjudge.submission.repository;

import com.project.handongjudge.submission.entity.SubmissionMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubmissionMetricRepository extends JpaRepository<SubmissionMetric, Long> {

    Optional<SubmissionMetric> findBySubmission_Id(Long submissionId);

    /**
     * 비동기 결과 폴링 1회: DOMjudge 조회 시간을 judging에 누적하고 polling_attempts + 1 (단일 UPDATE로 경합 완화).
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SubmissionMetric m SET " +
           "m.judgingDurationMs = COALESCE(m.judgingDurationMs, 0) + :deltaMs, " +
           "m.pollingAttempts = COALESCE(m.pollingAttempts, 0) + 1 " +
           "WHERE m.submission.id = :submissionId")
    int addAsyncPollSampleBySubmissionId(@Param("submissionId") Long submissionId, @Param("deltaMs") long deltaMs);

    @Query("SELECT sm.language, COUNT(sm), AVG(sm.e2eDurationMs), AVG(sm.pollingAttempts), " +
           "SUM(CASE WHEN sm.timedOut = true THEN 1 ELSE 0 END) " +
           "FROM SubmissionMetric sm GROUP BY sm.language")
    List<Object[]> getStatsByLanguage();
}
