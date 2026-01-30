package com.dev.HiddenBATHAuto.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.ProductionBulkCompleteRequest;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.production.ProductionTeamCommandService;

@RestController
@RequestMapping("/api/team/production")
public class ProductionTeamApiController {

	private final ProductionTeamCommandService productionTeamCommandService;

	public ProductionTeamApiController(ProductionTeamCommandService productionTeamCommandService) {
		this.productionTeamCommandService = productionTeamCommandService;
	}

	@PostMapping("/orders/complete")
	public ResponseEntity<?> completeProductionOrders(
			@AuthenticationPrincipal PrincipalDetails principal,
			@RequestBody ProductionBulkCompleteRequest req
	) {
		Member member = principal.getMember();

		if (member.getTeam() == null || !"생산팀".equals(member.getTeam().getName())) {
			throw new AccessDeniedException("접근 불가");
		}

		List<Long> orderIds = (req == null) ? null : req.getOrderIds();
		if (orderIds == null || orderIds.isEmpty()) {
			return ResponseEntity.badRequest().body("orderIds가 비어있습니다.");
		}

		try {
			int updated = productionTeamCommandService.bulkComplete(member, orderIds);

			return ResponseEntity.ok(Map.of(
					"updatedCount", updated
			));
		} catch (IllegalArgumentException | IllegalStateException e) {
			// ✅ 서버 검증 실패 메시지를 그대로 내려줌
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}
}