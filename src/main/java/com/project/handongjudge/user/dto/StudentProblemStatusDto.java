package com.project.handongjudge.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class StudentProblemStatusDto {
    private Long problemId;
    private String problemTitle;
    private String status;  // "ACCEPTED", "SUBMITTED", "NOT_SUBMITTED"
    private Integer submissionCount;  // 해당 문제에 대한 제출 횟수
}