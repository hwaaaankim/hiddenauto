package com.dev.HiddenBATHAuto.service.team.delivery;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.delivery.DeliveryStatementLayoutDtos.LayoutResponse;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.order.DeliveryRouteQueryRepository;
import com.dev.HiddenBATHAuto.service.order.DeliveryMethodAssignmentPolicy;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryStatementLayoutService.TeamStatementOrderRef;

import lombok.RequiredArgsConstructor;

/**
 * 배송팀 팀장 계정에서 선택 날짜의 모든 배송직원 현장명세서를 생성합니다.
 *
 * 대상 배송수단:
 * - 현장배송
 * - 화물
 *
 * 출력 순서:
 * - 배송팀 활성 멤버의 member.id 오름차순
 * - 같은 멤버 안에서는 DeliveryOrderIndex.orderIndex 오름차순
 *
 * 묶음 기준:
 * - 동일 배송직원
 * - 동일 업체
 * - 동일 실제 배송지
 * - 동일 배송수단
 * - 동일 배송일
 */
@Service
@RequiredArgsConstructor
public class DeliveryTeamSiteStatementService {

    private static final String DELIVERY_TEAM_NAME = "배송팀";
    private static final String TEAM_STATEMENT_LEADER_USERNAME = "deli001";

    private static final List<OrderStatus> VISIBLE_STATUSES = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PRODUCTION_DONE,
            OrderStatus.DISPATCH_DONE,
            OrderStatus.DELIVERY_DONE
    );

    private final MemberRepository memberRepository;
    private final DeliveryRouteQueryRepository deliveryRouteQueryRepository;
    private final DeliveryStatementLayoutService deliveryStatementLayoutService;

    @Transactional(readOnly = true)
    public boolean isTeamStatementLeader(Member member) {
        return member != null
                && member.isEnabled()
                && TEAM_STATEMENT_LEADER_USERNAME.equals(normalizeUsername(member.getUsername()))
                && member.getTeam() != null
                && DELIVERY_TEAM_NAME.equals(member.getTeam().getName());
    }

    public String normalizeLayoutType(String layoutType) {
        return deliveryStatementLayoutService.normalizeLayoutType(layoutType);
    }

    @Transactional(readOnly = true)
    public TeamSiteStatementPreview buildPreview(
            Member loginMember,
            LocalDate deliveryDate
    ) {
        validateTeamStatementLeader(loginMember);

        if (deliveryDate == null) {
            throw new IllegalArgumentException("배송팀 현장명세서로 출력할 배송일이 없습니다.");
        }

        TeamStatementData data = loadTeamStatementData(deliveryDate);

        int memberWithOrdersCount = (int) data.members().stream()
                .filter(member -> member.orderCount() > 0)
                .count();
        int totalGroupCount = data.members().stream()
                .mapToInt(MemberStatementPreview::groupCount)
                .sum();
        int totalOrderCount = data.members().stream()
                .mapToInt(MemberStatementPreview::orderCount)
                .sum();

        return new TeamSiteStatementPreview(
                deliveryDate,
                data.members().size(),
                memberWithOrdersCount,
                totalGroupCount,
                totalOrderCount,
                data.members()
        );
    }

    @Transactional(readOnly = true)
    public LayoutResponse buildLayoutResponse(
            Member loginMember,
            LocalDate deliveryDate,
            String layoutType
    ) {
        validateTeamStatementLeader(loginMember);
        validateDeliveryDate(deliveryDate);

        TeamStatementData data = loadTeamStatementData(deliveryDate);
        requireOutputOrders(deliveryDate, data.orderRefs());

        return deliveryStatementLayoutService.buildLayoutResponseForTeamSite(
                deliveryDate,
                layoutType,
                data.orderRefs()
        );
    }

    @Transactional(readOnly = true)
    public byte[] buildLayoutExcel(
            Member loginMember,
            LocalDate deliveryDate,
            String layoutType
    ) {
        validateTeamStatementLeader(loginMember);
        validateDeliveryDate(deliveryDate);

        TeamStatementData data = loadTeamStatementData(deliveryDate);
        requireOutputOrders(deliveryDate, data.orderRefs());

        return deliveryStatementLayoutService.buildLayoutExcelForTeamSite(
                deliveryDate,
                layoutType,
                data.orderRefs()
        );
    }

    private TeamStatementData loadTeamStatementData(LocalDate deliveryDate) {
        List<Member> deliveryMembers = memberRepository
                .findByTeam_NameAndEnabledTrueOrderByNameAsc(DELIVERY_TEAM_NAME)
                .stream()
                .filter(Objects::nonNull)
                .filter(member -> member.getId() != null)
                .sorted(Comparator.comparingLong(Member::getId))
                .toList();

        if (deliveryMembers.isEmpty()) {
            throw new IllegalStateException("활성화된 배송팀 멤버를 찾을 수 없습니다.");
        }

        List<MemberStatementPreview> memberPreviews = new ArrayList<>();
        List<TeamStatementOrderRef> allOrderRefs = new ArrayList<>();

        for (Member deliveryMember : deliveryMembers) {
            List<TeamStatementOrderRef> memberOrderRefs = findMemberStatementOrderRefs(
                    deliveryMember,
                    deliveryDate
            );

            int groupCount = deliveryStatementLayoutService.countTeamSiteGroups(
                    deliveryDate,
                    memberOrderRefs
            );

            memberPreviews.add(new MemberStatementPreview(
                    deliveryMember.getId(),
                    resolveMemberName(deliveryMember),
                    normalizeUsername(deliveryMember.getUsername()),
                    groupCount,
                    memberOrderRefs.size()
            ));
            allOrderRefs.addAll(memberOrderRefs);
        }

        return new TeamStatementData(
                List.copyOf(memberPreviews),
                List.copyOf(allOrderRefs)
        );
    }

    private List<TeamStatementOrderRef> findMemberStatementOrderRefs(
            Member deliveryMember,
            LocalDate deliveryDate
    ) {
        List<DeliveryOrderIndex> rows = deliveryRouteQueryRepository.findRouteRows(
                        deliveryMember.getId(),
                        deliveryDate,
                        VISIBLE_STATUSES
                )
                .stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getOrder() != null && row.getOrder().getId() != null)
                .filter(row -> isDeliveryTeamSiteStatementOrder(row.getOrder()))
                .sorted(Comparator
                        .comparingInt(DeliveryOrderIndex::getOrderIndex)
                        .thenComparingLong(this::safeOrderId))
                .toList();

        LinkedHashSet<Long> seenOrderIds = new LinkedHashSet<>();
        List<TeamStatementOrderRef> result = new ArrayList<>();

        for (DeliveryOrderIndex row : rows) {
            Long orderId = row.getOrder().getId();

            if (!seenOrderIds.add(orderId)) {
                continue;
            }

            result.add(new TeamStatementOrderRef(
                    deliveryMember.getId(),
                    resolveMemberName(deliveryMember),
                    orderId,
                    row.getOrderIndex()
            ));
        }

        return List.copyOf(result);
    }

    private boolean isDeliveryTeamSiteStatementOrder(Order order) {
        if (order == null || order.getDeliveryMethod() == null) {
            return false;
        }

        String methodName = order.getDeliveryMethod().getMethodName();

        return DeliveryMethodAssignmentPolicy.containsKeyword(methodName, "현장배송")
                || DeliveryMethodAssignmentPolicy.containsKeyword(methodName, "화물");
    }

    private void validateDeliveryDate(LocalDate deliveryDate) {
        if (deliveryDate == null) {
            throw new IllegalArgumentException("배송팀 현장명세서로 출력할 배송일이 없습니다.");
        }
    }

    private void requireOutputOrders(
            LocalDate deliveryDate,
            List<TeamStatementOrderRef> orderRefs
    ) {
        if (orderRefs == null || orderRefs.isEmpty()) {
            throw new IllegalArgumentException(
                    deliveryDate + " 배송팀 전체 현장명세서 대상 주문이 없습니다."
            );
        }
    }

    private void validateTeamStatementLeader(Member member) {
        if (member == null) {
            throw new AccessDeniedException("로그인 사용자 정보를 확인할 수 없습니다.");
        }

        if (!isTeamStatementLeader(member)) {
            throw new AccessDeniedException(
                    "배송팀 전체 현장명세서는 배송팀 deli001 계정만 사용할 수 있습니다."
            );
        }
    }

    private String resolveMemberName(Member member) {
        if (member == null) {
            return "-";
        }

        String name = member.getName() == null ? "" : member.getName().trim();
        return !name.isBlank() ? name : normalizeUsername(member.getUsername());
    }

    private String normalizeUsername(String username) {
        return username == null
                ? ""
                : username.trim().toLowerCase(Locale.ROOT);
    }

    private long safeOrderId(DeliveryOrderIndex index) {
        return index == null
                || index.getOrder() == null
                || index.getOrder().getId() == null
                ? Long.MAX_VALUE
                : index.getOrder().getId();
    }

    public record TeamSiteStatementPreview(
            LocalDate deliveryDate,
            int memberCount,
            int memberWithOrdersCount,
            int totalGroupCount,
            int totalOrderCount,
            List<MemberStatementPreview> members
    ) {
        public TeamSiteStatementPreview {
            members = members == null ? List.of() : List.copyOf(members);
        }
    }

    public record MemberStatementPreview(
            Long memberId,
            String memberName,
            String username,
            int groupCount,
            int orderCount
    ) {
    }

    private record TeamStatementData(
            List<MemberStatementPreview> members,
            List<TeamStatementOrderRef> orderRefs
    ) {
    }
}
