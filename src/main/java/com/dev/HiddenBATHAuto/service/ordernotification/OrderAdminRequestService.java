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
import com.dev.HiddenBATHAuto.service.order.OrderTeamAccessPolicyService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderAdminRequestService {

    private final OrderRepository orderRepository;
    private final OrderChangeAuditService orderChangeAuditService;
    private final OrderTeamAccessPolicyService accessPolicyService;
    private static final Long FIXED_ADMIN_MEMBER_ID = 1L;
    private static final String FIXED_ADMIN_USERNAME = "admin";

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
        accessPolicyService.assertCanRequestAdmin(actor, order, sourceArea);

        Member managedBy = resolveEnabledManagedBy(order);

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
                .managedById(managedBy != null ? managedBy.getId() : null)
                .managedByName(managedBy != null ? displayName(managedBy) : null)
                .message(buildRecipientMessage(managedBy))
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
     * 활성화된 Task 관리 담당자만 반환합니다.
     *
     * <p>
     * 담당자가 없거나 비활성화된 경우에도 관리자요청 자체는 막지 않고
     * 고정 admin에게만 전달합니다.
     * </p>
     */
    private Member resolveEnabledManagedBy(Order order) {
        Member managedBy = order.getTask() != null
                ? order.getTask().getManagedBy()
                : null;

        if (managedBy == null
                || managedBy.getId() == null
                || !managedBy.isEnabled()) {
            return null;
        }

        return managedBy;
    }

    private String buildRecipientMessage(Member managedBy) {
        if (managedBy == null || isFixedAdmin(managedBy)) {
            return "admin 관리자에게 관리자요청을 전달했습니다.";
        }

        return "admin 관리자와 "
                + displayName(managedBy)
                + " 담당자에게 관리자요청을 전달했습니다.";
    }

    private boolean isFixedAdmin(Member member) {
        return member != null
                && FIXED_ADMIN_MEMBER_ID.equals(member.getId())
                && FIXED_ADMIN_USERNAME.equals(member.getUsername());
    }
    
    /**
     * 관리자요청 사유 자체는 상태별로 다르게 제한하지 않습니다.
     * 다만 request() 진입 시 중앙 권한 정책으로 현재 팀 화면에서 조회·조작 가능한 오더인지 먼저 검증합니다.
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
