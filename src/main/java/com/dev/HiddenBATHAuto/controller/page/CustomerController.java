package com.dev.HiddenBATHAuto.controller.page;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.as.AsTaskRepository;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.dev.HiddenBATHAuto.service.as.AsTaskService;
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
	
	@GetMapping("/taskList")
	public String taskList(@AuthenticationPrincipal PrincipalDetails principal,
	                       @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
	                       Model model) {
	    Long companyId = principal.getMember().getCompany().getId();
	    Page<Task> taskPage = taskRepository.findByCompanyId(companyId, pageable);
	    model.addAttribute("taskPage", taskPage);
	    return "front/customer/task/taskList";
	}

	@GetMapping("/taskDetail/{taskId}")
	public String taskDetail(@PathVariable Long taskId,
	                         @AuthenticationPrincipal PrincipalDetails principal,
	                         Model model) {
	    Task task = taskRepository.findById(taskId)
	        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

	    if (!principal.getMember().getCompany().getId()
	        .equals(task.getRequestedBy().getCompany().getId())) {
	        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
	    }

	    model.addAttribute("task", task);
	    return "front/customer/task/taskDetail";
	}
	
	@GetMapping("/orderDetail/{orderId}")
    public String orderDetail(@PathVariable Long orderId,
                              @AuthenticationPrincipal PrincipalDetails principal,
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
                Map<String, String> parsedMap = objectMapper.readValue(item.getOptionJson(), new TypeReference<>() {});
                item.setParsedOptionMap(parsedMap);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "옵션 데이터를 파싱할 수 없습니다.");
            }
        }

        model.addAttribute("order", order);
        return "front/customer/task/orderDetail";
    }
	
	@GetMapping("/asList")
	public String asList(@AuthenticationPrincipal PrincipalDetails principal,
	                     @PageableDefault(size = 10, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable,
	                     Model model) {
	    Long companyId = principal.getMember().getCompany().getId();
	    Page<AsTask> asTaskPage = asTaskRepository.findByCompanyId(companyId, pageable);
	    model.addAttribute("asTaskPage", asTaskPage);
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
	        @RequestParam("images") List<MultipartFile> images
	) {
	    Map<String, Object> result = new HashMap<>();
	    try {
	        AsTask saved = asTaskService.submitAsTask(task, images, principal.getMember());
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
	public String updateMyInfo(@AuthenticationPrincipal PrincipalDetails principal,
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
	        if (companyName != null) company.setCompanyName(companyName);
	        company.setRoadAddress(roadAddress);
	        company.setDetailAddress(detailAddress);
	        company.setDoName(doName);
	        company.setSiName(siName);
	        company.setGuName(guName);
	        company.setZipCode(zipCode);
	        company.setUpdatedAt(LocalDateTime.now());

	        String baseDir = uploadPath + "/license/" + company.getId(); // spring.upload.path 사용

	        // 새 파일 업로드 시 기존 삭제 후 저장
	        if (businessLicenseFile != null && !businessLicenseFile.isEmpty()) {
	            FileUtil.deleteIfExists(company.getBusinessLicensePath());

	            String originalName = businessLicenseFile.getOriginalFilename();
	            String savedName = UUID.randomUUID() + "_" + originalName;
	            String fullPath = baseDir + "/" + savedName;

	            FileUtil.createDirIfNotExists(baseDir);

	            try {
	                businessLicenseFile.transferTo(new File(fullPath));
	                company.setBusinessLicenseFilename(originalName);
	                company.setBusinessLicensePath(fullPath);
	                company.setBusinessLicenseUrl("/files/license/" + company.getId() + "/" + savedName);
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
	    }

	    memberRepository.save(member);
	    companyRepository.save(company);

	    return "redirect:/customer/myInfo";
	}
}
