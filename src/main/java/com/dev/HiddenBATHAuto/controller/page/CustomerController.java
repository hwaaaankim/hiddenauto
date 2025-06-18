package com.dev.HiddenBATHAuto.controller.page;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.ProductMark;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.as.AsTaskRepository;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductMarkRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.dev.HiddenBATHAuto.service.as.AsTaskService;
import com.dev.HiddenBATHAuto.service.auth.MemberService;
import com.dev.HiddenBATHAuto.utils.FileUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/customer")
public class CustomerController {

	@Value("${spring.upload.path}")
	private String uploadPath;

	private final MemberRepository memberRepository;
	private final AsTaskService asTaskService;
	private final TaskRepository taskRepository;
	private final AsTaskRepository asTaskRepository;
	private final OrderRepository orderRepository;
	private final ObjectMapper objectMapper;
	private final CompanyRepository companyRepository;
	private final PasswordEncoder passwordEncoder;
	private final MemberService memberService;
	private final ProductMarkRepository productMarkRepository;

	@GetMapping("/productMarkList")
	public String productMarkList(Model model, @AuthenticationPrincipal PrincipalDetails principalDetails) throws Exception {
	    Member member = principalDetails.getMember();
	    List<ProductMark> markList = productMarkRepository.findByMember(member);

	    ObjectMapper mapper = new ObjectMapper();

	    // JSON → Map으로 파싱된 리스트 준비
	    List<Map<String, String>> localizedMapList = markList.stream()
	            .map(mark -> {
	                try {
	                    return mapper.readValue(mark.getLocalizedOptionJson(), new TypeReference<Map<String, String>>() {});
	                } catch (Exception e) {
	                    return new HashMap<String, String>(); // fallback
	                }
	            })
	            .collect(Collectors.toList());

	    model.addAttribute("markList", markList);
	    model.addAttribute("localizedOptionMapList", localizedMapList);
	    return "front/order/productMarkList"; // 렌더할 HTML
	}
	
	@GetMapping("/taskList")
	public String taskList(
	        @AuthenticationPrincipal PrincipalDetails principal,
	        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
	        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
	        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
	        Model model) {

	    Long companyId = principal.getMember().getCompany().getId();

	    LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : null;
	    LocalDateTime end = (endDate != null) ? endDate.atTime(LocalTime.MAX) : null;

	    Page<Task> taskPage = taskRepository.findByCompanyIdAndCreatedAtBetween(companyId, start, end, pageable);

	    model.addAttribute("taskPage", taskPage);
	    model.addAttribute("startDate", startDate);
	    model.addAttribute("endDate", endDate);

	    return "front/customer/task/taskList";
	}

	@GetMapping("/taskDetail/{taskId}")
	public String taskDetail(@PathVariable Long taskId, @AuthenticationPrincipal PrincipalDetails principal,
			Model model) {
		Task task = taskRepository.findById(taskId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

		if (!principal.getMember().getCompany().getId().equals(task.getRequestedBy().getCompany().getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}

		model.addAttribute("task", task);
		return "front/customer/task/taskDetail";
	}

	@GetMapping("/orderDetail/{orderId}")
	public String orderDetail(@PathVariable Long orderId, @AuthenticationPrincipal PrincipalDetails principal,
			Model model) {

		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 주문을 찾을 수 없습니다."));

		Long memberCompanyId = principal.getMember().getCompany().getId();
		Long orderCompanyId = order.getTask().getRequestedBy().getCompany().getId();

		if (!memberCompanyId.equals(orderCompanyId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 주문에 접근할 수 없습니다.");
		}

		OrderItem item = order.getOrderItem();
		if (item != null && item.getOptionJson() != null) {
			try {
				Map<String, String> parsedMap = objectMapper.readValue(item.getOptionJson(), new TypeReference<>() {
				});
				item.setParsedOptionMap(parsedMap);
			} catch (IOException e) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "옵션 데이터를 파싱할 수 없습니다.");
			}
		}

		model.addAttribute("order", order);
		return "front/customer/task/orderDetail";
	}

	@GetMapping("/asList")
	public String asList(
	        @AuthenticationPrincipal PrincipalDetails principal,
	        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
	        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
	        @PageableDefault(size = 10, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable,
	        Model model) {

	    Long companyId = principal.getMember().getCompany().getId();
	    LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : null;
	    LocalDateTime end = (endDate != null) ? endDate.atTime(LocalTime.MAX) : null;

	    Page<AsTask> asTaskPage = asTaskRepository.findByCompanyIdAndRequestedAtBetween(companyId, start, end, pageable);

	    model.addAttribute("asTaskPage", asTaskPage);
	    model.addAttribute("startDate", startDate);
	    model.addAttribute("endDate", endDate);

	    return "front/customer/task/asList";
	}

	@GetMapping("/asRequest")
	public String asRequest(Model model, @AuthenticationPrincipal PrincipalDetails principalDetails) {

		Company c = principalDetails.getMember().getCompany();
		model.addAttribute("mainAddress", c.getRoadAddress());
		model.addAttribute("detailAddress", c.getDetailAddress());
		model.addAttribute("doName", c.getDoName());
		model.addAttribute("siName", c.getSiName());
		model.addAttribute("guName", c.getGuName());
		model.addAttribute("zipCode", c.getZipCode());

		return "front/customer/as/asRequest";
	}

	@PostMapping("/asSubmit")
	@ResponseBody
	public Map<String, Object> submitAsTask(
			@AuthenticationPrincipal PrincipalDetails principal,
			@ModelAttribute AsTask task, 
			@RequestParam("imageFile") List<MultipartFile> imageFile) {
		Map<String, Object> result = new HashMap<>();
		try {
			AsTask saved = asTaskService.submitAsTask(task, imageFile, principal.getMember());
			result.put("success", true);
			result.put("message", "AS 신청이 완료되었습니다.");
			result.put("redirectUrl", "/index");
		} catch (Exception e) {
			e.printStackTrace();
			result.put("success", false);
			result.put("message", "AS 신청 중 오류가 발생했습니다.");
			result.put("redirectUrl", "/customer/asRequest");
		}
		return result;
	}

	@GetMapping("/myInfo")
	public String myInfoPage(@AuthenticationPrincipal PrincipalDetails principal, Model model) {
		Long memberId = principal.getMember().getId();
		Member freshMember = memberRepository.findById(memberId)
				.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

		Company company = freshMember.getCompany();

		model.addAttribute("member", freshMember);
		model.addAttribute("company", company);
		model.addAttribute("isRepresentative", freshMember.getRole() == MemberRole.CUSTOMER_REPRESENTATIVE);

		return "front/customer/info/myInfo";
	}

	@PostMapping("/myInfoUpdate")
	public String updateMyInfo(
			@AuthenticationPrincipal PrincipalDetails principal, 
			@RequestParam String name,
			@RequestParam String phone, 
			@RequestParam String email, 
			@RequestParam(required = false) String companyName,
			@RequestParam String roadAddress,
			@RequestParam String detailAddress, 
			@RequestParam String doName,
			@RequestParam String siName, 
			@RequestParam String guName, 
			@RequestParam String zipCode,
			@RequestParam(defaultValue = "false") boolean removeBusinessLicense,
			@RequestParam(required = false) MultipartFile businessLicenseFile) {

		Member member = principal.getMember();
		Company company = member.getCompany();

		// 멤버 정보
		member.setName(name);
		member.setPhone(phone);
		member.setEmail(email);
		member.setUpdatedAt(LocalDateTime.now());

		// 대표만 회사 정보 수정 가능
		if (principal.getMember().getRole() == MemberRole.CUSTOMER_REPRESENTATIVE) {
			if (companyName != null)
				company.setCompanyName(companyName);
			company.setRoadAddress(roadAddress);
			company.setDetailAddress(detailAddress);
			company.setDoName(doName);
			company.setSiName(siName);
			company.setGuName(guName);
			company.setZipCode(zipCode);
			company.setUpdatedAt(LocalDateTime.now());

			// 새 파일 업로드 시 기존 삭제 후 저장
			if (businessLicenseFile != null && !businessLicenseFile.isEmpty()) {
				FileUtil.deleteIfExists(company.getBusinessLicensePath());

				String originalName = businessLicenseFile.getOriginalFilename();
				String username = member.getUsername();

				// 동일한 경로로 변경 (회원가입과 일치)
				String relativePath = username + "/signUp/licence";
				String saveDir = Paths.get(uploadPath, relativePath).toString();
				FileUtil.createDirIfNotExists(saveDir);

				Path filePath = Paths.get(saveDir, originalName);
				try {
					businessLicenseFile.transferTo(filePath.toFile());

					company.setBusinessLicenseFilename(originalName);
					company.setBusinessLicensePath(filePath.toString()); // 물리 경로
					company.setBusinessLicenseUrl("/upload/" + relativePath + "/" + originalName); // 브라우저 접근용 URL
				} catch (IOException e) {
					throw new RuntimeException("파일 저장 실패", e);
				}
			}

			// 파일 삭제만 요청한 경우
			if (removeBusinessLicense) {
				FileUtil.deleteIfExists(company.getBusinessLicensePath());
				company.setBusinessLicenseFilename(null);
				company.setBusinessLicensePath(null);
				company.setBusinessLicenseUrl(null);
			}
			companyRepository.save(company);
		}

		memberRepository.save(member);

		return "redirect:/customer/myInfo";
	}

	@PostMapping("/generateRegistrationKey")
	@ResponseBody
	public Map<String, String> generateRegistrationKey(@AuthenticationPrincipal PrincipalDetails principal) {
		Member member = principal.getMember();
		if (member.getRole() != MemberRole.CUSTOMER_REPRESENTATIVE) {
			throw new AccessDeniedException("권한 없음");
		}

		Company company = member.getCompany();
		String newKey = UUID.randomUUID().toString().substring(0, 8); // 혹은 원하는 규칙
		company.setRegistrationKey(newKey);
		companyRepository.save(company);

		return Map.of("key", newKey);
	}

	
	@PostMapping("/changePassword")
	public String changePassword(@AuthenticationPrincipal PrincipalDetails principal,
	                             @RequestParam String newPassword) {
	    Member member = principal.getMember();
	    String encoded = passwordEncoder.encode(newPassword);
	    member.setPassword(encoded);
	    memberRepository.save(member);

	    return "redirect:/customer/myInfo";
	}
	
	@PreAuthorize("hasAuthority('ROLE_CUSTOMER_REPRESENTATIVE')")
    @GetMapping("/memberList")
    public String memberList(@AuthenticationPrincipal PrincipalDetails principal, Model model) {
        Member loginMember = principal.getMember();
        List<Member> employees = memberService.getCompanyEmployees(loginMember.getCompany());

        model.addAttribute("employees", employees);
        return "front/customer/member/memberList";
    }

    @PreAuthorize("hasAuthority('ROLE_CUSTOMER_REPRESENTATIVE')")
    @GetMapping("/memberManager/{id}")
    public String memberManager(@PathVariable Long id, Model model) {
        Member member = memberService.getMemberById(id);
        model.addAttribute("member", member);
        return "front/customer/member/memberManager";
    }
    
    @PostMapping("/toggleMemberEnabled")
    @ResponseBody
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER_REPRESENTATIVE')")
    public Map<String, String> toggleMemberEnabled(@RequestBody Map<String, Object> payload,
                                                   @AuthenticationPrincipal PrincipalDetails principal) {
    	Long memberId = Long.valueOf(payload.get("memberId").toString());
    	Boolean enabled = Boolean.valueOf(payload.get("enabled").toString());

    	Member member = memberService.getMemberById(memberId);

    	// 소속 검증
    	if (!member.getCompany().getId().equals(principal.getMember().getCompany().getId())) {
    		throw new AccessDeniedException("해당 직원은 당신 회사 소속이 아닙니다.");
    	}

    	member.setEnabled(enabled);
    	memberRepository.save(member);

    	return Map.of("message", enabled ? "접속이 허용되었습니다." : "접속이 차단되었습니다.");
    }

}
