package com.dev.HiddenBATHAuto.repository.as;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.dto.as.AsTaskScheduleSummaryProjection;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTaskSchedule;

public interface AsTaskScheduleRepository extends JpaRepository<AsTaskSchedule, Long> {

	@Query("select s.asTask.id as taskId, s.scheduledDate as scheduledDate, s.orderIndex as orderIndex " +
	           "from AsTaskSchedule s " +
	           "where s.asTask.id in :taskIds")
    List<AsTaskScheduleSummaryProjection> findSummariesByTaskIdIn(@Param("taskIds") List<Long> taskIds);
	
	@Query("select coalesce(max(s.orderIndex), -1) from AsTaskSchedule s where s.scheduledDate = :scheduledDate")
	Integer findMaxOrderIndexByScheduledDate(@Param("scheduledDate") LocalDate scheduledDate);
	
	List<AsTaskSchedule> findByAsTask_Id(Long asTaskId);
	
	/**
     * 화면 표시용 최소 필드만 프로젝션으로 조회 (Lazy 이슈/불필요 fetch 방지)
     */
    interface AsTaskScheduleSimpleView {
        Long getAsTaskId();
        java.time.LocalDate getScheduledDate();
    }

    @Query("""
        select s.asTask.id as asTaskId,
               s.scheduledDate as scheduledDate
          from AsTaskSchedule s
         where s.asTask.id in :taskIds
    """)
    List<AsTaskScheduleSimpleView> findSimpleByAsTaskIdIn(@Param("taskIds") Collection<Long> taskIds);
	
    Optional<AsTaskSchedule> findByAsTaskId(Long asTaskId);

    List<AsTaskSchedule> findByScheduledDateOrderByOrderIndexAsc(LocalDate date);

    // ✅ 기존 유지(누락 없이 그대로 둠)
    @Query("""
        select s from AsTaskSchedule s
        where s.scheduledDate between :start and :end
    """)
    List<AsTaskSchedule> findBetweenDates(@Param("start") LocalDate start,
                                          @Param("end") LocalDate end);

    // ✅ FullCalendar용(권장): end exclusive + 정렬
    @Query("""
        select s
        from AsTaskSchedule s
        where s.scheduledDate >= :start
          and s.scheduledDate <  :end
        order by s.scheduledDate asc, s.orderIndex asc
    """)
    List<AsTaskSchedule> findBetweenDatesForCalendar(@Param("start") LocalDate start,
                                                     @Param("end") LocalDate end);

    @Query("""
        select max(s.orderIndex) from AsTaskSchedule s
        where s.scheduledDate = :date
    """)
    Integer findMaxOrderIndexByDate(@Param("date") LocalDate date);

    @Modifying
    @Query("""
        delete from AsTaskSchedule s
        where s.asTask.id = :taskId
    """)
    int deleteByTaskId(@Param("taskId") Long taskId);

    @Query("""
      select s from AsTaskSchedule s
      where s.asTask.id in :taskIds
    """)
    List<AsTaskSchedule> findByTaskIds(@Param("taskIds") List<Long> taskIds);

    @Query("""
	    select s
	    from AsTaskSchedule s
	    join fetch s.asTask t
	    left join fetch t.requestedBy
	    left join fetch t.assignedHandler
	    where s.scheduledDate = :scheduledDate
	      and t.status = :status
	""")
	List<AsTaskSchedule> findSchedulesByDateAndTaskStatus(
	        @Param("scheduledDate") LocalDate scheduledDate,
	        @Param("status") AsStatus status
	);
}