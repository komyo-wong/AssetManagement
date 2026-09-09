package com.assetmanagement.auth;

import com.assetmanagement.security.AuthProperties;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Component
public class RefreshCookieService {

    private final AuthProperties properties;

    public RefreshCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    public String require(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw missing();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> properties.refreshToken().cookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow(RefreshCookieService::missing);
    }

    public String find(HttpServletRequest request) {
        try {
            return require(request);
        } catch (BusinessException exception) {
            return null;
        }
    }

    public void set(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(refreshToken, properties.refreshToken().ttl()));
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO));
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(properties.refreshToken().cookieName(), value)
                .httpOnly(true)
                .secure(properties.refreshToken().secure())
                .sameSite(properties.refreshToken().sameSite())
                .path(properties.refreshToken().cookiePath())
                .maxAge(maxAge)
                .build()
                .toString();
    }

    private static BusinessException missing() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh session is required");
    }
}
