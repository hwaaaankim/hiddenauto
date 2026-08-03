package com.dev.HiddenBATHAuto.repository.order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

	/**
	 * 배송담당자 변경이력 기록 전/후 스냅샷용 경량 조회입니다.
	 * 엔티티 캐시 상태와 무관하게 DB의 현재 담당자 값을 스칼라로 조회합니다.
	 */
	@Query("""
			select o.id, handler.id, handler.username, handler.name
			from Order o
			left join o.assignedDeliveryHandler handler
			where o.id in :orderIds
			""")
	List<Object[]> findDeliveryHandlerAuditRows(@Param("orderIds") Collection<Long> orderIds);

	// OrderRepository 인터페이스 내부에 추가
	@EntityGraph(attributePaths = { "deliveryMethod", "assignedDeliveryHandler" })
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
	            SELECT o
	            FROM Order o
	            WHERE o.id IN :orderIds
	            ORDER BY o.id ASC
	        """)
	List<Order> findAllByIdInForBulkConfirm(@Param("orderIds") Collection<Long> orderIds);
	
	@Query("""
			    select distinct o
			    from Order o
			    join fetch o.task t
			    left join fetch o.orderImages oi
			    left join fetch o.orderItem item
			    where o.id in :orderIds
			""")
	List<Order> findAllForBulkDeleteByIds(@Param("orderIds") Collection<Long> orderIds);

	@Query("""
			    select distinct o
			    from Order o
			    join fetch o.task t
			    left join fetch o.orderImages oi
			    left join fetch o.orderItem item
			    where t.id in :taskIds
			""")
	List<Order> findAllForBulkDeleteByTaskIds(@Param("taskIds") Collection<Long> taskIds);

	long countByTask_Id(Long taskId);

	List<Order> findByTask_RequestedByAndPreferredDeliveryDateBetween(Member member, LocalDateTime start,
			LocalDateTime end);

	// =========================
	// 생산팀 목록 (기본 정렬 고정: 상태 우선 + 날짜 ASC + id DESC)
	// 기존 productionFilter(IN_PROGRESS/DONE/ALL) 기반 조회 유지
	// =========================
	@EntityGraph(attributePaths = { "orderItem", "productCategory", "task", "task.requestedBy",
			"task.requestedBy.company" })
	@Query("""
			    SELECT o FROM Order o
			    WHERE o.status IN :statuses
			      AND o.productCategory.id = :categoryId
			      AND (
			            :productionFilter = 'ALL'
			         OR (:productionFilter = 'IN_PROGRESS' AND o.status = 'CONFIRMED')
			         OR (:productionFilter = 'DONE' AND (o.status = 'PRODUCTION_DONE' OR o.status = 'DELIVERY_DONE'))
			      )
			      AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
			      AND (:end IS NULL OR o.preferredDeliveryDate < :end)
			    ORDER BY
			      CASE
			        WHEN o.status = 'CONFIRMED' THEN 0
			        WHEN o.status = 'PRODUCTION_DONE' THEN 1
			        WHEN o.status = 'DELIVERY_DONE' THEN 2
			        ELSE 9
			      END ASC,
			      o.preferredDeliveryDate ASC,
			      o.id DESC
			""")
	Page<Order> findProductionListByPreferredRange(@Param("statuses") List<OrderStatus> statuses,
			@Param("categoryId") Long categoryId, @Param("productionFilter") String productionFilter,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	@EntityGraph(attributePaths = { "orderItem", "productCategory", "task", "task.requestedBy",
			"task.requestedBy.company" })
	@Query("""
			    SELECT o FROM Order o
			    WHERE o.status IN :statuses
			      AND o.productCategory.id = :categoryId
			      AND (
			            :productionFilter = 'ALL'
			         OR (:productionFilter = 'IN_PROGRESS' AND o.status = 'CONFIRMED')
			         OR (:productionFilter = 'DONE' AND (o.status = 'PRODUCTION_DONE' OR o.status = 'DELIVERY_DONE'))
			      )
			      AND (:start IS NULL OR o.createdAt >= :start)
			      AND (:end IS NULL OR o.createdAt < :end)
			    ORDER BY
			      CASE
			        WHEN o.status = 'CONFIRMED' THEN 0
			        WHEN o.status = 'PRODUCTION_DONE' THEN 1
			        WHEN o.status = 'DELIVERY_DONE' THEN 2
			        ELSE 9
			      END ASC,
			      o.createdAt ASC,
			      o.id DESC
			""")
	Page<Order> findProductionListByCreatedRange(@Param("statuses") List<OrderStatus> statuses,
			@Param("categoryId") Long categoryId, @Param("productionFilter") String productionFilter,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	// =========================
	// 생산팀 목록 (정렬 버튼용)
	// Pageable Sort 사용용이므로 ORDER BY 없음
	// =========================
	@EntityGraph(attributePaths = { "orderItem", "productCategory", "task", "task.requestedBy",
			"task.requestedBy.company" })
	@Query("""
			    SELECT o FROM Order o
			    WHERE o.status IN :statuses
			      AND o.productCategory.id = :categoryId
			      AND (
			            :productionFilter = 'ALL'
			         OR (:productionFilter = 'IN_PROGRESS' AND o.status = 'CONFIRMED')
			         OR (:productionFilter = 'DONE' AND (o.status = 'PRODUCTION_DONE' OR o.status = 'DELIVERY_DONE'))
			      )
			      AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
			      AND (:end IS NULL OR o.preferredDeliveryDate < :end)
			""")
	Page<Order> findProductionListByPreferredRangeSortable(@Param("statuses") List<OrderStatus> statuses,
			@Param("categoryId") Long categoryId, @Param("productionFilter") String productionFilter,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	@EntityGraph(attributePaths = { "orderItem", "productCategory", "task", "task.requestedBy",
			"task.requestedBy.company" })
	@Query("""
			    SELECT o FROM Order o
			    WHERE o.status IN :statuses
			      AND o.productCategory.id = :categoryId
			      AND (
			            :productionFilter = 'ALL'
			         OR (:productionFilter = 'IN_PROGRESS' AND o.status = 'CONFIRMED')
			         OR (:productionFilter = 'DONE' AND (o.status = 'PRODUCTION_DONE' OR o.status = 'DELIVERY_DONE'))
			      )
			      AND (:start IS NULL OR o.createdAt >= :start)
			      AND (:end IS NULL OR o.createdAt < :end)
			""")
	Page<Order> findProductionListByCreatedRangeSortable(@Param("statuses") List<OrderStatus> statuses,
			@Param("categoryId") Long categoryId, @Param("productionFilter") String productionFilter,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE o.status IN :statuses
			      AND o.productCategory.id = :categoryId
			      AND (:preferredDate IS NULL OR DATE(o.preferredDeliveryDate) = :preferredDate)
			    ORDER BY o.preferredDeliveryDate ASC
			""")
	Page<Order> findProductionOrders(@Param("statuses") List<OrderStatus> statuses,
			@Param("categoryId") Long categoryId, @Param("preferredDate") LocalDate preferredDate, Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE o.status IN :statuses
			      AND o.assignedDeliveryHandler.id = :memberId
			      AND (:preferredDate IS NULL OR DATE(o.preferredDeliveryDate) = :preferredDate)
			    ORDER BY o.preferredDeliveryDate ASC
			""")
	Page<Order> findDeliveryOrders(List<OrderStatus> statuses, Long memberId, LocalDate preferredDate,
			Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE o.assignedDeliveryHandler.id = :handlerId
			      AND o.status IN :statuses
			      AND o.preferredDeliveryDate BETWEEN :startDate AND :endDate
			""")
	Page<Order> findDeliveryOrdersByHandlerAndStatusAndDateRange(@Param("handlerId") Long handlerId,
			@Param("statuses") List<OrderStatus> statuses, @Param("startDate") LocalDateTime startDate,
			@Param("endDate") LocalDateTime endDate, Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE o.status IN :statuses
			      AND o.productCategory.id = :categoryId
			      AND o.preferredDeliveryDate >= :startOfDay
			      AND o.preferredDeliveryDate < :endOfDay
			    ORDER BY o.preferredDeliveryDate ASC
			""")
	Page<Order> findFilteredOrders(@Param("statuses") List<OrderStatus> statuses, @Param("categoryId") Long categoryId,
			@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay,
			Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE o.status IN :statuses
			      AND o.productCategory.id = :categoryId
			      AND o.preferredDeliveryDate >= :start
			      AND o.preferredDeliveryDate < :end
			    ORDER BY o.preferredDeliveryDate ASC
			""")
	Page<Order> findByPreferredDateRange(@Param("statuses") List<OrderStatus> statuses,
			@Param("categoryId") Long categoryId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE o.status IN :statuses
			      AND o.productCategory.id = :categoryId
			      AND o.createdAt >= :start
			      AND o.createdAt < :end
			    ORDER BY o.createdAt ASC
			""")
	Page<Order> findByCreatedDateRange(@Param("statuses") List<OrderStatus> statuses,
			@Param("categoryId") Long categoryId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE o.status IN :statuses
			      AND o.productCategory.id = :categoryId
			      AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
			      AND (:end IS NULL OR o.preferredDeliveryDate < :end)
			    ORDER BY o.preferredDeliveryDate ASC
			""")
	Page<Order> findByPreferredDateRangeFlexible(@Param("statuses") List<OrderStatus> statuses,
			@Param("categoryId") Long categoryId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE o.status IN :statuses
			      AND o.productCategory.id = :categoryId
			      AND (:start IS NULL OR o.createdAt >= :start)
			      AND (:end IS NULL OR o.createdAt < :end)
			    ORDER BY o.createdAt ASC
			""")
	Page<Order> findByCreatedDateRangeFlexible(@Param("statuses") List<OrderStatus> statuses,
			@Param("categoryId") Long categoryId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE (:category IS NULL OR o.productCategory = :category)
			      AND (:status IS NULL OR o.status = :status)
			      AND o.preferredDeliveryDate BETWEEN :start AND :end
			""")
	Page<Order> findOrdersByConditions(@Param("category") TeamCategory category, @Param("status") OrderStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    JOIN FETCH o.task t
			    JOIN FETCH t.requestedBy m
			    JOIN FETCH m.company c
			    LEFT JOIN FETCH c.salesManager
			    WHERE o.id = :id
			""")
	Optional<Order> findWithFullRelationsById(@Param("id") Long id);

	@Query("""
			    SELECT o FROM Order o
			    WHERE (:category IS NULL OR o.productCategory = :category)
			      AND (:status IS NULL OR o.status = :status)
			      AND o.preferredDeliveryDate BETWEEN :start AND :end
			""")
	List<Order> findAllByConditions(@Param("category") TeamCategory category, @Param("status") OrderStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

	Page<Order> findAllByOrderByPreferredDeliveryDateDesc(Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE
			        (:keyword IS NULL OR
			            LOWER(o.task.requestedBy.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
			            LOWER(o.task.requestedBy.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))
			        AND (:productCategoryId IS NULL OR o.productCategory.id = :productCategoryId)
			        AND (:status IS NULL OR o.status = :status)
			        AND (:deliveryMethodId IS NULL OR o.deliveryMethod.id = :deliveryMethodId)
			        AND (
			            (:dateCriteria = 'order' AND
			                (:startDateTime IS NULL OR o.createdAt >= :startDateTime) AND
			                (:endDateTime IS NULL OR o.createdAt <= :endDateTime))
			            OR (:dateCriteria = 'delivery' AND
			                (:startDateTime IS NULL OR o.preferredDeliveryDate >= :startDateTime) AND
			                (:endDateTime IS NULL OR o.preferredDeliveryDate <= :endDateTime))
			            OR :dateCriteria = 'all'
			        )
			    ORDER BY o.preferredDeliveryDate DESC
			""")
	Page<Order> findFilteredOrders(@Param("keyword") String keyword, @Param("dateCriteria") String dateCriteria,
			@Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime,
			@Param("productCategoryId") Long productCategoryId, @Param("status") OrderStatus status,
			@Param("deliveryMethodId") Long deliveryMethodId, Pageable pageable);

	@EntityGraph(attributePaths = { "task", "task.requestedBy", "task.requestedBy.company", "orderItem",
			"deliveryMethod", "productCategory", "assignedDeliveryHandler", "checkStatus" })
	@Query("""
			SELECT o FROM Order o
			WHERE
			    (:keyword IS NULL OR
			        LOWER(o.task.requestedBy.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
			        LOWER(o.task.requestedBy.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))
			    AND (:productCategoryId IS NULL OR o.productCategory.id = :productCategoryId)
			    AND (:status IS NULL OR o.status = :status)
			    AND (:standard IS NULL OR o.standard = :standard)
			    AND (
			        (:dateCriteria = 'order' AND
			            (:startDateTime IS NULL OR o.createdAt >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.createdAt <= :endDateTime))
			        OR (:dateCriteria = 'delivery' AND
			            (:startDateTime IS NULL OR o.preferredDeliveryDate >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.preferredDeliveryDate <= :endDateTime))
			        OR :dateCriteria = 'all'
			    )
			""")
	Page<Order> findFilteredOrders(@Param("keyword") String keyword, @Param("dateCriteria") String dateCriteria,
			@Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime,
			@Param("productCategoryId") Long productCategoryId, @Param("status") OrderStatus status,
			@Param("standard") Boolean standard, Pageable pageable);

	/**
	 * 관리자 발주 목록용 조회입니다. 기존 필터에 오더 ID 정확 일치 조건을 추가합니다.
	 * 기존 findFilteredOrders 호출부와의 충돌을 피하기 위해 별도 메서드로 유지합니다.
	 */
	@EntityGraph(attributePaths = { "task", "task.requestedBy", "task.requestedBy.company", "orderItem",
			"deliveryMethod", "productCategory", "assignedDeliveryHandler", "checkStatus" })
	@Query("""
			SELECT o FROM Order o
			WHERE
			    (:orderId IS NULL OR o.id = :orderId)
			    AND (:keyword IS NULL OR
			        LOWER(o.task.requestedBy.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
			        LOWER(o.task.requestedBy.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))
			    AND (:productCategoryId IS NULL OR o.productCategory.id = :productCategoryId)
			    AND (:status IS NULL OR o.status = :status)
			    AND (:standard IS NULL OR o.standard = :standard)
			    AND (
			        (:dateCriteria = 'order' AND
			            (:startDateTime IS NULL OR o.createdAt >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.createdAt <= :endDateTime))
			        OR (:dateCriteria = 'delivery' AND
			            (:startDateTime IS NULL OR o.preferredDeliveryDate >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.preferredDeliveryDate <= :endDateTime))
			        OR :dateCriteria = 'all'
			    )
			""")
	Page<Order> findFilteredOrdersWithOrderId(@Param("keyword") String keyword, @Param("orderId") Long orderId,
			@Param("dateCriteria") String dateCriteria, @Param("startDateTime") LocalDateTime startDateTime,
			@Param("endDateTime") LocalDateTime endDateTime, @Param("productCategoryId") Long productCategoryId,
			@Param("status") OrderStatus status, @Param("standard") Boolean standard, Pageable pageable);

	/**
	 * 관리자 발주 목록의 제품명 포함 검색까지 적용하는 조회입니다.
	 * 기존 메서드 호출부 보호를 위해 별도 메서드로 유지합니다.
	 */
	@EntityGraph(attributePaths = { "task", "task.requestedBy", "task.requestedBy.company", "orderItem",
			"deliveryMethod", "productCategory", "assignedDeliveryHandler", "checkStatus" })
	@Query("""
			SELECT o FROM Order o
			LEFT JOIN o.orderItem filterItem
			WHERE
			    (:orderId IS NULL OR o.id = :orderId)
			    AND (:productName IS NULL OR
			        LOWER(filterItem.productName) LIKE LOWER(CONCAT('%', :productName, '%')))
			    AND (:keyword IS NULL OR
			        LOWER(o.task.requestedBy.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
			        LOWER(o.task.requestedBy.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))
			    AND (:productCategoryId IS NULL OR o.productCategory.id = :productCategoryId)
			    AND (:status IS NULL OR o.status = :status)
			    AND (:standard IS NULL OR o.standard = :standard)
			    AND (
			        (:dateCriteria = 'order' AND
			            (:startDateTime IS NULL OR o.createdAt >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.createdAt <= :endDateTime))
			        OR (:dateCriteria = 'delivery' AND
			            (:startDateTime IS NULL OR o.preferredDeliveryDate >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.preferredDeliveryDate <= :endDateTime))
			        OR :dateCriteria = 'all'
			    )
			""")
	Page<Order> findFilteredOrdersWithOrderIdAndProductName(
			@Param("keyword") String keyword,
			@Param("orderId") Long orderId,
			@Param("productName") String productName,
			@Param("dateCriteria") String dateCriteria,
			@Param("startDateTime") LocalDateTime startDateTime,
			@Param("endDateTime") LocalDateTime endDateTime,
			@Param("productCategoryId") Long productCategoryId,
			@Param("status") OrderStatus status,
			@Param("standard") Boolean standard,
			Pageable pageable
	);

	@Query("""
			    SELECT o FROM Order o
			    WHERE
			        (:keyword IS NULL OR
			            LOWER(o.task.requestedBy.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
			            LOWER(o.task.requestedBy.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))
			        AND (:productCategoryId IS NULL OR o.productCategory.id = :productCategoryId)
			        AND (:status IS NULL OR o.status = :status)
			        AND (:deliveryMethodId IS NULL OR o.deliveryMethod.id = :deliveryMethodId)
			        AND (
			            (:dateCriteria = 'order' AND
			                (:startDateTime IS NULL OR o.createdAt >= :startDateTime) AND
			                (:endDateTime IS NULL OR o.createdAt <= :endDateTime))
			            OR (:dateCriteria = 'delivery' AND
			                (:startDateTime IS NULL OR o.preferredDeliveryDate >= :startDateTime) AND
			                (:endDateTime IS NULL OR o.preferredDeliveryDate <= :endDateTime))
			            OR :dateCriteria = 'all'
			        )
			    ORDER BY o.preferredDeliveryDate DESC
			""")
	List<Order> findFilteredOrdersForExcel(@Param("keyword") String keyword, @Param("dateCriteria") String dateCriteria,
			@Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime,
			@Param("productCategoryId") Long productCategoryId, @Param("status") OrderStatus status,
			@Param("deliveryMethodId") Long deliveryMethodId);

	@Query("""
			    SELECT o FROM Order o
			    WHERE
			        (:keyword IS NULL OR
			            LOWER(o.task.requestedBy.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
			            LOWER(o.task.requestedBy.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))
			        AND (:productCategoryId IS NULL OR o.productCategory.id = :productCategoryId)
			        AND (:status IS NULL OR o.status = :status)
			        AND (:standard IS NULL OR o.standard = :standard)
			        AND (
			            (:dateCriteria = 'order' AND
			                (:startDateTime IS NULL OR o.createdAt >= :startDateTime) AND
			                (:endDateTime IS NULL OR o.createdAt <= :endDateTime))
			            OR (:dateCriteria = 'delivery' AND
			                (:startDateTime IS NULL OR o.preferredDeliveryDate >= :startDateTime) AND
			                (:endDateTime IS NULL OR o.preferredDeliveryDate <= :endDateTime))
			            OR :dateCriteria = 'all'
			        )
			    ORDER BY o.preferredDeliveryDate DESC
			""")
	List<Order> findFilteredOrdersForExcel(@Param("keyword") String keyword, @Param("dateCriteria") String dateCriteria,
			@Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime,
			@Param("productCategoryId") Long productCategoryId, @Param("status") OrderStatus status,
			@Param("standard") Boolean standard);

	@EntityGraph(attributePaths = { "task", "task.requestedBy", "task.requestedBy.company", "orderItem",
			"deliveryMethod", "productCategory", "assignedProductionTeam", "assignedDeliveryHandler" })
	@Query("""
			SELECT o FROM Order o
			LEFT JOIN o.orderItem filterItem
			WHERE
			    (:productName IS NULL OR
			        LOWER(filterItem.productName) LIKE LOWER(CONCAT('%', :productName, '%')))
			    AND (:keyword IS NULL OR
			        LOWER(o.task.requestedBy.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
			        LOWER(o.task.requestedBy.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))
			    AND (:productCategoryId IS NULL OR o.productCategory.id = :productCategoryId)
			    AND (:status IS NULL OR o.status = :status)
			    AND (:standard IS NULL OR o.standard = :standard)
			    AND (
			        (:dateCriteria = 'order' AND
			            (:startDateTime IS NULL OR o.createdAt >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.createdAt <= :endDateTime))
			        OR (:dateCriteria = 'delivery' AND
			            (:startDateTime IS NULL OR o.preferredDeliveryDate >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.preferredDeliveryDate <= :endDateTime))
			        OR :dateCriteria = 'all'
			    )
			ORDER BY o.preferredDeliveryDate DESC, o.id DESC
			""")
	List<Order> findFilteredOrdersForExcelWithProductName(
			@Param("keyword") String keyword,
			@Param("productName") String productName,
			@Param("dateCriteria") String dateCriteria,
			@Param("startDateTime") LocalDateTime startDateTime,
			@Param("endDateTime") LocalDateTime endDateTime,
			@Param("productCategoryId") Long productCategoryId,
			@Param("status") OrderStatus status,
			@Param("standard") Boolean standard
	);

	@Query("""
			    SELECT o FROM Order o
			    WHERE (:orderId IS NULL OR o.id = :orderId)
			        AND (:keyword IS NULL OR
			            LOWER(o.task.requestedBy.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
			            LOWER(o.task.requestedBy.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))
			        AND (:productCategoryId IS NULL OR o.productCategory.id = :productCategoryId)
			        AND (:status IS NULL OR o.status = :status)
			        AND (:standard IS NULL OR o.standard = :standard)
			        AND (
			            (:dateCriteria = 'order' AND
			                (:startDateTime IS NULL OR o.createdAt >= :startDateTime) AND
			                (:endDateTime IS NULL OR o.createdAt <= :endDateTime))
			            OR (:dateCriteria = 'delivery' AND
			                (:startDateTime IS NULL OR o.preferredDeliveryDate >= :startDateTime) AND
			                (:endDateTime IS NULL OR o.preferredDeliveryDate <= :endDateTime))
			            OR :dateCriteria = 'all'
			        )
			    ORDER BY o.preferredDeliveryDate DESC
			""")
	List<Order> findFilteredOrdersForExcelWithOrderId(@Param("keyword") String keyword,
			@Param("orderId") Long orderId, @Param("dateCriteria") String dateCriteria,
			@Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime,
			@Param("productCategoryId") Long productCategoryId, @Param("status") OrderStatus status,
			@Param("standard") Boolean standard);

	@EntityGraph(attributePaths = { "task", "task.requestedBy", "task.requestedBy.company", "orderItem",
			"deliveryMethod", "productCategory", "assignedProductionTeam", "assignedDeliveryHandler", "checkStatus" })
	@Query("""
			SELECT o FROM Order o
			LEFT JOIN o.orderItem filterItem
			WHERE (:orderId IS NULL OR o.id = :orderId)
			    AND (:productName IS NULL OR
			        LOWER(filterItem.productName) LIKE LOWER(CONCAT('%', :productName, '%')))
			    AND (:keyword IS NULL OR
			        LOWER(o.task.requestedBy.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
			        LOWER(o.task.requestedBy.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))
			    AND (:productCategoryId IS NULL OR o.productCategory.id = :productCategoryId)
			    AND (:status IS NULL OR o.status = :status)
			    AND (:standard IS NULL OR o.standard = :standard)
			    AND (
			        (:dateCriteria = 'order' AND
			            (:startDateTime IS NULL OR o.createdAt >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.createdAt <= :endDateTime))
			        OR (:dateCriteria = 'delivery' AND
			            (:startDateTime IS NULL OR o.preferredDeliveryDate >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.preferredDeliveryDate <= :endDateTime))
			        OR :dateCriteria = 'all'
			    )
			ORDER BY o.preferredDeliveryDate DESC, o.id DESC
			""")
	List<Order> findFilteredOrdersForExcelWithOrderIdAndProductName(
			@Param("keyword") String keyword,
			@Param("orderId") Long orderId,
			@Param("productName") String productName,
			@Param("dateCriteria") String dateCriteria,
			@Param("startDateTime") LocalDateTime startDateTime,
			@Param("endDateTime") LocalDateTime endDateTime,
			@Param("productCategoryId") Long productCategoryId,
			@Param("status") OrderStatus status,
			@Param("standard") Boolean standard
	);

	@Query("""
			    SELECT o FROM Order o
			    WHERE (:category IS NULL OR o.productCategory = :category)
			      AND (:status IS NULL OR o.status = :status)
			      AND o.preferredDeliveryDate BETWEEN :start AND :end
			""")
	Page<Order> findByPreferredDateRange(@Param("category") TeamCategory category, @Param("status") OrderStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE (:category IS NULL OR o.productCategory = :category)
			      AND (:status IS NULL OR o.status = :status)
			      AND o.createdAt BETWEEN :start AND :end
			""")
	Page<Order> findByCreatedDateRange(@Param("category") TeamCategory category, @Param("status") OrderStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE (:category IS NULL OR o.productCategory = :category)
			      AND (:status IS NULL OR o.status = :status)
			      AND o.preferredDeliveryDate BETWEEN :start AND :end
			""")
	List<Order> findAllByPreferredDateRange(@Param("category") TeamCategory category,
			@Param("status") OrderStatus status, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

	@Query("""
			    SELECT o FROM Order o
			    WHERE (:category IS NULL OR o.productCategory = :category)
			      AND (:status IS NULL OR o.status = :status)
			      AND o.createdAt BETWEEN :start AND :end
			""")
	List<Order> findAllByCreatedDateRange(@Param("category") TeamCategory category, @Param("status") OrderStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

	@Query("""
			    SELECT o FROM Order o
			    WHERE (:category IS NULL OR o.productCategory = :category)
			      AND (:assigned IS NULL OR o.assignedDeliveryHandler = :assigned)
			      AND (:status IS NULL OR o.status = :status)
			      AND (
			        (:start IS NULL OR o.preferredDeliveryDate >= :start)
			        AND (:end IS NULL OR o.preferredDeliveryDate <= :end)
			      )
			    ORDER BY o.preferredDeliveryDate DESC
			""")
	Page<Order> findByPreferredDateRange(@Param("category") TeamCategory category, @Param("assigned") Member assigned,
			@Param("status") OrderStatus status, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE (:category IS NULL OR o.productCategory = :category)
			      AND (:assigned IS NULL OR o.assignedDeliveryHandler = :assigned)
			      AND (:status IS NULL OR o.status = :status)
			      AND (
			        (:start IS NULL OR o.createdAt >= :start)
			        AND (:end IS NULL OR o.createdAt <= :end)
			      )
			    ORDER BY o.createdAt DESC
			""")
	Page<Order> findByCreatedDateRange(@Param("category") TeamCategory category, @Param("assigned") Member assigned,
			@Param("status") OrderStatus status, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			Pageable pageable);

	@Query("""
			    SELECT o FROM Order o
			    WHERE (:category IS NULL OR o.productCategory = :category)
			      AND (:status IS NULL OR o.status = :status)
			      AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
			      AND o.preferredDeliveryDate BETWEEN :start AND :end
			    ORDER BY o.preferredDeliveryDate DESC
			""")
	List<Order> findAllByPreferredDateRange(@Param("category") TeamCategory category,
			@Param("status") OrderStatus status, @Param("assignedMemberId") Long assignedMemberId,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

	@Query("""
			    SELECT o FROM Order o
			    WHERE (:category IS NULL OR o.productCategory = :category)
			      AND (:status IS NULL OR o.status = :status)
			      AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
			      AND o.createdAt BETWEEN :start AND :end
			    ORDER BY o.createdAt DESC
			""")
	List<Order> findAllByCreatedDateRange(@Param("category") TeamCategory category, @Param("status") OrderStatus status,
			@Param("assignedMemberId") Long assignedMemberId, @Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end);

	@EntityGraph(attributePaths = { "task", "task.requestedBy", "task.requestedBy.company", "productCategory",
			"assignedDeliveryHandler", "orderItem" })
	@Query(value = """
			    SELECT o FROM Order o
			    JOIN o.task t
			    JOIN t.requestedBy rb
			    JOIN rb.company c
			    WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
			      AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
			      AND (:status IS NULL OR o.status = :status)
			      AND (:start IS NULL OR o.createdAt >= :start)
			      AND (:end IS NULL OR o.createdAt <= :end)
			""", countQuery = """
			    SELECT COUNT(o) FROM Order o
			    JOIN o.task t
			    JOIN t.requestedBy rb
			    JOIN rb.company c
			    WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
			      AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
			      AND (:status IS NULL OR o.status = :status)
			      AND (:start IS NULL OR o.createdAt >= :start)
			      AND (:end IS NULL OR o.createdAt <= :end)
			""")
	Page<Order> findByCreatedDateRange(@Param("categoryId") Long categoryId,
			@Param("assignedMemberId") Long assignedMemberId, @Param("status") OrderStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	@EntityGraph(attributePaths = { "task", "task.requestedBy", "task.requestedBy.company", "productCategory",
			"assignedDeliveryHandler", "orderItem" })
	@Query(value = """
			    SELECT o FROM Order o
			    JOIN o.task t
			    JOIN t.requestedBy rb
			    JOIN rb.company c
			    WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
			      AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
			      AND (:status IS NULL OR o.status = :status)
			      AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
			      AND (:end IS NULL OR o.preferredDeliveryDate <= :end)
			""", countQuery = """
			    SELECT COUNT(o) FROM Order o
			    JOIN o.task t
			    JOIN t.requestedBy rb
			    JOIN rb.company c
			    WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
			      AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
			      AND (:status IS NULL OR o.status = :status)
			      AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
			      AND (:end IS NULL OR o.preferredDeliveryDate <= :end)
			""")
	Page<Order> findByPreferredDateRange(@Param("categoryId") Long categoryId,
			@Param("assignedMemberId") Long assignedMemberId, @Param("status") OrderStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	@EntityGraph(attributePaths = { "task", "task.requestedBy", "task.requestedBy.company", "productCategory",
			"assignedDeliveryHandler", "orderItem" })
	@Query("""
			    SELECT o FROM Order o
			    WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
			      AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
			      AND (:status IS NULL OR o.status = :status)
			      AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
			      AND (:end IS NULL OR o.preferredDeliveryDate <= :end)
			    ORDER BY o.preferredDeliveryDate DESC
			""")
	List<Order> findAllByPreferredDateRange(@Param("categoryId") Long categoryId,
			@Param("assignedMemberId") Long assignedMemberId, @Param("status") OrderStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

	@EntityGraph(attributePaths = { "task", "task.requestedBy", "task.requestedBy.company", "productCategory",
			"assignedDeliveryHandler", "orderItem" })
	@Query("""
			    SELECT o FROM Order o
			    WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
			      AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
			      AND (:status IS NULL OR o.status = :status)
			      AND (:start IS NULL OR o.createdAt >= :start)
			      AND (:end IS NULL OR o.createdAt <= :end)
			    ORDER BY o.createdAt DESC
			""")
	List<Order> findAllByCreatedDateRange(@Param("categoryId") Long categoryId,
			@Param("assignedMemberId") Long assignedMemberId, @Param("status") OrderStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

	@Query("""
			    select distinct o
			    from Order o
			    left join fetch o.orderItem oi
			    left join fetch o.productCategory pc
			    left join fetch o.task t
			    left join fetch t.requestedBy rb
			    left join fetch rb.company c
			    where o.id in :ids
			""")
	List<Order> findAllForStickerPrint(@Param("ids") List<Long> ids);

	// =========================
	// 생산팀 목록 - statusFilter 기반 정렬용
	// categoryId null 허용
	// =========================
	@EntityGraph(attributePaths = { "orderItem", "productCategory", "task", "task.requestedBy",
			"task.requestedBy.company" })
	@Query("""
			    SELECT o FROM Order o
			    WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
			      AND (:allStatus = true OR o.status = :statusFilter)
			      AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
			      AND (:end IS NULL OR o.preferredDeliveryDate < :end)
			""")
	Page<Order> findProductionListByPreferredRangeSortable(@Param("categoryId") Long categoryId,
			@Param("allStatus") boolean allStatus, @Param("statusFilter") OrderStatus statusFilter,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	@EntityGraph(attributePaths = { "orderItem", "productCategory", "task", "task.requestedBy",
			"task.requestedBy.company" })
	@Query("""
			    SELECT o FROM Order o
			    WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
			      AND (:allStatus = true OR o.status = :statusFilter)
			      AND (:start IS NULL OR o.createdAt >= :start)
			      AND (:end IS NULL OR o.createdAt < :end)
			""")
	Page<Order> findProductionListByCreatedRangeSortable(@Param("categoryId") Long categoryId,
			@Param("allStatus") boolean allStatus, @Param("statusFilter") OrderStatus statusFilter,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	@EntityGraph(attributePaths = { "orderItem", "productCategory", "task", "task.requestedBy",
			"task.requestedBy.company", "task.managedBy", "checkStatus" })
	@Query("""
			    SELECT o FROM Order o
			    WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
			      AND (:mirrorCuttingOnly = false OR o.mirrorCuttingProduct = true)
			      AND (:orderId IS NULL OR o.id = :orderId)
              AND (:productNameKeyword IS NULL OR LOWER(o.orderItem.productName) LIKE LOWER(CONCAT('%', :productNameKeyword, '%')))
              AND (:standard IS NULL OR o.standard = :standard)
			      AND o.status IN :visibleStatuses
			      AND (:allStatus = true OR o.status = :statusFilter)
			      AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
			      AND (:end IS NULL OR o.preferredDeliveryDate < :end)
			""")
	Page<Order> findProductionListByPreferredRangeStatusSortable(
			@Param("categoryId") Long categoryId,
			@Param("mirrorCuttingOnly") boolean mirrorCuttingOnly,
			@Param("orderId") Long orderId,
            @Param("productNameKeyword") String productNameKeyword,
            @Param("standard") Boolean standard,
			@Param("allStatus") boolean allStatus,
			@Param("statusFilter") OrderStatus statusFilter,
			@Param("visibleStatuses") List<OrderStatus> visibleStatuses,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end,
			Pageable pageable
	);

	@EntityGraph(attributePaths = { "orderItem", "productCategory", "task", "task.requestedBy",
			"task.requestedBy.company", "task.managedBy", "checkStatus" })
	@Query("""
			    SELECT o FROM Order o
			    WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
			      AND (:mirrorCuttingOnly = false OR o.mirrorCuttingProduct = true)
			      AND (:orderId IS NULL OR o.id = :orderId)
              AND (:productNameKeyword IS NULL OR LOWER(o.orderItem.productName) LIKE LOWER(CONCAT('%', :productNameKeyword, '%')))
              AND (:standard IS NULL OR o.standard = :standard)
			      AND o.status IN :visibleStatuses
			      AND (:allStatus = true OR o.status = :statusFilter)
			      AND (:start IS NULL OR o.createdAt >= :start)
			      AND (:end IS NULL OR o.createdAt < :end)
			""")
	Page<Order> findProductionListByCreatedRangeStatusSortable(
			@Param("categoryId") Long categoryId,
			@Param("mirrorCuttingOnly") boolean mirrorCuttingOnly,
			@Param("orderId") Long orderId,
            @Param("productNameKeyword") String productNameKeyword,
            @Param("standard") Boolean standard,
			@Param("allStatus") boolean allStatus,
			@Param("statusFilter") OrderStatus statusFilter,
			@Param("visibleStatuses") List<OrderStatus> visibleStatuses,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end,
			Pageable pageable
	);

	// =========================
	// 생산팀 목록 - 사용자 다중 정렬용 전체 조회
	//
	// 화면에서 선택한 여러 정렬 조건을 클릭 순서대로 적용하려면
	// 체크상태와 optionJson 기반 중분류까지 함께 비교해야 하므로,
	// 조건에 맞는 전체 목록을 조회한 뒤 TeamTaskService에서 정렬/페이징합니다.
	// 기본 조회(정렬 조건 없음)는 기존 DB 페이징 쿼리를 그대로 사용합니다.
	// =========================
	@EntityGraph(attributePaths = { "orderItem", "productCategory", "task", "task.requestedBy",
			"task.requestedBy.company", "task.managedBy", "checkStatus" })
	@Query("""
			    SELECT o FROM Order o
			    WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
			      AND (:mirrorCuttingOnly = false OR o.mirrorCuttingProduct = true)
			      AND (:orderId IS NULL OR o.id = :orderId)
			      AND (:productNameKeyword IS NULL OR LOWER(o.orderItem.productName) LIKE LOWER(CONCAT('%', :productNameKeyword, '%')))
			      AND (:standard IS NULL OR o.standard = :standard)
			      AND o.status IN :visibleStatuses
			      AND (:allStatus = true OR o.status = :statusFilter)
			      AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
			      AND (:end IS NULL OR o.preferredDeliveryDate < :end)
			""")
	List<Order> findProductionListByPreferredRangeStatusForMultiSort(
			@Param("categoryId") Long categoryId,
			@Param("mirrorCuttingOnly") boolean mirrorCuttingOnly,
			@Param("orderId") Long orderId,
			@Param("productNameKeyword") String productNameKeyword,
			@Param("standard") Boolean standard,
			@Param("allStatus") boolean allStatus,
			@Param("statusFilter") OrderStatus statusFilter,
			@Param("visibleStatuses") List<OrderStatus> visibleStatuses,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end
	);

	@EntityGraph(attributePaths = { "orderItem", "productCategory", "task", "task.requestedBy",
			"task.requestedBy.company", "task.managedBy", "checkStatus" })
	@Query("""
			    SELECT o FROM Order o
			    WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
			      AND (:mirrorCuttingOnly = false OR o.mirrorCuttingProduct = true)
			      AND (:orderId IS NULL OR o.id = :orderId)
			      AND (:productNameKeyword IS NULL OR LOWER(o.orderItem.productName) LIKE LOWER(CONCAT('%', :productNameKeyword, '%')))
			      AND (:standard IS NULL OR o.standard = :standard)
			      AND o.status IN :visibleStatuses
			      AND (:allStatus = true OR o.status = :statusFilter)
			      AND (:start IS NULL OR o.createdAt >= :start)
			      AND (:end IS NULL OR o.createdAt < :end)
			""")
	List<Order> findProductionListByCreatedRangeStatusForMultiSort(
			@Param("categoryId") Long categoryId,
			@Param("mirrorCuttingOnly") boolean mirrorCuttingOnly,
			@Param("orderId") Long orderId,
			@Param("productNameKeyword") String productNameKeyword,
			@Param("standard") Boolean standard,
			@Param("allStatus") boolean allStatus,
			@Param("statusFilter") OrderStatus statusFilter,
			@Param("visibleStatuses") List<OrderStatus> visibleStatuses,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end
	);

	// =========================
	// 생산팀 목록 - 체크 상태 정렬
	// 정렬 순서 고정:
	// 1) REVISED_AFTER_CHECK 재수정
	// 2) UNCHECKED / checkStatus row 없음 미확인
	// 3) CHECKED 확인
	//
	// sortDir는 기존 TeamTaskService 호출부 호환을 위해 유지합니다.
	// =========================
	@EntityGraph(attributePaths = { "orderItem", "productCategory", "task", "task.requestedBy",
			"task.requestedBy.company", "task.managedBy" })
	@Query(value = """
                SELECT o
                FROM Order o
                LEFT JOIN OrderMemberCheckStatus mcs
                  ON mcs.order = o
                 AND mcs.member.id = :memberId
                 AND mcs.workArea = :workArea
                LEFT JOIN OrderWorkRevision wr
                  ON wr.order = o
                 AND wr.workArea = :workArea
                WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
                  AND (:mirrorCuttingOnly = false OR o.mirrorCuttingProduct = true)
                  AND (:orderId IS NULL OR o.id = :orderId)
                  AND (:productNameKeyword IS NULL OR LOWER(o.orderItem.productName) LIKE LOWER(CONCAT('%', :productNameKeyword, '%')))
                  AND (:standard IS NULL OR o.standard = :standard)
                  AND o.status IN :visibleStatuses
                  AND (:allStatus = true OR o.status = :statusFilter)
                  AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
                  AND (:end IS NULL OR o.preferredDeliveryDate < :end)
                ORDER BY
                  CASE
                    WHEN mcs.id IS NOT NULL AND mcs.lastCheckedVersion < COALESCE(wr.currentVersion, 0) THEN 0
                    WHEN :prioritizeUnchecked = true AND mcs.id IS NULL THEN 1
                    WHEN :prioritizeUnchecked = true THEN 2
                    ELSE 1
                  END ASC,
                  o.preferredDeliveryDate DESC,
                  o.id DESC
            """, countQuery = """
                SELECT COUNT(o)
                FROM Order o
                LEFT JOIN OrderMemberCheckStatus mcs
                  ON mcs.order = o
                 AND mcs.member.id = :memberId
                 AND mcs.workArea = :workArea
                LEFT JOIN OrderWorkRevision wr
                  ON wr.order = o
                 AND wr.workArea = :workArea
                WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
                  AND (:mirrorCuttingOnly = false OR o.mirrorCuttingProduct = true)
                  AND (:orderId IS NULL OR o.id = :orderId)
                  AND (:productNameKeyword IS NULL OR LOWER(o.orderItem.productName) LIKE LOWER(CONCAT('%', :productNameKeyword, '%')))
                  AND (:standard IS NULL OR o.standard = :standard)
                  AND o.status IN :visibleStatuses
                  AND (:allStatus = true OR o.status = :statusFilter)
                  AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
                  AND (:end IS NULL OR o.preferredDeliveryDate < :end)
                  AND (:prioritizeUnchecked = true OR :prioritizeUnchecked = false)
            """)
	Page<Order> findProductionListByPreferredRangeStatusCheckSorted(
			@Param("categoryId") Long categoryId,
			@Param("mirrorCuttingOnly") boolean mirrorCuttingOnly,
			@Param("orderId") Long orderId,
            @Param("productNameKeyword") String productNameKeyword,
            @Param("standard") Boolean standard,
			@Param("allStatus") boolean allStatus,
			@Param("statusFilter") OrderStatus statusFilter,
			@Param("visibleStatuses") List<OrderStatus> visibleStatuses,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end,
            @Param("memberId") Long memberId,
            @Param("workArea") OrderWorkArea workArea,
            @Param("prioritizeUnchecked") boolean prioritizeUnchecked,
			Pageable pageable
	);

	@EntityGraph(attributePaths = {
            "orderItem",
            "productCategory",
            "task",
            "task.requestedBy",
            "task.requestedBy.company",
            "task.managedBy"
    })
	@Query(value = """
            SELECT o
            FROM Order o
            LEFT JOIN OrderMemberCheckStatus mcs
              ON mcs.order = o
             AND mcs.member.id = :memberId
             AND mcs.workArea = :workArea
            LEFT JOIN OrderWorkRevision wr
              ON wr.order = o
             AND wr.workArea = :workArea
            WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
              AND (:mirrorCuttingOnly = false OR o.mirrorCuttingProduct = true)
              AND (:orderId IS NULL OR o.id = :orderId)
              AND (:productNameKeyword IS NULL OR LOWER(o.orderItem.productName) LIKE LOWER(CONCAT('%', :productNameKeyword, '%')))
              AND (:standard IS NULL OR o.standard = :standard)
              AND o.status IN :visibleStatuses
              AND (:allStatus = true OR o.status = :statusFilter)
              AND (:start IS NULL OR o.createdAt >= :start)
              AND (:end IS NULL OR o.createdAt < :end)
            ORDER BY
              CASE
                WHEN mcs.id IS NOT NULL AND mcs.lastCheckedVersion < COALESCE(wr.currentVersion, 0) THEN 0
                WHEN :prioritizeUnchecked = true AND mcs.id IS NULL THEN 1
                WHEN :prioritizeUnchecked = true THEN 2
                ELSE 1
              END ASC,
              o.createdAt DESC,
              o.id DESC
        """, countQuery = """
            SELECT COUNT(o)
            FROM Order o
            LEFT JOIN OrderMemberCheckStatus mcs
              ON mcs.order = o
             AND mcs.member.id = :memberId
             AND mcs.workArea = :workArea
            LEFT JOIN OrderWorkRevision wr
              ON wr.order = o
             AND wr.workArea = :workArea
            WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
              AND (:mirrorCuttingOnly = false OR o.mirrorCuttingProduct = true)
              AND (:orderId IS NULL OR o.id = :orderId)
              AND (:productNameKeyword IS NULL OR LOWER(o.orderItem.productName) LIKE LOWER(CONCAT('%', :productNameKeyword, '%')))
              AND (:standard IS NULL OR o.standard = :standard)
              AND o.status IN :visibleStatuses
              AND (:allStatus = true OR o.status = :statusFilter)
              AND (:start IS NULL OR o.createdAt >= :start)
              AND (:end IS NULL OR o.createdAt < :end)
              AND (:prioritizeUnchecked = true OR :prioritizeUnchecked = false)
        """)
	Page<Order> findProductionListByCreatedRangeStatusCheckSorted(
	        @Param("categoryId") Long categoryId,
	        @Param("mirrorCuttingOnly") boolean mirrorCuttingOnly,
	        @Param("orderId") Long orderId,
            @Param("productNameKeyword") String productNameKeyword,
            @Param("standard") Boolean standard,
	        @Param("allStatus") boolean allStatus,
	        @Param("statusFilter") OrderStatus statusFilter,
	        @Param("visibleStatuses") List<OrderStatus> visibleStatuses,
	        @Param("start") LocalDateTime start,
	        @Param("end") LocalDateTime end,
            @Param("memberId") Long memberId,
            @Param("workArea") OrderWorkArea workArea,
            @Param("prioritizeUnchecked") boolean prioritizeUnchecked,
	        Pageable pageable
	);

	@Query("""
			    SELECT o
			    FROM Order o
			    join o.task t
			    join t.requestedBy rb
			    join rb.company c
			    WHERE (:category IS NULL OR o.productCategory = :category)
			      AND (:status IS NULL OR o.status = :status)
			      AND (:start IS NULL OR o.createdAt >= :start)
			      AND (:end IS NULL OR o.createdAt <= :end)
			""")
	Page<Order> findProductionListByCreatedDate(@Param("category") TeamCategory category,
			@Param("status") OrderStatus status, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			Pageable pageable);

	@Query("""
			    SELECT o
			    FROM Order o
			    join o.task t
			    join t.requestedBy rb
			    join rb.company c
			    WHERE (:category IS NULL OR o.productCategory = :category)
			      AND (:status IS NULL OR o.status = :status)
			      AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
			      AND (:end IS NULL OR o.preferredDeliveryDate <= :end)
			""")
	Page<Order> findProductionListByPreferredDate(@Param("category") TeamCategory category,
			@Param("status") OrderStatus status, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			Pageable pageable);

	@Query("""
			    SELECT o
			    FROM Order o
			    join o.task t
			    join t.requestedBy rb
			    join rb.company c
			    WHERE (:category IS NULL OR o.productCategory = :category)
			      AND (:status IS NULL OR o.status = :status)
			      AND (:start IS NULL OR o.createdAt >= :start)
			      AND (:end IS NULL OR o.createdAt <= :end)
			""")
	List<Order> findAllProductionListByCreatedDate(@Param("category") TeamCategory category,
			@Param("status") OrderStatus status, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			Sort sort);

	@Query("""
			    SELECT o
			    FROM Order o
			    join o.task t
			    join t.requestedBy rb
			    join rb.company c
			    WHERE (:category IS NULL OR o.productCategory = :category)
			      AND (:status IS NULL OR o.status = :status)
			      AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
			      AND (:end IS NULL OR o.preferredDeliveryDate <= :end)
			""")
	List<Order> findAllProductionListByPreferredDate(@Param("category") TeamCategory category,
			@Param("status") OrderStatus status, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			Sort sort);

	@EntityGraph(attributePaths = { "task", "task.requestedBy", "task.requestedBy.company", "orderItem",
			"deliveryMethod", "productCategory", "assignedDeliveryHandler", "checkStatus" })
	@Query("""
			SELECT o FROM Order o
			LEFT JOIN o.checkStatus cs
			WHERE
			    (:keyword IS NULL OR
			        LOWER(o.task.requestedBy.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
			        LOWER(o.task.requestedBy.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))
			    AND (:productCategoryId IS NULL OR o.productCategory.id = :productCategoryId)
			    AND (:status IS NULL OR o.status = :status)
			    AND (:standard IS NULL OR o.standard = :standard)
			    AND (
			        (:dateCriteria = 'order' AND
			            (:startDateTime IS NULL OR o.createdAt >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.createdAt <= :endDateTime))
			        OR (:dateCriteria = 'delivery' AND
			            (:startDateTime IS NULL OR o.preferredDeliveryDate >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.preferredDeliveryDate <= :endDateTime))
			        OR :dateCriteria = 'all'
			    )
			ORDER BY
			    CASE
			        WHEN cs.checkState = 'REVISED_AFTER_CHECK' THEN 0
			        WHEN cs.id IS NULL THEN 1
			        WHEN cs.checkState IS NULL AND cs.checked = false THEN 1
			        WHEN cs.checkState = 'UNCHECKED' THEN 1
			        WHEN cs.checkState = 'CHECKED' THEN 2
			        WHEN cs.checkState IS NULL AND cs.checked = true THEN 2
			        ELSE 1
			    END ASC,
			    o.createdAt DESC
			""")
	List<Order> findFilteredOrdersForBulkView(@Param("keyword") String keyword,
			@Param("dateCriteria") String dateCriteria, @Param("startDateTime") LocalDateTime startDateTime,
			@Param("endDateTime") LocalDateTime endDateTime, @Param("productCategoryId") Long productCategoryId,
			@Param("status") OrderStatus status, @Param("standard") Boolean standard);

	@EntityGraph(attributePaths = { "task", "task.requestedBy", "task.requestedBy.company", "orderItem",
			"deliveryMethod", "productCategory", "assignedDeliveryHandler", "checkStatus" })
	@Query("""
			SELECT o FROM Order o
			LEFT JOIN o.checkStatus cs
			WHERE (:orderId IS NULL OR o.id = :orderId)
			    AND (:keyword IS NULL OR
			        LOWER(o.task.requestedBy.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
			        LOWER(o.task.requestedBy.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))
			    AND (:productCategoryId IS NULL OR o.productCategory.id = :productCategoryId)
			    AND (:status IS NULL OR o.status = :status)
			    AND (:standard IS NULL OR o.standard = :standard)
			    AND (
			        (:dateCriteria = 'order' AND
			            (:startDateTime IS NULL OR o.createdAt >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.createdAt <= :endDateTime))
			        OR (:dateCriteria = 'delivery' AND
			            (:startDateTime IS NULL OR o.preferredDeliveryDate >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.preferredDeliveryDate <= :endDateTime))
			        OR :dateCriteria = 'all'
			    )
			ORDER BY
			    CASE
			        WHEN cs.checkState = 'REVISED_AFTER_CHECK' THEN 0
			        WHEN cs.id IS NULL THEN 1
			        WHEN cs.checkState IS NULL AND cs.checked = false THEN 1
			        WHEN cs.checkState = 'UNCHECKED' THEN 1
			        WHEN cs.checkState = 'CHECKED' THEN 2
			        WHEN cs.checkState IS NULL AND cs.checked = true THEN 2
			        ELSE 1
			    END ASC,
			    o.createdAt DESC
			""")
	List<Order> findFilteredOrdersForBulkViewWithOrderId(@Param("keyword") String keyword,
			@Param("orderId") Long orderId, @Param("dateCriteria") String dateCriteria,
			@Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime,
			@Param("productCategoryId") Long productCategoryId, @Param("status") OrderStatus status,
			@Param("standard") Boolean standard);

	@EntityGraph(attributePaths = { "task", "task.requestedBy", "task.requestedBy.company", "orderItem",
			"deliveryMethod", "productCategory", "assignedDeliveryHandler", "checkStatus" })
	@Query("""
			SELECT o FROM Order o
			LEFT JOIN o.checkStatus cs
			LEFT JOIN o.orderItem filterItem
			WHERE (:orderId IS NULL OR o.id = :orderId)
			    AND (:productName IS NULL OR
			        LOWER(filterItem.productName) LIKE LOWER(CONCAT('%', :productName, '%')))
			    AND (:keyword IS NULL OR
			        LOWER(o.task.requestedBy.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
			        LOWER(o.task.requestedBy.company.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')))
			    AND (:productCategoryId IS NULL OR o.productCategory.id = :productCategoryId)
			    AND (:status IS NULL OR o.status = :status)
			    AND (:standard IS NULL OR o.standard = :standard)
			    AND (
			        (:dateCriteria = 'order' AND
			            (:startDateTime IS NULL OR o.createdAt >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.createdAt <= :endDateTime))
			        OR (:dateCriteria = 'delivery' AND
			            (:startDateTime IS NULL OR o.preferredDeliveryDate >= :startDateTime) AND
			            (:endDateTime IS NULL OR o.preferredDeliveryDate <= :endDateTime))
			        OR :dateCriteria = 'all'
			    )
			ORDER BY
			    CASE
			        WHEN cs.checkState = 'REVISED_AFTER_CHECK' THEN 0
			        WHEN cs.id IS NULL THEN 1
			        WHEN cs.checkState IS NULL AND cs.checked = false THEN 1
			        WHEN cs.checkState = 'UNCHECKED' THEN 1
			        WHEN cs.checkState = 'CHECKED' THEN 2
			        WHEN cs.checkState IS NULL AND cs.checked = true THEN 2
			        ELSE 1
			    END ASC,
			    o.createdAt DESC,
			    o.id DESC
			""")
	List<Order> findFilteredOrdersForBulkViewWithOrderIdAndProductName(
			@Param("keyword") String keyword,
			@Param("orderId") Long orderId,
			@Param("productName") String productName,
			@Param("dateCriteria") String dateCriteria,
			@Param("startDateTime") LocalDateTime startDateTime,
			@Param("endDateTime") LocalDateTime endDateTime,
			@Param("productCategoryId") Long productCategoryId,
			@Param("status") OrderStatus status,
			@Param("standard") Boolean standard
	);

	@Query("""
			    select distinct o
			    from Order o
			    left join fetch o.task t
			    left join fetch t.requestedBy rb
			    left join fetch rb.company c
			    left join fetch t.managedBy mb
			    left join fetch o.productCategory pc
			    left join fetch o.orderItem oi
			    left join fetch o.orderImages imgs
			    left join fetch o.checkStatus cs
			    where o.id in :orderIds
			""")
	List<Order> findAllForProductionOverviewByIds(@Param("orderIds") List<Long> orderIds);

	@Query("""
			    select distinct o
			    from Order o
			    left join fetch o.task t
			    left join fetch t.requestedBy rb
			    left join fetch rb.company c
			    left join fetch t.managedBy mb
			    left join fetch o.productCategory pc
			    left join fetch o.orderItem oi
			    left join fetch o.orderImages imgs
			    left join fetch o.checkStatus cs
			    where o.id = :orderId
			""")
	Optional<Order> findByIdForProductionDetail(@Param("orderId") Long orderId);

	@Query("""
			    select o
			    from Order o
			    left join fetch o.productCategory pc
			    left join fetch o.checkStatus cs
			    where o.id = :orderId
			""")
	Optional<Order> findByIdForProductionCheck(@Param("orderId") Long orderId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from Order o
            where o.id = :orderId
            """)
    Optional<Order> findByIdForChangeAuditLock(@Param("orderId") Long orderId);

	@Query("""
			    select o
			    from Order o
			    left join fetch o.productCategory pc
			    where o.id = :orderId
			""")
	Optional<Order> findByIdForProductionStatusUpdate(@Param("orderId") Long orderId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			    update Order o
			    set o.status = :toStatus,
			        o.updatedAt = :updatedAt
			    where o.id = :orderId
			      and o.status = :fromStatus
			""")
	int updateProductionStatusIfCurrentStatus(@Param("orderId") Long orderId,
			@Param("fromStatus") OrderStatus fromStatus, @Param("toStatus") OrderStatus toStatus,
			@Param("updatedAt") LocalDateTime updatedAt);

	@EntityGraph(attributePaths = {
	        "task",
	        "task.requestedBy",
	        "task.requestedBy.company",
	        "orderItem",
	        "deliveryMethod"
	})
	@Query("""
	        select distinct o
	        from Order o
	        where o.id in :orderIds
	        """)
	List<Order> findAllForDeliveryStatementByIds(@Param("orderIds") Collection<Long> orderIds);

}