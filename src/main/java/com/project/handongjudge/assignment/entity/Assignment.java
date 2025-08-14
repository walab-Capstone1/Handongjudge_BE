package com.project.handongjudge.assignment.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.handongjudge.section.entity.Section;
import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    private String assignmentNumber;

    private String title;

    @Column(length = 1000)
    private String description;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @Column(name = "is_new", nullable = false)
    @Builder.Default
    private boolean isNew = true;
    @JsonIgnore
    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AssignmentProblem> assignmentProblems;


}
