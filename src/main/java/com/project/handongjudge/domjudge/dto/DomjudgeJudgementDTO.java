package com.project.handongjudge.domjudge.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DomjudgeJudgementDTO {
    private String result;
    private Double maxRunTime;
    private Double maxMemoryUsed;
}
