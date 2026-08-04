package com.dev.HiddenBATHAuto.service.ordernotification;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.config.notification.OrderNotificationProperties;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationAudience;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationCategory;
import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderNotificationRecipientResolver {

    private static final String PRODUCTION_TEAM = "생산팀";
    private static final String DELIVERY_TEAM = "배송팀";
    private static final String DISPATCH_TEAM = "출고팀";

    private final MemberRepository memberRepository;
    private final OrderNotificationProperties properties;

    @Transactional(readOnly = true)
    public List<RecipientTarget> resolve(
            Order order,
            OrderChangeSourceArea sourceArea,
            Set<OrderWorkArea> affectedAreas,
            OrderNotificationAudience audience,
            Long actorMemberId,
            Collection<Long> additionalRecipientMemberIds
    ) {
        if (order == null || order.getId() == null || audience == null || audience == OrderNotificationAudience.NONE) {
            return List.of();
        }

        Map<Long, RecipientTarget> targets = new LinkedHashMap<>();

        if (audience == OrderNotificationAudience.MANAGED_BY_ONLY) {
            Member managedBy = order.getTask() != null ? order.getTask().getManagedBy() : null;
            if (managedBy == null || managedBy.getId() == null || !managedBy.isEnabled()) {
                throw new IllegalStateException(
                        "해당 발주의 우리회사 관리 담당자(Task.managedBy)가 지정되어 있지 않아 관리자요청을 보낼 수 없습니다."
                );
            }
            addTarget(targets, managedBy, OrderNotificationCategory.EMERGENCY, actorMemberId);
            return List.copyOf(targets.values());
        }

        EnumSet<OrderWorkArea> areas = normalizeAreas(affectedAreas);

        /*
         * 알림 수신 범위와 생산팀의 개인별 재확인 버전은 분리합니다.
         * affectedAreas는 체크 버전 증가 범위이고, 알림은 사용자가 요구한 대로
         * 해당 오더의 실제 관리/생산/출고/배송 관련자 전체에게 전달합니다.
         */
        Member managedBy = order.getTask() != null ? order.getTask().getManagedBy() : null;
        addTarget(
                targets,
                managedBy,
                categoryForTarget(sourceArea, categoryForSource(sourceArea, areas)),
                actorMemberId
        );

        OrderNotificationCategory productionCategory = categoryForTarget(
                sourceArea,
                OrderNotificationCategory.PRODUCTION
        );
        addTarget(targets, order.getAssignedProductionHandler(), productionCategory, actorMemberId);
        addProductionTeamTargets(targets, order, productionCategory, actorMemberId);

        OrderNotificationCategory deliveryCategory = categoryForTarget(
                sourceArea,
                OrderNotificationCategory.DELIVERY
        );
        addTarget(targets, order.getAssignedDeliveryHandler(), deliveryCategory, actorMemberId);
        addDeliveryTeamTargets(targets, order, deliveryCategory, actorMemberId);

        addMembers(
                targets,
                memberRepository.findByTeam_NameAndEnabledTrueOrderByNameAscUsernameAsc(DISPATCH_TEAM),
                categoryForTarget(sourceArea, OrderNotificationCategory.DISPATCH),
                actorMemberId
        );

        addAdditionalTargets(
                targets,
                additionalRecipientMemberIds,
                sourceArea,
                areas,
                actorMemberId
        );

        return List.copyOf(targets.values());
    }

    private void addAdditionalTargets(
            Map<Long, RecipientTarget> targets,
            Collection<Long> memberIds,
            OrderChangeSourceArea sourceArea,
            EnumSet<OrderWorkArea> affectedAreas,
            Long actorMemberId
    ) {
        if (memberIds == null || memberIds.isEmpty()) return;

        LinkedHashSet<Long> normalizedIds = memberIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (normalizedIds.isEmpty()) return;

        for (Member member : memberRepository.findAllById(normalizedIds)) {
            addTarget(
                    targets,
                    member,
                    categoryForAdditionalMember(sourceArea, affectedAreas, member),
                    actorMemberId
            );
        }
    }

    private OrderNotificationCategory categoryForAdditionalMember(
            OrderChangeSourceArea sourceArea,
            EnumSet<OrderWorkArea> affectedAreas,
            Member member
    ) {
        OrderNotificationCategory sourceCategory = categoryForTarget(
                sourceArea,
                categoryForSource(sourceArea, affectedAreas)
        );
        if (sourceArea == OrderChangeSourceArea.PRODUCTION
                || sourceArea == OrderChangeSourceArea.DELIVERY
                || sourceArea == OrderChangeSourceArea.DISPATCH) {
            return sourceCategory;
        }

        String teamName = member != null && member.getTeam() != null
                ? member.getTeam().getName()
                : null;
        if (PRODUCTION_TEAM.equals(teamName)) return OrderNotificationCategory.PRODUCTION;
        if (DELIVERY_TEAM.equals(teamName)) return OrderNotificationCategory.DELIVERY;
        if (DISPATCH_TEAM.equals(teamName)) return OrderNotificationCategory.DISPATCH;
        return sourceCategory;
    }

    private void addProductionTeamTargets(
            Map<Long, RecipientTarget> targets,
            Order order,
            OrderNotificationCategory category,
            Long actorMemberId
    ) {
        Long categoryId = order.getAssignedProductionTeam() != null
                ? order.getAssignedProductionTeam().getId()
                : order.getProductCategory() != null ? order.getProductCategory().getId() : null;

        List<Member> members = categoryId != null
                ? memberRepository.findByTeam_NameAndTeamCategory_IdAndEnabledTrueOrderByNameAscIdAsc(
                        PRODUCTION_TEAM,
                        categoryId
                )
                : memberRepository.findByTeam_NameAndEnabledTrueOrderByNameAscUsernameAsc(PRODUCTION_TEAM);

        addMembers(targets, members, category, actorMemberId);
    }

    private void addDeliveryTeamTargets(
            Map<Long, RecipientTarget> targets,
            Order order,
            OrderNotificationCategory category,
            Long actorMemberId
    ) {
        Long categoryId = order.getAssignedDeliveryTeam() != null
                ? order.getAssignedDeliveryTeam().getId()
                : null;

        List<Member> members;
        if (categoryId != null) {
            members = memberRepository.findByTeam_NameAndTeamCategory_IdAndEnabledTrueOrderByNameAscIdAsc(
                    DELIVERY_TEAM,
                    categoryId
            );
        } else if (order.getAssignedDeliveryHandler() != null) {
            members = List.of(order.getAssignedDeliveryHandler());
        } else {
            // 담당자가 아직 배정되지 않은 배송업무는 배송팀 공용 큐에 노출되므로 배송팀 전체가 관련자입니다.
            members = memberRepository.findByTeam_NameAndEnabledTrueOrderByNameAscUsernameAsc(DELIVERY_TEAM);
        }

        addMembers(targets, members, category, actorMemberId);
    }

    private void addMembers(
            Map<Long, RecipientTarget> targets,
            Collection<Member> members,
            OrderNotificationCategory category,
            Long actorMemberId
    ) {
        if (members == null) return;
        for (Member member : members) {
            addTarget(targets, member, category, actorMemberId);
        }
    }

    private void addTarget(
            Map<Long, RecipientTarget> targets,
            Member member,
            OrderNotificationCategory category,
            Long actorMemberId
    ) {
        if (member == null || member.getId() == null || !member.isEnabled()) return;
        if (!properties.isNotifyActor() && Objects.equals(member.getId(), actorMemberId)) return;

        targets.putIfAbsent(member.getId(), new RecipientTarget(member, category));
    }

    private EnumSet<OrderWorkArea> normalizeAreas(Set<OrderWorkArea> affectedAreas) {
        if (affectedAreas == null || affectedAreas.isEmpty()) {
            return EnumSet.noneOf(OrderWorkArea.class);
        }
        return EnumSet.copyOf(affectedAreas);
    }


    /**
     * 생산/배송/출고팀이 발생시킨 작업은 모든 수신자의 동일한 원천 팀 탭에 표시합니다.
     * 관리/시스템 작업은 별도 관리 탭이 없으므로 각 수신자의 영향 업무 탭으로 분류합니다.
     */
    private OrderNotificationCategory categoryForTarget(
            OrderChangeSourceArea sourceArea,
            OrderNotificationCategory fallback
    ) {
        if (sourceArea == OrderChangeSourceArea.PRODUCTION) return OrderNotificationCategory.PRODUCTION;
        if (sourceArea == OrderChangeSourceArea.DELIVERY) return OrderNotificationCategory.DELIVERY;
        if (sourceArea == OrderChangeSourceArea.DISPATCH) return OrderNotificationCategory.DISPATCH;
        return fallback;
    }

    private OrderNotificationCategory categoryForSource(
            OrderChangeSourceArea sourceArea,
            EnumSet<OrderWorkArea> areas
    ) {
        if (sourceArea == OrderChangeSourceArea.PRODUCTION) return OrderNotificationCategory.PRODUCTION;
        if (sourceArea == OrderChangeSourceArea.DELIVERY) return OrderNotificationCategory.DELIVERY;
        if (sourceArea == OrderChangeSourceArea.DISPATCH) return OrderNotificationCategory.DISPATCH;
        if (areas.contains(OrderWorkArea.PRODUCTION)) return OrderNotificationCategory.PRODUCTION;
        if (areas.contains(OrderWorkArea.DELIVERY)) return OrderNotificationCategory.DELIVERY;
        return OrderNotificationCategory.DISPATCH;
    }

    public record RecipientTarget(Member member, OrderNotificationCategory category) {
    }
}
