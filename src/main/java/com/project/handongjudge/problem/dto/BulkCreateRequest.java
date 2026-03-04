package com.project.handongjudge.problem.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkCreateRequest {
    private List<ProblemCreateRequest> problems;
}
