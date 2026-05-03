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
    /** 강사·튜터 코멘트(과제 전용, 학생에게 표시) */
    private String gradeComment;
    /** 반려 태그(과제 전용) */
    private Boolean gradeRejected;
    /** 반려 처리 시각(과제·반려인 경우 gradedAt) */
    private LocalDateTime gradeRejectedAt;
}