package com.project.handongjudge.submission.entity;

import javax.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "submission_metrics")
public class SubmissionMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = true)
    private Submission submission;

    @Column(name = "domjudge_submission_id")
    private String domjudgeSubmissionId;

    @Column(name = "submit_duration_ms")
    private Long submitDurationMs;

    @Column(name = "judging_duration_ms")
    private Long judgingDurationMs;

    @Column(name = "e2e_duration_ms")
    private Long e2eDurationMs;

    @Column(name = "polling_attempts")
    private Integer pollingAttempts;

    @Column(name = "language")
    private String language;

    @Column(name = "problem_id")
    private Long problemId;

    @Column(name = "section_id")
    private Long sectionId;

    @Column(name = "timed_out")
    private Boolean timedOut;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "measured_at")
    private LocalDateTime measuredAt;

    @Column(name = "flow_type")
    private String flowType;
}
