package com.dev.HiddenBATHAuto.service.asnotification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.enums.notification.AsNotificationAction;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationRecipientGroup;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationSourceArea;

@Component
public class AsNotificationPolicyCatalog {

    private final List<Definition> definitions = buildDefinitions();

    public List<Definition> getDefinitions() {
        return definitions;
    }

    public Definition find(AsNotificationSourceArea source, AsNotificationAction action,
                           AsNotificationRecipientGroup group) {
        return definitions.stream()
                .filter(d -> d.sourceArea() == source && d.action() == action && d.recipientGroup() == group)
                .findFirst().orElse(null);
    }

    private List<Definition> buildDefinitions() {
        List<Definition> rows = new ArrayList<>();

        // 고객 행동 -> admin + manager_02 + 배정 AS 담당자. 내부 사용자는 3개 채널 모두 관리 가능.
        for (AsNotificationAction action : List.of(
                AsNotificationAction.REQUEST_CREATED,
                AsNotificationAction.CUSTOMER_UPDATE,
                AsNotificationAction.CUSTOMER_CANCEL)) {
            addInternal(rows, AsNotificationSourceArea.CUSTOMER, action,
                    AsNotificationRecipientGroup.ADMIN,
                    "고객이 처리한 AS 변경 내용을 최고관리자(admin)에게 전달합니다.");
        }
        addInternal(rows, AsNotificationSourceArea.CUSTOMER, AsNotificationAction.REQUEST_CREATED,
                AsNotificationRecipientGroup.MANAGER_02, "고객의 신규 AS 신청을 AS 관리담당자에게 전달합니다.");
        addInternal(rows, AsNotificationSourceArea.CUSTOMER, AsNotificationAction.REQUEST_CREATED,
                AsNotificationRecipientGroup.AS_HANDLER_CURRENT, "지역 자동배정된 AS 담당직원에게 신규 신청을 전달합니다.");
        addInternal(rows, AsNotificationSourceArea.CUSTOMER, AsNotificationAction.CUSTOMER_UPDATE,
                AsNotificationRecipientGroup.MANAGER_02, "접수 상태에서 고객이 수정한 AS 내용을 전달합니다.");
        addInternal(rows, AsNotificationSourceArea.CUSTOMER, AsNotificationAction.CUSTOMER_UPDATE,
                AsNotificationRecipientGroup.AS_HANDLER_CURRENT, "접수 상태에서 고객이 수정한 최신 AS 내용을 전달합니다.");
        addInternal(rows, AsNotificationSourceArea.CUSTOMER, AsNotificationAction.CUSTOMER_CANCEL,
                AsNotificationRecipientGroup.MANAGER_02, "고객이 접수 상태 AS를 취소/삭제한 사실을 전달합니다.");
        addInternal(rows, AsNotificationSourceArea.CUSTOMER, AsNotificationAction.CUSTOMER_CANCEL,
                AsNotificationRecipientGroup.AS_HANDLER_CURRENT, "배정된 담당직원에게 고객 취소 사실을 전달합니다.");

        // 관리 행동 -> 고객(카카오만) + 현재 배정된 AS 담당직원(3채널).
        for (AsNotificationAction action : List.of(
                AsNotificationAction.DETAIL_UPDATE,
                AsNotificationAction.STATUS_IN_PROGRESS,
                AsNotificationAction.STATUS_CANCELED,
                AsNotificationAction.STATUS_CHANGE,
                AsNotificationAction.HANDLER_CHANGE,
                AsNotificationAction.DELETE)) {
            addInternal(rows, AsNotificationSourceArea.MANAGEMENT, action,
                    AsNotificationRecipientGroup.ADMIN,
                    "관리자가 처리한 AS 변경 내용을 최고관리자(admin)에게도 항상 전달합니다.");
            addCustomer(rows, AsNotificationSourceArea.MANAGEMENT, action,
                    "관리자가 처리한 AS 변경 내용을 신청 고객에게 카카오톡으로 전달합니다.");
            addInternal(rows, AsNotificationSourceArea.MANAGEMENT, action,
                    AsNotificationRecipientGroup.AS_HANDLER_CURRENT,
                    "관리자가 처리한 AS 변경 내용을 현재 담당직원에게 전달합니다.");
        }
        // AS팀 행동 -> manager_02(3채널) + 고객(카카오만). 내부메모/첨부 변경은 고객에게 보내지 않습니다.
        for (AsNotificationAction action : List.of(
                AsNotificationAction.HANDLER_CHANGE,
                AsNotificationAction.VISIT_SCHEDULE_UPDATE,
                AsNotificationAction.COMPLETE)) {
            addInternal(rows, AsNotificationSourceArea.AS_TEAM, action,
                    AsNotificationRecipientGroup.ADMIN,
                    "AS 담당직원의 처리 결과를 최고관리자(admin)에게도 항상 전달합니다.");
            addInternal(rows, AsNotificationSourceArea.AS_TEAM, action,
                    AsNotificationRecipientGroup.MANAGER_02,
                    "AS 담당직원의 처리 결과를 AS 관리담당자에게 전달합니다.");
            addCustomer(rows, AsNotificationSourceArea.AS_TEAM, action,
                    "AS 담당직원의 처리 결과를 신청 고객에게 카카오톡으로 전달합니다.");
        }
        addInternal(rows, AsNotificationSourceArea.AS_TEAM, AsNotificationAction.INTERNAL_UPDATE,
                AsNotificationRecipientGroup.ADMIN,
                "담당자 메모·첨부 자료처럼 내부 확인이 필요한 변경을 최고관리자(admin)에게도 전달합니다.");
        addInternal(rows, AsNotificationSourceArea.AS_TEAM, AsNotificationAction.INTERNAL_UPDATE,
                AsNotificationRecipientGroup.MANAGER_02,
                "담당자 메모·첨부 자료처럼 내부 확인이 필요한 변경을 관리담당자에게 전달합니다.");

        return List.copyOf(rows);
    }

    private void addInternal(List<Definition> rows, AsNotificationSourceArea source,
                             AsNotificationAction action, AsNotificationRecipientGroup group, String description) {
        rows.add(new Definition(source, action, group,
                true, true, false,
                true, true, true,
                description));
    }

    private void addCustomer(List<Definition> rows, AsNotificationSourceArea source,
                             AsNotificationAction action, String description) {
        rows.add(new Definition(source, action, AsNotificationRecipientGroup.CUSTOMER,
                false, true, false,
                false, true, false,
                description));
    }

    public record Definition(
            AsNotificationSourceArea sourceArea,
            AsNotificationAction action,
            AsNotificationRecipientGroup recipientGroup,
            boolean defaultWebEnabled,
            boolean defaultKakaoEnabled,
            boolean defaultImportantEnabled,
            boolean webAllowed,
            boolean kakaoAllowed,
            boolean importantAllowed,
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
