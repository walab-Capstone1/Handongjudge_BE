package com.project.handongjudge.mypage.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitHubRepositoryDto {
    private String repositoryName;
    private String repositoryUrl;
    private String branchName;
}