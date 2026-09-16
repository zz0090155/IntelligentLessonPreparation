package com.example.backend.config;

import com.example.backend.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;

@Component
public class AuthContextInterceptor implements HandlerInterceptor {

    public static final String ATTR_AUTH_HEADER = "auth.context.header";
    public static final String ATTR_TEACHER_ID = "auth.context.teacher_id";

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
        String normalizedHeader = normalizeAuthHeader(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (normalizedHeader == null) {
            return true;
        }

        request.setAttribute(ATTR_AUTH_HEADER, normalizedHeader);
        String token = normalizedHeader.substring(7).trim();

        if (token.isEmpty() || !JwtUtils.checkToken(token)) {
            return true;
        }

        try {
            int teacherId = JwtUtils.getIdFromJwt(token);
            request.setAttribute(ATTR_TEACHER_ID, teacherId);
        } catch (Exception ignore) {
            return true;
        }
        return true;
    }

    private String normalizeAuthHeader(String authHeader) {
        if (authHeader == null) {
            return null;
        }
        String text = authHeader.trim();
        if (text.isEmpty()) {
            return null;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("bearer ")) {
            String token = text.substring(7).trim();
            return token.isEmpty() ? null : "Bearer " + token;
        }
        return "Bearer " + text;
    }
}
