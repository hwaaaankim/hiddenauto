package com.dev.HiddenBATHAuto.repository.order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface DeliveryOrderIndexRepository extends JpaRepository<DeliveryOrderIndex, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			    select d
			    from DeliveryOrderIndex d
			    where d.order.id = :orderId
			""")
	Optional<DeliveryOrderIndex> findByOrderIdForUpdate(@Param("orderId") Long orderId);

	/*
	 * ============================================================ 기존 기능 유지 영역
	 * ============================================================
	 */

	@Query("""
			    select max(d.orderIndex)
			    from DeliveryOrderIndex d
			    where d.deliveryHandler.id = :deliveryHandlerId
			      and d.deliveryDate = :deliveryDate
			""")
	Optional<Integer> findMaxOrderIndexByDeliveryHandlerAndDeliveryDate(
			@Param("deliveryHandlerId") Long deliveryHandlerId, @Param("deliveryDate") LocalDate deliveryDate);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			    delete from DeliveryOrderIndex d
			    where d.order.id in :orderIds
			""")
	void deleteByOrderIdIn(@Param("orderIds") Collection<Long> orderIds);

	List<DeliveryOrderIndex> findByDeliveryHandlerAndDeliveryDateOrderByOrderIndex(Member handler, LocalDate date);

	int countByDeliveryHandlerAndDeliveryDate(Member handler, LocalDate date);

	boolean existsByOrder_Id(Long orderId);

	boolean existsByOrder(Order order);

	@Query("""
			    select doi
			    from DeliveryOrderIndex doi
			    where doi.deliveryHandler.id = :handlerId
			      and doi.deliveryDate = :deliveryDate
			      and doi.order.status in :statuses
			    order by doi.orderIndex asc
			""")
	Page<DeliveryOrderIndex> findByHandlerAndDateAndStatusIn(@Param("handlerId") Long handlerId,
			@Param("deliveryDate") LocalDate deliveryDate, @Param("statuses") List<OrderStatus> statuses,
			Pageable pageable);

	void deleteByOrder(Order order);

	/*
	 * 기존 배송팀 리스트 조회 - 기존 메서드명 유지 - order / orderItem / productCategory / task /
	 * requestedBy / company / assignedDeliveryHandler fetch 추가 - 기존 화면에서 Lazy 로딩
	 * 문제를 줄이기 위한 안전 보강
	 */
	@Query("""
			    select distinct doi
			    from DeliveryOrderIndex doi
			    join fetch doi.order o
			    left join fetch o.orderItem oi
			    left join fetch o.productCategory pc
			    left join fetch o.task t
			    left join fetch t.requestedBy rb
			    left join fetch rb.company c
			    left join fetch o.assignedDeliveryHandler adh
			    where doi.deliveryHandler.id = :handlerId
			      and doi.deliveryDate = :deliveryDate
			      and o.status in :statuses
			    order by
			      case
			        when o.status = com.dev.HiddenBATHAuto.model.task.OrderStatus.DELIVERY_DONE then 1
			        else 0
			      end asc,
			      doi.orderIndex asc
			""")
	List<DeliveryOrderIndex> findListByHandlerAndDateAndStatusIn(@Param("handlerId") Long handlerId,
			@Param("deliveryDate") LocalDate deliveryDate, @Param("statuses") List<OrderStatus> statuses);

	Optional<DeliveryOrderIndex> findByOrder(Order order);

	@Query("""
			    select max(doi.orderIndex)
			    from DeliveryOrderIndex doi
			    where doi.deliveryHandler.id = :handlerId
			      and doi.deliveryDate = :deliveryDate
			""")
	Integer findMaxIndexByHandlerAndDate(@Param("handlerId") Long handlerId,
			@Param("deliveryDate") LocalDate deliveryDate);

	/*
	 * 업체별 정렬용 - 기존 메서드명 유지 - orderItem, productCategory, requestedBy.company fetch
	 * 추가
	 */
	@Query("""
			    select distinct doi
			    from DeliveryOrderIndex doi
			    join fetch doi.order o
			    left join fetch o.orderItem oi
			    left join fetch o.productCategory pc
			    left join fetch o.task t
			    left join fetch t.requestedBy rb
			    left join fetch rb.company c
			    left join fetch o.assignedDeliveryHandler adh
			    where doi.deliveryHandler.id = :handlerId
			      and doi.deliveryDate = :deliveryDate
			    order by
			      case
			        when o.status = com.dev.HiddenBATHAuto.model.task.OrderStatus.DELIVERY_DONE then 1
			        else 0
			      end asc,
			      doi.orderIndex asc
			""")
	List<DeliveryOrderIndex> findAllByHandlerAndDateForTaskGrouping(@Param("handlerId") Long handlerId,
			@Param("deliveryDate") LocalDate deliveryDate);

	/*
	 * 기존 derived query를 명시 JPQL로 변경 - 메서드명은 그대로 유지 - 내부에서 필요한 order까지 fetch
	 */
	@Query("""
			    select doi
			    from DeliveryOrderIndex doi
			    join fetch doi.order o
			    where doi.deliveryHandler.id = :handlerId
			      and doi.deliveryDate = :deliveryDate
			      and o.id = :orderId
			""")
	Optional<DeliveryOrderIndex> findByDeliveryHandlerIdAndDeliveryDateAndOrderId(@Param("handlerId") Long handlerId,
			@Param("deliveryDate") LocalDate deliveryDate, @Param("orderId") Long orderId);

	@EntityGraph(attributePaths = { "order", "order.orderImages", "order.orderItem", "order.task",
			"order.task.requestedBy", "order.task.requestedBy.company", "order.assignedDeliveryHandler" })
	Optional<DeliveryOrderIndex> findByOrder_Id(Long orderId);

	void deleteByOrder_Id(Long orderId);

	@Query("""
			    select coalesce(max(doi.orderIndex), 0)
			    from DeliveryOrderIndex doi
			    where doi.deliveryHandler.id = :deliveryHandlerId
			      and doi.deliveryDate = :deliveryDate
			""")
	int findMaxOrderIndexByHandlerIdAndDeliveryDate(@Param("deliveryHandlerId") Long deliveryHandlerId,
			@Param("deliveryDate") LocalDate deliveryDate);

	/*
	 * ============================================================ 신규 배송관리 페이지
	 * /team/deliveryManager 전용
	 * ============================================================
	 *
	 * 기준: - 현재 로그인한 배송 담당자 기준 - Order.preferredDeliveryDate 범위 검색 - 상태는 서비스에서
	 * CONFIRMED / PRODUCTION_DONE / DISPATCH_DONE / DELIVERY_DONE 전달 - 직배송 여부는
	 * DeliveryManagerService에서 한 번 더 필터링
	 */
	@Query("""
			    select distinct doi
			    from DeliveryOrderIndex doi
			    join fetch doi.order o
			    left join fetch o.orderItem oi
			    left join fetch o.productCategory pc
			    left join fetch o.task t
			    left join fetch t.requestedBy rb
			    left join fetch rb.company c
			    left join fetch o.assignedDeliveryHandler adh
			    where doi.deliveryHandler.id = :handlerId
			      and o.status in :statuses
			      and (:fromDateTime is null or o.preferredDeliveryDate >= :fromDateTime)
			      and (:toDateTime is null or o.preferredDeliveryDate < :toDateTime)
			    order by
			      o.preferredDeliveryDate asc,
			      doi.orderIndex asc,
			      o.id desc
			""")
	List<DeliveryOrderIndex> findDeliveryManagerBaseRows(@Param("handlerId") Long handlerId,
			@Param("statuses") List<OrderStatus> statuses, @Param("fromDateTime") LocalDateTime fromDateTime,
			@Param("toDateTime") LocalDateTime toDateTime);
}