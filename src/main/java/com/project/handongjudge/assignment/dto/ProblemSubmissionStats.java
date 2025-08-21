package com.project.handongjudge.assignment.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProblemSubmissionStats {
    private Long problemId;
    private String problemTitle;
    private Integer problemOrder;
    private Integer totalStudents;        // 분반 전체 학생 수
    private Integer submittedStudents;    // 문제 제출한 학생 수
    private Integer correctSubmissions;   // 정답 제출 수
    private Double submissionRate;        // 문제 제출률 (%)
    private Double correctRate;           // 정답률 (%)
}