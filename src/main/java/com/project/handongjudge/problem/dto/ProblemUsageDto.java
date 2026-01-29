package com.project.handongjudge.problem.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProblemUsageDto {
    // 과제 사용 정보
    private List<AssignmentUsage> assignments;
    
    // 문제집 사용 정보
    private List<ProblemSetUsage> problemSets;
    
    // 퀴즈 사용 정보
    private List<QuizUsage> quizzes;
    
    @Data
    @Builder
    public static class AssignmentUsage {
        private Long assignmentId;
        private String assignmentTitle;
        private String assignmentNumber;
        private LocalDateTime assignmentStartDate;
        private LocalDateTime assignmentEndDate;
        
        // 수업 정보
        private Long sectionId;
        private String courseTitle;
        private Integer sectionNumber;
        private Integer year;
        private String semester;
    }
    
    @Data
    @Builder
    public static class ProblemSetUsage {
        private Long problemSetId;
        private String problemSetTitle;
        private String description;
        private LocalDateTime createdAt;
    }
    
    @Data
    @Builder
    public static class QuizUsage {
        private Long quizId;
        private String quizTitle;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        
        // 수업 정보
        private Long sectionId;
        private String courseTitle;
        private Integer sectionNumber;
        private Integer year;
        private String semester;
    }
}

