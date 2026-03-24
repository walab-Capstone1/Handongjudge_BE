package com.project.handongjudge.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 환경별 Refresh Token 쿠키 옵션
 * application-local.yml / application-deploy.yml 에서 auth.cookie.* 로 설정
 */
@Component
@ConfigurationProperties(prefix = "auth.cookie")
@Getter
@Setter
public class AuthCookieProperties {
    private String name = "refreshToken";
    private boolean secure = true;
    private String sameSite = "None";
    private String domain = "";
    private int maxAge = 604800;  // 7일 (초)
    private String path = "/";
}
