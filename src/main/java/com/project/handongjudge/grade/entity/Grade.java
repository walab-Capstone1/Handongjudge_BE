package com.project.handongjudge.grade.entity;

import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.user.entity.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "grades", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"assignment_id", "problem_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Grade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User student;
    
    @Column(nullable = false)
    private Integer points; // 배점 (AssignmentProblem에서 가져옴)
    
    @Column(nullable = true)
    private Integer score; // 입력한 점수 (null 가능 - 미채점)
    
    @Column(length = 500)
    private String comment; // 코멘트 (학생에게도 표시)

    /** 강사·튜터 반려 태그 (true면 점수 0으로 처리) */
    @Column(nullable = false)
    @Builder.Default
    private boolean rejected = false;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy; // 채점한 튜터
    
    @Column(name = "graded_at")
    private LocalDateTime gradedAt; // 채점 시간
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

