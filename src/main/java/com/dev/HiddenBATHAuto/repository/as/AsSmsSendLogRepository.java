package com.dev.HiddenBATHAuto.repository.as;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.task.as.AsSmsSendLog;

@Repository
public interface AsSmsSendLogRepository extends JpaRepository<AsSmsSendLog, Long> {

    boolean existsByAsTaskSchedule_IdAndSendDate(Long asTaskScheduleId, LocalDate sendDate);
}