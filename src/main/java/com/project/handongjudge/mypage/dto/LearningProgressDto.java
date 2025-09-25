package com.project.handongjudge.mypage.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningProgressDto {
    private int totalAssignments; // 전체 과제 수
    private int completedAssignments; // 완료된 과제 수
    private double progressRate; // 진행률 (%)
    private int totalProblems; // 전체 문제 수
    private int solvedProblems; // 해결한 문제 수
}