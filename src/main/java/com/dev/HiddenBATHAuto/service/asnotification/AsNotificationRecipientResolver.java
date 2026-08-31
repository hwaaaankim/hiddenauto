package com.dev.HiddenBATHAuto.service.asnotification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.config.notification.AsNotificationProperties;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationAction;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationRecipientGroup;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationSourceArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.as.audit.AsChangeEvent;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsNotificationRecipientResolver {

    public static final String AS_MANAGER_USERNAME = "manager_02";
    public static final String ADMIN_USERNAME = "admin";

    private final MemberRepository memberRepository;
    private final AsNotificationProperties properties;

    @Transactional(readOnly = true)
    public List<RecipientTarget> resolve(AsChangeEvent event) {
        if (event == null) return List.of();
        Map<Long, RecipientTarget> targets = new LinkedHashMap<>();
        AsNotificationSourceArea source = event.getSourceArea();
        AsNotificationAction action = event.getAction();

        // 최고관리자(admin)는 AS 이벤트의 발생 주체/영역과 관계없이 항상 수신 후보에 포함합니다.
        addAdmin(targets, event);

        if (source == AsNotificationSourceArea.CUSTOMER) {
            addManager(targets, event);
            addCurrentHandler(targets, event);
        } else if (source == AsNotificationSourceArea.MANAGEMENT) {
            addCustomer(targets, event);
            addCurrentHandler(targets, event);
        } else if (source == AsNotificationSourceArea.AS_TEAM) {
            addManager(targets, event);
            if (action != AsNotificationAction.INTERNAL_UPDATE) addCustomer(targets, event);
        }
        return List.copyOf(targets.values());
    }

    private void addAdmin(Map<Long, RecipientTarget> targets, AsChangeEvent event) {
        Member admin = memberRepository.findByUsername(ADMIN_USERNAME)
                .filter(Member::isEnabled)
                .orElse(null);
        if (admin == null) {
            log.warn("AS 알림 최고관리자 계정 '{}'을 찾지 못했거나 비활성 계정입니다. AS ID={}",
                    ADMIN_USERNAME, event.resolveAsTaskId());
            return;
        }
        // 'admin은 무조건 수신' 요구사항에 따라 본인이 처리한 AS 이벤트도 자기 알림에서 제외하지 않습니다.
        add(targets, admin, AsNotificationRecipientGroup.ADMIN, event, true);
    }

    private void addManager(Map<Long, RecipientTarget> targets, AsChangeEvent event) {
        Member manager = memberRepository.findByUsername(AS_MANAGER_USERNAME)
                .filter(Member::isEnabled)
                .filter(member -> member.getRole() == MemberRole.MANAGEMENT)
                .orElse(null);
        if (manager == null) {
            log.warn("AS 알림 수신 관리팀 계정 '{}'을 찾지 못했거나 활성 MANAGEMENT 계정이 아닙니다. AS ID={}",
                    AS_MANAGER_USERNAME, event.resolveAsTaskId());
            return;
        }
        add(targets, manager, AsNotificationRecipientGroup.MANAGER_02, event);
    }

    private void addCurrentHandler(Map<Long, RecipientTarget> targets, AsChangeEvent event) {
        AsTask task = event.getAsTask();
        Member handler = task != null ? task.getAssignedHandler() : null;
        if (handler == null && event.getAssignedHandlerIdSnapshot() != null) {
            handler = memberRepository.findById(event.getAssignedHandlerIdSnapshot()).orElse(null);
        }
        add(targets, handler, AsNotificationRecipientGroup.AS_HANDLER_CURRENT, event);
    }

    private void addCustomer(Map<Long, RecipientTarget> targets, AsChangeEvent event) {
        AsTask task = event.getAsTask();
        Member customer = task != null ? task.getRequestedBy() : null;
        if (customer == null && event.getRequestedByMemberIdSnapshot() != null) {
            customer = memberRepository.findById(event.getRequestedByMemberIdSnapshot()).orElse(null);
        }
        add(targets, customer, AsNotificationRecipientGroup.CUSTOMER, event);
    }

    private void add(Map<Long, RecipientTarget> targets, Member member,
                     AsNotificationRecipientGroup group, AsChangeEvent event) {
        add(targets, member, group, event, false);
    }

    private void add(Map<Long, RecipientTarget> targets, Member member,
                     AsNotificationRecipientGroup group, AsChangeEvent event, boolean forceActorNotification) {
        if (member == null || member.getId() == null || !member.isEnabled()) return;
        if (!forceActorNotification && !properties.isNotifyActor() && event.getActorMemberId() != null
                && event.getActorMemberId().equals(member.getId())) return;
        targets.putIfAbsent(member.getId(), new RecipientTarget(member, group));
    }

    public record RecipientTarget(Member member, AsNotificationRecipientGroup recipientGroup) {}
}
