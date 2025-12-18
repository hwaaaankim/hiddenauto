package com.dev.HiddenBATHAuto.service.production;

import java.time.LocalDateTime;
import java.util.List;

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

		boolean isSubLeaderTeam = (loginMember.getTeamCategory() != null && "하부장".equals(loginMember.getTeamCategory().getName()));
		Long myCategoryId = (loginMember.getTeamCategory() != null) ? loginMember.getTeamCategory().getId() : null;

		int updatedCount = 0;

		for (Long orderId : orderIds) {
			Order order = orderRepository.findById(orderId)
					.orElseThrow(() -> new IllegalArgumentException("주문 없음: " + orderId));

			// 하부장팀 제한: 내 팀카테고리 주문만 처리 가능
			if (isSubLeaderTeam) {
				if (myCategoryId == null || order.getProductCategory() == null || order.getProductCategory().getId() == null) {
					throw new AccessDeniedException("하부장 권한 처리 불가(카테고리 정보 없음)");
				}
				if (!myCategoryId.equals(order.getProductCategory().getId())) {
					throw new AccessDeniedException("하부장팀은 하부장 생산만 완료처리할 수 있습니다.");
				}
			}

			// 상태 검증: 생산중(=CONFIRMED)만 완료처리 대상
			if (order.getStatus() != OrderStatus.CONFIRMED) {
				continue; // 이미 완료/기타면 스킵(원하시면 예외로 바꿀 수 있습니다)
			}

			order.setStatus(OrderStatus.PRODUCTION_DONE);
			order.setUpdatedAt(LocalDateTime.now());
			order.setAssignedProductionHandler(loginMember);

			updatedCount++;
		}

		return updatedCount;
	}
}