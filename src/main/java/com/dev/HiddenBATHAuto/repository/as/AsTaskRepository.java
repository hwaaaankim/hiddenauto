package com.dev.HiddenBATHAuto.repository.as;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;

@Repository
public interface AsTaskRepository extends JpaRepository<AsTask, Long> {

	Page<AsTask> findAllByOrderByRequestedAtDesc(Pageable pageable);
	
	List<AsTask> findByRequestedBy(Member member);

	List<AsTask> findByRequestedByAndAsProcessDateBetween(Member member, LocalDateTime start, LocalDateTime end);
	
	// AsTaskRepository
	List<AsTask> findByRequestedByAndRequestedAtBetween(Member member, LocalDateTime start, LocalDateTime end);

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
	    WHERE a.requestedBy.company.id = :companyId
	      AND (:start IS NULL OR a.requestedAt >= :start)
	      AND (:end IS NULL OR a.requestedAt <= :end)
	""")
	Page<AsTask> findByCompanyIdAndRequestedAtBetween(
	        @Param("companyId") Long companyId,
	        @Param("start") LocalDateTime start,
	        @Param("end") LocalDateTime end,
	        Pageable pageable);
	
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

	
	@Query("""
		    SELECT a FROM AsTask a
		    WHERE (:statuses IS NULL OR a.status = :statuses)
		      AND (:memberId IS NULL OR a.assignedHandler.id = :memberId)
		      AND (:asDateStart IS NULL OR a.requestedAt >= :asDateStart)
		      AND (:asDateEnd IS NULL OR a.requestedAt < :asDateEnd)
		    ORDER BY a.requestedAt DESC
		""")
	List<AsTask> findByFilterWithDateRangeNonPageable(
	    @Param("memberId") Long memberId,
	    @Param("statuses") AsStatus statuses,
	    @Param("asDateStart") LocalDateTime asDateStart,
	    @Param("asDateEnd") LocalDateTime asDateEnd
	);

	@Query("""
		    SELECT a FROM AsTask a
		    WHERE a.assignedHandler.id = :handlerId
		      AND a.requestedAt >= :start
		      AND a.requestedAt < :end
		    ORDER BY a.requestedAt DESC
		""")
	Page<AsTask> findByAssignedHandlerAndDate(
	    @Param("handlerId") Long handlerId,
	    @Param("start") LocalDateTime start,
	    @Param("end") LocalDateTime end,
	    Pageable pageable
	);
	
	@Query("""
		    SELECT a FROM AsTask a
		    WHERE a.assignedHandler.id = :handlerId
		      AND (:status IS NULL OR a.status = :status)
		      AND (
		           (:dateType = 'requested' AND a.requestedAt BETWEEN :start AND :end)
		        OR (:dateType = 'processed' AND a.asProcessDate BETWEEN :start AND :end)
		      )
		    ORDER BY a.requestedAt DESC
		""")
	Page<AsTask> findAsTasksByFilter(
	    @Param("handlerId") Long handlerId,
	    @Param("status") AsStatus status,
	    @Param("dateType") String dateType, // "requested" or "processed"
	    @Param("start") LocalDateTime start,
	    @Param("end") LocalDateTime end,
	    Pageable pageable
	);
	
	@Query("""
	    SELECT a FROM AsTask a
	    WHERE a.assignedHandler.id = :handlerId
	      AND (:status IS NULL OR a.status = :status)
	      AND a.requestedAt BETWEEN :start AND :end
	    ORDER BY a.requestedAt DESC
	""")
	Page<AsTask> findByRequestedDate(
	    @Param("handlerId") Long handlerId,
	    @Param("status") AsStatus status,
	    @Param("start") LocalDateTime start,
	    @Param("end") LocalDateTime end,
	    Pageable pageable
	);

	@Query("""
	    SELECT a FROM AsTask a
	    WHERE a.assignedHandler.id = :handlerId
	      AND (:status IS NULL OR a.status = :status)
	      AND (:start IS NULL OR a.requestedAt >= :start)
	      AND (:end IS NULL OR a.requestedAt < :end)

	      AND (
	            :companyKeyword IS NULL OR :companyKeyword = '' OR
	            (a.requestedBy.company IS NOT NULL AND a.requestedBy.company.companyName LIKE CONCAT('%', :companyKeyword, '%'))
	      )

	      AND (
	            :provinceNames IS NULL OR a.doName IN :provinceNames
	      )
	      AND (:cityName IS NULL OR :cityName = '' OR a.siName = :cityName)
	      AND (:districtName IS NULL OR :districtName = '' OR a.guName = :districtName)

	    ORDER BY a.requestedAt DESC
	""")
	Page<AsTask> findByRequestedDateFlexible(
	        @Param("handlerId") Long handlerId,
	        @Param("status") AsStatus status,
	        @Param("start") LocalDateTime start,
	        @Param("end") LocalDateTime end,
	        @Param("companyKeyword") String companyKeyword,
	        @Param("provinceNames") List<String> provinceNames,
	        @Param("cityName") String cityName,
	        @Param("districtName") String districtName,
	        Pageable pageable
	);


	@Query("""
	    SELECT a FROM AsTask a
	    WHERE a.assignedHandler.id = :handlerId
	      AND a.asProcessDate IS NOT NULL

	      AND (:status IS NULL OR a.status = :status)
	      AND (:start IS NULL OR a.asProcessDate >= :start)
	      AND (:end IS NULL OR a.asProcessDate < :end)

	      AND (
	            :companyKeyword IS NULL OR :companyKeyword = '' OR
	            (a.requestedBy.company IS NOT NULL AND a.requestedBy.company.companyName LIKE CONCAT('%', :companyKeyword, '%'))
	      )

	      AND (
	            :provinceNames IS NULL OR a.doName IN :provinceNames
	      )
	      AND (:cityName IS NULL OR :cityName = '' OR a.siName = :cityName)
	      AND (:districtName IS NULL OR :districtName = '' OR a.guName = :districtName)

	    ORDER BY a.asProcessDate DESC
	""")
	Page<AsTask> findByProcessedDateFlexible(
	        @Param("handlerId") Long handlerId,
	        @Param("status") AsStatus status,
	        @Param("start") LocalDateTime start,
	        @Param("end") LocalDateTime end,
	        @Param("companyKeyword") String companyKeyword,
	        @Param("provinceNames") List<String> provinceNames,
	        @Param("cityName") String cityName,
	        @Param("districtName") String districtName,
	        Pageable pageable
	);

	
	@Query("""
	    SELECT a
	    FROM AsTaskSchedule s
	    JOIN s.asTask a
	    WHERE a.assignedHandler.id = :handlerId

	      AND (:status IS NULL OR a.status = :status)

	      AND (:startDate IS NULL OR s.scheduledDate >= :startDate)
	      AND (:endDate IS NULL OR s.scheduledDate < :endDate)

	      AND (
	            :companyKeyword IS NULL OR :companyKeyword = '' OR
	            (a.requestedBy.company IS NOT NULL 
	             AND a.requestedBy.company.companyName LIKE CONCAT('%', :companyKeyword, '%'))
	      )

	      AND (
	            :provinceNames IS NULL OR a.doName IN :provinceNames
	      )
	      AND (:cityName IS NULL OR :cityName = '' OR a.siName = :cityName)
	      AND (:districtName IS NULL OR :districtName = '' OR a.guName = :districtName)

	    ORDER BY s.scheduledDate DESC, s.orderIndex ASC
	""")
	Page<AsTask> findByScheduledDateFlexible(
	        @Param("handlerId") Long handlerId,
	        @Param("status") AsStatus status,

	        @Param("startDate") LocalDate startDate,
	        @Param("endDate") LocalDate endDate,

	        @Param("companyKeyword") String companyKeyword,
	        @Param("provinceNames") List<String> provinceNames,
	        @Param("cityName") String cityName,
	        @Param("districtName") String districtName,
	        Pageable pageable
	);

	@Query("""
	    SELECT a FROM AsTask a
	    WHERE a.assignedHandler.id = :handlerId
	      AND (:status IS NULL OR a.status = :status)
	      AND a.asProcessDate BETWEEN :start AND :end
	    ORDER BY a.requestedAt DESC
	""")
	Page<AsTask> findByProcessedDate(
	    @Param("handlerId") Long handlerId,
	    @Param("status") AsStatus status,
	    @Param("start") LocalDateTime start,
	    @Param("end") LocalDateTime end,
	    Pageable pageable
	);

	@Query("""
			SELECT a FROM AsTask a
			WHERE (:statuses IS NULL OR a.status = :statuses)
			  AND (:memberId IS NULL OR a.assignedHandler.id = :memberId)
			  AND (:startDate IS NULL OR a.requestedAt >= :startDate)
			  AND (:endDate IS NULL OR a.requestedAt < :endDate)
			ORDER BY a.requestedAt DESC
		""")
	Page<AsTask> findByRequestedDateRange(
		@Param("memberId") Long memberId,
		@Param("statuses") AsStatus statuses,
		@Param("startDate") LocalDateTime startDate,
		@Param("endDate") LocalDateTime endDate,
		Pageable pageable);

	@Query("""
			SELECT a FROM AsTask a
			WHERE (:statuses IS NULL OR a.status = :statuses)
			  AND (:memberId IS NULL OR a.assignedHandler.id = :memberId)
			  AND (:startDate IS NULL OR a.asProcessDate >= :startDate)
			  AND (:endDate IS NULL OR a.asProcessDate < :endDate)
			ORDER BY a.asProcessDate DESC
		""")
	Page<AsTask> findByProcessedDateRange(
		@Param("memberId") Long memberId,
		@Param("statuses") AsStatus statuses,
		@Param("startDate") LocalDateTime startDate,
		@Param("endDate") LocalDateTime endDate,
		Pageable pageable);

	 // ========= 신청일 기준 (엑셀 전체) =========
    @EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
    @Query("""
        SELECT a FROM AsTask a
        WHERE (:status IS NULL OR a.status = :status)
          AND (:handlerId IS NULL OR a.assignedHandler.id = :handlerId)
          AND (:startDate IS NULL OR a.requestedAt >= :startDate)
          AND (:endDate IS NULL OR a.requestedAt < :endDate)
        ORDER BY a.requestedAt DESC
    """)
    List<AsTask> findByRequestedDateRangeList(
            @Param("handlerId") Long handlerId,
            @Param("status") AsStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // ========= 처리일 기준 (엑셀 전체) =========
    @EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
    @Query("""
        SELECT a FROM AsTask a
        WHERE (:status IS NULL OR a.status = :status)
          AND (:handlerId IS NULL OR a.assignedHandler.id = :handlerId)
          AND (:startDate IS NULL OR a.asProcessDate >= :startDate)
          AND (:endDate IS NULL OR a.asProcessDate < :endDate)
        ORDER BY a.asProcessDate DESC
    """)
    List<AsTask> findByProcessedDateRangeList(
            @Param("handlerId") Long handlerId,
            @Param("status") AsStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // ========= 신청일 기준 (화면 페이지) =========
    @EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
    @Query("""
        SELECT a FROM AsTask a
        WHERE (:status IS NULL OR a.status = :status)
          AND (:handlerId IS NULL OR a.assignedHandler.id = :handlerId)
          AND (:startDate IS NULL OR a.requestedAt >= :startDate)
          AND (:endDate IS NULL OR a.requestedAt < :endDate)
    """)
    Page<AsTask> findByRequestedDateRangePage(
            @Param("handlerId") Long handlerId,
            @Param("status") AsStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    // ========= 처리일 기준 (화면 페이지) =========
    @EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
    @Query("""
        SELECT a FROM AsTask a
        WHERE (:status IS NULL OR a.status = :status)
          AND (:handlerId IS NULL OR a.assignedHandler.id = :handlerId)
          AND (:startDate IS NULL OR a.asProcessDate >= :startDate)
          AND (:endDate IS NULL OR a.asProcessDate < :endDate)
    """)
    Page<AsTask> findByProcessedDateRangePage(
            @Param("handlerId") Long handlerId,
            @Param("status") AsStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
	 // requested/processed 조회용(기존이 있다면 그걸 사용)
    @Query("""
      select t from AsTask t
      left join t.requestedBy rb
      left join rb.company c
      where (:status is null or t.status = :status)
        and (:companyKeyword is null or :companyKeyword = '' or c.companyName like concat('%', :companyKeyword, '%'))
    """)
    Page<AsTask> searchBase(
            @Param("status") AsStatus status,
            @Param("companyKeyword") String companyKeyword,
            Pageable pageable
    );

    // scheduled 조회: 스케줄에 등록된 것만
    @Query("""
      select t from AsTaskSchedule s
      join s.asTask t
      left join t.requestedBy rb
      left join rb.company c
      where (:status is null or t.status = :status)
        and (:companyKeyword is null or :companyKeyword = '' or c.companyName like concat('%', :companyKeyword, '%'))
        and (:startDate is null or s.scheduledDate >= :startDate)
        and (:endDate is null or s.scheduledDate < :endDate) 
    """)
    Page<AsTask> searchByScheduledDate(
            @Param("status") AsStatus status,
            @Param("companyKeyword") String companyKeyword,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate,
            Pageable pageable
    );
}
