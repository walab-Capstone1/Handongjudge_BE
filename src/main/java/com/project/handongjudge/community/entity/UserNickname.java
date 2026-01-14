package com.project.handongjudge.community.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.user.entity.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_nicknames",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_section_user_nickname",
           columnNames = {"section_id", "user_id"}
       ),
       indexes = {
           @Index(name = "idx_user_nickname_user", columnList = "user_id"),
           @Index(name = "idx_user_nickname_section", columnList = "section_id"),
           @Index(name = "idx_user_nickname_nickname", columnList = "nickname")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNickname {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "nickname_id")
    private Long id;

    // 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    // 섹션 (각 수업마다 다른 별명 가능)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    @JsonIgnore
    private Section section;

    // 별명
    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}


