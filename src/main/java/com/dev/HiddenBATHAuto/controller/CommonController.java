package com.dev.HiddenBATHAuto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.service.auth.MemberService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class CommonController {
	
	private final MemberService memberService;
	
	@GetMapping("/loginForm")
	public String loginForm() {
		
		return "front/common/signIn";
	}
	
	@GetMapping("/signUp")
	public String signUp() {
		
		return "front/common/signUp";
	}
	
	@PostMapping("/signUpProcess")
	public String signUpProcess(
	        @ModelAttribute Company company,
	        @ModelAttribute Member member,
	        @RequestParam("role") String role,
	        @RequestParam(value = "registrationKey", required = false) String registrationKey,
	        @RequestParam(value = "businessLicenseFile", required = false) MultipartFile file,
	        Model model
	) {
	    try {
	        if ("CUSTOMER_REPRESENTATIVE".equals(role)) {
	            memberService.registerCustomerRepresentative(company, member, role, file);
	            model.addAttribute("successMessage", "회사 대표자 회원가입이 완료되었습니다.");
	        } else if ("CUSTOMER_EMPLOYEE".equals(role)) {
	            memberService.registerCustomerEmployee(member, registrationKey);
	            model.addAttribute("successMessage", "직원 회원가입이 완료되었습니다.");
	        } else {
	            model.addAttribute("errorMessage", "잘못된 회원 유형입니다.");
	            return "front/common/signUp";
	        }

	        return "redirect:/loginForm";

	    } catch (IllegalArgumentException e) {
	        // 사용자 입력 오류 (대표 or 직원 모두 해당)
	        model.addAttribute("errorMessage", e.getMessage());
	        return "front/common/signUp";

	    } catch (Exception e) {
	        // 시스템 예외
	        model.addAttribute("errorMessage", "회원가입 중 알 수 없는 오류 발생: " + e.getMessage());
	        return "front/common/signUp";
	    }
	}
}
