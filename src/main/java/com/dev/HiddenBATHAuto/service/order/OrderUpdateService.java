package com.dev.HiddenBATHAuto.service.order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;

@Service
@Transactional
public class OrderUpdateService {

	@Autowired
	private OrderRepository orderRepository;
	@Autowired
	private DeliveryMethodRepository deliveryMethodRepository;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private TeamCategoryRepository teamCategoryRepository;
	@Autowired
	private DeliveryOrderIndexService deliveryOrderIndexService;
	@Autowired
	private DeliveryOrderIndexRepository deliveryOrderIndexRepository;

	public void updateOrder(Long orderId, int productCost, LocalDate preferredDeliveryDate, String statusStr,
			Optional<Long> deliveryMethodId, Optional<Long> deliveryHandlerId, Optional<Long> productCategoryId) {

		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new IllegalArgumentException("Order not found"));

		OrderStatus status = OrderStatus.valueOf(statusStr);

		order.setProductCost(productCost);
		order.setPreferredDeliveryDate(preferredDeliveryDate.atStartOfDay());
		order.setStatus(status);

		deliveryMethodId.ifPresentOrElse(id -> {
			DeliveryMethod method = deliveryMethodRepository.findById(id)
					.orElseThrow(() -> new IllegalArgumentException("Invalid deliveryMethodId"));
			order.setDeliveryMethod(method);
		}, () -> order.setDeliveryMethod(null));

		deliveryHandlerId.ifPresentOrElse(id -> {
			Member member = memberRepository.findById(id)
					.orElseThrow(() -> new IllegalArgumentException("Invalid deliveryHandlerId"));
			order.setAssignedDeliveryHandler(member);
		}, () -> order.setAssignedDeliveryHandler(null));

		productCategoryId.ifPresentOrElse(id -> {
			TeamCategory category = teamCategoryRepository.findById(id)
					.orElseThrow(() -> new IllegalArgumentException("Invalid productCategoryId"));
			order.setProductCategory(category);
		}, () -> order.setProductCategory(null));

		order.setUpdatedAt(LocalDateTime.now());

		if (status == OrderStatus.CANCELED) {
			// 🔥 상태가 취소인 경우, 인덱스 삭제
			deliveryOrderIndexRepository.findByOrder(order).ifPresent(deliveryOrderIndexRepository::delete);
		} else {
			// ✅ 그 외 상태는 기존 로직 유지
			deliveryOrderIndexService.ensureIndex(order);
		}
	}

}
