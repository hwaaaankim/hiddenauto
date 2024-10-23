package com.dev.HiddenBATHAuto.handler;

import java.io.IOException;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        // 사용자 권한을 확인
        Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());

        // 권한에 따라 리다이렉트 경로 설정
        if (roles.contains("ROLE_ADMIN")) {
            response.sendRedirect("/test/admin");  // 관리자 페이지로 리다이렉트
        } else {
            response.sendRedirect("/");  // 일반 사용자 페이지로 리다이렉트
        }
    }
}
