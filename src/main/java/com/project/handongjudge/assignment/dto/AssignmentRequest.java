package com.project.handongjudge.assignment.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AssignmentRequest {

    private String assignmentNumber;
    private String title;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private List<Long> problemIds; // 추가: 연결할 문제 ID 리스트
}
