package com.project.handongjudge.problem.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProblemSetUpdateRequest {
    private String title;
    private String description;
    private String tags;  // JSON string 형식: ["태그1", "태그2"]
}

