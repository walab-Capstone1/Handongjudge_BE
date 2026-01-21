package com.project.handongjudge.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardStatsDto {
    // 전체 통계
    private Long totalSections;          // 전체 수업 수
    private Long totalAssignments;       // 전체 과제 수
    private Long totalProblems;          // 전체 문제 수
    private Long totalStudents;          // 전체 수강생 수
    
    // 최근 활동
    private Long recentSubmissions;      // 최근 제출 수 (7일)
    private Long recentAssignments;      // 최근 과제 수 (7일)
    
    // 과제별 통계
    private List<AssignmentStatsDto> assignmentStats;
    
    // 수업별 통계
    private List<SectionStatsDto> sectionStats;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentStatsDto {
        private Long assignmentId;
        private String assignmentTitle;
        private Long sectionId;
        private String sectionTitle;
        private Long totalStudents;
        private Long submittedStudents;
        private Long completedStudents;
        private Double submissionRate;
        private Double completionRate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionStatsDto {
        private Long sectionId;
        private String sectionTitle;
        private Long studentCount;
        private Long assignmentCount;
        private Long activeAssignmentCount;
        private Long noticeCount;
    }
}

