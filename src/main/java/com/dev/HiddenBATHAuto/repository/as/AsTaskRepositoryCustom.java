package com.dev.HiddenBATHAuto.repository.as;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;

public interface AsTaskRepositoryCustom {

    default Page<AsTask> findByRequestedDateFlexible(Long handlerId, AsStatus status,
            LocalDateTime start, LocalDateTime end,
            String companyKeyword, List<String> provinceNames, String cityName, String districtName,
            String visitTimeSort, Pageable pageable) {

        return findByRequestedDateFlexible(handlerId, status, start, end,
                companyKeyword, provinceNames, cityName, districtName,
                visitTimeSort, null, pageable);
    }

    Page<AsTask> findByRequestedDateFlexible(Long handlerId, AsStatus status,
            LocalDateTime start, LocalDateTime end,
            String companyKeyword, List<String> provinceNames, String cityName, String districtName,
            String visitTimeSort, String scheduledDateSort, Pageable pageable);

    default Page<AsTask> findByProcessedDateFlexible(Long handlerId, AsStatus status,
            LocalDateTime start, LocalDateTime end,
            String companyKeyword, List<String> provinceNames, String cityName, String districtName,
            String visitTimeSort, Pageable pageable) {

        return findByProcessedDateFlexible(handlerId, status, start, end,
                companyKeyword, provinceNames, cityName, districtName,
                visitTimeSort, null, pageable);
    }

    Page<AsTask> findByProcessedDateFlexible(Long handlerId, AsStatus status,
            LocalDateTime start, LocalDateTime end,
            String companyKeyword, List<String> provinceNames, String cityName, String districtName,
            String visitTimeSort, String scheduledDateSort, Pageable pageable);

    default Page<AsTask> findByScheduledDateFlexible(Long handlerId, AsStatus status,
            LocalDate startDate, LocalDate endDate,
            String companyKeyword, List<String> provinceNames, String cityName, String districtName,
            String visitTimeSort, Pageable pageable) {

        return findByScheduledDateFlexible(handlerId, status, startDate, endDate,
                companyKeyword, provinceNames, cityName, districtName,
                visitTimeSort, null, pageable);
    }

    Page<AsTask> findByScheduledDateFlexible(Long handlerId, AsStatus status,
            LocalDate startDate, LocalDate endDate,
            String companyKeyword, List<String> provinceNames, String cityName, String districtName,
            String visitTimeSort, String scheduledDateSort, Pageable pageable);
}