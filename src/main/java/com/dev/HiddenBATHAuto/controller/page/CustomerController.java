package com.dev.HiddenBATHAuto.controller.page;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/customer")
public class CustomerController {
	
	private final MemberRepository memberRepository;

    // 방식 1: PathVariable
    @GetMapping("/check-role/{memberId}")
    public String checkCustomerRoleByPath(@PathVariable Long memberId) {
        return checkCustomerRole(memberId);
    }

    // 방식 2: RequestBody
    @PostMapping("/check-role")
    public String checkCustomerRoleByBody(@RequestBody Map<String, Long> body) {
        Long memberId = body.get("memberId");
        return checkCustomerRole(memberId);
    }

    private String checkCustomerRole(Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new RuntimeException("해당 회원이 존재하지 않습니다."));

        switch (member.getRole()) {
            case CUSTOMER_REPRESENTATIVE:
                return "고객사 대표입니다. (회사: " + member.getCompany().getName() + ")";
            case CUSTOMER_EMPLOYEE:
                return "고객사 직원입니다. (회사: " + member.getCompany().getName() + ")";
            default:
                return "고객사 소속이 아닙니다.";
        }
    }
	
	@GetMapping("/shared")
	@ResponseBody
    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER_REPRESENTATIVE', 'ROLE_CUSTOMER_EMPLOYEE')")
    public String customerSharedAccess() {
        return "고객사 대표 또는 직원 모두 접근 가능한 페이지입니다.";
    }

    // 대표만 접근 가능
    @GetMapping("/representative-only")
    @ResponseBody
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER_REPRESENTATIVE')")
    public String customerRepresentativeOnly() {
        return "고객사 대표만 접근 가능한 페이지입니다.";
    }
}
