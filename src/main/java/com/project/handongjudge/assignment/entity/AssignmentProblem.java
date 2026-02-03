package com.project.handongjudge.assignment.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.handongjudge.problem.entity.Problem;
import lombok.*;

import javax.persistence.*;

@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"assignment_id", "problem_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentProblem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;

    private Integer problemOrder; // 문제 순서 (선택)

    @Column(nullable = false)
    @Builder.Default
    private Integer points = 1; // 기본 배점 1점 // 문제 배점 (기본값 0)
}
