package com.project.handongjudge.notice.entity;

import com.project.handongjudge.section.entity.Section;
import javax.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // section_id 외래키
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private String difficulty; // 예: EASY, MEDIUM, HARD 등 (enum 고려해도 됨)

    @Column(name = "is_new", nullable = false)
    private boolean isNew;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}
