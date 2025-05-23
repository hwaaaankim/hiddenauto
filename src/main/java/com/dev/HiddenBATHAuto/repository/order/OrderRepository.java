package com.dev.HiddenBATHAuto.repository.order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>{

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
}























