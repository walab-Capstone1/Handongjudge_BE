package com.project.handongjudge.auth.util;

import com.project.handongjudge.auth.config.AuthCookieProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Refresh Token 쿠키를 환경별 옵션(SameSite, Secure, Domain 등)에 맞게 생성·삭제·추출.
 * ResponseCookie 를 사용해 SameSite 속성까지 제어한다.
 */
@Component
@RequiredArgsConstructor
public class AuthCookieUtil {

    private final AuthCookieProperties cookieProperties;

    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(refreshToken, cookieProperties.getMaxAge()).toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0).toString());
    }

    public String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (cookieProperties.getName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie buildCookie(String value, long maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieProperties.getName(), value)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path(cookieProperties.getPath())
                .maxAge(maxAge);

        if (StringUtils.hasText(cookieProperties.getDomain())) {
            builder = builder.domain(cookieProperties.getDomain());
        }

        return builder.build();
    }
}
