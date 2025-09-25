package com.project.handongjudge.mypage.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitHubStatusDto {
    private boolean isConnected;
    private String githubUsername;
    private String githubProfileUrl;
    private String repositoryName;
    private boolean autoCommitEnabled;
}