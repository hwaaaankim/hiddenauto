package com.dev.HiddenBATHAuto.controller.page;

import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.ordernotification.OrderNotificationPolicyService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/order-notification-policy")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderNotificationPolicyController {

    private final OrderNotificationPolicyService policyService;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("policySections", policyService.getPolicySections());
        return "administration/notification/order-notification-policy";
    }

    @PostMapping
    public String save(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam(required = false, name = "webKeys") Set<String> webKeys,
            @RequestParam(required = false, name = "kakaoKeys") Set<String> kakaoKeys,
            RedirectAttributes redirectAttributes
    ) {
        policyService.saveAll(
                webKeys,
                kakaoKeys,
                principal != null ? principal.getMember() : null
        );
        redirectAttributes.addFlashAttribute("savedMessage", "로깅 알림 발송 정책을 저장했습니다.");
        return "redirect:/admin/order-notification-policy";
    }
}
