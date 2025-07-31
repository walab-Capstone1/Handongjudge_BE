package com.project.handongjudge.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.Map;

@Getter
@AllArgsConstructor
public class OAuth2UserInfo {
    private String provider;
    private String id;
    private String email;
    private String name;
    private String imageUrl;

    public static OAuth2UserInfo of(String registrationId, Map<String, Object> attributes) {
        switch (registrationId) {
            case "google":
                return ofGoogle(attributes);
            case "github":
                return ofGithub(attributes);
            default:
                throw new RuntimeException("지원하지 않는 OAuth provider입니다: " + registrationId);
        }
    }

    private static OAuth2UserInfo ofGoogle(Map<String, Object> attributes) {
        return new OAuth2UserInfo(
                "google",
                String.valueOf(attributes.get("sub")),
                (String) attributes.get("email"),
                (String) attributes.get("name"),
                (String) attributes.get("picture")
        );
    }

    private static OAuth2UserInfo ofGithub(Map<String, Object> attributes) {
        return new OAuth2UserInfo(
                "github",
                String.valueOf(attributes.get("id")),
                (String) attributes.get("email"),
                (String) attributes.get("name"),
                (String) attributes.get("avatar_url")
        );
    }
}