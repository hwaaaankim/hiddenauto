package com.dev.HiddenBATHAuto.controller.page;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.dev.HiddenBATHAuto.handler.InternalNavigationPolicy;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;

@Controller
@RequestMapping("/common")
public class CommonPagingController {

    @GetMapping("/main")
    public String commonMain(@AuthenticationPrincipal PrincipalDetails principal) {
        Member member = principal != null ? principal.getMember() : null;
        if (member == null || member.getRole() == null) {
            return "redirect:/loginForm?error=unauthorized";
        }

        if (canUseManagementDashboard(member)) {
            return "administration/index";
        }

        String redirectUrl = InternalNavigationPolicy.resolveDefaultRedirectUrl(member);
        if ("/common/main".equals(redirectUrl)) {
            return "redirect:/loginForm?error=unauthorized";
        }

        return "redirect:" + redirectUrl;
    }

    private boolean canUseManagementDashboard(Member member) {
        if (member.getRole() == MemberRole.ADMIN) {
            return true;
        }

        return member.getRole() == MemberRole.MANAGEMENT
                && member.getTeam() != null
                && "관리팀".equals(member.getTeam().getName());
    }
}
