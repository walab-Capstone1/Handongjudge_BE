package com.project.handongjudge.progress.entity;

import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.user.entity.User;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "code_progress",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"user_id", "problem_id", "section_id", "language"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @Column(name = "language", nullable = false, length = 20)
    private String language;

    @Column(name = "code", columnDefinition = "TEXT")
    private String code;

    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt;
}
