package com.project.handongjudge.problem.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddProblemToSetRequest {
    private Long problemId;
    private Integer order;  // 문제 순서 (선택, 없으면 마지막에 추가)
}

