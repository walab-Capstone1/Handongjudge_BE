package com.project.handongjudge.submission.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.ManyToOne;
import javax.persistence.JoinColumn;
import javax.persistence.Column;
import lombok.*;
import java.time.LocalDateTime;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.user.entity.User;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Entity
@Table(name = "submissions")    
public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id") // domjudge submissionId
    private String submissionId;

    @ManyToOne
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(name = "language")
    String language;

    @Column(name = "code")
    String code;

    @Column(name = "submitted_at")
    LocalDateTime submittedAt;

    @Column(name = "result")
    String result;

    
}
