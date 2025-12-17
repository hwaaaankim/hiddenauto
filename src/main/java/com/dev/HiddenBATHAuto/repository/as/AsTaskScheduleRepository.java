package com.dev.HiddenBATHAuto.repository.as;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.task.AsTaskSchedule;

public interface AsTaskScheduleRepository extends JpaRepository<AsTaskSchedule, Long> {

    Optional<AsTaskSchedule> findByAsTaskId(Long asTaskId);

    List<AsTaskSchedule> findByScheduledDateOrderByOrderIndexAsc(LocalDate date);

    @Query("""
        select s from AsTaskSchedule s
        where s.scheduledDate between :start and :end
    """)
    List<AsTaskSchedule> findBetweenDates(@Param("start") LocalDate start,
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
}