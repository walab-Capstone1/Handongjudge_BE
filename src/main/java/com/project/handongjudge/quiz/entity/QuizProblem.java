package com.project.handongjudge.quiz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.handongjudge.problem.entity.Problem;
import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "quiz_problems")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizProblem {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @Column(name = "problem_order")
    private Integer problemOrder; // 문제 순서

    @Column(name = "points")
    private Integer points = 1; // 문제 배점 (기본 1점, null이면 1로 처리)
}


