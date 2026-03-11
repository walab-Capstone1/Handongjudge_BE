package com.project.handongjudge.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class StudentProblemStatusDto {
    private Long problemId;
    private String problemTitle;
    private String status;  // "ACCEPTED", "SUBMITTED", "NOT_SUBMITTED"
    private Integer submissionCount;  // 해당 문제에 대한 제출 횟수
    private Boolean isOnTime;  // 제출 시 마감일 이전 여부 (미제출이면 null)
    /** 제출 시각 (미제출이면 null) */
    private LocalDateTime submittedAt;
    /** 지각 제출 시 마감일 대비 늦은 분 수 (제시간 제출·미제출이면 null) */
    private Integer minutesLate;
}