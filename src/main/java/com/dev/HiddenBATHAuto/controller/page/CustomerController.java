package com.dev.HiddenBATHAuto.controller.page;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.CompanyDeliveryAddress;
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

import lombok.Data;
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
        if (company == null) {
            throw new IllegalStateException("회사 정보가 없습니다.");
        }

        // ✅ 배송지 목록 DTO 변환(엔티티 직접 노출 방지)
        List<CompanyDeliveryAddressDto> deliveryAddresses = company.getDeliveryAddresses() == null
                ? new ArrayList<>()
                : company.getDeliveryAddresses().stream()
                    .sorted(Comparator.comparing(CompanyDeliveryAddress::getId, Comparator.nullsLast(Long::compareTo)))
                    .map(CompanyDeliveryAddressDto::from)
                    .collect(Collectors.toList());

        model.addAttribute("member", freshMember);
        model.addAttribute("company", company);
        model.addAttribute("deliveryAddresses", deliveryAddresses);

        model.addAttribute("isRepresentative",
                freshMember.getRole() == MemberRole.CUSTOMER_REPRESENTATIVE);

        return "front/customer/info/myInfo";
    }

	// =========================
    // ✅ 중복체크 API (휴대폰)
    // =========================
    @GetMapping("/api/dup-check/phone")
    public ResponseEntity<?> checkPhoneDup(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam(required = false) String phone
    ) {
        Member me = memberRepository.findById(principal.getMember().getId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String normalized = normalizePhone(phone);
        Map<String, Object> body = new HashMap<>();

        if (normalized.isEmpty() || normalized.length() < 12) { // 010-1234-5678 = 13, 010-123-4567 = 12
            body.put("ok", false);
            body.put("duplicate", false);
            body.put("message", "휴대폰 번호 형식이 올바르지 않습니다.");
            return ResponseEntity.ok(body);
        }

        // 본인 값이면 중복 아님으로 처리
        String myPhone = normalizePhone(me.getPhone());
        if (!myPhone.isEmpty() && myPhone.equals(normalized)) {
            body.put("ok", true);
            body.put("duplicate", false);
            body.put("message", "중복 아님");
            return ResponseEntity.ok(body);
        }

        boolean dup = memberRepository.existsByPhoneAndIdNot(normalized, me.getId());
        body.put("ok", true);
        body.put("duplicate", dup);
        body.put("message", dup ? "중복임" : "중복 아님");
        return ResponseEntity.ok(body);
    }

    // =========================
    // ✅ 중복체크 API (사업자등록번호)
    // =========================
    @GetMapping("/api/dup-check/business-number")
    public ResponseEntity<?> checkBusinessNumberDup(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam(required = false) String businessNumber
    ) {
        Member me = memberRepository.findById(principal.getMember().getId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Company company = me.getCompany();
        if (company == null) {
            throw new IllegalStateException("회사 정보가 없습니다.");
        }

        String digits = onlyDigits(businessNumber); // DB에는 숫자 10자리만 저장 전제로
        Map<String, Object> body = new HashMap<>();

        if (digits.length() != 10) {
            body.put("ok", false);
            body.put("duplicate", false);
            body.put("message", "사업자등록번호는 숫자 10자리여야 합니다.");
            return ResponseEntity.ok(body);
        }

        // 본인 회사 값이면 중복 아님
        String my = onlyDigits(company.getBusinessNumber());
        if (!my.isEmpty() && my.equals(digits)) {
            body.put("ok", true);
            body.put("duplicate", false);
            body.put("message", "중복 아님");
            return ResponseEntity.ok(body);
        }

        boolean dup = companyRepository.existsByBusinessNumberAndIdNot(digits, company.getId());
        body.put("ok", true);
        body.put("duplicate", dup);
        body.put("message", dup ? "중복임" : "중복 아님");
        return ResponseEntity.ok(body);
    }

 // =========================
    // ✅ 헬퍼: 숫자만
    // =========================
    private String onlyDigits(String v) {
        if (v == null) return "";
        return v.replaceAll("[^0-9]", "");
    }

    // =========================
    // ✅ 헬퍼: 휴대폰 정규화 (010-1234-5678 / 010-123-4567)
    // - 숫자만 받은 뒤 일반 포맷으로 변환
    // =========================
    private String normalizePhone(String phone) {
        String digits = onlyDigits(phone);
        if (digits.isEmpty()) return "";

        // 02 지역번호 케이스까지 완벽히 하려면 규칙이 더 필요하지만,
        // 현재 요구는 휴대폰 중심이므로 010/011/016/017/018/019 등을 우선 지원합니다.
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
        }
        if (digits.length() == 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
        }
        return ""; // 형식 불가
    }
    
    // =========================
    // ✅ /myInfoUpdate (POST) - 변경(전체 교체 권장)
    // =========================
    @PostMapping("/myInfoUpdate")
    public String updateMyInfo(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam String name,
            @RequestParam String phone,
            @RequestParam String email,

            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String businessNumber, // ✅ 수정 가능하도록 받기
            @RequestParam String roadAddress,
            @RequestParam String detailAddress,
            @RequestParam String doName,
            @RequestParam String siName,
            @RequestParam String guName,
            @RequestParam String zipCode,

            @RequestParam(required = false) String companyDeliveryAddressesJson,

            @RequestParam(defaultValue = "false") boolean removeBusinessLicense,
            @RequestParam(required = false) MultipartFile businessLicenseFile,

            RedirectAttributes ra
    ) {
        Long memberId = principal.getMember().getId();

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Company company = member.getCompany();
        if (company == null) {
            throw new IllegalStateException("회사 정보가 없습니다.");
        }

        // ===== 0) 입력값 정규화 =====
        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone.isEmpty()) {
            ra.addFlashAttribute("myInfoError", "휴대폰 번호 형식이 올바르지 않습니다.");
            return "redirect:/customer/myInfo";
        }
        String normalizedBizDigits = (businessNumber == null) ? "" : onlyDigits(businessNumber);

        // ===== 1) 멤버 정보 =====
        member.setName(name);
        member.setPhone(normalizedPhone);
        member.setEmail(email);
        member.setUpdatedAt(LocalDateTime.now());

        // ✅ 휴대폰 중복 최종 방어(동시성 대비)
        if (memberRepository.existsByPhoneAndIdNot(normalizedPhone, member.getId())) {
            ra.addFlashAttribute("myInfoError", "휴대폰 번호가 이미 사용 중입니다.");
            return "redirect:/customer/myInfo";
        }

        // ===== 2) 추가 배송지: 대표/직원 공통 회사 반영 =====
        applyCompanyDeliveryAddresses(company, companyDeliveryAddressesJson);

        boolean isRepresentative = (member.getRole() == MemberRole.CUSTOMER_REPRESENTATIVE);

        // ===== 3) 회사 기본 정보: 대표만 수정 가능 =====
        if (isRepresentative) {

            if (companyName != null) {
                company.setCompanyName(companyName);
            }

            // ✅ 사업자등록번호 수정 가능
            if (normalizedBizDigits.length() != 10) {
                ra.addFlashAttribute("myInfoError", "사업자등록번호는 숫자 10자리여야 합니다.");
                return "redirect:/customer/myInfo";
            }

            // 중복 최종 방어
            if (companyRepository.existsByBusinessNumberAndIdNot(normalizedBizDigits, company.getId())) {
                ra.addFlashAttribute("myInfoError", "사업자등록번호가 이미 등록되어 있습니다.");
                return "redirect:/customer/myInfo";
            }

            company.setBusinessNumber(normalizedBizDigits);

            company.setRoadAddress(roadAddress);
            company.setDetailAddress(detailAddress);
            company.setDoName(doName);
            company.setSiName(siName);
            company.setGuName(guName);
            company.setZipCode(zipCode);
            company.setUpdatedAt(LocalDateTime.now());

            // ===== 4) 사업자등록증 파일 =====
            // 케이스 A) 새 파일 업로드: 기존 파일 삭제 후 교체
            if (businessLicenseFile != null && !businessLicenseFile.isEmpty()) {
                FileUtil.deleteIfExists(company.getBusinessLicensePath());

                String originalName = businessLicenseFile.getOriginalFilename();
                String username = member.getUsername();

                String relativePath = username + "/signUp/licence";
                String saveDir = java.nio.file.Paths.get(uploadPath, relativePath).toString();
                FileUtil.createDirIfNotExists(saveDir);

                java.nio.file.Path filePath = java.nio.file.Paths.get(saveDir, originalName);
                try {
                    businessLicenseFile.transferTo(filePath.toFile());

                    company.setBusinessLicenseFilename(originalName);
                    company.setBusinessLicensePath(filePath.toString());
                    company.setBusinessLicenseUrl("/upload/" + relativePath + "/" + originalName);
                } catch (java.io.IOException e) {
                    throw new RuntimeException("파일 저장 실패", e);
                }
            }

            // 케이스 B) 삭제 요청(removeBusinessLicense=true)인데 새 파일이 없으면 => 업데이트 불가(필수)
            if (removeBusinessLicense) {
                boolean hasNewUpload = (businessLicenseFile != null && !businessLicenseFile.isEmpty());
                if (!hasNewUpload) {
                    ra.addFlashAttribute("myInfoError", "사업자등록증은 필수입니다. 삭제 후에는 새 파일을 업로드해 주세요.");
                    return "redirect:/customer/myInfo";
                }
                // hasNewUpload=true 인 경우는 위에서 교체 저장되므로 별도 삭제 처리 불필요(이미 교체됨)
            }

            companyRepository.save(company);

        } else {
            // 대표 아니어도 배송지 변경은 회사에 반영 -> 저장 필요
            companyRepository.save(company);
        }

        // ===== 5) 사업자등록증 최종 필수 방어(대표/직원 공통) =====
        // 혹시라도 회사에 등록증이 없는 상태라면 업데이트 불가
        // (정상 운영이라면 거의 발생하지 않지만, 데이터 꼬임 방지용)
        Company savedCompany = companyRepository.findById(company.getId())
                .orElseThrow(() -> new IllegalStateException("회사 저장 실패"));
        if (savedCompany.getBusinessLicensePath() == null || savedCompany.getBusinessLicensePath().trim().isEmpty()) {
            ra.addFlashAttribute("myInfoError", "사업자등록증은 필수입니다. 사업자등록증을 등록해 주세요.");
            return "redirect:/customer/myInfo";
        }

        try {
            memberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            // unique 제약 등 최종 방어
            ra.addFlashAttribute("myInfoError", "저장 중 오류가 발생했습니다. 중복 여부를 확인해 주세요.");
            return "redirect:/customer/myInfo";
        }

        ra.addFlashAttribute("myInfoSuccess", "변경사항이 저장되었습니다.");
        return "redirect:/customer/myInfo";
    }

    /**
     * ✅ 회사 배송지 목록을 JSON 기반으로 "추가/삭제/수정" 동기화합니다.
     * - 기존 id가 있으면 업데이트
     * - id가 없으면 신규 추가
     * - 요청에 없는 기존 항목은 제거(orphanRemoval로 DB 삭제)
     */
    private void applyCompanyDeliveryAddresses(Company company, String json) {
        List<CompanyDeliveryAddressInput> incoming = parseDeliveryJson(json);

        if (company.getDeliveryAddresses() == null) {
            company.setDeliveryAddresses(new ArrayList<>());
        }

        // 기존 주소들을 id 기준 맵으로
        Map<Long, CompanyDeliveryAddress> existingById = company.getDeliveryAddresses().stream()
                .filter(x -> x.getId() != null)
                .collect(Collectors.toMap(CompanyDeliveryAddress::getId, Function.identity(), (a, b) -> a));

        Set<Long> incomingIds = incoming.stream()
                .map(CompanyDeliveryAddressInput::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 1) 제거: incoming에 없는 기존 id는 삭제
        company.getDeliveryAddresses().removeIf(addr ->
                addr.getId() != null && !incomingIds.contains(addr.getId())
        );

        // 2) 업데이트/추가
        for (CompanyDeliveryAddressInput in : incoming) {
            if (in.getRoadAddress() == null || in.getRoadAddress().trim().isEmpty()) {
                continue;
            }

            if (in.getId() != null && existingById.containsKey(in.getId())) {
                CompanyDeliveryAddress target = existingById.get(in.getId());
                target.setZipCode(nvl(in.getZipCode()));
                target.setDoName(nvl(in.getDoName()));
                target.setSiName(nvl(in.getSiName()));
                target.setGuName(nvl(in.getGuName()));
                target.setRoadAddress(nvl(in.getRoadAddress()));
                target.setDetailAddress(nvl(in.getDetailAddress()));
                target.setUpdatedAt(LocalDateTime.now());
            } else {
                CompanyDeliveryAddress created = new CompanyDeliveryAddress();
                created.setCompany(company);
                created.setZipCode(nvl(in.getZipCode()));
                created.setDoName(nvl(in.getDoName()));
                created.setSiName(nvl(in.getSiName()));
                created.setGuName(nvl(in.getGuName()));
                created.setRoadAddress(nvl(in.getRoadAddress()));
                created.setDetailAddress(nvl(in.getDetailAddress()));
                created.setCreatedAt(LocalDateTime.now());
                created.setUpdatedAt(null);

                company.getDeliveryAddresses().add(created);
            }
        }

        // 회사 수정일
        company.setUpdatedAt(LocalDateTime.now());
    }

    private List<CompanyDeliveryAddressInput> parseDeliveryJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CompanyDeliveryAddressInput>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("추가 배송지 JSON 파싱 실패", e);
        }
    }

    private String nvl(String v) {
        return v == null ? "" : v.trim();
    }

    // =========================
    // DTO들
    // =========================
    @Data
    public static class CompanyDeliveryAddressDto {
        private Long id;
        private String zipCode;
        private String doName;
        private String siName;
        private String guName;
        private String roadAddress;
        private String detailAddress;

        public static CompanyDeliveryAddressDto from(CompanyDeliveryAddress e) {
            CompanyDeliveryAddressDto d = new CompanyDeliveryAddressDto();
            d.setId(e.getId());
            d.setZipCode(e.getZipCode());
            d.setDoName(e.getDoName());
            d.setSiName(e.getSiName());
            d.setGuName(e.getGuName());
            d.setRoadAddress(e.getRoadAddress());
            d.setDetailAddress(e.getDetailAddress());
            return d;
        }
    }

    @Data
    public static class CompanyDeliveryAddressInput {
        private Long id; // 기존 항목은 id 포함, 신규는 null
        private String zipCode;
        private String doName;
        private String siName;
        private String guName;
        private String roadAddress;
        private String detailAddress;
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
