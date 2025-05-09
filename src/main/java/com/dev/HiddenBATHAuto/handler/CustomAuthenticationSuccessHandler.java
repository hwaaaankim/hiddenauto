package com.dev.HiddenBATHAuto.handler;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        PrincipalDetails principal = (PrincipalDetails) authentication.getPrincipal();
        Member member = principal.getMember();
        String role = principal.getMember().getRole().name();
        
        String redirectUrl;
        switch (role) {
            case "ADMIN":
                redirectUrl = "/admin/index";
                break;
            case "MANAGEMENT":
                redirectUrl = "/management/index";
                break;
            case "INTERNAL_EMPLOYEE":
                Long memberId = member.getId();
                redirectUrl = String.format("/team/%d", memberId);
                break;
            case "CUSTOMER_REPRESENTATIVE":
                redirectUrl = "/index";
                break;
            case "CUSTOMER_EMPLOYEE":
                redirectUrl = "/index";
                break;
            default:
                redirectUrl = "/loginForm?error=unauthorized";
        }

        response.sendRedirect(redirectUrl);
    }
}