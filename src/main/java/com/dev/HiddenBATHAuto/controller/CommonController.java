package com.dev.HiddenBATHAuto.controller;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Optional;

import org.apache.commons.codec.EncoderException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.service.SMSService;
import com.dev.HiddenBATHAuto.service.auth.MemberService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CommonController {

	private final MemberService memberService;
	private final MemberRepository memberRepository;
	private final SMSService smsService;
	private final PasswordEncoder passwordEncoder;

	@GetMapping("/excelConvert")
	public String excelConvert() {
		
		return "front/excelConvert";
	}
	
	@GetMapping("/excelMemberInsertForm")
	public String excelMemberInsertForm() {
		
		return "front/excelMemberInsertForm";
	}
	
	@GetMapping("/loginForm")
	public String loginForm() {

		return "front/common/signIn";
	}

	@GetMapping("/signUp")
	public String signUp() {

		return "front/common/signUp";
	}

	@GetMapping("/findUsername")
	public String findUsername() {

		return "front/common/findUsername";
	}

	@PostMapping("/findUsernameProcess")
	public void findUsernameByPhone(@RequestParam("phone") String phone, HttpServletResponse response)
			throws IOException {
		System.out.println(phone);
		Optional<Member> memberOpt = memberRepository.findByPhone(phone);

		response.setContentType("text/html;charset=UTF-8");

		if (memberOpt.isEmpty()) {
			response.getWriter().println("<script>alert('등록된 연락처가 없습니다.'); location.href='/findUsername';</script>");
			return;
		}

		Member member = memberOpt.get();
		String message = "[히든바스] 가입하신 아이디는 [" + member.getUsername() + "] 입니다.";

		smsService.sendMessage(phone, message);

		response.getWriter().println("<script>alert('가입시 입력한 아이디를 문자로 발송했습니다.'); location.href='/loginForm';</script>");
	}

	@GetMapping("/findPassword")
	public String findPassword() {

		return "front/common/findPassword";
	}

	@PostMapping("/findPasswordProcess")
	public void findPassword(@RequestParam("username") String username, @RequestParam("phone") String phone,
			HttpServletResponse response) throws IOException {

		response.setContentType("text/html;charset=UTF-8");
		Optional<Member> memberOpt = memberRepository.findAll().stream().filter(m -> m.getUsername().equals(username))
				.findFirst();

		if (memberOpt.isEmpty()) {
			response.getWriter().println("<script>alert('아이디가 존재하지 않습니다.'); location.href='/findPassword';</script>");
			return;
		}

		Member member = memberOpt.get();

		if (member.getPhone() == null || member.getPhone().trim().isEmpty()) {
			response.getWriter()
					.println("<script>alert('연락처 정보가 등록되어 있지 않습니다.'); location.href='/findPassword';</script>");
			return;
		}

		if (!member.getPhone().equals(phone)) {
			response.getWriter()
					.println("<script>alert('아이디와 휴대폰 번호가 일치하지 않습니다.'); location.href='/findPassword';</script>");
			return;
		}

		// 임시 비밀번호 생성
		String tempPassword = generateRandomPassword(8);
		String encodedPassword = passwordEncoder.encode(tempPassword);
		member.setPassword(encodedPassword);
		memberRepository.save(member);

		// 문자 내용
		String message = "__" + tempPassword + "__로 비밀번호를 변경 하였습니다. '나의정보수정' 을 이용 해 주시기 바랍니다.";

		smsService.sendMessage(phone, message);

		response.getWriter().println(
				"<script>alert('임시 비밀번호를 문자로 발송하였습니다. 로그인 후 나의정보수정에서 비밀번호를 변경해 주세요.'); location.href='/loginForm';</script>");
	}

	private String generateRandomPassword(int length) {
		final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		SecureRandom random = new SecureRandom();
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < length; i++) {
			int idx = random.nextInt(chars.length());
			sb.append(chars.charAt(idx));
		}

		return sb.toString();
	}

	@PostMapping("/signUpProcess")
    public String signUpProcess(
            @ModelAttribute Company company,
            @ModelAttribute Member member,
            @RequestParam("role") String role,
            @RequestParam(value = "registrationKey", required = false) String registrationKey,
            @RequestParam(value = "businessLicenseFile", required = false) MultipartFile file,

            // ✅ 추가 배송지 JSON (대표/직원 공통)
            @RequestParam(value = "deliveryAddressesJson", required = false) String deliveryAddressesJson,

            Model model
    ) {
        try {
            if ("CUSTOMER_REPRESENTATIVE".equals(role)) {
                memberService.registerCustomerRepresentative(company, member, role, file, deliveryAddressesJson);
                model.addAttribute("successMessage", "회사 대표자 회원가입이 완료되었습니다.");
            } else if ("CUSTOMER_EMPLOYEE".equals(role)) {
                memberService.registerCustomerEmployee(member, registrationKey, deliveryAddressesJson);
                model.addAttribute("successMessage", "직원 회원가입이 완료되었습니다.");
            } else {
                model.addAttribute("errorMessage", "잘못된 회원 유형입니다.");
                return "front/common/signUp";
            }

            return "redirect:/loginForm";

        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "front/common/signUp";

        } catch (Exception e) {
            model.addAttribute("errorMessage", "회원가입 중 알 수 없는 오류 발생: " + e.getMessage());
            return "front/common/signUp";
        }
    }
}
