package com.dev.HiddenBATHAuto.service.ordernotification;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.orderchange.OrderFieldChangeCommand;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderAdminRequestDto;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderAdminRequestResponse;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationAudience;
import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeEvent;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.service.order.OrderChangeAuditService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderAdminRequestService {

    private final OrderRepository orderRepository;
    private final OrderChangeAuditService orderChangeAuditService;

    @Transactional
    public OrderAdminRequestResponse request(
            Long orderId,
            Member actor,
            OrderAdminRequestDto request
    ) {
        if (actor == null || actor.getId() == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("발주 ID가 없습니다.");
        }

        OrderChangeSourceArea sourceArea = resolveSourceArea(actor);
        Order order = orderRepository.findByIdForOrderNotification(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 발주를 찾을 수 없습니다. orderId=" + orderId));

        Member managedBy = order.getTask() != null ? order.getTask().getManagedBy() : null;
        if (managedBy == null || managedBy.getId() == null || !managedBy.isEnabled()) {
            throw new IllegalStateException(
                    "해당 발주의 우리회사 관리 담당자(Task.managedBy)가 지정되어 있지 않습니다. 관리자를 먼저 배정해 주세요."
            );
        }

        String reason = resolveReason(sourceArea, request);
        String actorName = actor.getName() != null && !actor.getName().isBlank()
                ? actor.getName().trim()
                : actor.getUsername();
        String notificationMessage = actorName + "님이 발주 #" + orderId
                + "에 대해 긴급 관리자 확인을 요청했습니다. 사유: " + reason;

        OrderChangeEvent event = orderChangeAuditService.recordOrderChange(
                order,
                sourceArea,
                actor.getId(),
                actor.getUsername(),
                actorName,
                "ADMIN_REQUEST_" + sourceArea.name(),
                "긴급 관리자요청",
                "/api/internal/orders/" + orderId + "/admin-request",
                List.of(OrderFieldChangeCommand.of(
                        "adminRequest",
                        "관리자요청",
                        null,
                        reason
                )),
                OrderNotificationAudience.MANAGED_BY_ONLY,
                "긴급 관리자요청 · 발주 #" + orderId,
                notificationMessage
        );

        return OrderAdminRequestResponse.builder()
                .success(true)
                .orderId(orderId)
                .taskId(order.getTask() != null ? order.getTask().getId() : null)
                .eventId(event != null ? event.getId() : null)
                .managedById(managedBy.getId())
                .managedByName(displayName(managedBy))
                .message(displayName(managedBy) + " 담당자에게 관리자요청을 전달했습니다.")
                .build();
    }

    private OrderChangeSourceArea resolveSourceArea(Member actor) {
        String teamName = actor.getTeam() != null ? actor.getTeam().getName() : null;
        if ("생산팀".equals(teamName)) return OrderChangeSourceArea.PRODUCTION;
        if ("배송팀".equals(teamName)) return OrderChangeSourceArea.DELIVERY;
        if ("출고팀".equals(teamName)) return OrderChangeSourceArea.DISPATCH;
        throw new AccessDeniedException("생산팀, 배송팀, 출고팀만 관리자요청을 보낼 수 있습니다.");
    }

    /**
     * 관리자요청은 발주의 현재 상태와 무관하게 허용합니다.
     * 각 팀 화면에서 조회 가능한 발주라면 REQUESTED, CONFIRMED,
     * PRODUCTION_DONE, DISPATCH_DONE, DELIVERY_DONE, CANCELED 등
     * 어떤 상태에서도 Task.managedBy에게 긴급 확인을 요청할 수 있습니다.
     *
     * 상태 변경 가능 범위와 관리자 확인 요청 가능 범위는 서로 다른 업무 규칙이므로
     * 이 서비스에서는 Order.status를 제한 조건으로 사용하지 않습니다.
     */
    private String resolveReason(OrderChangeSourceArea sourceArea, OrderAdminRequestDto request) {
        String customMessage = request != null ? normalize(request.getMessage()) : null;
        if (customMessage != null) return customMessage;

        return switch (sourceArea) {
            case PRODUCTION -> "생산 진행, 생산완료 또는 재생산 여부에 대한 관리자 확인이 필요합니다.";
            case DELIVERY -> "배송 누락 또는 현장 배송 문제에 대한 관리자 확인이 필요합니다.";
            case DISPATCH -> "출고 절차 또는 출고 정보 오류에 대한 관리자 확인이 필요합니다.";
            default -> "발주 확인이 필요합니다.";
        };
    }

    private String displayName(Member member) {
        if (member == null) return "관리 담당자";
        if (member.getName() != null && !member.getName().isBlank()) return member.getName().trim();
        if (member.getUsername() != null && !member.getUsername().isBlank()) return member.getUsername().trim();
        return "관리 담당자";
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
    }
}
