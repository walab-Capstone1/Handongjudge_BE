package com.project.handongjudge.problem.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemFileParseResult {
    private String title;
    private String description;
    private String inputFormat;
    private String outputFormat;
    /** 예제 입출력 [{input, output}, ...] */
    private List<Map<String, String>> sampleInputs;
    private Double timeLimit;
    private Integer memoryLimit;
    private List<TestCaseDto> testcases;
    private Boolean strictWhitespaceGrading;
}
