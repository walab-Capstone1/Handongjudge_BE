package com.project.handongjudge.mypage.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDto {
    private Long id;
    private String email;
    private String name;
    private String profileImageUrl;
    private String role;
    private String provider; // GITHUB, GOOGLE, LOCAL
    private String providerId;
    private String githubUsername; // GitHub 사용자명
    private String githubProfileUrl; // GitHub 프로필 URL
    private String createdAt;
    private String updatedAt;

    public static UserProfileDto from(com.project.handongjudge.user.entity.User user) {
        String githubUsername = null;
        String githubProfileUrl = null;

        // GitHub 연동 사용자인 경우 GitHub 정보 추출
        if (user.getProvider() == com.project.handongjudge.user.entity.User.AuthProvider.GITHUB) {
            githubUsername = user.getName(); // GitHub에서 받은 이름이 보통 사용자명
            githubProfileUrl = "https://github.com/" + user.getName();
        }

        return UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .profileImageUrl(user.getProfileImageUrl())
                .role(user.getRole().name())
                .provider(user.getProvider().name())
                .providerId(user.getProviderId())
                .githubUsername(githubUsername)
                .githubProfileUrl(githubProfileUrl)
                .createdAt(user.getCreatedAt().toString())
                .updatedAt(user.getUpdatedAt().toString())
                .build();
    }
}