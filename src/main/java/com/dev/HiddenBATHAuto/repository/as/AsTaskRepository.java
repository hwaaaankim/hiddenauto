package com.dev.HiddenBATHAuto.repository.as;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;

@Repository
public interface AsTaskRepository extends JpaRepository<AsTask, Long> {

	Page<AsTask> findAllByOrderByRequestedAtDesc(Pageable pageable);
	
	@Query("SELECT a FROM AsTask a WHERE a.requestedBy.company.id = :companyId")
	Page<AsTask> findByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

	@Query("""
	    SELECT a FROM AsTask a
	    WHERE a.status IN :statuses
	      AND a.assignedHandler.id = :memberId
	      AND (:asDate IS NULL OR DATE(a.asProcessDate) = :asDate)
	    ORDER BY a.asProcessDate ASC
	""")
	Page<AsTask> findByAssignedHandlerAndStatusInAndDate(Long memberId, List<AsStatus> statuses, LocalDate asDate, Pageable pageable);
	
	@Query("""
		    SELECT a FROM AsTask a
		    WHERE (:statuses IS NULL OR a.status = :statuses)
		      AND (:memberId IS NULL OR a.assignedHandler.id = :memberId)
		      AND (:asDate IS NULL OR a.requestedAt BETWEEN :asDate AND :asDatePlusOne)
		    ORDER BY a.requestedAt DESC
		""")
	Page<AsTask> findByFilter(
	    @Param("memberId") Long memberId,
	    @Param("statuses") AsStatus statuses,
	    @Param("asDate") LocalDate asDate,
	    @Param("asDatePlusOne") LocalDate asDatePlusOne,
	    Pageable pageable
	);
	@Query("""
		    SELECT a FROM AsTask a
		    WHERE (:statuses IS NULL OR a.status = :statuses)
		      AND (:memberId IS NULL OR a.assignedHandler.id = :memberId)
		      AND (:asDateStart IS NULL OR a.requestedAt >= :asDateStart)
		      AND (:asDateEnd IS NULL OR a.requestedAt < :asDateEnd)
		    ORDER BY a.requestedAt DESC
		""")
	Page<AsTask> findByFilterWithDateRange(
	    @Param("memberId") Long memberId,
	    @Param("statuses") AsStatus statuses,
	    @Param("asDateStart") LocalDateTime asDateStart,
	    @Param("asDateEnd") LocalDateTime asDateEnd,
	    Pageable pageable
	);


}
