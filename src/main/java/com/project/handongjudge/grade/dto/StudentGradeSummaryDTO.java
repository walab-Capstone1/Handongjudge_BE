package com.project.handongjudge.grade.dto;

import com.project.handongjudge.submission.entity.Output;
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
        private String result;
        /** Domjudge 제출 ID (테스트케이스 상세 조회 시 사용) */
        private String submissionDomjudgeId;
        /** includeTestCaseResults=true 일 때만 채움 */
        private List<Output> testCaseResults;
        private Integer passedTestCaseCount;
        private Integer totalTestCaseCount;
        private Boolean allTestCasesPassed;
    }
}

