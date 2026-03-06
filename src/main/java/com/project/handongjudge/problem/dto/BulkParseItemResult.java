package com.project.handongjudge.problem.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkParseItemResult {
    private String filename;   // 폴더/문제명
    private boolean success;
    private ProblemFileParseResult parseResult;
    private List<String> validationErrors;
}
