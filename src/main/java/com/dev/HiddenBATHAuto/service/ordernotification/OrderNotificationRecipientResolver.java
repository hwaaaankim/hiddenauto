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
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeEvent;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeField;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeImpact;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.service.order.DeliveryMethodAssignmentPolicy;

import lombok.RequiredArgsConstructor;

/**
 * 발주 변경 종류별 실제 영향 대상자를 산정합니다.
 *
 * <p>
 * 단순히 모든 팀에 전파하지 않고 주문→생산→출고→배송 흐름에서 다음 행동이 필요한 사용자에게만 알림을 생성합니다. 취소/비노출/재개/단계
 * 되돌림은 일반 수정과 분리하여 모든 관련 팀의 작업 중지 또는 재확인을 유도합니다.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class OrderNotificationRecipientResolver {

	private static final String PRODUCTION_TEAM = "생산팀";
	private static final String DELIVERY_TEAM = "배송팀";
	private static final String DISPATCH_TEAM = "출고팀";

	private static final Long FIXED_ADMIN_MEMBER_ID = 1L;
	private static final String FIXED_ADMIN_USERNAME = "admin";

	private final MemberRepository memberRepository;
	private final OrderNotificationProperties properties;

	@Transactional(readOnly = true)
	public List<RecipientTarget> resolve(OrderChangeEvent event, Set<OrderWorkArea> affectedAreas,
			OrderNotificationAudience audience, Collection<Long> additionalRecipientMemberIds) {
		if (event == null || event.getOrder() == null || event.getOrder().getId() == null || audience == null
				|| audience == OrderNotificationAudience.NONE) {
			return List.of();
		}

		Order order = event.getOrder();
		Long actorMemberId = event.getActorMemberId();
		Map<Long, RecipientTarget> targets = new LinkedHashMap<>();

		if (audience == OrderNotificationAudience.MANAGED_BY_ONLY) {
			addManagedBy(targets, event, actorMemberId, "긴급 관리자요청 · 발주 #" + order.getId(), emergencyMessage(event));
			return List.copyOf(targets.values());
		}

		EnumSet<OrderWorkArea> areas = normalizeAreas(affectedAreas);
		StatusTransition transition = resolveStatusTransition(event);
		String operationCode = safeUpper(event.getOperationCode());

		/*
		 * 팀 화면에서 사라지거나 다시 나타나는 상태 전환은 일반 수정 정책보다 우선합니다. 취소/비노출 시 모든 관련 팀이 즉시 작업을 멈춰야
		 * 하고, 복구/단계 역행 시 최신 내용을 다시 확인해야 합니다.
		 */
		if (transition.isVisibleToHidden()) {
			String title = transition.after() == OrderStatus.CANCELED ? "발주 취소 · 작업 중지" : "발주 비노출 전환 · 작업 중지";
			String message = buildHiddenTransitionMessage(event, transition);
			addManagedBy(targets, event, actorMemberId, title, message);
			addProductionRelated(targets, event, actorMemberId, title, message);
			addDeliveryRelatedForVisibilityTransition(targets, event, actorMemberId, title, message,
					additionalRecipientMemberIds);
			addDispatchTeam(targets, event, actorMemberId, title, message);
			/* 같은 수정에서 생산 분류까지 바뀐 경우, 변경 전 실제 작업자도 중지 사실을 받아야 합니다. */
			addPreviousProductionCategoryMembers(targets, event, actorMemberId, title, message);
			return List.copyOf(targets.values());
		}

		if (transition.isHiddenToVisible()) {
			String title = transition.before() == OrderStatus.REQUESTED ? "발주 승인 · 업무 시작" : "발주 업무 재개 · 재확인";
			String message = buildResumeMessage(event, transition);
			addAllWorkflowParticipants(targets, event, actorMemberId, title, message);
			return List.copyOf(targets.values());
		}

		if (transition.isBackwardVisibleStep()) {
			String title = "발주 단계 되돌림 · 재확인";
			String message = buildRollbackMessage(event, transition);
			addAllWorkflowParticipants(targets, event, actorMemberId, title, message);
			/* 되돌림과 동시에 담당 범위가 변경되면 변경 전 작업자도 기존 후속 작업을 멈춰야 합니다. */
			addPreviousProductionCategoryMembers(targets, event, actorMemberId, title, message);
			addAdditionalTargets(targets, additionalRecipientMemberIds, event, actorMemberId, title, message);
			return List.copyOf(targets.values());
		}

		/* REQUESTED↔CANCELED처럼 양쪽 모두 비노출인 전환은 팀이 아직 보지 않은 업무이므로 감사이력만 남깁니다. */
		if (transition.isHiddenToHidden()) {
			return List.of();
		}

		/* 승인 전/취소 상태의 단순 편집은 팀 업무에 아직 노출되지 않으므로 팀 알림을 만들지 않습니다. */
		if (isHidden(order.getStatus()) && !transition.hasChange()) {
			return List.of();
		}

		if (isRegistration(operationCode)) {
			addManagedBy(targets, event, actorMemberId, null, null);
			addProductionRelated(targets, event, actorMemberId, null, null);
			addDeliveryHandlerOnly(targets, event, actorMemberId, null, null);
			addDispatchTeam(targets, event, actorMemberId, null, null);
		} else if (isProductionComplete(operationCode)) {
			addManagedBy(targets, event, actorMemberId, null, null);
			addDeliveryHandlerOnly(targets, event, actorMemberId, null, null);
			addDispatchTeam(targets, event, actorMemberId, null, null);
		} else if (isDeliveryComplete(operationCode)) {
			addManagedBy(targets, event, actorMemberId, null, null);
			addDispatchTeam(targets, event, actorMemberId, null, null);
		} else if (isDispatchComplete(operationCode)) {
			addManagedBy(targets, event, actorMemberId, null, null);
		} else if (isDeliveryHandlerChange(operationCode)) {
			addManagedBy(targets, event, actorMemberId, null, null);
			addCurrentAndPreviousDeliveryHandlers(targets, event, actorMemberId, additionalRecipientMemberIds);
			return List.copyOf(targets.values());
		} else if (isDispatchDeliveryMethodChange(operationCode)) {
			resolveDispatchDeliveryMethodTargets(targets, event, actorMemberId);
			return List.copyOf(targets.values());
		} else if (event.getSourceArea() == OrderChangeSourceArea.MANAGEMENT) {
			/* 다른 관리자가 수정한 경우 Task 관리 담당자도 변경 사실을 받아 인수인계를 놓치지 않게 합니다. */
			addManagedBy(targets, event, actorMemberId, null, null);
			/* 관리자 수정은 해당 생산 분류, 현재 배송 담당자, 출고팀에 영향을 줍니다. */
			addProductionRelated(targets, event, actorMemberId, null, null);
			if (hasChangedField(event, "productCategory")) {
				addPreviousProductionCategoryMembers(targets, event, actorMemberId);
			}
			if (hasChangedField(event, "assignedDeliveryHandler")) {
				addCurrentAndPreviousDeliveryHandlers(targets, event, actorMemberId, additionalRecipientMemberIds);
			} else {
				addDeliveryHandlerOnly(targets, event, actorMemberId, null, null);
			}
			addDispatchTeam(targets, event, actorMemberId, null, null);
		} else if (event.getSourceArea() == OrderChangeSourceArea.PRODUCTION) {
			addManagedBy(targets, event, actorMemberId, null, null);
			addDeliveryHandlerOnly(targets, event, actorMemberId, null, null);
			addDispatchTeam(targets, event, actorMemberId, null, null);
		} else if (event.getSourceArea() == OrderChangeSourceArea.DELIVERY) {
			addManagedBy(targets, event, actorMemberId, null, null);
			addDispatchTeam(targets, event, actorMemberId, null, null);
		} else if (event.getSourceArea() == OrderChangeSourceArea.DISPATCH) {
			addManagedBy(targets, event, actorMemberId, null, null);
			addDeliveryHandlerOnly(targets, event, actorMemberId, null, null);
		} else if (event.getSourceArea() == OrderChangeSourceArea.CUSTOMER) {
			addManagedBy(targets, event, actorMemberId, null, null);
			if (isVisible(order.getStatus())) {
				addProductionRelated(targets, event, actorMemberId, null, null);
				addDeliveryHandlerOnly(targets, event, actorMemberId, null, null);
				addDispatchTeam(targets, event, actorMemberId, null, null);
			}
		} else {
			addManagedBy(targets, event, actorMemberId, null, null);
			if (areas.contains(OrderWorkArea.PRODUCTION)) {
				addProductionRelated(targets, event, actorMemberId, null, null);
			}
			if (areas.contains(OrderWorkArea.DELIVERY)) {
				addDeliveryHandlerOnly(targets, event, actorMemberId, null, null);
			}
			if (areas.contains(OrderWorkArea.DISPATCH)) {
				addDispatchTeam(targets, event, actorMemberId, null, null);
			}
		}

		addAdditionalTargets(targets, additionalRecipientMemberIds, event, actorMemberId, null, null);
		return List.copyOf(targets.values());
	}

	private void resolveDispatchDeliveryMethodTargets(Map<Long, RecipientTarget> targets, OrderChangeEvent event,
			Long actorMemberId) {
		Order order = event.getOrder();
		String methodName = order.getDeliveryMethod() != null ? safe(order.getDeliveryMethod().getMethodName()) : "미지정";

		if (!DeliveryMethodAssignmentPolicy.requiresHandler(order.getDeliveryMethod())) {
			addManagedBy(targets, event, actorMemberId, "배송수단 변경 · 관리자 확인",
					"발주 #" + order.getId() + "의 배송수단이 '" + methodName + "'(으)로 변경되어 배송 담당자 배정이 해제되거나 필요하지 않습니다.");
			return;
		}

		if (order.getAssignedDeliveryHandler() != null) {
			addTarget(targets, order.getAssignedDeliveryHandler(),
					categoryForMember(order.getAssignedDeliveryHandler(), event), actorMemberId,
					"배송업무 배정 · 발주 #" + order.getId(), "배송수단이 '" + methodName + "'(으)로 변경되어 해당 발주의 배송 담당자로 지정되었습니다.");
			return;
		}

		addManagedBy(targets, event, actorMemberId, "배송 담당자 배정 필요",
				"발주 #" + order.getId() + "의 배송수단이 '" + methodName + "'(으)로 변경되었지만 배송 담당자가 지정되지 않았습니다. 담당자를 배정해 주세요.");
	}

	private void addCurrentAndPreviousDeliveryHandlers(Map<Long, RecipientTarget> targets, OrderChangeEvent event,
			Long actorMemberId, Collection<Long> previousIds) {
		Order order = event.getOrder();
		Member current = order.getAssignedDeliveryHandler();

		if (current != null) {
			addTarget(targets, current, categoryForMember(current, event), actorMemberId,
					"배송 담당자 지정 · 발주 #" + order.getId(),
					"발주 #" + order.getId() + "의 새 배송 담당자로 지정되었습니다. 배송일과 배송수단을 확인해 주세요.");
		}

		LinkedHashSet<Long> normalized = normalizeMemberIds(previousIds);
		if (!normalized.isEmpty()) {
			for (Member previous : memberRepository.findAllById(normalized)) {
				if (current != null && Objects.equals(current.getId(), previous.getId()))
					continue;
				addTarget(targets, previous, categoryForMember(previous, event), actorMemberId,
						"배송 담당 해제 · 발주 #" + order.getId(),
						"발주 #" + order.getId() + "의 배송 담당자가 변경되어 기존 담당 업무에서 해제되었습니다.");
			}
		}
	}

	private void addAllWorkflowParticipants(Map<Long, RecipientTarget> targets, OrderChangeEvent event,
			Long actorMemberId, String title, String message) {
		addManagedBy(targets, event, actorMemberId, title, message);
		addProductionRelated(targets, event, actorMemberId, title, message);
		addDeliveryRelatedForVisibilityTransition(targets, event, actorMemberId, title, message);
		addDispatchTeam(targets, event, actorMemberId, title, message);
	}

	/**
	 * 관리자 대상 알림의 공통 수신자를 추가합니다.
	 *
	 * <p>
	 * 고정 관리자(id=1, username=admin)는 작업자 본인 여부와 관계없이 항상 포함합니다. Task.managedBy가 활성
	 * 사용자로 지정되어 있고 고정 관리자와 다른 사용자라면 해당 관리 담당자도 추가로 포함합니다.
	 * </p>
	 */
	private void addManagedBy(Map<Long, RecipientTarget> targets, OrderChangeEvent event, Long actorMemberId,
			String title, String message) {
		Member fixedAdmin = requireFixedAdmin();

		/*
		 * 고정 admin은 app.order-notification.notify-actor=false인 경우에도 반드시 수신해야 하므로
		 * forceNotifyActor=true로 추가합니다.
		 */
		addTarget(targets, fixedAdmin, categoryForMember(fixedAdmin, event), actorMemberId, title, message, true);

		Member managedBy = event.getOrder().getTask() != null ? event.getOrder().getTask().getManagedBy() : null;

		/*
		 * Task 담당자가 없으면 admin에게만 전달합니다. Task 담당자가 admin과 동일하면 중복 알림을 생성하지 않습니다.
		 */
		if (managedBy == null || Objects.equals(fixedAdmin.getId(), managedBy.getId())) {
			return;
		}

		addTarget(targets, managedBy, categoryForMember(managedBy, event), actorMemberId, title, message);
	}

	/**
	 * 시스템의 고정 관리자 계정을 조회하고 DB 설정을 검증합니다.
	 */
	private Member requireFixedAdmin() {
		Member fixedAdmin = memberRepository.findById(FIXED_ADMIN_MEMBER_ID).orElseThrow(
				() -> new IllegalStateException("고정 관리자 계정을 찾을 수 없습니다. " + "Member.id=1, username=admin 계정을 확인해 주세요."));

		if (!FIXED_ADMIN_USERNAME.equals(fixedAdmin.getUsername())) {
			throw new IllegalStateException(
					"Member.id=1 계정의 username이 admin이 아닙니다. 현재 username=" + safe(fixedAdmin.getUsername()));
		}

		if (!fixedAdmin.isEnabled()) {
			throw new IllegalStateException("고정 관리자 계정(id=1, username=admin)이 비활성화되어 있습니다.");
		}

		return fixedAdmin;
	}

	private void addProductionRelated(Map<Long, RecipientTarget> targets, OrderChangeEvent event, Long actorMemberId,
			String title, String message) {
		Order order = event.getOrder();
		addTarget(targets, order.getAssignedProductionHandler(), OrderNotificationCategory.PRODUCTION, actorMemberId,
				title, message);

		Long categoryId = order.getAssignedProductionTeam() != null ? order.getAssignedProductionTeam().getId()
				: order.getProductCategory() != null ? order.getProductCategory().getId() : null;

		if (categoryId == null)
			return;

		List<Member> members = memberRepository
				.findByTeam_NameAndTeamCategory_IdAndEnabledTrueOrderByNameAscIdAsc(PRODUCTION_TEAM, categoryId);

		addMembers(targets, members, event, actorMemberId, title, message);
	}

	private void addPreviousProductionCategoryMembers(Map<Long, RecipientTarget> targets, OrderChangeEvent event,
			Long actorMemberId) {
		addPreviousProductionCategoryMembers(targets, event, actorMemberId,
				"생산업무 담당 해제 · 발주 #" + event.getOrder().getId(),
				"관리자가 생산 카테고리를 변경하여 해당 발주가 기존 생산 업무에서 제외되었습니다. " + "이미 진행한 작업이 있으면 Task 관리 담당자에게 공유해 주세요.");
	}

	private void addPreviousProductionCategoryMembers(Map<Long, RecipientTarget> targets, OrderChangeEvent event,
			Long actorMemberId, String title, String message) {
		OrderChangeField categoryChange = findChangedField(event, "productCategory");
		Long previousCategoryId = categoryChange != null ? parseEntityId(categoryChange.getBeforeValue()) : null;
		Long currentCategoryId = event.getOrder().getProductCategory() != null
				? event.getOrder().getProductCategory().getId()
				: null;

		if (previousCategoryId == null || Objects.equals(previousCategoryId, currentCategoryId))
			return;

		addMembers(targets, memberRepository.findByTeam_NameAndTeamCategory_IdAndEnabledTrueOrderByNameAscIdAsc(
				PRODUCTION_TEAM, previousCategoryId), event, actorMemberId, title, message);
	}

	private void addDeliveryHandlerOnly(Map<Long, RecipientTarget> targets, OrderChangeEvent event, Long actorMemberId,
			String title, String message) {
		Member handler = event.getOrder().getAssignedDeliveryHandler();
		addTarget(targets, handler, OrderNotificationCategory.DELIVERY, actorMemberId, title, message);
	}

	/**
	 * 취소·비노출·재개·단계 되돌림은 기존에 화면을 보고 있었을 가능성이 있는 배송 업무 관계자에게 반드시 전달합니다. 담당자가 있으면 해당
	 * 담당자만, 담당자가 없으면 배정된 배송 카테고리, 카테고리도 없으면 배송팀 공용 업무로 간주하여 활성 배송팀 구성원에게 전달합니다.
	 */
	private void addDeliveryRelatedForVisibilityTransition(Map<Long, RecipientTarget> targets, OrderChangeEvent event,
			Long actorMemberId, String title, String message) {
		addDeliveryRelatedForVisibilityTransition(targets, event, actorMemberId, title, message, List.of());
	}

	private void addDeliveryRelatedForVisibilityTransition(Map<Long, RecipientTarget> targets, OrderChangeEvent event,
			Long actorMemberId, String title, String message, Collection<Long> previousHandlerIds) {
		Order order = event.getOrder();
		Member handler = order.getAssignedDeliveryHandler();
		if (handler != null) {
			addTarget(targets, handler, OrderNotificationCategory.DELIVERY, actorMemberId, title, message);
			return;
		}

		/* 취소 처리 과정에서 현재 배정이 먼저 제거된 경우 취소 직전 담당자를 우선합니다. */
		LinkedHashSet<Long> previousIds = normalizeMemberIds(previousHandlerIds);
		if (!previousIds.isEmpty()) {
			addMembers(targets, memberRepository.findAllById(previousIds), event, actorMemberId, title, message);
			return;
		}

		Long categoryId = order.getAssignedDeliveryTeam() != null ? order.getAssignedDeliveryTeam().getId() : null;
		List<Member> members = categoryId != null
				? memberRepository.findByTeam_NameAndTeamCategory_IdAndEnabledTrueOrderByNameAscIdAsc(DELIVERY_TEAM,
						categoryId)
				: memberRepository.findByTeam_NameAndEnabledTrueOrderByNameAscUsernameAsc(DELIVERY_TEAM);
		addMembers(targets, members, event, actorMemberId, title, message);
	}

	private void addDispatchTeam(Map<Long, RecipientTarget> targets, OrderChangeEvent event, Long actorMemberId,
			String title, String message) {
		addMembers(targets, memberRepository.findByTeam_NameAndEnabledTrueOrderByNameAscUsernameAsc(DISPATCH_TEAM),
				event, actorMemberId, title, message);
	}

	private void addAdditionalTargets(Map<Long, RecipientTarget> targets, Collection<Long> memberIds,
			OrderChangeEvent event, Long actorMemberId, String title, String message) {
		LinkedHashSet<Long> normalizedIds = normalizeMemberIds(memberIds);
		if (normalizedIds.isEmpty())
			return;

		for (Member member : memberRepository.findAllById(normalizedIds)) {
			addTarget(targets, member, categoryForMember(member, event), actorMemberId, title, message);
		}
	}

	private LinkedHashSet<Long> normalizeMemberIds(Collection<Long> memberIds) {
		LinkedHashSet<Long> result = new LinkedHashSet<>();
		if (memberIds == null)
			return result;
		memberIds.stream().filter(Objects::nonNull).filter(id -> id > 0).forEach(result::add);
		return result;
	}

	private void addMembers(Map<Long, RecipientTarget> targets, Collection<Member> members, OrderChangeEvent event,
			Long actorMemberId, String title, String message) {
		if (members == null)
			return;
		for (Member member : members) {
			addTarget(targets, member, categoryForMember(member, event), actorMemberId, title, message);
		}
	}

	private void addTarget(
	        Map<Long, RecipientTarget> targets,
	        Member member,
	        OrderNotificationCategory category,
	        Long actorMemberId,
	        String title,
	        String message
	) {
	    addTarget(
	            targets,
	            member,
	            category,
	            actorMemberId,
	            title,
	            message,
	            false
	    );
	}

	private void addTarget(
	        Map<Long, RecipientTarget> targets,
	        Member member,
	        OrderNotificationCategory category,
	        Long actorMemberId,
	        String title,
	        String message,
	        boolean forceNotifyActor
	) {
	    if (member == null || member.getId() == null || !member.isEnabled()) {
	        return;
	    }

	    /*
	     * 일반 수신자는 기존 notifyActor 설정을 따릅니다.
	     * 고정 admin은 forceNotifyActor=true로 호출되어 작업자 본인이어도 알림을 받습니다.
	     */
	    if (!forceNotifyActor
	            && !properties.isNotifyActor()
	            && Objects.equals(member.getId(), actorMemberId)) {
	        return;
	    }

	    RecipientTarget target = new RecipientTarget(
	            member,
	            category == null
	                    ? OrderNotificationCategory.DISPATCH
	                    : category,
	            blankToNull(title),
	            blankToNull(message)
	    );

	    /*
	     * 같은 사용자가 admin과 Task.managedBy 양쪽 조건에 걸려도
	     * Member ID 기준으로 한 건만 생성합니다.
	     */
	    targets.putIfAbsent(member.getId(), target);
	}

	private OrderNotificationCategory categoryForMember(Member member, OrderChangeEvent event) {
		if (event != null && safeUpper(event.getOperationCode()).startsWith("ADMIN_REQUEST_")) {
			return OrderNotificationCategory.EMERGENCY;
		}

		/* 생산/배송/출고팀이 발생시킨 작업은 관리자의 해당 원천 팀 탭에 표시합니다. */
		OrderNotificationCategory sourceCategory = categoryForSource(event != null ? event.getSourceArea() : null);
		if (sourceCategory != null)
			return sourceCategory;

		/* 관리/고객/시스템 작업은 실제 수신자의 업무 영역으로 분류합니다. */
		String teamName = member != null && member.getTeam() != null ? safe(member.getTeam().getName()) : "";
		if (PRODUCTION_TEAM.equals(teamName))
			return OrderNotificationCategory.PRODUCTION;
		if (DELIVERY_TEAM.equals(teamName))
			return OrderNotificationCategory.DELIVERY;
		if (DISPATCH_TEAM.equals(teamName))
			return OrderNotificationCategory.DISPATCH;

		/* 관리 담당자에게 전달되는 관리/고객 작업은 영향 업무의 첫 단계 탭에 한 번만 표시합니다. */
		return categoryForImpact(event);
	}

	private OrderNotificationCategory categoryForSource(OrderChangeSourceArea sourceArea) {
		if (sourceArea == OrderChangeSourceArea.PRODUCTION)
			return OrderNotificationCategory.PRODUCTION;
		if (sourceArea == OrderChangeSourceArea.DELIVERY)
			return OrderNotificationCategory.DELIVERY;
		if (sourceArea == OrderChangeSourceArea.DISPATCH)
			return OrderNotificationCategory.DISPATCH;
		return null;
	}

	private OrderNotificationCategory categoryForImpact(OrderChangeEvent event) {
		if (event != null && event.getImpacts() != null) {
			Set<OrderWorkArea> impacted = event.getImpacts().stream().filter(Objects::nonNull)
					.map(OrderChangeImpact::getWorkArea).filter(Objects::nonNull)
					.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
			if (impacted.contains(OrderWorkArea.PRODUCTION))
				return OrderNotificationCategory.PRODUCTION;
			if (impacted.contains(OrderWorkArea.DELIVERY))
				return OrderNotificationCategory.DELIVERY;
			if (impacted.contains(OrderWorkArea.DISPATCH))
				return OrderNotificationCategory.DISPATCH;
		}
		return OrderNotificationCategory.DISPATCH;
	}

	private EnumSet<OrderWorkArea> normalizeAreas(Set<OrderWorkArea> affectedAreas) {
		if (affectedAreas == null || affectedAreas.isEmpty()) {
			return EnumSet.noneOf(OrderWorkArea.class);
		}
		return EnumSet.copyOf(affectedAreas);
	}

	private OrderChangeField findChangedField(OrderChangeEvent event, String fieldKey) {
		if (event == null || event.getFields() == null || fieldKey == null)
			return null;
		return event.getFields().stream().filter(Objects::nonNull)
				.filter(field -> fieldKey.equalsIgnoreCase(safe(field.getFieldKey()))).findFirst().orElse(null);
	}

	private Long parseEntityId(String value) {
		String normalized = safe(value);
		if (normalized.isBlank() || "-".equals(normalized))
			return null;
		String firstToken = normalized.split("\\s*/\\s*", 2)[0].trim();
		try {
			long id = Long.parseLong(firstToken);
			return id > 0 ? id : null;
		} catch (NumberFormatException ignore) {
			return null;
		}
	}

	private boolean hasChangedField(OrderChangeEvent event, String fieldKey) {
		if (event == null || event.getFields() == null || fieldKey == null)
			return false;
		return event.getFields().stream().filter(Objects::nonNull)
				.anyMatch(field -> fieldKey.equalsIgnoreCase(safe(field.getFieldKey())));
	}

	private StatusTransition resolveStatusTransition(OrderChangeEvent event) {
		if (event == null || event.getFields() == null)
			return StatusTransition.none();
		for (OrderChangeField field : event.getFields()) {
			if (field == null)
				continue;
			if ("status".equalsIgnoreCase(safe(field.getFieldKey())) || "오더 상태".equals(safe(field.getFieldLabel()))
					|| "발주상태".equals(safe(field.getFieldLabel()))) {
				return new StatusTransition(parseStatus(field.getBeforeValue()), parseStatus(field.getAfterValue()));
			}
		}
		return StatusTransition.none();
	}

	private OrderStatus parseStatus(String value) {
		String normalized = safe(value);
		if (normalized.isBlank() || "-".equals(normalized))
			return null;
		for (OrderStatus status : OrderStatus.values()) {
			if (status.name().equalsIgnoreCase(normalized) || status.getLabel().equals(normalized)) {
				return status;
			}
		}
		return null;
	}

	private boolean isHidden(OrderStatus status) {
		return status == OrderStatus.REQUESTED || status == OrderStatus.CANCELED;
	}

	private boolean isVisible(OrderStatus status) {
		return status != null && !isHidden(status);
	}

	private boolean isRegistration(String code) {
		return code.contains("ORDER_CREATED") || code.contains("ORDER_REGISTER") || code.contains("REGISTRATION");
	}

	private boolean isProductionComplete(String code) {
		return code.startsWith("PRODUCTION_") && code.contains("COMPLETE");
	}

	private boolean isDeliveryComplete(String code) {
		return code.startsWith("DELIVERY_") && code.contains("COMPLETE");
	}

	private boolean isDispatchComplete(String code) {
		return code.startsWith("DISPATCH_") && code.contains("COMPLETE") && !code.contains("DELIVERY_METHOD");
	}

	private boolean isDeliveryHandlerChange(String code) {
		return code.contains("HANDLER") && code.contains("CHANGE");
	}

	private boolean isDispatchDeliveryMethodChange(String code) {
		return code.startsWith("DISPATCH_") && code.contains("DELIVERY_METHOD") && code.contains("CHANGE");
	}

	private String emergencyMessage(OrderChangeEvent event) {
		String actor = actorName(event);
		return actor + "님이 발주 #" + event.getOrder().getId() + "에 대해 긴급 관리자 확인을 요청했습니다. " + safe(event.getSummary());
	}

	private String buildHiddenTransitionMessage(OrderChangeEvent event, StatusTransition transition) {
		String actor = actorName(event);
		if (transition.after() == OrderStatus.CANCELED) {
			return actor + "님이 발주 #" + event.getOrder().getId()
					+ "을(를) 취소했습니다. 생산·출고·배송 작업을 즉시 중지하고 이미 진행한 내용이 있으면 관리 담당자에게 공유해 주세요.";
		}
		return actor + "님이 발주 #" + event.getOrder().getId()
				+ "을(를) 승인 전 상태로 변경했습니다. 팀 화면에서 제외되는 건이므로 진행 중인 생산·출고·배송 작업을 중지해 주세요.";
	}

	private String buildResumeMessage(OrderChangeEvent event, StatusTransition transition) {
		if (transition.before() == OrderStatus.REQUESTED) {
			return actorName(event) + "님이 발주 #" + event.getOrder().getId() + "을(를) 승인하여 "
					+ statusLabel(transition.after()) + " 상태로 업무 대상에 포함했습니다. 최신 발주 내용을 확인하고 작업을 시작해 주세요.";
		}
		return actorName(event) + "님이 발주 #" + event.getOrder().getId() + "의 상태를 " + statusLabel(transition.before())
				+ "에서 " + statusLabel(transition.after()) + "(으)로 복구해 업무 대상에 다시 포함했습니다. 최신 수정사항을 확인한 뒤 작업을 재개해 주세요.";
	}

	private String buildRollbackMessage(OrderChangeEvent event, StatusTransition transition) {
		return actorName(event) + "님이 발주 #" + event.getOrder().getId() + "의 상태를 " + statusLabel(transition.before())
				+ "에서 " + statusLabel(transition.after()) + "(으)로 되돌렸습니다. 이후 단계 작업을 중지하고 최신 발주 내용을 다시 확인해 주세요.";
	}

	private String statusLabel(OrderStatus status) {
		return status == null ? "미확인" : status.getLabel();
	}

	private String actorName(OrderChangeEvent event) {
		if (event == null)
			return "시스템";
		if (!safe(event.getActorDisplayName()).isBlank())
			return safe(event.getActorDisplayName());
		if (!safe(event.getActorUsername()).isBlank())
			return safe(event.getActorUsername());
		return "시스템";
	}

	private String safeUpper(String value) {
		return safe(value).toUpperCase(java.util.Locale.ROOT);
	}

	private String safe(String value) {
		return value == null ? "" : value.trim();
	}

	private String blankToNull(String value) {
		String normalized = safe(value);
		return normalized.isBlank() ? null : normalized;
	}

	private record StatusTransition(OrderStatus before, OrderStatus after) {
		static StatusTransition none() {
			return new StatusTransition(null, null);
		}

		boolean hasChange() {
			return before != null && after != null && before != after;
		}

		boolean isVisibleToHidden() {
			return hasChange() && !isHiddenStatic(before) && isHiddenStatic(after);
		}

		boolean isHiddenToVisible() {
			return hasChange() && isHiddenStatic(before) && !isHiddenStatic(after);
		}

		boolean isHiddenToHidden() {
			return hasChange() && isHiddenStatic(before) && isHiddenStatic(after);
		}

		boolean isBackwardVisibleStep() {
			return hasChange() && !isHiddenStatic(before) && !isHiddenStatic(after)
					&& rankStatic(after) < rankStatic(before);
		}

		private static boolean isHiddenStatic(OrderStatus status) {
			return status == OrderStatus.REQUESTED || status == OrderStatus.CANCELED;
		}

		private static int rankStatic(OrderStatus status) {
			if (status == null || status == OrderStatus.CANCELED)
				return -1;
			return switch (status) {
			case REQUESTED -> 0;
			case CONFIRMED -> 1;
			case PRODUCTION_DONE -> 2;
			case DISPATCH_DONE -> 3;
			case DELIVERY_DONE -> 4;
			case CANCELED -> -1;
			};
		}
	}

	public record RecipientTarget(Member member, OrderNotificationCategory category, String title, String message) {
	}
}
