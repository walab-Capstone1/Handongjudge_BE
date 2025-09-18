package com.project.handongjudge.problem.entity;

import com.project.handongjudge.assignment.entity.AssignmentProblem;
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

    // 새로 추가되는 필드들
    @Column(name = "time_limit")
    private Double timeLimit;  // 초 단위

    @Column(name = "memory_limit")
    private Integer memoryLimit;  // MB 단위


    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AssignmentProblem> assignmentProblems;
}