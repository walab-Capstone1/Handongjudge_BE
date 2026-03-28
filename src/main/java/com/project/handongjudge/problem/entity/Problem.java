package com.project.handongjudge.problem.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.handongjudge.assignment.entity.AssignmentProblem;
import com.project.handongjudge.user.entity.User;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Problem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "LONGTEXT")
    private String description;

    private String difficulty;

    @Column(name = "domjudge_problem_id")
    private String domjudgeProblemId;

    @Column(name = "time_limit")
    private Double timeLimit;

    @Column(name = "memory_limit")
    private Integer memoryLimit;

    /**
     * true면 problem.yaml에 validator_flags: space_change_sensitive 등을 넣어 공백/형식을 엄격히 채점 (Domjudge 기본 비교와 구분)
     */
    @Column(name = "strict_whitespace_grading")
    private Boolean strictWhitespaceGrading;

    private LocalDateTime createdAt;

    // 문제를 만든 instructor 정보
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    // 원본 ZIP 파일 경로 (기존 호환성 유지, 마이그레이션용)
    @Column(name = "zip_file_path", length = 500)
    private String zipFilePath;

    // ZIP 파일 데이터 (BLOB)
    @Lob
    @Column(name = "zip_file_data", columnDefinition = "BLOB")
    @JsonIgnore
    private byte[] zipFileData;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AssignmentProblem> assignmentProblems;
}