package com.project.handongjudge.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class StudentAssignmentProgressDto {
    private Long assignmentId;
    private String assignmentTitle;
    private String description;
    private Integer totalProblems;      // 과제의 전체 문제 수
    private Integer solvedProblems;     // 학생이 풀은 문제 수 (ACCEPTED)
    private Double progressRate;        // 진도율 (%)
}