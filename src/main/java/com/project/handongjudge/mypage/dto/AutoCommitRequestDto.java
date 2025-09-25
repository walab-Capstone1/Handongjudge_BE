package com.project.handongjudge.mypage.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoCommitRequestDto {
    private String problemTitle;
    private String problemId;
    private String sectionName;
    private String language;
    private String code;
    private String result;
}