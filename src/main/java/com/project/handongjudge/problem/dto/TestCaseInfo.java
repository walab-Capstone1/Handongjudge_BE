package com.project.handongjudge.problem.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseInfo {
    private String name;        // 테스트케이스 이름 (예: "sample-1", "secret-1")
    private String input;       // 입력 데이터
    private String output;      // 출력 데이터
    private String type;        // "sample" 또는 "secret"
}