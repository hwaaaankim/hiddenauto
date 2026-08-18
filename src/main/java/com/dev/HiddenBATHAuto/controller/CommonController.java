package com.dev.HiddenBATHAuto.controller;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.codec.EncoderException;
import org.springframework.http.ResponseEntity;
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
    public String loginForm(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "username", required = false) String username,
            Model model
    ) {
        model.addAttribute("loginError", error != null);
        model.addAttribute("loginErrorMessage", message);
        model.addAttribute("loginUsername", username);
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

	@PostMapping("/api/v1/account-recovery/username")
	public ResponseEntity<Map<String, Object>> findUsernameByPhoneApi(@RequestParam("phone") String phone) {
		String normalizedPhone = phone == null ? "" : phone.trim();
		if (normalizedPhone.isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of(
					"success", false,
					"message", "휴대폰 번호를 입력해 주세요."
			));
		}

		Optional<Member> memberOpt = memberRepository.findByPhone(normalizedPhone);
		if (memberOpt.isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of(
					"success", false,
					"message", "등록된 연락처가 없습니다."
			));
		}

		try {
			Member member = memberOpt.get();
			String message = "[히든바스] 가입하신 아이디는 [" + member.getUsername() + "] 입니다.";
			smsService.sendMessage(normalizedPhone, message);

			return ResponseEntity.ok(Map.of(
					"success", true,
					"message", "가입 시 입력한 아이디를 문자로 발송했습니다."
			));
		} catch (RuntimeException e) {
			log.warn("[계정찾기] 아이디 문자 발송 실패 phone={}", normalizedPhone, e);
			return ResponseEntity.internalServerError().body(Map.of(
					"success", false,
					"message", "문자 발송 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
			));
		}
	}

	@PostMapping("/api/v1/account-recovery/password")
	public ResponseEntity<Map<String, Object>> findPasswordApi(
			@RequestParam("username") String username,
			@RequestParam("phone") String phone
	) {
		String normalizedUsername = username == null ? "" : username.trim();
		String normalizedPhone = phone == null ? "" : phone.trim();

		if (normalizedUsername.isEmpty() || normalizedPhone.isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of(
					"success", false,
					"message", "아이디와 휴대폰 번호를 모두 입력해 주세요."
			));
		}

		Optional<Member> memberOpt = memberRepository.findByUsername(normalizedUsername);
		if (memberOpt.isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of(
					"success", false,
					"message", "아이디가 존재하지 않습니다."
			));
		}

		Member member = memberOpt.get();
		if (member.getPhone() == null || member.getPhone().trim().isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of(
					"success", false,
					"message", "연락처 정보가 등록되어 있지 않습니다."
			));
		}

		if (!member.getPhone().equals(normalizedPhone)) {
			return ResponseEntity.badRequest().body(Map.of(
					"success", false,
					"message", "아이디와 휴대폰 번호가 일치하지 않습니다."
			));
		}

		String previousEncodedPassword = member.getPassword();
		try {
			String tempPassword = generateRandomPassword(8);
			member.setPassword(passwordEncoder.encode(tempPassword));
			memberRepository.save(member);

			String message = "__" + tempPassword + "__로 비밀번호를 변경 하였습니다. '나의정보수정' 을 이용 해 주시기 바랍니다.";
			smsService.sendMessage(normalizedPhone, message);

			return ResponseEntity.ok(Map.of(
					"success", true,
					"message", "임시 비밀번호를 문자로 발송했습니다. 로그인 후 나의정보수정에서 비밀번호를 변경해 주세요."
			));
		} catch (RuntimeException e) {
			/*
			 * SMS 발송 실패 후 비밀번호만 바뀌어 사용자가 로그인할 수 없게 되는 상황을 방지합니다.
			 * 기존 비밀번호 해시를 즉시 복원하되, 복원 자체가 실패하면 원인 로그를 별도로 남깁니다.
			 */
			try {
				member.setPassword(previousEncodedPassword);
				memberRepository.save(member);
			} catch (RuntimeException restoreException) {
				log.error("[계정찾기] 임시 비밀번호 실패 후 기존 비밀번호 복원 실패 username={}",
						normalizedUsername, restoreException);
			}

			log.warn("[계정찾기] 임시 비밀번호 처리 실패 username={}", normalizedUsername, e);
			return ResponseEntity.internalServerError().body(Map.of(
					"success", false,
					"message", "임시 비밀번호 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
			));
		}
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
