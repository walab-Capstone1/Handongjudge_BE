package com.project.handongjudge.grade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentGradeSummaryDTO {
    private Long userId;
    private String studentName;
    private String studentId;
    private List<ProblemGradeDTO> problemGrades;
    private Integer totalScore; // 총 점수
    private Integer totalPoints; // 총 배점
    private String gradeRatio; // "85/100" 형식
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProblemGradeDTO {
        private Long problemId;
        private String problemTitle;
        private Integer points; // 배점
        private Integer score; // 입력한 점수
        private Boolean submitted;
        private LocalDateTime submittedAt;
        private Boolean isOnTime;
        /** ZIP submissions.csv의 lateDuration과 동일 (SubmissionDeadlineComparison) */
        private String lateDuration;
        private String result;
        /** 맞은 테스트케이스 수 (제출 시 저장된 값) */
        private Integer passedTestCases;
        /** 전체 테스트케이스 수 */
        private Integer totalTestCases;
    }
}

