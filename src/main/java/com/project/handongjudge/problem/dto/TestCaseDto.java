package com.project.handongjudge.problem.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseDto {
    private String name;   // 예: "1", "sample1"
    private String input;  // 입력 데이터
    private String output; // 출력 데이터
    private String type;   // "sample" 또는 "secret" (기본 "secret")
}
