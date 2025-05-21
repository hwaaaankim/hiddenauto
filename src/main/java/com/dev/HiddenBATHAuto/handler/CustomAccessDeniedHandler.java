package com.dev.HiddenBATHAuto.handler;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException)
            throws IOException, ServletException {

        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));

        if (isAjax) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"접근이 거부되었습니다.\", \"code\": 403}");
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String role = "UNKNOWN";

            if (auth != null && auth.getPrincipal() instanceof PrincipalDetails principal) {
                role = principal.getMember().getRole().name();
            }

            String redirectUrl = switch (role) {
                case "ADMIN", "MANAGEMENT", "INTERNAL_EMPLOYEE" -> "/common/main";
                case "CUSTOMER_REPRESENTATIVE", "CUSTOMER_EMPLOYEE" -> "/index";
                default -> "/loginForm?error=forbidden";
            };

            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(
                "<script>" +
                "alert('접근 권한이 없습니다.\\n권한에 맞는 화면으로 이동합니다.');" +
                "window.location.href = '" + redirectUrl + "';" +
                "</script>"
            );
        }
    }
}