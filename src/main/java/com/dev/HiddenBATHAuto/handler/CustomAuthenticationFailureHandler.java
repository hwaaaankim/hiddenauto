package com.dev.HiddenBATHAuto.handler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        String username = request.getParameter("username");
        String message;

        if (exception instanceof UsernameNotFoundException) {
            message = "존재하지 않는 계정입니다.";
        } else if (exception instanceof BadCredentialsException) {
            message = "비밀번호가 올바르지 않습니다.";
        } else if (exception instanceof DisabledException) {
            message = "비활성화된 계정입니다. 관리자에게 문의해주세요.";
        } else if (exception instanceof LockedException) {
            message = "잠긴 계정입니다. 관리자에게 문의해주세요.";
        } else if (exception instanceof CredentialsExpiredException) {
            message = "비밀번호 사용기간이 만료되었습니다.";
        } else {
            message = "로그인에 실패했습니다. 다시 시도해주세요.";
        }

        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
        String encodedUsername = URLEncoder.encode(username == null ? "" : username, StandardCharsets.UTF_8);

        response.sendRedirect("/loginForm?error=true&message=" + encodedMessage + "&username=" + encodedUsername);
    }
}