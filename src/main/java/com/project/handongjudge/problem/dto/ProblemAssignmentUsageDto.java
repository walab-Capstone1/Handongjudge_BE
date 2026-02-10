package com.project.handongjudge.problem.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProblemAssignmentUsageDto {
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

