package com.dev.HiddenBATHAuto.service.production;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.service.order.OrderOperationalChangeRecorder;
import com.dev.HiddenBATHAuto.service.order.OrderTeamAccessPolicyService;

@Service
public class ProductionTeamCommandService {

    private final OrderRepository orderRepository;
    private final OrderOperationalChangeRecorder changeRecorder;
    private final OrderTeamAccessPolicyService accessPolicyService;

    public ProductionTeamCommandService(
            OrderRepository orderRepository,
            OrderOperationalChangeRecorder changeRecorder,
            OrderTeamAccessPolicyService accessPolicyService
    ) {
        this.orderRepository = orderRepository;
        this.changeRecorder = changeRecorder;
        this.accessPolicyService = accessPolicyService;
    }

    @Transactional
    public int bulkComplete(Member loginMember, List<Long> orderIds) {
        if (loginMember == null) {
            throw new AccessDeniedException("접근 불가");
        }
        if (orderIds == null || orderIds.isEmpty()) {
            throw new IllegalArgumentException("orderIds가 비어있습니다.");
        }

        List<Long> uniqueIds = orderIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (uniqueIds.isEmpty()) {
            throw new IllegalArgumentException("유효한 orderIds가 없습니다.");
        }

        List<Order> orders = orderRepository.findAllById(uniqueIds);
        Set<Long> foundIds = orders.stream().map(Order::getId).collect(Collectors.toSet());
        for (Long requestedId : uniqueIds) {
            if (!foundIds.contains(requestedId)) {
                throw new IllegalArgumentException("주문 없음: " + requestedId);
            }
        }

        /*
         * 화면에서 버튼이 숨겨져 있더라도 API를 직접 호출할 수 있으므로 모든 오더를 서버에서 재검증합니다.
         * 생산팀은 다른 카테고리를 조회·확인할 수 있지만 완료 처리는 본인 TeamCategory만 가능합니다.
         * 재단/재단(거울) 구성원은 조회·확인만 가능하고 완료 처리는 불가합니다.
         */
        for (Order order : orders) {
            accessPolicyService.assertCanOperateProduction(loginMember, order);
            if (order.getStatus() != OrderStatus.CONFIRMED) {
                throw new IllegalStateException(order.getId() + "번 오더는 완료처리할 수 없습니다.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (Order order : orders) {
            order.setStatus(OrderStatus.PRODUCTION_DONE);
            order.setUpdatedAt(now);
            order.setAssignedProductionHandler(loginMember);
        }

        orderRepository.saveAll(orders);
        orderRepository.flush();

        for (Order order : orders) {
            changeRecorder.recordStatusChange(
                    order,
                    OrderChangeSourceArea.PRODUCTION,
                    loginMember,
                    OrderStatus.CONFIRMED,
                    OrderStatus.PRODUCTION_DONE,
                    "PRODUCTION_BULK_COMPLETE",
                    "생산완료 일괄 처리",
                    "/api/team/production/orders/complete",
                    OrderWorkArea.DISPATCH,
                    OrderWorkArea.DELIVERY
            );
        }

        return orders.size();
    }
}
