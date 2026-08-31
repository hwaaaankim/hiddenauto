package com.dev.HiddenBATHAuto.service.asnotification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.asnotification.AsNotificationPolicyRowDto;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationAction;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationRecipientGroup;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationSourceArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.notification.AsNotificationPolicy;
import com.dev.HiddenBATHAuto.repository.notification.AsNotificationPolicyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AsNotificationPolicyService {

    private final AsNotificationPolicyRepository repository;
    private final AsNotificationPolicyCatalog catalog;

    @Transactional(readOnly = true)
    public ChannelPolicy resolvePolicy(AsNotificationSourceArea source, AsNotificationAction action,
                                       AsNotificationRecipientGroup group) {
        AsNotificationPolicyCatalog.Definition definition = catalog.find(source, action, group);
        if (definition == null) return ChannelPolicy.disabledPolicy();

        AsNotificationPolicy row = repository.findBySourceAreaAndActionAndRecipientGroup(source, action, group)
                .orElse(null);
        boolean web = row != null ? row.isWebEnabled() : definition.defaultWebEnabled();
        boolean kakao = row != null ? row.isKakaoEnabled() : definition.defaultKakaoEnabled();
        boolean important = row != null ? row.isImportantEnabled() : definition.defaultImportantEnabled();

        // 고객은 어떤 DB 오입력에도 종/강제팝업 채널이 생기지 않도록 허용 채널을 서버에서 재검증합니다.
        return new ChannelPolicy(
                definition.webAllowed() && web,
                definition.kakaoAllowed() && kakao,
                definition.importantAllowed() && important
        );
    }

    @Transactional(readOnly = true)
    public Map<String, List<AsNotificationPolicyRowDto>> getPolicySections() {
        Map<String, AsNotificationPolicy> stored = repository.findAll().stream()
                .collect(Collectors.toMap(
                        row -> key(row.getSourceArea(), row.getAction(), row.getRecipientGroup()),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, List<AsNotificationPolicyRowDto>> sections = new LinkedHashMap<>();
        for (AsNotificationPolicyCatalog.Definition d : catalog.getDefinitions()) {
            AsNotificationPolicy row = stored.get(d.key());
            boolean web = d.webAllowed() && (row != null ? row.isWebEnabled() : d.defaultWebEnabled());
            boolean kakao = d.kakaoAllowed() && (row != null ? row.isKakaoEnabled() : d.defaultKakaoEnabled());
            boolean important = d.importantAllowed() && (row != null ? row.isImportantEnabled() : d.defaultImportantEnabled());
            AsNotificationPolicyRowDto dto = AsNotificationPolicyRowDto.builder()
                    .key(d.key())
                    .sourceArea(d.sourceArea().name()).sourceAreaLabel(d.sourceArea().getLabel())
                    .action(d.action().name()).actionLabel(d.action().getLabel())
                    .recipientGroup(d.recipientGroup().name()).recipientGroupLabel(d.recipientGroup().getLabel())
                    .description(d.description())
                    .webEnabled(web).kakaoEnabled(kakao).importantEnabled(important)
                    .webAllowed(d.webAllowed()).kakaoAllowed(d.kakaoAllowed()).importantAllowed(d.importantAllowed())
                    .build();
            sections.computeIfAbsent(d.sourceArea().getLabel(), ignored -> new ArrayList<>()).add(dto);
        }
        sections.replaceAll((k, v) -> List.copyOf(v));
        return sections;
    }

    @Transactional
    public void saveAll(Set<String> webKeys, Set<String> kakaoKeys, Set<String> importantKeys, Member actor) {
        Set<String> webSet = normalize(webKeys);
        Set<String> kakaoSet = normalize(kakaoKeys);
        Set<String> importantSet = normalize(importantKeys);
        Map<String, AsNotificationPolicy> existing = repository.findAll().stream()
                .collect(Collectors.toMap(
                        row -> key(row.getSourceArea(), row.getAction(), row.getRecipientGroup()),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<AsNotificationPolicy> saves = new ArrayList<>();
        for (AsNotificationPolicyCatalog.Definition d : catalog.getDefinitions()) {
            boolean web = d.webAllowed() && webSet.contains(d.key());
            boolean kakao = d.kakaoAllowed() && kakaoSet.contains(d.key());
            boolean important = d.importantAllowed() && importantSet.contains(d.key());
            AsNotificationPolicy row = existing.get(d.key());
            if (row == null) {
                row = AsNotificationPolicy.create(d.sourceArea(), d.action(), d.recipientGroup(),
                        web, kakao, important,
                        actor != null ? actor.getId() : null,
                        actor != null ? actor.getUsername() : null);
            } else {
                row.update(web, kakao, important,
                        actor != null ? actor.getId() : null,
                        actor != null ? actor.getUsername() : null);
            }
            saves.add(row);
        }
        repository.saveAll(saves);
        repository.flush();
    }

    private Set<String> normalize(Set<String> values) {
        if (values == null) return Set.of();
        return values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String key(AsNotificationSourceArea source, AsNotificationAction action,
                       AsNotificationRecipientGroup group) {
        return source.name() + "|" + action.name() + "|" + group.name();
    }

    public record ChannelPolicy(boolean webEnabled, boolean kakaoEnabled, boolean importantEnabled) {
        public boolean disabled() { return !webEnabled && !kakaoEnabled && !importantEnabled; }
        public static ChannelPolicy disabledPolicy() { return new ChannelPolicy(false, false, false); }
    }
}
