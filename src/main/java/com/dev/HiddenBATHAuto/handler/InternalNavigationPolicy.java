package com.dev.HiddenBATHAuto.handler;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;

/**
 * 내부 사용자 로그인/접근 거부 시 이동 경로를 한 곳에서 결정합니다.
 * ADMIN은 기존 경로를 유지하고, 그 외 내부 사용자는 권한보다 소속 팀을 먼저 적용합니다.
 */
public final class InternalNavigationPolicy {

    private InternalNavigationPolicy() {
    }

    public static String resolveDefaultRedirectUrl(Member member) {
        if (member == null || member.getRole() == null) {
            return "/loginForm?error=unauthorized";
        }

        MemberRole role = member.getRole();
        if (role == MemberRole.ADMIN) {
            return "/common/main";
        }

        if (role == MemberRole.CUSTOMER_REPRESENTATIVE
                || role == MemberRole.CUSTOMER_EMPLOYEE) {
            return "/index";
        }

        String teamName = member.getTeam() != null && member.getTeam().getName() != null
                ? member.getTeam().getName().trim()
                : "";

        // 팀을 먼저 나누고, 각 팀 안에서 MANAGEMENT / INTERNAL_EMPLOYEE 경로를 분리합니다.
        // 현재 두 권한의 목적지는 같지만 이후 팀장 전용 화면을 추가할 때 한쪽 경로만 변경할 수 있습니다.
        return switch (teamName) {
            case "생산팀" -> resolveByInternalRole(
                    role, "/team/productionList", "/team/productionList");
            case "배송팀" -> resolveByInternalRole(
                    role, "/team/deliveryRoute", "/team/deliveryRoute");
            case "AS팀" -> resolveByInternalRole(
                    role, "/team/asList", "/team/asList");
            case "출고팀" -> resolveByInternalRole(
                    role, "/team/dispatchList", "/team/dispatchList");
            case "관리팀" -> resolveByInternalRole(
                    role, "/common/main", "/common/main");
            default -> resolveByInternalRole(role, "/common/main", "/common/main");
        };
    }

    private static String resolveByInternalRole(
            MemberRole role,
            String managementRedirect,
            String employeeRedirect
    ) {
        return switch (role) {
            case MANAGEMENT -> managementRedirect;
            case INTERNAL_EMPLOYEE -> employeeRedirect;
            default -> "/loginForm?error=unauthorized";
        };
    }
}
