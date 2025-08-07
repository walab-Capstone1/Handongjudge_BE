package com.project.handongjudge.user.entity;

import com.project.handongjudge.section.entity.Section;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;


    @Column(name = "team_id", nullable = true)  
    private String teamId;

    private String roleInCourse;

    private LocalDateTime joinedAt;
}
