package com.dev.HiddenBATHAuto.handler;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.RequestDispatcher;
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
            request.setAttribute("userRole", request.isUserInRole("ADMIN") ? "ADMIN"
                    : request.isUserInRole("INTERNAL_EMPLOYEE") ? "INTERNAL_EMPLOYEE"
                    : request.isUserInRole("MANAGEMENT") ? "MANAGEMENT"
                    : "UNKNOWN");
            request.setAttribute("requestedUri", request.getRequestURI());

            RequestDispatcher dispatcher = request.getRequestDispatcher("/error/403");
            dispatcher.forward(request, response);
        }
    }
}
