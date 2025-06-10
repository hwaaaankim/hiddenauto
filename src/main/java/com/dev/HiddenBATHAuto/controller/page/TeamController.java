package com.dev.HiddenBATHAuto.controller.page;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dev.HiddenBATHAuto.dto.DeliveryOrderIndexUpdateRequest;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.task.AsImage;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.as.AsImageRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.service.as.AsTaskService;
import com.dev.HiddenBATHAuto.service.order.DeliveryOrderIndexService;
import com.dev.HiddenBATHAuto.service.order.OrderService;
import com.dev.HiddenBATHAuto.service.team.TeamTaskService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/team")
@PreAuthorize("hasRole('INTERNAL_EMPLOYEE')")
@RequiredArgsConstructor
public class TeamController {

	private final TeamTaskService teamTaskService;
	private final TeamCategoryRepository teamCategoryRepository;
	private final OrderRepository orderRepository;
	private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
	private final DeliveryOrderIndexService deliveryOrderIndexService;
	private final AsTaskService asTaskService;
	private final AsImageRepository asImageRepository;
	private final OrderService orderService;
	
	@GetMapping("/productionList")
	public String getProductionOrders(
			@AuthenticationPrincipal PrincipalDetails principal,
			@RequestParam(required = false) 
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate preferredDate,
			@RequestParam(required = false) Long productCategoryId, 
			Pageable pageable, 
			Model model) {
	
		Member member = principal.getMember();

		if (!"생산팀".equals(member.getTeam().getName())) {
			throw new AccessDeniedException("접근 불가: 생산팀만 접근 가능합니다.");
		}

		Long targetCategoryId = (productCategoryId != null) ? productCategoryId : member.getTeamCategory().getId();
		if (preferredDate == null) {
			preferredDate = LocalDate.now().plusDays(1);
		}

		Page<Order> orderPage = teamTaskService.getProductionOrders(
				List.of(OrderStatus.CONFIRMED, OrderStatus.PRODUCTION_DONE, OrderStatus.DELIVERY_DONE),
				targetCategoryId, preferredDate, pageable);

		List<TeamCategory> productCategories = teamCategoryRepository.findByTeamName("생산팀");

		model.addAttribute("orders", orderPage.getContent());
		model.addAttribute("page", orderPage);
		model.addAttribute("productCategoryId", productCategoryId);
		model.addAttribute("preferredDate", preferredDate);
		model.addAttribute("productCategories", productCategories);

		return "administration/team/production/productionList";
	}

	@GetMapping("/productionDetail/{orderId}")
	public String getProductionDetail(@PathVariable Long orderId, @AuthenticationPrincipal PrincipalDetails principal,
			Model model) {
		// 주문 조회
		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("해당 주문을 찾을 수 없습니다."));

		// 옵션 파싱
		OrderItem orderItem = order.getOrderItem();
		if (orderItem != null && orderItem.getOptionJson() != null) {
			try {
				ObjectMapper mapper = new ObjectMapper();
				Map<String, String> parsedMap = mapper.readValue(orderItem.getOptionJson(), new TypeReference<>() {
				});
				orderItem.setParsedOptionMap(parsedMap);
			} catch (Exception e) {
				e.printStackTrace(); // 또는 로그 처리
			}
		}

		// 로그인한 멤버의 팀카테고리와 주문의 제품분류 일치 여부
		Member loginMember = principal.getMember();
		boolean canChangeStatus = false;

		if (loginMember.getTeamCategory() != null && order.getProductCategory() != null
				&& loginMember.getTeamCategory().getId().equals(order.getProductCategory().getId())
				&& order.getStatus() == OrderStatus.CONFIRMED) {
			canChangeStatus = true;
		}

		model.addAttribute("order", order);
		model.addAttribute("orderItem", orderItem);
		model.addAttribute("canChangeStatus", canChangeStatus);

		return "administration/team/production/productionDetail";
	}

	@PostMapping("/updateStatus/{orderId}")
	public String updateProductionStatus(@PathVariable Long orderId, @RequestParam("status") OrderStatus newStatus,
			@AuthenticationPrincipal PrincipalDetails principal, RedirectAttributes redirectAttributes) {
		// 1. 로그인한 멤버 확인
		Member loginMember = principal.getMember();

		// 2. 오더 조회
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new IllegalArgumentException("주문이 존재하지 않습니다."));

		// 3. 권한 체크: 로그인한 멤버의 팀 카테고리와 오더의 제품 카테고리가 같아야 함
		if (!loginMember.getTeamCategory().getId().equals(order.getProductCategory().getId())) {
			redirectAttributes.addFlashAttribute("errorMessage", "상태 변경 권한이 없습니다.");
			return "redirect:/team/productionDetail/" + orderId;
		}

		// 4. 상태 변경 허용 조건 확인
		if (order.getStatus() != OrderStatus.CONFIRMED || newStatus != OrderStatus.PRODUCTION_DONE) {
			redirectAttributes.addFlashAttribute("errorMessage", "변경 가능한 상태가 아닙니다.");
			return "redirect:/team/productionDetail/" + orderId;
		}

		// 5. 상태 변경
		order.setStatus(OrderStatus.PRODUCTION_DONE);
		order.setUpdatedAt(LocalDateTime.now());
		orderRepository.save(order);

		redirectAttributes.addFlashAttribute("successMessage", "상태가 성공적으로 변경되었습니다.");
		return "redirect:/team/productionDetail/" + orderId;
	}

	@GetMapping("/deliveryList")
	public String getDeliveryOrders(
	        @AuthenticationPrincipal PrincipalDetails principal,
	        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate preferredDate,
	        Pageable pageable,
	        Model model) {

	    Member member = principal.getMember();

	    // 1. 접근 제한: 배송팀만 접근 가능
	    if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
	        throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
	    }

	    // 2. 기본 조회일은 내일
	    if (preferredDate == null) {
	        preferredDate = LocalDate.now().plusDays(1);
	    }

	    // 3. 조회 대상 상태
	    List<OrderStatus> statuses = List.of(OrderStatus.PRODUCTION_DONE, OrderStatus.DELIVERY_DONE);

	    // 4. 인덱스 기준 정렬된 배송리스트 조회
	    Page<DeliveryOrderIndex> page = deliveryOrderIndexRepository
	            .findByHandlerAndDateAndStatusIn(member.getId(), preferredDate, statuses, pageable);

	    // 5. 모델에 데이터 전달
	    model.addAttribute("deliveryHandlerId", member.getId());
	    model.addAttribute("orders", page.getContent()); // 실제 리스트
	    model.addAttribute("page", page); // 페이지네이션을 위한 Page 객체
	    model.addAttribute("preferredDate", preferredDate); // 날짜 필터 값

	    return "administration/team/delivery/deliveryList";
	}

	@GetMapping("/deliveryDetail/{id}")
	public String getDeliveryDetailPage(
			@PathVariable Long id, 
			Model model) {
		// 주문 조회
		Order order = orderRepository.findById(id).orElseThrow(() -> new RuntimeException("해당 주문을 찾을 수 없습니다."));

		// 옵션 파싱
		OrderItem orderItem = order.getOrderItem();
		if (orderItem != null && orderItem.getOptionJson() != null) {
			try {
				ObjectMapper mapper = new ObjectMapper();
				Map<String, String> parsedMap = mapper.readValue(orderItem.getOptionJson(), new TypeReference<>() {
				});
				orderItem.setParsedOptionMap(parsedMap);
			} catch (Exception e) {
				e.printStackTrace(); // 또는 로그 처리
			}
		}

		model.addAttribute("order", order);
		model.addAttribute("orderItem", orderItem);

	    return "administration/team/delivery/deliveryDetail"; // 뷰 이름
	}
	
	@PostMapping("/updateOrderIndex")
	@ResponseBody
	public ResponseEntity<?> updateOrderIndex(@RequestBody DeliveryOrderIndexUpdateRequest request) {
	    deliveryOrderIndexService.updateIndexes(request);
	    return ResponseEntity.ok().build();
	}
	
	@PostMapping("/deliveryStatus/{orderId}")
	public String updateDeliveryStatusAndUploadImages(
	        @PathVariable Long orderId,
	        @RequestParam(value = "status", required = false) String status,
	        @RequestParam(value = "files", required = false) List<MultipartFile> files,
	        RedirectAttributes redirectAttributes) {

	    try {
	        orderService.updateDeliveryStatusAndImages(orderId, status, files);
	        redirectAttributes.addFlashAttribute("successMessage", "배송 상태가 변경되었습니다.");
	    } catch (Exception e) {
	        e.printStackTrace();
	        redirectAttributes.addFlashAttribute("errorMessage", "배송 상태 변경 실패: " + e.getMessage());
	    }

	    return "redirect:/team/deliveryDetail/" + orderId;
	}
	
	@GetMapping("/asList")
	public String getAsList(
	        @AuthenticationPrincipal PrincipalDetails principal,
	        @RequestParam(required = false, defaultValue = "processed") String dateType,
	        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
	        @RequestParam(required = false) AsStatus status,
	        Pageable pageable,
	        Model model
	) {
	    Member member = principal.getMember();

	    if (member.getTeam() == null || !"AS팀".equals(member.getTeam().getName())) {
	        throw new AccessDeniedException("AS팀만 접근할 수 있습니다.");
	    }

	    Page<AsTask> asPage = asTaskService.getAsTasks(member, dateType, date, status, pageable);

	    model.addAttribute("asPage", asPage);
	    model.addAttribute("preferredDate", date != null ? date : LocalDate.now());
	    model.addAttribute("dateType", dateType);
	    model.addAttribute("selectedStatus", status);

	    return "administration/team/as/asList";
	}
	
	@GetMapping("/asDetail/{id}")
	public String asDetail(
			@PathVariable Long id, 
			Model model,
			@AuthenticationPrincipal PrincipalDetails principal) {
		AsTask asTask = asTaskService.getAsDetail(id);

		model.addAttribute("asTask", asTask);
		model.addAttribute("asStatuses", AsStatus.values());

		return "administration/team/as/asDetail";
	}
	@PostMapping("/asUpdate/{id}")
	public String updateAsTaskFromTeam(
	        @PathVariable Long id,
	        @RequestParam(value = "status", required = false) AsStatus status,
	        @RequestParam(value = "resultImages", required = false) List<MultipartFile> resultImages,
	        RedirectAttributes redirectAttributes) {

	    try {
	        asTaskService.updateAsTaskByHandler(id, status, resultImages);
	        redirectAttributes.addFlashAttribute("success", "AS 상태 및 결과 이미지가 저장되었습니다.");
	    } catch (Exception e) {
	        e.printStackTrace();
	        redirectAttributes.addFlashAttribute("error", "저장 중 오류가 발생했습니다.");
	    }

	    return "redirect:/team/asDetail/" + id;
	}
	@DeleteMapping("/asImageDelete/{id}")
	@ResponseBody
	public ResponseEntity<Void> deleteAsImage(@PathVariable Long id) {
	    Optional<AsImage> imageOpt = asImageRepository.findById(id);
	    if (imageOpt.isEmpty()) {
	        return ResponseEntity.notFound().build();
	    }

	    AsImage image = imageOpt.get();

	    // 파일 삭제
	    if (image.getPath() != null) {
	        try {
	            Files.deleteIfExists(Paths.get(image.getPath()));
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	    }

	    asImageRepository.delete(image);
	    return ResponseEntity.ok().build();
	}

}





















