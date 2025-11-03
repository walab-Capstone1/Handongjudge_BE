package com.project.handongjudge.assignment.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class StudentProgressResponse {
    private Long userId;
    private String studentId;      // 학번
    private String studentName;    // 학생 이름
    private List<Long> solvedProblems;  // 푼 문제 ID 리스트
    private Map<Long, LocalDateTime> problemSubmissionTimes;  // 문제별 첫 정답 제출 시간 (문제ID -> 제출시간)
    private LocalDateTime assignmentCompletedAt;  // 과제 완료 시간 (모든 문제를 다 푼 시점)
}