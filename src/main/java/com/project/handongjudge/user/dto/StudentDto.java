package com.project.handongjudge.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class StudentDto {
    private Long userId;
    private String name;
    private String email;
    private String studentId; // 학번 (User 엔티티에 필드 추가 필요하거나 임시로 null)
    private String teamId;
    private Long sectionId;
    private String sectionName;
    private String courseTitle;
    private Integer sectionNumber;
    private LocalDateTime enrolledAt;
    private LocalDateTime lastLogin; // User의 updatedAt 활용
}