package com.project.handongjudge.problem.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.handongjudge.user.entity.User;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "problem_set")
public class ProblemSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "LONGTEXT")
    private String description;

    @Column(length = 500)
    private String tags;  // JSON string 형식: ["태그1", "태그2"]

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 문제집을 만든 instructor 정보
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "problemSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProblemSetProblem> problemSetProblems;
}

