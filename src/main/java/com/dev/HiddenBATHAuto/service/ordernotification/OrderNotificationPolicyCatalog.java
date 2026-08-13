package com.dev.HiddenBATHAuto.service.ordernotification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationAction;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationRecipientGroup;
import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;

/**
 * 관리 화면에 노출할 발주 알림 정책의 전체 목록과 초기 기본값입니다.
 *
 * <p>DB에 아직 행이 없는 신규 정책은 이 목록의 기본값으로 동작하므로,
 * 마이그레이션 직후에도 기존 알림 흐름이 갑자기 중단되지 않습니다.</p>
 */
@Component
public class OrderNotificationPolicyCatalog {

    private final List<Definition> definitions = buildDefinitions();

    public List<Definition> getDefinitions() {
        return definitions;
    }

    public Definition find(
            OrderChangeSourceArea sourceArea,
            OrderNotificationAction action,
            OrderNotificationRecipientGroup recipientGroup
    ) {
        return definitions.stream()
                .filter(row -> row.sourceArea() == sourceArea
                        && row.action() == action
                        && row.recipientGroup() == recipientGroup)
                .findFirst()
                .orElse(null);
    }

    private List<Definition> buildDefinitions() {
        List<Definition> rows = new ArrayList<>();

        // 관리팀: 등록/수정/상태변경은 생산·배송·출고의 현재 업무 관계자에게 전달합니다.
        add(rows, OrderChangeSourceArea.MANAGEMENT, OrderNotificationAction.REGISTER,
                OrderNotificationRecipientGroup.MANAGEMENT, "Task 관리 담당자와 고정 admin에게 등록 사실을 전달합니다.");
        add(rows, OrderChangeSourceArea.MANAGEMENT, OrderNotificationAction.REGISTER,
                OrderNotificationRecipientGroup.PRODUCTION_CURRENT, "등록된 제품 분류의 생산팀에게 전달합니다.");
        add(rows, OrderChangeSourceArea.MANAGEMENT, OrderNotificationAction.REGISTER,
                OrderNotificationRecipientGroup.DELIVERY_CURRENT, "현재 배송 담당자가 있을 때 전달합니다.");
        add(rows, OrderChangeSourceArea.MANAGEMENT, OrderNotificationAction.REGISTER,
                OrderNotificationRecipientGroup.DISPATCH, "활성 출고팀 구성원에게 전달합니다.");

        addManagementAllWorkflow(rows, OrderNotificationAction.UPDATE,
                "관리자가 발주 내용을 수정한 경우 최신 내용을 다시 확인하도록 전달합니다.");
        addManagementAllWorkflow(rows, OrderNotificationAction.STATUS_CHANGE,
                "관리자가 진행 상태를 변경한 경우 관련 업무팀에게 전달합니다.");
        addManagementAllWorkflow(rows, OrderNotificationAction.CANCEL_OR_HIDE,
                "취소 또는 승인 전 상태 전환으로 업무가 사라질 때 작업 중지를 알립니다.");
        addManagementAllWorkflow(rows, OrderNotificationAction.RESTORE_OR_ROLLBACK,
                "업무 재개 또는 이전 단계로 되돌릴 때 재확인을 요청합니다.");
        addManagementAllWorkflow(rows, OrderNotificationAction.DELETE,
                "삭제 직전 관계자에게 삭제 사실을 전달합니다. 삭제 알림에는 바로가기 링크가 없습니다.");

        add(rows, OrderChangeSourceArea.MANAGEMENT, OrderNotificationAction.DELIVERY_HANDLER_CHANGE,
                OrderNotificationRecipientGroup.MANAGEMENT, "관리 담당자에게 배송 담당자 변경을 기록·전달합니다.");
        add(rows, OrderChangeSourceArea.MANAGEMENT, OrderNotificationAction.DELIVERY_HANDLER_CHANGE,
                OrderNotificationRecipientGroup.PRODUCTION_CURRENT, "현재 생산 담당 분류에도 담당자 변경 사실을 전달합니다.");
        add(rows, OrderChangeSourceArea.MANAGEMENT, OrderNotificationAction.DELIVERY_HANDLER_CHANGE,
                OrderNotificationRecipientGroup.DELIVERY_CURRENT, "새 배송 담당자에게 업무 배정을 전달합니다.");
        add(rows, OrderChangeSourceArea.MANAGEMENT, OrderNotificationAction.DELIVERY_HANDLER_CHANGE,
                OrderNotificationRecipientGroup.DELIVERY_PREVIOUS, "기존 배송 담당자에게 업무 해제를 전달합니다.");
        add(rows, OrderChangeSourceArea.MANAGEMENT, OrderNotificationAction.DELIVERY_HANDLER_CHANGE,
                OrderNotificationRecipientGroup.DISPATCH, "출고팀에게 변경된 배송 담당 정보를 전달합니다.");

        add(rows, OrderChangeSourceArea.MANAGEMENT, OrderNotificationAction.DELIVERY_METHOD_CHANGE,
                OrderNotificationRecipientGroup.MANAGEMENT, "관리 담당자에게 배송수단 변경을 전달합니다.");
        add(rows, OrderChangeSourceArea.MANAGEMENT, OrderNotificationAction.DELIVERY_METHOD_CHANGE,
                OrderNotificationRecipientGroup.PRODUCTION_CURRENT, "관리자의 배송수단 수정도 현재 생산 담당 분류에 전달합니다.");
        add(rows, OrderChangeSourceArea.MANAGEMENT, OrderNotificationAction.DELIVERY_METHOD_CHANGE,
                OrderNotificationRecipientGroup.DELIVERY_CURRENT, "배송 담당자가 필요한 수단이면 현재 담당자에게 전달합니다.");
        add(rows, OrderChangeSourceArea.MANAGEMENT, OrderNotificationAction.DELIVERY_METHOD_CHANGE,
                OrderNotificationRecipientGroup.DELIVERY_PREVIOUS, "배송 담당이 해제되면 기존 담당자에게 전달합니다.");
        add(rows, OrderChangeSourceArea.MANAGEMENT, OrderNotificationAction.DELIVERY_METHOD_CHANGE,
                OrderNotificationRecipientGroup.DISPATCH, "출고팀에게 변경된 배송수단을 전달합니다.");

        addAuditOnly(rows, OrderChangeSourceArea.MANAGEMENT,
                "관리자 화면에서 발생한 조회 확인은 감사이력만 저장합니다.");

        // 생산팀
        add(rows, OrderChangeSourceArea.PRODUCTION, OrderNotificationAction.PRODUCTION_COMPLETE,
                OrderNotificationRecipientGroup.MANAGEMENT, "Task 관리 담당자와 고정 admin에게 생산완료를 전달합니다.");
        add(rows, OrderChangeSourceArea.PRODUCTION, OrderNotificationAction.PRODUCTION_COMPLETE,
                OrderNotificationRecipientGroup.DELIVERY_CURRENT, "현재 배송 담당자에게 생산완료를 전달합니다.");
        add(rows, OrderChangeSourceArea.PRODUCTION, OrderNotificationAction.PRODUCTION_COMPLETE,
                OrderNotificationRecipientGroup.DISPATCH, "출고팀에게 생산완료를 전달합니다.");
        add(rows, OrderChangeSourceArea.PRODUCTION, OrderNotificationAction.ADMIN_REQUEST,
                OrderNotificationRecipientGroup.MANAGEMENT, "생산팀 관리자요청은 관리 담당자와 고정 admin에게만 전달합니다.");
        add(rows, OrderChangeSourceArea.PRODUCTION, OrderNotificationAction.UPDATE,
                OrderNotificationRecipientGroup.MANAGEMENT, "향후 생산팀 정보 수정이 추가될 경우 관리 담당자에게 전달합니다.");
        add(rows, OrderChangeSourceArea.PRODUCTION, OrderNotificationAction.UPDATE,
                OrderNotificationRecipientGroup.DELIVERY_CURRENT, "향후 생산팀 정보 수정이 배송에 영향을 주면 담당자에게 전달합니다.");
        add(rows, OrderChangeSourceArea.PRODUCTION, OrderNotificationAction.UPDATE,
                OrderNotificationRecipientGroup.DISPATCH, "향후 생산팀 정보 수정이 출고에 영향을 주면 출고팀에 전달합니다.");
        addAuditOnly(rows, OrderChangeSourceArea.PRODUCTION,
                "생산팀의 최초 확인·재수정 확인은 사용자별 확인 상태와 감사이력만 저장하며 알림은 보내지 않습니다.");

        // 배송팀
        add(rows, OrderChangeSourceArea.DELIVERY, OrderNotificationAction.DELIVERY_COMPLETE,
                OrderNotificationRecipientGroup.MANAGEMENT, "Task 관리 담당자와 고정 admin에게 배송완료를 전달합니다.");
        add(rows, OrderChangeSourceArea.DELIVERY, OrderNotificationAction.DELIVERY_COMPLETE,
                OrderNotificationRecipientGroup.DISPATCH, "출고팀에게 배송완료를 전달합니다.");
        add(rows, OrderChangeSourceArea.DELIVERY, OrderNotificationAction.DELIVERY_HANDLER_CHANGE,
                OrderNotificationRecipientGroup.MANAGEMENT, "관리 담당자에게 배송 담당자 변경을 전달합니다.");
        add(rows, OrderChangeSourceArea.DELIVERY, OrderNotificationAction.DELIVERY_HANDLER_CHANGE,
                OrderNotificationRecipientGroup.DELIVERY_CURRENT, "새 배송 담당자에게 업무 배정을 전달합니다.");
        add(rows, OrderChangeSourceArea.DELIVERY, OrderNotificationAction.DELIVERY_HANDLER_CHANGE,
                OrderNotificationRecipientGroup.DELIVERY_PREVIOUS, "기존 배송 담당자에게 업무 해제를 전달합니다.");
        add(rows, OrderChangeSourceArea.DELIVERY, OrderNotificationAction.ADMIN_REQUEST,
                OrderNotificationRecipientGroup.MANAGEMENT, "배송팀 관리자요청은 관리 담당자와 고정 admin에게만 전달합니다.");
        add(rows, OrderChangeSourceArea.DELIVERY, OrderNotificationAction.UPDATE,
                OrderNotificationRecipientGroup.MANAGEMENT, "배송팀의 기타 수정이 발생하면 관리 담당자에게 전달합니다.");
        add(rows, OrderChangeSourceArea.DELIVERY, OrderNotificationAction.UPDATE,
                OrderNotificationRecipientGroup.DISPATCH, "배송팀의 기타 수정이 출고 흐름에 영향을 주면 출고팀에 전달합니다.");
        addAuditOnly(rows, OrderChangeSourceArea.DELIVERY,
                "배송팀의 단순 조회 확인은 감사이력만 저장하며 알림은 보내지 않습니다.");

        // 출고팀
        add(rows, OrderChangeSourceArea.DISPATCH, OrderNotificationAction.DISPATCH_COMPLETE,
                OrderNotificationRecipientGroup.MANAGEMENT, "Task 관리 담당자와 고정 admin에게 출고완료를 전달합니다.");
        add(rows, OrderChangeSourceArea.DISPATCH, OrderNotificationAction.DELIVERY_HANDLER_CHANGE,
                OrderNotificationRecipientGroup.MANAGEMENT, "관리 담당자에게 배송 담당자 변경을 전달합니다.");
        add(rows, OrderChangeSourceArea.DISPATCH, OrderNotificationAction.DELIVERY_HANDLER_CHANGE,
                OrderNotificationRecipientGroup.DELIVERY_CURRENT, "새 배송 담당자에게 업무 배정을 전달합니다.");
        add(rows, OrderChangeSourceArea.DISPATCH, OrderNotificationAction.DELIVERY_HANDLER_CHANGE,
                OrderNotificationRecipientGroup.DELIVERY_PREVIOUS, "기존 배송 담당자에게 업무 해제를 전달합니다.");
        add(rows, OrderChangeSourceArea.DISPATCH, OrderNotificationAction.DELIVERY_METHOD_CHANGE,
                OrderNotificationRecipientGroup.MANAGEMENT, "담당자가 필요 없거나 미지정인 배송수단이면 관리 담당자에게 전달합니다.");
        add(rows, OrderChangeSourceArea.DISPATCH, OrderNotificationAction.DELIVERY_METHOD_CHANGE,
                OrderNotificationRecipientGroup.DELIVERY_CURRENT, "담당자가 필요한 배송수단이면 지정된 배송 담당자에게 전달합니다.");
        add(rows, OrderChangeSourceArea.DISPATCH, OrderNotificationAction.DELIVERY_METHOD_CHANGE,
                OrderNotificationRecipientGroup.DELIVERY_PREVIOUS, "배송수단 변경으로 담당이 해제되면 기존 담당자에게 전달합니다.");
        add(rows, OrderChangeSourceArea.DISPATCH, OrderNotificationAction.ADMIN_REQUEST,
                OrderNotificationRecipientGroup.MANAGEMENT, "출고팀 관리자요청은 관리 담당자와 고정 admin에게만 전달합니다.");
        add(rows, OrderChangeSourceArea.DISPATCH, OrderNotificationAction.UPDATE,
                OrderNotificationRecipientGroup.MANAGEMENT, "출고팀의 기타 수정이 발생하면 관리 담당자에게 전달합니다.");
        add(rows, OrderChangeSourceArea.DISPATCH, OrderNotificationAction.UPDATE,
                OrderNotificationRecipientGroup.DELIVERY_CURRENT, "출고팀의 기타 수정이 배송에 영향을 주면 담당자에게 전달합니다.");
        addAuditOnly(rows, OrderChangeSourceArea.DISPATCH,
                "출고팀의 단순 조회 확인은 감사이력만 저장하며 알림은 보내지 않습니다.");

        // 향후 고객 직접 발주 및 시스템 자동 처리 확장용
        add(rows, OrderChangeSourceArea.CUSTOMER, OrderNotificationAction.REGISTER,
                OrderNotificationRecipientGroup.MANAGEMENT, "향후 고객 직접 발주 시 관리 담당자와 고정 admin에게 전달합니다.");
        add(rows, OrderChangeSourceArea.CUSTOMER, OrderNotificationAction.REGISTER,
                OrderNotificationRecipientGroup.PRODUCTION_CURRENT, "승인되어 노출되는 고객 발주를 생산 담당 분류에 전달합니다.");
        add(rows, OrderChangeSourceArea.CUSTOMER, OrderNotificationAction.REGISTER,
                OrderNotificationRecipientGroup.DELIVERY_CURRENT, "배송 담당자가 지정된 고객 발주를 담당자에게 전달합니다.");
        add(rows, OrderChangeSourceArea.CUSTOMER, OrderNotificationAction.REGISTER,
                OrderNotificationRecipientGroup.DISPATCH, "승인되어 노출되는 고객 발주를 출고팀에 전달합니다.");
        add(rows, OrderChangeSourceArea.CUSTOMER, OrderNotificationAction.UPDATE,
                OrderNotificationRecipientGroup.MANAGEMENT, "고객 수정은 우선 관리 담당자에게 전달합니다.");

        add(rows, OrderChangeSourceArea.SYSTEM, OrderNotificationAction.STATUS_CHANGE,
                OrderNotificationRecipientGroup.MANAGEMENT, "시스템 자동 상태변경을 관리 담당자에게 전달합니다.");
        add(rows, OrderChangeSourceArea.SYSTEM, OrderNotificationAction.STATUS_CHANGE,
                OrderNotificationRecipientGroup.PRODUCTION_CURRENT, "시스템 자동 상태변경을 생산 담당 분류에 전달합니다.");
        add(rows, OrderChangeSourceArea.SYSTEM, OrderNotificationAction.STATUS_CHANGE,
                OrderNotificationRecipientGroup.DELIVERY_CURRENT, "시스템 자동 상태변경을 배송 담당자에게 전달합니다.");
        add(rows, OrderChangeSourceArea.SYSTEM, OrderNotificationAction.STATUS_CHANGE,
                OrderNotificationRecipientGroup.DISPATCH, "시스템 자동 상태변경을 출고팀에 전달합니다.");

        return List.copyOf(rows);
    }

    private void addManagementAllWorkflow(
            List<Definition> rows,
            OrderNotificationAction action,
            String description
    ) {
        add(rows, OrderChangeSourceArea.MANAGEMENT, action,
                OrderNotificationRecipientGroup.MANAGEMENT, description);
        add(rows, OrderChangeSourceArea.MANAGEMENT, action,
                OrderNotificationRecipientGroup.PRODUCTION_CURRENT, description);
        add(rows, OrderChangeSourceArea.MANAGEMENT, action,
                OrderNotificationRecipientGroup.PRODUCTION_PREVIOUS,
                description + " 생산 분류가 바뀐 경우 변경 전 분류에도 마지막 알림을 보냅니다.");
        add(rows, OrderChangeSourceArea.MANAGEMENT, action,
                OrderNotificationRecipientGroup.DELIVERY_CURRENT, description);
        add(rows, OrderChangeSourceArea.MANAGEMENT, action,
                OrderNotificationRecipientGroup.DELIVERY_PREVIOUS,
                description + " 담당자가 바뀐 경우 기존 배송 담당자에게도 마지막 알림을 보냅니다.");
        add(rows, OrderChangeSourceArea.MANAGEMENT, action,
                OrderNotificationRecipientGroup.DISPATCH, description);
    }

    private void addAuditOnly(List<Definition> rows, OrderChangeSourceArea sourceArea, String description) {
        rows.add(new Definition(
                sourceArea,
                OrderNotificationAction.CHECK_CONFIRM,
                OrderNotificationRecipientGroup.AUDIT_ONLY,
                false,
                false,
                false,
                false,
                description
        ));
    }

    private void add(
            List<Definition> rows,
            OrderChangeSourceArea sourceArea,
            OrderNotificationAction action,
            OrderNotificationRecipientGroup recipientGroup,
            String description
    ) {
        // 중요알림은 업무를 강제로 막는 수단이므로 기존 배포 시 갑자기 팝업이 쏟아지지 않도록 기본값은 OFF입니다.
        rows.add(new Definition(sourceArea, action, recipientGroup, true, true, false, true, description));
    }

    public record Definition(
            OrderChangeSourceArea sourceArea,
            OrderNotificationAction action,
            OrderNotificationRecipientGroup recipientGroup,
            boolean defaultWebEnabled,
            boolean defaultKakaoEnabled,
            boolean defaultImportantEnabled,
            boolean configurable,
            String description
    ) {
        public Definition {
            Objects.requireNonNull(sourceArea);
            Objects.requireNonNull(action);
            Objects.requireNonNull(recipientGroup);
        }

        public String key() {
            return sourceArea.name() + "|" + action.name() + "|" + recipientGroup.name();
        }
    }
}
