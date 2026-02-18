package com.dev.HiddenBATHAuto.repository.order;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Task;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>{

    // ✅ 신청일 기준에서도 모달 상세에 orders가 필요하므로 fetch join
    @Query("select distinct t from Task t left join fetch t.orders o where t.requestedBy = :member")
    List<Task> findByRequestedByFetchOrders(@Param("member") Member member);

    // ✅ 처리일 기준: preferredDeliveryDate 있는 주문이 있는 Task만 + fetch join
    @Query("""
        select distinct t
        from Task t
        join fetch t.orders o
        where t.requestedBy = :member
          and o.preferredDeliveryDate is not null
    """)
    List<Task> findByRequestedByAndPreferredDeliveryNotNullFetchOrders(@Param("member") Member member);
	
	Page<Task> findAllByOrderByIdDesc(Pageable pageable);
	
	List<Task> findByRequestedBy(Member member);
	
	@Query("SELECT t FROM Task t WHERE t.requestedBy.company.id = :companyId")
	Page<Task> findByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

	List<Task> findByRequestedByAndCreatedAtBetween(Member member, LocalDateTime start, LocalDateTime end);
	
	@Query("""
	    SELECT t FROM Task t 
	    WHERE t.requestedBy.company.id = :companyId
	      AND (:start IS NULL OR t.createdAt >= :start)
	      AND (:end IS NULL OR t.createdAt <= :end)
	""")
	Page<Task> findByCompanyIdAndCreatedAtBetween(
	        @Param("companyId") Long companyId,
	        @Param("start") LocalDateTime start,
	        @Param("end") LocalDateTime end,
	        Pageable pageable
	);
	
}
