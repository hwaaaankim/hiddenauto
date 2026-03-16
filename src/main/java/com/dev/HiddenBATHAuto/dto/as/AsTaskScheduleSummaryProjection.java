package com.dev.HiddenBATHAuto.dto.as;

import java.time.LocalDate;

public interface AsTaskScheduleSummaryProjection {

    Long getTaskId();

    LocalDate getScheduledDate();

    int getOrderIndex();
}