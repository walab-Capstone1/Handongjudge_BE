package com.project.handongjudge.mypage.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatsDto {
    private int totalSubmissions; // 총 제출 수
    private int correctSubmissions; // 정답 제출 수
    private double accuracyRate; // 정답률 (%)
    private int solvedProblems; // 해결한 문제 수
    private int enrolledSections; // 수강 중인 분반 수
    private Map<String, Integer> languageStats; // 언어별 사용 통계
    private List<RecentActivityDto> recentActivities; // 최근 활동

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentActivityDto {
        private String problemTitle;
        private String sectionName;
        private String result;
        private String language;
        private String submittedAt;
    }
}