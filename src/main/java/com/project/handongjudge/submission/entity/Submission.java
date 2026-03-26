package com.project.handongjudge.submission.entity;

import javax.persistence.*;

import com.project.handongjudge.section.entity.Section;
import lombok.*;
import java.time.LocalDateTime;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.user.entity.User;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Entity
@Table(name = "submissions")    
public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id") // domjudge submissionId
    private String submissionId;

    @ManyToOne
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(name = "language")
    String language;

    @Column(name = "code", columnDefinition = "TEXT")
    String code;

    @Column(name = "submitted_at")
    LocalDateTime submittedAt;

    @Column(name = "result")
    String result;

    /** 채점 완료 시 맞은 테스트케이스 수 (DomJudge output 기준, 없으면 null) */
    @Column(name = "passed_test_cases")
    private Integer passedTestCases;

    /** 전체 테스트케이스 수 */
    @Column(name = "total_test_cases")
    private Integer totalTestCases;

    // Submission.java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;



}
