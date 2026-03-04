package com.project.handongjudge.problem.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCreateResponse {
    private int successCount;
    private int failureCount;
    private List<Long> createdIds;
    private List<BulkCreateFailure> failures;
}
