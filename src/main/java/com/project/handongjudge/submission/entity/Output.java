package com.project.handongjudge.submission.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.persistence.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@Builder
@NoArgsConstructor  // 기본 생성자 추가
@AllArgsConstructor // 모든 필드를 파라미터로 받는 생성자 추가
public class Output {
    @JsonProperty("testcase_rank")  // JSON 필드 이름 매핑
    String testcase;

    @JsonProperty("result")
    private String result;

    @JsonProperty("runtime")
    private int runtime;

    @JsonProperty("memory_limit")
    private int memoryLimit;

    @JsonProperty("memory_used")  // JSON 필드 이름 매핑
    private int memoryUsed;

    @JsonProperty("output")
    private String output;

    @JsonProperty("output_error")
    private String outputError;

    @JsonProperty("output_diff")
    private String outputDiff;

}
//"testcase_rank": 0,
//        "result": "string",
//        "runtime": 0,
//        "memory_limit": 0,
//        "memory_used": 0,
//        "output": "string",
//        "output_error": "string",
//        "output_diff": "string",
//        "team_message": "string"