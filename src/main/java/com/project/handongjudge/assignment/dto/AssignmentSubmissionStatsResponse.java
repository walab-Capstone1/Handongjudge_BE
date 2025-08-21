package com.project.handongjudge.assignment.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class AssignmentSubmissionStatsResponse {
    private Long assignmentId;
    private String assignmentTitle;
    private Long sectionId;
    private String sectionName;
    private Integer totalStudents;        // 분반 전체 학생 수
    private Integer submittedStudents;    // 과제 제출한 학생 수
    private Double submissionRate;        // 과제 제출률 (%)
    private List<ProblemSubmissionStats> problemStats; // 각 문제별 제출 통계
}

