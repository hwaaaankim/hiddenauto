package com.dev.HiddenBATHAuto.service.ordernotification;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationPolicyRowDto;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationAction;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationRecipientGroup;
import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.notification.OrderNotificationPolicy;
import com.dev.HiddenBATHAuto.repository.notification.OrderNotificationPolicyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderNotificationPolicyService {

    private final OrderNotificationPolicyRepository policyRepository;
    private final OrderNotificationPolicyCatalog catalog;

    @Transactional(readOnly = true)
    public Map<OrderNotificationRecipientGroup, ChannelPolicy> resolvePolicies(
            OrderChangeSourceArea sourceArea,
            OrderNotificationAction action
    ) {
        OrderChangeSourceArea safeSource = sourceArea == null ? OrderChangeSourceArea.SYSTEM : sourceArea;
        OrderNotificationAction safeAction = action == null ? OrderNotificationAction.UPDATE : action;

        Map<OrderNotificationRecipientGroup, OrderNotificationPolicy> stored = policyRepository
                .findBySourceAreaAndAction(safeSource, safeAction)
                .stream()
                .collect(Collectors.toMap(
                        OrderNotificationPolicy::getRecipientGroup,
                        row -> row,
                        (left, right) -> left,
                        () -> new EnumMap<>(OrderNotificationRecipientGroup.class)
                ));

        Map<OrderNotificationRecipientGroup, ChannelPolicy> result =
                new EnumMap<>(OrderNotificationRecipientGroup.class);
        for (OrderNotificationRecipientGroup group : OrderNotificationRecipientGroup.values()) {
            OrderNotificationPolicy row = stored.get(group);
            if (row != null) {
                result.put(group, new ChannelPolicy(
                        row.isWebEnabled(),
                        row.isKakaoEnabled(),
                        row.isImportantEnabled()
                ));
                continue;
            }

            OrderNotificationPolicyCatalog.Definition definition = catalog.find(safeSource, safeAction, group);
            if (definition != null) {
                result.put(group, new ChannelPolicy(
                        definition.defaultWebEnabled(),
                        definition.defaultKakaoEnabled(),
                        definition.defaultImportantEnabled()
                ));
            } else {
                // 아직 관리화면 카탈로그에 추가되지 않은 신규 operation도 기존 발송 흐름을 보존합니다.
                result.put(group, new ChannelPolicy(true, true, false));
            }
        }
        return result;
    }

    public ChannelPolicy defaultPolicy(
            OrderChangeSourceArea sourceArea,
            OrderNotificationAction action,
            OrderNotificationRecipientGroup recipientGroup
    ) {
        OrderChangeSourceArea safeSource = sourceArea == null ? OrderChangeSourceArea.SYSTEM : sourceArea;
        OrderNotificationAction safeAction = action == null ? OrderNotificationAction.UPDATE : action;
        OrderNotificationRecipientGroup safeGroup = recipientGroup == null
                ? OrderNotificationRecipientGroup.MANAGEMENT
                : recipientGroup;
        OrderNotificationPolicyCatalog.Definition definition = catalog.find(safeSource, safeAction, safeGroup);
        return definition == null
                ? new ChannelPolicy(true, true, false)
                : new ChannelPolicy(
                        definition.defaultWebEnabled(),
                        definition.defaultKakaoEnabled(),
                        definition.defaultImportantEnabled()
                );
    }

    @Transactional(readOnly = true)
    public Map<String, List<OrderNotificationPolicyRowDto>> getPolicySections() {
        Map<String, OrderNotificationPolicy> storedByKey = policyRepository.findAll().stream()
                .collect(Collectors.toMap(
                        row -> key(row.getSourceArea(), row.getAction(), row.getRecipientGroup()),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<String, List<OrderNotificationPolicyRowDto>> sections = new LinkedHashMap<>();
        for (OrderNotificationPolicyCatalog.Definition definition : catalog.getDefinitions()) {
            String key = definition.key();
            OrderNotificationPolicy stored = storedByKey.get(key);
            boolean webEnabled = stored != null
                    ? stored.isWebEnabled()
                    : definition.defaultWebEnabled();
            boolean kakaoEnabled = stored != null
                    ? stored.isKakaoEnabled()
                    : definition.defaultKakaoEnabled();
            boolean importantEnabled = stored != null
                    ? stored.isImportantEnabled()
                    : definition.defaultImportantEnabled();

            OrderNotificationPolicyRowDto dto = OrderNotificationPolicyRowDto.builder()
                    .key(key)
                    .sourceArea(definition.sourceArea().name())
                    .sourceAreaLabel(definition.sourceArea().getLabel())
                    .action(definition.action().name())
                    .actionLabel(definition.action().getLabel())
                    .recipientGroup(definition.recipientGroup().name())
                    .recipientGroupLabel(definition.recipientGroup().getLabel())
                    .description(definition.description())
                    .webEnabled(webEnabled)
                    .kakaoEnabled(kakaoEnabled)
                    .importantEnabled(importantEnabled)
                    .configurable(definition.configurable())
                    .build();

            sections.computeIfAbsent(definition.sourceArea().getLabel(), ignored -> new ArrayList<>()).add(dto);
        }
        sections.replaceAll((label, rows) -> List.copyOf(rows));
        return sections;
    }

    @Transactional
    public void saveAll(
            Set<String> webKeys,
            Set<String> kakaoKeys,
            Set<String> importantKeys,
            Member actor
    ) {
        Set<String> normalizedWebKeys = normalizeKeys(webKeys);
        Set<String> normalizedKakaoKeys = normalizeKeys(kakaoKeys);
        Set<String> normalizedImportantKeys = normalizeKeys(importantKeys);
        Map<String, OrderNotificationPolicy> existing = policyRepository.findAll().stream()
                .collect(Collectors.toMap(
                        row -> key(row.getSourceArea(), row.getAction(), row.getRecipientGroup()),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Long actorId = actor != null ? actor.getId() : null;
        String actorUsername = actor != null ? actor.getUsername() : null;
        List<OrderNotificationPolicy> saves = new ArrayList<>();

        for (OrderNotificationPolicyCatalog.Definition definition : catalog.getDefinitions()) {
            if (!definition.configurable()) continue;

            String key = definition.key();
            boolean webEnabled = normalizedWebKeys.contains(key);
            boolean kakaoEnabled = normalizedKakaoKeys.contains(key);
            boolean importantEnabled = normalizedImportantKeys.contains(key);
            OrderNotificationPolicy row = existing.get(key);
            if (row == null) {
                row = OrderNotificationPolicy.create(
                        definition.sourceArea(),
                        definition.action(),
                        definition.recipientGroup(),
                        webEnabled,
                        kakaoEnabled,
                        importantEnabled,
                        actorId,
                        actorUsername
                );
            } else {
                row.update(webEnabled, kakaoEnabled, importantEnabled, actorId, actorUsername);
            }
            saves.add(row);
        }

        policyRepository.saveAll(saves);
        policyRepository.flush();
    }

    private Set<String> normalizeKeys(Set<String> keys) {
        if (keys == null || keys.isEmpty()) return Set.of();
        return keys.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String key(
            OrderChangeSourceArea sourceArea,
            OrderNotificationAction action,
            OrderNotificationRecipientGroup recipientGroup
    ) {
        return sourceArea.name() + "|" + action.name() + "|" + recipientGroup.name();
    }

    public record ChannelPolicy(boolean webEnabled, boolean kakaoEnabled, boolean importantEnabled) {
        public boolean disabled() {
            return !webEnabled && !kakaoEnabled && !importantEnabled;
        }
    }
}
