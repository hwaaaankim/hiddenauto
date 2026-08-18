package com.dev.HiddenBATHAuto.service.order;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;

import lombok.RequiredArgsConstructor;

/**
 * 팀 화면 조회권한과 변경권한을 한 곳에서 판정합니다.
 *
 * <p>화면의 버튼 숨김만으로 권한을 보장하지 않고 모든 변경 API가 이 정책을 다시 검증하도록 사용합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class OrderTeamAccessPolicyService {

    private static final Long PRODUCTION_TEAM_ID = 2L;
    private static final String PRODUCTION_TEAM = "생산팀";
    private static final String DELIVERY_TEAM = "배송팀";
    private static final String DISPATCH_TEAM = "출고팀";

    /*
     * 생산 실무상 거울과 LED거울은 동일 작업 그룹입니다.
     * DB의 TeamCategory는 분리된 상태를 유지하되 생산 변경 권한만 상호 허용합니다.
     */
    private static final Set<String> MIRROR_EQUIVALENT_CATEGORY_KEYS = Set.of(
            "거울",
            "led거울"
    );

    private static final Set<String> PRODUCTION_REAL_CATEGORY_KEYS = Set.of(
            "슬라이드장",
            "상부장",
            "하부장",
            "플랩장",
            "거울",
            "led거울"
    );

    private static final List<OrderStatus> PRODUCTION_VISIBLE_STATUSES = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PRODUCTION_DONE,
            OrderStatus.DISPATCH_DONE,
            OrderStatus.DELIVERY_DONE
    );

    private static final List<OrderStatus> DISPATCH_VISIBLE_STATUSES = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PRODUCTION_DONE,
            OrderStatus.DISPATCH_DONE
    );

    private static final List<OrderStatus> DELIVERY_VISIBLE_STATUSES = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PRODUCTION_DONE,
            OrderStatus.DISPATCH_DONE,
            OrderStatus.DELIVERY_DONE
    );

    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;

    /** 생산팀은 허용 상태의 모든 카테고리를 조회하고 개인별 확인 처리할 수 있습니다. */
    public boolean canViewProductionOrder(Member member, Order order) {
        return isTeam(member, PRODUCTION_TEAM)
                && order != null
                && isProductionRealCategory(order.getProductCategory())
                && order.getStatus() != null
                && PRODUCTION_VISIBLE_STATUSES.contains(order.getStatus());
    }

    /**
     * 생산 변경은 재단 계열을 제외하고 동일 TeamCategory에 허용하며, 거울과 LED거울은 동일 작업 그룹으로 봅니다.
     */
    public boolean canOperateProductionOrder(Member member, Order order) {
        if (!canViewProductionOrder(member, order) || isCuttingProductionMember(member)) return false;
        return canOperateProductionCategory(member, order.getProductCategory());
    }

    /**
     * 생산 카테고리 작업 가능 여부를 판정합니다.
     * 기본은 동일 TeamCategory만 허용하고, 거울과 LED거울만 동일 작업 그룹으로 상호 허용합니다.
     */
    public boolean canOperateProductionCategory(Member member, TeamCategory orderCategory) {
        if (!isTeam(member, PRODUCTION_TEAM) || isCuttingProductionMember(member)) return false;
        if (!isProductionRealCategory(orderCategory)) return false;

        TeamCategory memberCategory = member.getTeamCategory();
        if (memberCategory == null || memberCategory.getId() == null) return false;
        if (orderCategory == null || orderCategory.getId() == null) return false;

        if (Objects.equals(memberCategory.getId(), orderCategory.getId())) {
            return true;
        }

        String memberCategoryKey = normalizeCategoryKey(memberCategory.getName());
        String orderCategoryKey = normalizeCategoryKey(orderCategory.getName());

        return MIRROR_EQUIVALENT_CATEGORY_KEYS.contains(memberCategoryKey)
                && MIRROR_EQUIVALENT_CATEGORY_KEYS.contains(orderCategoryKey);
    }

    /** 배송팀은 현재 자신에게 배정된 오더만 조회·조작할 수 있습니다. */
    @Transactional(readOnly = true)
    public boolean canViewDeliveryOrder(Member member, Order order) {
        if (!isTeam(member, DELIVERY_TEAM) || order == null || order.getId() == null) return false;
        if (order.getStatus() == null || !DELIVERY_VISIBLE_STATUSES.contains(order.getStatus())) return false;

        /*
         * Order의 현재 담당자가 있으면 그 관계를 최우선으로 사용합니다.
         * 담당자 변경 직후 정리되지 않은 과거 DeliveryOrderIndex가 남아 있어도 이전 담당자가 다시 조회하면 안 됩니다.
         */
        if (order.getAssignedDeliveryHandler() != null) {
            return Objects.equals(order.getAssignedDeliveryHandler().getId(), member.getId());
        }

        /* 기존 데이터 중 Order 담당자 컬럼 없이 배송 인덱스로만 배정된 건을 위한 호환 경로입니다. */
        return member.getId() != null
                && deliveryOrderIndexRepository.existsByOrder_IdAndDeliveryHandler_Id(order.getId(), member.getId());
    }

    /** 출고팀은 카테고리와 무관하게 출고 허용 상태의 오더를 조회·조작할 수 있습니다. */
    public boolean canViewDispatchOrder(Member member, Order order) {
        return isTeam(member, DISPATCH_TEAM)
                && order != null
                && order.getStatus() != null
                && DISPATCH_VISIBLE_STATUSES.contains(order.getStatus());
    }

    @Transactional(readOnly = true)
    public void assertCanRequestAdmin(Member actor, Order order, OrderChangeSourceArea sourceArea) {
        boolean allowed = switch (sourceArea) {
            case PRODUCTION -> canOperateProductionOrder(actor, order);
            case DELIVERY -> canViewDeliveryOrder(actor, order);
            case DISPATCH -> canViewDispatchOrder(actor, order);
            default -> false;
        };
        if (!allowed) {
            throw new AccessDeniedException("현재 로그인 사용자가 조회·조작할 수 있는 발주만 관리자요청을 보낼 수 있습니다.");
        }
    }

    public void assertCanOperateProduction(Member actor, Order order) {
        if (!canOperateProductionOrder(actor, order)) {
            throw new AccessDeniedException("자신의 생산 카테고리 또는 거울·LED거울 공통 작업 그룹의 발주만 생산완료 처리할 수 있습니다.");
        }
    }

    public boolean isCuttingProductionMember(Member member) {
        if (!isTeam(member, PRODUCTION_TEAM) || member.getTeamCategory() == null) return false;
        String categoryName = normalize(member.getTeamCategory().getName());
        return categoryName != null && categoryName.contains("재단");
    }

    private boolean isProductionRealCategory(TeamCategory category) {
        if (category == null || category.getTeam() == null
                || !Objects.equals(PRODUCTION_TEAM_ID, category.getTeam().getId())) {
            return false;
        }
        String categoryKey = normalizeCategoryKey(category.getName());
        return categoryKey != null && PRODUCTION_REAL_CATEGORY_KEYS.contains(categoryKey);
    }

    private boolean isTeam(Member member, String teamName) {
        return member != null
                && member.getId() != null
                && member.isEnabled()
                && member.getTeam() != null
                && teamName.equals(normalize(member.getTeam().getName()));
    }

    private String normalizeCategoryKey(String value) {
        String normalized = normalize(value);
        if (normalized == null) return null;
        return normalized.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
