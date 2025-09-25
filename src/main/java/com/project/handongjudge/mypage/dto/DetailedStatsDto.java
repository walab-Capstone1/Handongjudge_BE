package com.project.handongjudge.mypage.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetailedStatsDto {
    private Map<String, Integer> dailySubmissions; // 일별 제출 통계
    private Map<String, Integer> languageStats; // 언어별 통계
    private Map<String, Double> accuracyTrend; // 정답률 트렌드
    private Map<String, Integer> weeklyActivity; // 주간 활동 통계
}