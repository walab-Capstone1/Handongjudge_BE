package com.project.handongjudge.assignment.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class StudentProgressResponse {
    private Long userId;
    private String studentId;      // 학번
    private String studentName;    // 학생 이름
    private List<Long> solvedProblems;  // 푼 문제 ID 리스트
}