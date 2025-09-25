package com.project.handongjudge.mypage.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoCommitResultDto {
    private boolean success;
    private String commitMessage;
    private String commitUrl;
    private String commitHash;
    private String errorMessage;
}