package com.project.handongjudge.quiz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.handongjudge.section.entity.Section;
import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    @JsonIgnore
    private Section section;

    private String title;

    @Column(length = 1000)
    private String description;

    private LocalDateTime startTime;
    
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private QuizStatus status = QuizStatus.WAITING;

    @JsonIgnore
    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuizProblem> quizProblems;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    public enum QuizStatus {
        WAITING,  // 대기중
        ACTIVE,   // 진행중
        ENDED     // 종료됨
    }

    public void updateStatus(QuizStatus newStatus) {
        this.status = newStatus;
    }
}


