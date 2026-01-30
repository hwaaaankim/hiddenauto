package com.dev.HiddenBATHAuto.service.production;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;

@Service
public class ProductionTeamCommandService {

	private final OrderRepository orderRepository;

	public ProductionTeamCommandService(OrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	@Transactional
	public int bulkComplete(Member loginMember, List<Long> orderIds) {

		if (loginMember == null) {
			throw new AccessDeniedException("접근 불가");
		}
		if (orderIds == null || orderIds.isEmpty()) {
			throw new IllegalArgumentException("orderIds가 비어있습니다.");
		}

		// ✅ 중복 제거(체크박스 중복은 흔치 않지만 방어)
		List<Long> uniqueIds = orderIds.stream()
				.filter(Objects::nonNull)
				.distinct()
				.toList();

		boolean isSubLeaderTeam = (loginMember.getTeamCategory() != null
				&& "하부장".equals(loginMember.getTeamCategory().getName()));
		Long myCategoryId = (loginMember.getTeamCategory() != null) ? loginMember.getTeamCategory().getId() : null;

		// ✅ 한번에 조회 (N+1 방지 + 검증/처리 단순화)
		List<Order> orders = orderRepository.findAllById(uniqueIds);

		// ✅ 존재하지 않는 주문ID 검증 (요청한 id 중 누락이 있으면 즉시 실패)
		Set<Long> foundIds = orders.stream().map(Order::getId).collect(Collectors.toSet());
		for (Long requestedId : uniqueIds) {
			if (!foundIds.contains(requestedId)) {
				throw new IllegalArgumentException("주문 없음: " + requestedId);
			}
		}

		// =========================
		// 1) 서버 검증 단계 (하나라도 위반이면 즉시 실패)
		// =========================

		// 1-1) 하부장팀 제한 검증
		if (isSubLeaderTeam) {
			if (myCategoryId == null) {
				throw new AccessDeniedException("하부장 권한 처리 불가(내 카테고리 정보 없음)");
			}

			for (Order order : orders) {
				if (order.getProductCategory() == null || order.getProductCategory().getId() == null) {
					throw new AccessDeniedException("하부장 권한 처리 불가(카테고리 정보 없음)");
				}
				if (!myCategoryId.equals(order.getProductCategory().getId())) {
					throw new AccessDeniedException("하부장팀은 하부장 생산만 완료처리할 수 있습니다.");
				}
			}
		}

		// 1-2) 상태 검증: 선택된 것 중 하나라도 CONFIRMED가 아니면 실패
		// ✅ 요구사항 메시지: "__번 오더는 완료처리할 수 없다"
		for (Order order : orders) {
			if (order.getStatus() != OrderStatus.CONFIRMED) {
				throw new IllegalStateException(order.getId() + "번 오더는 완료처리할 수 없습니다.");
			}
		}

		// =========================
		// 2) 처리 단계 (여기까지 왔으면 모두 처리 가능 상태)
		// =========================
		LocalDateTime now = LocalDateTime.now();

		for (Order order : orders) {
			order.setStatus(OrderStatus.PRODUCTION_DONE);
			order.setUpdatedAt(now);
			order.setAssignedProductionHandler(loginMember);
		}

		// ✅ 명시적 저장 (JPA 더티체킹으로도 되지만, 운영 안정성 위해 saveAll 권장)
		orderRepository.saveAll(orders);

		return orders.size();
	}
}