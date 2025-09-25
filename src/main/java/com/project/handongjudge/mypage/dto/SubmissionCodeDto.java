package com.project.handongjudge.mypage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionCodeDto {
    private Long submissionId;
    private String problemTitle;
    private String sectionName;
    private String language;
    private String code;
    private String result;
    private LocalDateTime submittedAt;
    private String executionTime;  // 실행 시간
    private String memoryUsage;    // 메모리 사용량
    private String compileMessage; // 컴파일 메시지 (에러 시)
}