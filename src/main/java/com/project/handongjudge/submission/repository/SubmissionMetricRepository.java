package com.project.handongjudge.submission.repository;

import com.project.handongjudge.submission.entity.SubmissionMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SubmissionMetricRepository extends JpaRepository<SubmissionMetric, Long> {

    @Query("SELECT sm.language, COUNT(sm), AVG(sm.e2eDurationMs), AVG(sm.pollingAttempts), " +
           "SUM(CASE WHEN sm.timedOut = true THEN 1 ELSE 0 END) " +
           "FROM SubmissionMetric sm GROUP BY sm.language")
    List<Object[]> getStatsByLanguage();
}
