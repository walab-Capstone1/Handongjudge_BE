package com.project.handongjudge.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class StudentDto {
    private Long userId;
    private String name;
    private String email;
    private String studentId;
    private String teamId;
    private Long sectionId;
    private String sectionName;
    private String courseTitle;
    private Integer sectionNumber;
    private LocalDateTime enrolledAt;
    private LocalDateTime lastLogin;

    // 추가: 과제 진도율
    private Double assignmentCompletionRate;  // 전체 과제 완료율 (%)
    private Integer completedAssignments;      // 완료한 과제 수
    private Integer totalAssignments;          // 전체 과제 수
}