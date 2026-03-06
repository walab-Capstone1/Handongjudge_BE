package com.project.handongjudge.problem.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCreateFailure {
    private int index;
    private String reason;
}
