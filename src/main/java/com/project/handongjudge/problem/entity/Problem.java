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

    @Column(length = 1000)
    private String description;

    private String difficulty;

    @Column(name = "domjudge_problem_id")
    private String domjudgeProblemId;

    @Column(name = "time_limit")
    private Double timeLimit;

    @Column(name = "memory_limit")
    private Integer memoryLimit;

    private LocalDateTime createdAt;

    // 문제를 만든 instructor 정보
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    // 원본 ZIP 파일 경로 (복사 시 재사용)
    @Column(name = "zip_file_path", length = 500)
    private String zipFilePath;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AssignmentProblem> assignmentProblems;
}