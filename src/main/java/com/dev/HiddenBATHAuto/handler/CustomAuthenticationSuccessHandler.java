package com.dev.HiddenBATHAuto.handler;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

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
        String role = principal.getMember().getRole().name();

        HttpSession session = request.getSession(false);
        String redirectUrl = null;

        if (session != null) {
            SavedRequest savedRequest = (SavedRequest) session.getAttribute("SPRING_SECURITY_SAVED_REQUEST");

            if (savedRequest != null) {
                String requestedUrl = savedRequest.getRedirectUrl();

                // ❗ WebSocket 등 잘못된 URL 혹은 권한 없는 경로로 요청한 경우 차단
                if (isInvalidRedirectUrl(requestedUrl) || !isAccessibleByRole(role, requestedUrl)) {
                	redirectUrl = getDefaultRedirectUrl(role, principal);
                } else {
                    redirectUrl = requestedUrl;
                }
            }
        }

        // ❗ 저장된 요청이 없거나 세션 없음
        if (redirectUrl == null) {
        	redirectUrl = getDefaultRedirectUrl(role, principal);
        }

        redirectStrategy.sendRedirect(request, response, redirectUrl);
    }

    private String getDefaultRedirectUrl(String role, PrincipalDetails principal) {
        return switch (role) {
            case "ADMIN", "MANAGEMENT" -> "/common/main";
            case "INTERNAL_EMPLOYEE" -> {
                String teamName = principal.getMember().getTeam().getName();
                yield switch (teamName) {
                    case "생산팀" -> "/team/productionList";
                    case "배송팀" -> "/team/deliveryList";
                    case "AS팀" -> "/team/asList";
                    default -> "/common/main"; // 기본 fallback
                };
            }
            case "CUSTOMER_REPRESENTATIVE", "CUSTOMER_EMPLOYEE" -> "/index";
            default -> "/loginForm?error=unauthorized";
        };
    }


    private boolean isInvalidRedirectUrl(String url) {
        return url.contains("/ws/") || !url.startsWith("/");
    }

    private boolean isAccessibleByRole(String role, String url) {
        return switch (role) {
            case "ADMIN", "MANAGEMENT", "INTERNAL_EMPLOYEE" -> !url.startsWith("/index") && !url.equals("/");
            case "CUSTOMER_REPRESENTATIVE", "CUSTOMER_EMPLOYEE" -> !url.startsWith("/admin") && !url.startsWith("/management") && !url.startsWith("/team");
            default -> false;
        };
    }
}