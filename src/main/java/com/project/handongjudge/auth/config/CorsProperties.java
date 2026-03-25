package com.project.handongjudge.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 환경별 CORS 허용 오리진
 * application-local.yml / application-deploy.yml 에서 auth.cors.* 로 설정
 */
@Component
@ConfigurationProperties(prefix = "auth.cors")
@Getter
@Setter
public class CorsProperties {
    private List<String> allowedOrigins = new ArrayList<>();
}
