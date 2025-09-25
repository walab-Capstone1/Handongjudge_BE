package com.project.handongjudge.mypage.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitHistoryDto {
    private String commitMessage;
    private String commitDate;
    private String problemTitle;
    private String commitUrl;
    private String commitHash;
}