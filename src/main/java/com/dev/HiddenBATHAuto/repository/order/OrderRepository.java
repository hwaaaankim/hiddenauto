package com.dev.HiddenBATHAuto.repository.order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>{

	List<Order> findByTask_RequestedByAndPreferredDeliveryDateBetween(Member member, LocalDateTime start, LocalDateTime end);

	// =========================
	// 생산팀 목록 (기본 정렬 고정: 상태 우선 + 날짜 ASC + id DESC)  ✅ 기존 유지
	// =========================
	@EntityGraph(attributePaths = {
			"orderItem",
			"productCategory",
			"task",
			"task.requestedBy",
			"task.requestedBy.company"
	})
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
	Page<Order> findProductionListByPreferredRange(
			@Param("statuses") List<OrderStatus> statuses,
			@Param("categoryId") Long categoryId,
			@Param("productionFilter") String productionFilter,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end,
			Pageable pageable
	);

	@EntityGraph(attributePaths = {
			"orderItem",
			"productCategory",
			"task",
			"task.requestedBy",
			"task.requestedBy.company"
	})
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
	Page<Order> findProductionListByCreatedRange(
			@Param("statuses") List<OrderStatus> statuses,
			@Param("categoryId") Long categoryId,
			@Param("productionFilter") String productionFilter,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end,
			Pageable pageable
	);

	// =========================
	// 생산팀 목록 (정렬 버튼용) ✅ 신규 추가
	// - ORDER BY 제거: Pageable의 Sort가 붙어야 페이징까지 완벽히 반영됩니다.
	// - 헤더 정렬 클릭 시 이 메서드들을 호출하세요.
	// =========================
	@EntityGraph(attributePaths = {
			"orderItem",
			"productCategory",
			"task",
			"task.requestedBy",
			"task.requestedBy.company"
	})
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
	Page<Order> findProductionListByPreferredRangeSortable(
			@Param("statuses") List<OrderStatus> statuses,
			@Param("categoryId") Long categoryId,
			@Param("productionFilter") String productionFilter,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end,
			Pageable pageable
	);

	@EntityGraph(attributePaths = {
			"orderItem",
			"productCategory",
			"task",
			"task.requestedBy",
			"task.requestedBy.company"
	})
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
	Page<Order> findProductionListByCreatedRangeSortable(
			@Param("statuses") List<OrderStatus> statuses,
			@Param("categoryId") Long categoryId,
			@Param("productionFilter") String productionFilter,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end,
			Pageable pageable
	);

	// ====== 이하 기존 코드 전부 유지 ======

	@Query("""
		    SELECT o FROM Order o
		    WHERE o.status IN :statuses
		      AND o.productCategory.id = :categoryId
		      AND (:preferredDate IS NULL OR DATE(o.preferredDeliveryDate) = :preferredDate)
		    ORDER BY o.preferredDeliveryDate ASC
		""")
	Page<Order> findProductionOrders(
		    @Param("statuses") List<OrderStatus> statuses,
		    @Param("categoryId") Long categoryId,
		    @Param("preferredDate") LocalDate preferredDate,
		    Pageable pageable);

	@Query("""
	    SELECT o FROM Order o
	    WHERE o.status IN :statuses
	      AND o.assignedDeliveryHandler.id = :memberId
	      AND (:preferredDate IS NULL OR DATE(o.preferredDeliveryDate) = :preferredDate)
	    ORDER BY o.preferredDeliveryDate ASC
	""")
	Page<Order> findDeliveryOrders(List<OrderStatus> statuses, Long memberId, LocalDate preferredDate, Pageable pageable);

	@Query("""
	    SELECT o FROM Order o
	    WHERE o.assignedDeliveryHandler.id = :handlerId
	      AND o.status IN :statuses
	      AND o.preferredDeliveryDate BETWEEN :startDate AND :endDate
	""")
	Page<Order> findDeliveryOrdersByHandlerAndStatusAndDateRange(
	    @Param("handlerId") Long handlerId,
	    @Param("statuses") List<OrderStatus> statuses,
	    @Param("startDate") LocalDateTime startDate,
	    @Param("endDate") LocalDateTime endDate,
	    Pageable pageable);

	@Query("""
		    SELECT o FROM Order o
		    WHERE o.status IN :statuses
		      AND o.productCategory.id = :categoryId
		      AND o.preferredDeliveryDate >= :startOfDay
		      AND o.preferredDeliveryDate < :endOfDay
		    ORDER BY o.preferredDeliveryDate ASC
		""")
	Page<Order> findFilteredOrders(
	        @Param("statuses") List<OrderStatus> statuses,
	        @Param("categoryId") Long categoryId,
	        @Param("startOfDay") LocalDateTime startOfDay,
	        @Param("endOfDay") LocalDateTime endOfDay,
	        Pageable pageable
	);

	@Query("""
	    SELECT o FROM Order o
	    WHERE o.status IN :statuses
	      AND o.productCategory.id = :categoryId
	      AND o.preferredDeliveryDate >= :start
	      AND o.preferredDeliveryDate < :end
	    ORDER BY o.preferredDeliveryDate ASC
	""")
	Page<Order> findByPreferredDateRange(
	        @Param("statuses") List<OrderStatus> statuses,
	        @Param("categoryId") Long categoryId,
	        @Param("start") LocalDateTime start,
	        @Param("end") LocalDateTime end,
	        Pageable pageable
	);

	@Query("""
	    SELECT o FROM Order o
	    WHERE o.status IN :statuses
	      AND o.productCategory.id = :categoryId
	      AND o.createdAt >= :start
	      AND o.createdAt < :end
	    ORDER BY o.createdAt ASC
	""")
	Page<Order> findByCreatedDateRange(
	        @Param("statuses") List<OrderStatus> statuses,
	        @Param("categoryId") Long categoryId,
	        @Param("start") LocalDateTime start,
	        @Param("end") LocalDateTime end,
	        Pageable pageable
	);

	@Query("""
	    SELECT o FROM Order o
	    WHERE o.status IN :statuses
	      AND o.productCategory.id = :categoryId
	      AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
	      AND (:end IS NULL OR o.preferredDeliveryDate < :end)
	    ORDER BY o.preferredDeliveryDate ASC
	""")
	Page<Order> findByPreferredDateRangeFlexible(
	        @Param("statuses") List<OrderStatus> statuses,
	        @Param("categoryId") Long categoryId,
	        @Param("start") LocalDateTime start,
	        @Param("end") LocalDateTime end,
	        Pageable pageable
	);

	@Query("""
	    SELECT o FROM Order o
	    WHERE o.status IN :statuses
	      AND o.productCategory.id = :categoryId
	      AND (:start IS NULL OR o.createdAt >= :start)
	      AND (:end IS NULL OR o.createdAt < :end)
	    ORDER BY o.createdAt ASC
	""")
	Page<Order> findByCreatedDateRangeFlexible(
	        @Param("statuses") List<OrderStatus> statuses,
	        @Param("categoryId") Long categoryId,
	        @Param("start") LocalDateTime start,
	        @Param("end") LocalDateTime end,
	        Pageable pageable
	);

	@Query("""
		    SELECT o FROM Order o
		    WHERE (:category IS NULL OR o.productCategory = :category)
		      AND (:status IS NULL OR o.status = :status)
		      AND o.preferredDeliveryDate BETWEEN :start AND :end
		""")
	Page<Order> findOrdersByConditions(
	    @Param("category") TeamCategory category,
	    @Param("status") OrderStatus status,
	    @Param("start") LocalDateTime start,
	    @Param("end") LocalDateTime end,
	    Pageable pageable
	);

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
	List<Order> findAllByConditions(
	    @Param("category") TeamCategory category,
	    @Param("status") OrderStatus status,
	    @Param("start") LocalDateTime start,
	    @Param("end") LocalDateTime end
	);

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
	Page<Order> findFilteredOrders(
		    @Param("keyword") String keyword,
		    @Param("dateCriteria") String dateCriteria,
		    @Param("startDateTime") LocalDateTime startDateTime,
		    @Param("endDateTime") LocalDateTime endDateTime,
		    @Param("productCategoryId") Long productCategoryId,
		    @Param("status") OrderStatus status,
		    @Param("deliveryMethodId") Long deliveryMethodId,
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
    Page<Order> findFilteredOrders(
            @Param("keyword") String keyword,
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
	List<Order> findFilteredOrdersForExcel(
	    @Param("keyword") String keyword,
	    @Param("dateCriteria") String dateCriteria,
	    @Param("startDateTime") LocalDateTime startDateTime,
	    @Param("endDateTime") LocalDateTime endDateTime,
	    @Param("productCategoryId") Long productCategoryId,
	    @Param("status") OrderStatus status,
	    @Param("deliveryMethodId") Long deliveryMethodId
	);

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
	List<Order> findFilteredOrdersForExcel(
		@Param("keyword") String keyword,
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
	Page<Order> findByPreferredDateRange(@Param("category") TeamCategory category,
	                                     @Param("status") OrderStatus status,
	                                     @Param("start") LocalDateTime start,
	                                     @Param("end") LocalDateTime end,
	                                     Pageable pageable);

	@Query("""
	    SELECT o FROM Order o
	    WHERE (:category IS NULL OR o.productCategory = :category)
	      AND (:status IS NULL OR o.status = :status)
	      AND o.createdAt BETWEEN :start AND :end
	""")
	Page<Order> findByCreatedDateRange(@Param("category") TeamCategory category,
	                                   @Param("status") OrderStatus status,
	                                   @Param("start") LocalDateTime start,
	                                   @Param("end") LocalDateTime end,
	                                   Pageable pageable);

	@Query("""
	    SELECT o FROM Order o
	    WHERE (:category IS NULL OR o.productCategory = :category)
	      AND (:status IS NULL OR o.status = :status)
	      AND o.preferredDeliveryDate BETWEEN :start AND :end
	""")
	List<Order> findAllByPreferredDateRange(
	    @Param("category") TeamCategory category,
	    @Param("status") OrderStatus status,
	    @Param("start") LocalDateTime start,
	    @Param("end") LocalDateTime end
	);

	@Query("""
	    SELECT o FROM Order o
	    WHERE (:category IS NULL OR o.productCategory = :category)
	      AND (:status IS NULL OR o.status = :status)
	      AND o.createdAt BETWEEN :start AND :end
	""")
	List<Order> findAllByCreatedDateRange(
	    @Param("category") TeamCategory category,
	    @Param("status") OrderStatus status,
	    @Param("start") LocalDateTime start,
	    @Param("end") LocalDateTime end
	);

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
	Page<Order> findByPreferredDateRange(
	    @Param("category") TeamCategory category,
	    @Param("assigned") Member assigned,
	    @Param("status") OrderStatus status,
	    @Param("start") LocalDateTime start,
	    @Param("end") LocalDateTime end,
	    Pageable pageable
	);

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
	Page<Order> findByCreatedDateRange(
	    @Param("category") TeamCategory category,
	    @Param("assigned") Member assigned,
	    @Param("status") OrderStatus status,
	    @Param("start") LocalDateTime start,
	    @Param("end") LocalDateTime end,
	    Pageable pageable
	);

	@Query("""
	    SELECT o FROM Order o
	    WHERE (:category IS NULL OR o.productCategory = :category)
	      AND (:status IS NULL OR o.status = :status)
	      AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
	      AND o.preferredDeliveryDate BETWEEN :start AND :end
	    ORDER BY o.preferredDeliveryDate DESC
	""")
	List<Order> findAllByPreferredDateRange(
	    @Param("category") TeamCategory category,
	    @Param("status") OrderStatus status,
	    @Param("assignedMemberId") Long assignedMemberId,
	    @Param("start") LocalDateTime start,
	    @Param("end") LocalDateTime end
	);

	@Query("""
	    SELECT o FROM Order o
	    WHERE (:category IS NULL OR o.productCategory = :category)
	      AND (:status IS NULL OR o.status = :status)
	      AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
	      AND o.createdAt BETWEEN :start AND :end
	    ORDER BY o.createdAt DESC
	""")
	List<Order> findAllByCreatedDateRange(
	    @Param("category") TeamCategory category,
	    @Param("status") OrderStatus status,
	    @Param("assignedMemberId") Long assignedMemberId,
	    @Param("start") LocalDateTime start,
	    @Param("end") LocalDateTime end
	);

	@EntityGraph(attributePaths = {
            "task",
            "task.requestedBy",
            "task.requestedBy.company",
            "productCategory",
            "assignedDeliveryHandler",
            "orderItem"
    })
    @Query(
        value = """
            SELECT o FROM Order o
            JOIN o.task t
            JOIN t.requestedBy rb
            JOIN rb.company c
            WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
              AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
              AND (:status IS NULL OR o.status = :status)
              AND (:start IS NULL OR o.createdAt >= :start)
              AND (:end IS NULL OR o.createdAt <= :end)
        """,
        countQuery = """
            SELECT COUNT(o) FROM Order o
            JOIN o.task t
            JOIN t.requestedBy rb
            JOIN rb.company c
            WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
              AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
              AND (:status IS NULL OR o.status = :status)
              AND (:start IS NULL OR o.createdAt >= :start)
              AND (:end IS NULL OR o.createdAt <= :end)
        """
    )
    Page<Order> findByCreatedDateRange(
            @Param("categoryId") Long categoryId,
            @Param("assignedMemberId") Long assignedMemberId,
            @Param("status") OrderStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "task",
            "task.requestedBy",
            "task.requestedBy.company",
            "productCategory",
            "assignedDeliveryHandler",
            "orderItem"
    })
    @Query(
        value = """
            SELECT o FROM Order o
            JOIN o.task t
            JOIN t.requestedBy rb
            JOIN rb.company c
            WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
              AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
              AND (:status IS NULL OR o.status = :status)
              AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
              AND (:end IS NULL OR o.preferredDeliveryDate <= :end)
        """,
        countQuery = """
            SELECT COUNT(o) FROM Order o
            JOIN o.task t
            JOIN t.requestedBy rb
            JOIN rb.company c
            WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
              AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
              AND (:status IS NULL OR o.status = :status)
              AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
              AND (:end IS NULL OR o.preferredDeliveryDate <= :end)
        """
    )
    Page<Order> findByPreferredDateRange(
            @Param("categoryId") Long categoryId,
            @Param("assignedMemberId") Long assignedMemberId,
            @Param("status") OrderStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );
	@EntityGraph(attributePaths = {
			"task",
			"task.requestedBy",
			"task.requestedBy.company",
			"productCategory",
			"assignedDeliveryHandler",
			"orderItem"
	})
	@Query("""
		SELECT o FROM Order o
		WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
		  AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
		  AND (:status IS NULL OR o.status = :status)
		  AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
		  AND (:end IS NULL OR o.preferredDeliveryDate <= :end)
		ORDER BY o.preferredDeliveryDate DESC
	""")
	List<Order> findAllByPreferredDateRange(
			@Param("categoryId") Long categoryId,
			@Param("assignedMemberId") Long assignedMemberId,
			@Param("status") OrderStatus status,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end
	);

	@EntityGraph(attributePaths = {
			"task",
			"task.requestedBy",
			"task.requestedBy.company",
			"productCategory",
			"assignedDeliveryHandler",
			"orderItem"
	})
	@Query("""
		SELECT o FROM Order o
		WHERE (:categoryId IS NULL OR o.productCategory.id = :categoryId)
		  AND (:assignedMemberId IS NULL OR o.assignedDeliveryHandler.id = :assignedMemberId)
		  AND (:status IS NULL OR o.status = :status)
		  AND (:start IS NULL OR o.createdAt >= :start)
		  AND (:end IS NULL OR o.createdAt <= :end)
		ORDER BY o.createdAt DESC
	""")
	List<Order> findAllByCreatedDateRange(
			@Param("categoryId") Long categoryId,
			@Param("assignedMemberId") Long assignedMemberId,
			@Param("status") OrderStatus status,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end
	);

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
	
	@EntityGraph(attributePaths = {
            "orderItem",
            "productCategory",
            "task",
            "task.requestedBy",
            "task.requestedBy.company"
    })
    @Query("""
        SELECT o FROM Order o
        WHERE o.productCategory.id = :categoryId
          AND (:allStatus = true OR o.status = :statusFilter)
          AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
          AND (:end IS NULL OR o.preferredDeliveryDate < :end)
    """)
    Page<Order> findProductionListByPreferredRangeSortable(
            @Param("categoryId") Long categoryId,
            @Param("allStatus") boolean allStatus,
            @Param("statusFilter") OrderStatus statusFilter,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "orderItem",
            "productCategory",
            "task",
            "task.requestedBy",
            "task.requestedBy.company"
    })
    @Query("""
        SELECT o FROM Order o
        WHERE o.productCategory.id = :categoryId
          AND (:allStatus = true OR o.status = :statusFilter)
          AND (:start IS NULL OR o.createdAt >= :start)
          AND (:end IS NULL OR o.createdAt < :end)
    """)
    Page<Order> findProductionListByCreatedRangeSortable(
            @Param("categoryId") Long categoryId,
            @Param("allStatus") boolean allStatus,
            @Param("statusFilter") OrderStatus statusFilter,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );
    
 // ✅ (추가) preferredDeliveryDate 기준 + statusFilter(ALL 지원) + pageable sort 반영
 	@EntityGraph(attributePaths = {
 			"orderItem",
 			"productCategory",
 			"task",
 			"task.requestedBy",
 			"task.requestedBy.company"
 	})
 	@Query("""
 		SELECT o FROM Order o
 		WHERE o.productCategory.id = :categoryId
 		  AND (:allStatus = true OR o.status = :statusFilter)
 		  AND (:start IS NULL OR o.preferredDeliveryDate >= :start)
 		  AND (:end IS NULL OR o.preferredDeliveryDate < :end)
 	""")
 	Page<Order> findProductionListByPreferredRangeStatusSortable(
 			@Param("categoryId") Long categoryId,
 			@Param("allStatus") boolean allStatus,
 			@Param("statusFilter") OrderStatus statusFilter,
 			@Param("start") LocalDateTime start,
 			@Param("end") LocalDateTime end,
 			Pageable pageable
 	);

 	// ✅ (추가) createdAt 기준 + statusFilter(ALL 지원) + pageable sort 반영
 	@EntityGraph(attributePaths = {
 			"orderItem",
 			"productCategory",
 			"task",
 			"task.requestedBy",
 			"task.requestedBy.company"
 	})
 	@Query("""
 		SELECT o FROM Order o
 		WHERE o.productCategory.id = :categoryId
 		  AND (:allStatus = true OR o.status = :statusFilter)
 		  AND (:start IS NULL OR o.createdAt >= :start)
 		  AND (:end IS NULL OR o.createdAt < :end)
 	""")
 	Page<Order> findProductionListByCreatedRangeStatusSortable(
 			@Param("categoryId") Long categoryId,
 			@Param("allStatus") boolean allStatus,
 			@Param("statusFilter") OrderStatus statusFilter,
 			@Param("start") LocalDateTime start,
 			@Param("end") LocalDateTime end,
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
                                               @Param("status") OrderStatus status,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end,
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
                                                 @Param("status") OrderStatus status,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end,
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
                                                  @Param("status") OrderStatus status,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end,
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
                                                    @Param("status") OrderStatus status,
                                                    @Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end,
                                                    Sort sort);

}






















