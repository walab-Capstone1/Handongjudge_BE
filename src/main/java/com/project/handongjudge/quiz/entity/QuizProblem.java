package com.project.handongjudge.quiz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.handongjudge.problem.entity.Problem;
import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "quiz_problems")
@Getter
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
}

