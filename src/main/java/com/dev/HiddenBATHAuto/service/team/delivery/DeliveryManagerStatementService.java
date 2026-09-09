package com.dev.HiddenBATHAuto.service.team.delivery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.delivery.DeliveryStatementLayoutDtos.LayoutResponse;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;
import com.dev.HiddenBATHAuto.service.order.DeliveryMethodAssignmentPolicy;

import lombok.RequiredArgsConstructor;

/**
 * 배송관리(/team/deliveryManager) 체크 선택용 현장명세서 서비스입니다.
 *
 * 보안/기능 원칙:
 * - 클라이언트가 보낸 Order ID를 그대로 신뢰하지 않습니다.
 * - 현재 로그인 배송담당자의 DeliveryOrderIndex에 속한 주문만 다시 조회합니다.
 * - 배송팀의 기존 현장명세서 기준과 동일하게 현장배송/화물만 포함합니다.
 * - 비대상 주문은 전체 작업을 실패시키지 않고 제외 사유를 반환합니다.
 * - 실제 출력/다운로드에서도 다시 같은 검증을 수행합니다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryManagerStatementService {

    private static final String DELIVERY_TEAM_NAME = "배송팀";

    private static final Set<OrderStatus> VISIBLE_STATUSES = Set.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PRODUCTION_DONE,
            OrderStatus.DISPATCH_DONE,
            OrderStatus.DELIVERY_DONE
    );

    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
    private final DeliveryStatementLayoutService deliveryStatementLayoutService;

    @Transactional(readOnly = true)
    public SelectionPreview preview(Member loginMember, List<Long> orderIds) {
        validateDeliveryTeamMember(loginMember);

        List<Long> requestedOrderIds = normalizeOrderIds(orderIds);
        if (requestedOrderIds.isEmpty()) {
            throw new IllegalArgumentException("현장명세서로 출력할 배송건을 하나 이상 선택해 주세요.");
        }

        List<DeliveryOrderIndex> ownedRows = deliveryOrderIndexRepository
                .findAllByHandlerAndOrderIdsForDeliveryExcel(
                        loginMember.getId(),
                        requestedOrderIds
                );

        Map<Long, DeliveryOrderIndex> ownedRowByOrderId = new LinkedHashMap<>();
        for (DeliveryOrderIndex row : ownedRows) {
            if (row == null || row.getOrder() == null || row.getOrder().getId() == null) {
                continue;
            }
            ownedRowByOrderId.putIfAbsent(row.getOrder().getId(), row);
        }

        List<Long> eligibleOrderIds = new ArrayList<>();
        List<ExcludedOrder> excludedOrders = new ArrayList<>();

        for (Long orderId : requestedOrderIds) {
            DeliveryOrderIndex row = ownedRowByOrderId.get(orderId);

            if (row == null) {
                excludedOrders.add(new ExcludedOrder(
                        orderId,
                        "현재 로그인 배송담당자의 배송관리 대상이 아니거나 배송순서 정보가 없습니다."
                ));
                continue;
            }

            Order order = row.getOrder();
            String exclusionReason = resolveExclusionReason(order, row);

            if (exclusionReason != null) {
                excludedOrders.add(new ExcludedOrder(orderId, exclusionReason));
                continue;
            }

            eligibleOrderIds.add(orderId);
        }

        return new SelectionPreview(
                requestedOrderIds.size(),
                eligibleOrderIds.size(),
                excludedOrders.size(),
                List.copyOf(eligibleOrderIds),
                List.copyOf(excludedOrders)
        );
    }

    @Transactional(readOnly = true)
    public LayoutResponse buildLayoutResponse(
            Member loginMember,
            List<Long> orderIds,
            String layoutType
    ) {
        SelectionPreview preview = preview(loginMember, orderIds);
        requireEligibleOrders(preview);

        return deliveryStatementLayoutService.buildLayoutResponseForDeliveryManagerSelection(
                preview.eligibleOrderIds(),
                layoutType,
                loginMember
        );
    }

    @Transactional(readOnly = true)
    public byte[] buildLayoutExcel(
            Member loginMember,
            List<Long> orderIds,
            String layoutType
    ) {
        SelectionPreview preview = preview(loginMember, orderIds);
        requireEligibleOrders(preview);

        return deliveryStatementLayoutService.buildLayoutExcelForDeliveryManagerSelection(
                preview.eligibleOrderIds(),
                layoutType,
                loginMember
        );
    }

    private String resolveExclusionReason(Order order, DeliveryOrderIndex row) {
        if (order == null) {
            return "주문 정보를 확인할 수 없습니다.";
        }

        if (order.getStatus() == null || !VISIBLE_STATUSES.contains(order.getStatus())) {
            return "현재 배송관리에서 현장명세서를 출력할 수 없는 주문 상태입니다.";
        }

        if (row == null || row.getDeliveryDate() == null) {
            return "배송순서의 배송일 정보가 없습니다.";
        }

        if (order.getPreferredDeliveryDate() == null) {
            return "주문의 배송일 정보가 없습니다.";
        }

        if (order.getDeliveryMethod() == null) {
            return "배송수단이 지정되어 있지 않습니다.";
        }

        String methodName = safeText(order.getDeliveryMethod().getMethodName());
        if (!isSiteStatementDeliveryMethod(methodName)) {
            String displayMethodName = methodName.isBlank() ? "미지정" : methodName;
            return "배송수단이 '" + displayMethodName
                    + "'이므로 배송팀 현장명세서 대상(현장배송/화물)이 아닙니다.";
        }

        return null;
    }

    private boolean isSiteStatementDeliveryMethod(String methodName) {
        return DeliveryMethodAssignmentPolicy.containsKeyword(methodName, "현장배송")
                || DeliveryMethodAssignmentPolicy.containsKeyword(methodName, "화물");
    }

    private void requireEligibleOrders(SelectionPreview preview) {
        if (preview == null || preview.eligibleOrderIds().isEmpty()) {
            throw new IllegalArgumentException(
                    "선택한 배송건 중 현장명세서 출력 대상(현장배송/화물)이 없습니다."
            );
        }
    }

    private void validateDeliveryTeamMember(Member member) {
        if (member == null || member.getId() == null) {
            throw new AccessDeniedException("로그인 배송담당자 정보를 확인할 수 없습니다.");
        }

        if (!member.isEnabled()) {
            throw new AccessDeniedException("비활성화된 계정은 배송명세서를 출력할 수 없습니다.");
        }

        if (member.getTeam() == null
                || !DELIVERY_TEAM_NAME.equals(member.getTeam().getName())) {
            throw new AccessDeniedException("배송팀만 배송관리 현장명세서를 출력할 수 있습니다.");
        }
    }

    private List<Long> normalizeOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long orderId : orderIds) {
            if (orderId != null && orderId > 0) {
                result.add(orderId);
            }
        }
        return List.copyOf(result);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    public record SelectionPreview(
            int requestedCount,
            int eligibleCount,
            int excludedCount,
            List<Long> eligibleOrderIds,
            List<ExcludedOrder> excludedOrders
    ) {
        public SelectionPreview {
            eligibleOrderIds = eligibleOrderIds == null
                    ? List.of()
                    : List.copyOf(eligibleOrderIds.stream().filter(Objects::nonNull).toList());
            excludedOrders = excludedOrders == null
                    ? List.of()
                    : List.copyOf(excludedOrders);
        }
    }

    public record ExcludedOrder(
            Long orderId,
            String reason
    ) {
    }
}
