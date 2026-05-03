package com.project.handongjudge.grade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeResponseDTO {
    private Long id;
    private Long assignmentId;
    private String assignmentTitle;
    private Long problemId;
    private String problemTitle;
    private Long userId;
    private String studentName;
    private String studentId;
    private Integer points; // 배점
    private Integer score; // 입력한 점수
    private String comment;
    private Boolean rejected;
    private LocalDateTime gradedAt;
    private String gradedByName;
    
    // 제출 정보
    private Boolean submitted;
    private LocalDateTime submittedAt;
    private Boolean isOnTime; // 제시간 제출 여부
    private String result; // AC, WA, TLE 등
}

