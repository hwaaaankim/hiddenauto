package com.dev.HiddenBATHAuto.handler;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        PrincipalDetails principal = (PrincipalDetails) authentication.getPrincipal();
        HttpSession session = request.getSession(false);
        String redirectUrl = null;

        if (session != null) {
            SavedRequest savedRequest = (SavedRequest) session.getAttribute("SPRING_SECURITY_SAVED_REQUEST");

            if (savedRequest != null) {
                String requestedUrl = savedRequest.getRedirectUrl();

                // ❗ WebSocket 등 잘못된 URL 혹은 권한 없는 경로로 요청한 경우 차단
                if (isInvalidRedirectUrl(requestedUrl)
                        || !isAccessibleByMember(principal.getMember(), requestedUrl)) {
                    redirectUrl = getDefaultRedirectUrl(principal);
                } else {
                    redirectUrl = requestedUrl;
                }
            }
        }

        // ❗ 저장된 요청이 없거나 세션 없음
        if (redirectUrl == null) {
            redirectUrl = getDefaultRedirectUrl(principal);
        }

        redirectStrategy.sendRedirect(request, response, redirectUrl);
    }

    private String getDefaultRedirectUrl(PrincipalDetails principal) {
        return InternalNavigationPolicy.resolveDefaultRedirectUrl(
                principal != null ? principal.getMember() : null
        );
    }

    private boolean isInvalidRedirectUrl(String url) {
        return url.contains("/ws/") || !url.startsWith("/");
    }

    /**
     * 로그인 전에 저장된 요청도 현재 보안 규칙과 같은 "팀 우선" 기준으로 검증합니다.
     *
     * MANAGEMENT는 더 이상 곧바로 관리팀을 의미하지 않습니다.
     * - 관리팀 MANAGEMENT: 기존 /management/** 및 /admin/process/** 사용 가능
     * - 타 팀 MANAGEMENT: 해당 팀의 /team/**만 사용
     * - ADMIN: 기존 관리자 범위 유지
     */
    private boolean isAccessibleByMember(Member member, String url) {
        if (member == null || member.getRole() == null || url == null || url.isBlank()) {
            return false;
        }

        MemberRole role = member.getRole();
        String teamName = member.getTeam() != null && member.getTeam().getName() != null
                ? member.getTeam().getName().trim()
                : "";

        if (url.startsWith("/management")) {
            return role == MemberRole.ADMIN
                    || (role == MemberRole.MANAGEMENT && "관리팀".equals(teamName));
        }

        if (url.startsWith("/admin/process")) {
            return role == MemberRole.ADMIN
                    || (role == MemberRole.MANAGEMENT && "관리팀".equals(teamName));
        }

        if (url.startsWith("/admin") || url.startsWith("/analytics")) {
            return role == MemberRole.ADMIN;
        }

        if (url.startsWith("/team")) {
            return (role == MemberRole.MANAGEMENT || role == MemberRole.INTERNAL_EMPLOYEE)
                    && !teamName.isBlank()
                    && !"관리팀".equals(teamName);
        }

        if (role == MemberRole.CUSTOMER_REPRESENTATIVE
                || role == MemberRole.CUSTOMER_EMPLOYEE) {
            return !url.startsWith("/common/main");
        }

        if (role == MemberRole.ADMIN
                || role == MemberRole.MANAGEMENT
                || role == MemberRole.INTERNAL_EMPLOYEE) {
            return !url.startsWith("/index") && !url.equals("/");
        }

        return false;
    }
}
