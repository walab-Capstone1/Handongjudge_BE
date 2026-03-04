package com.project.handongjudge.problem.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProblemCreateRequest {
    private String title;
    private String description;
    private String inputFormat;
    private String outputFormat;
    private String tags;  // JSON string
    private String difficulty;  // "1", "2", "3"
    private String timeLimit;
    private String memoryLimit;
    private String sampleInputs;  // JSON string
    private List<TestCaseDto> testcases;  // 테스트케이스 (name, input, output, type)
}