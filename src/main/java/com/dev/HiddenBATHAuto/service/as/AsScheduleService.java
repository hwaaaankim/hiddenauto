package com.dev.HiddenBATHAuto.service.as;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.as.CalendarEventDto;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.AsTaskSchedule;
import com.dev.HiddenBATHAuto.repository.as.AsTaskRepository;
import com.dev.HiddenBATHAuto.repository.as.AsTaskScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AsScheduleService {

    private final AsTaskRepository asTaskRepository;
    private final AsTaskScheduleRepository scheduleRepository;

    // ✅ 등록/이동 가능 상태: IN_PROGRESS만(=컨펌됨)
    private boolean canSchedule(AsTask task) {
        return task.getStatus() == AsStatus.IN_PROGRESS;
    }

    // ✅ 완료/취소면 삭제/이동 불가
    private boolean canRemoveOrMove(AsTask task) {
        return task.getStatus() != AsStatus.COMPLETED && task.getStatus() != AsStatus.CANCELED;
    }

    @Transactional
    public void registerToDate(Member actor, Long taskId, LocalDate date) {
        AsTask task = asTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("AS 업무 없음: " + taskId));

        if (!canSchedule(task)) {
            throw new IllegalStateException("진행중(IN_PROGRESS) 상태의 업무만 달력에 등록할 수 있습니다.");
        }

        scheduleRepository.findByAsTaskId(taskId).ifPresent(s -> {
            throw new IllegalStateException("이미 달력에 등록된 업무입니다. (재등록하려면 먼저 제거하세요)");
        });

        Integer max = scheduleRepository.findMaxOrderIndexByDate(date);
        int nextIndex = (max == null) ? 0 : (max + 1);

        AsTaskSchedule schedule = AsTaskSchedule.builder()
                .asTask(task)
                .scheduledDate(date)
                .orderIndex(nextIndex)
                .createdBy(actor)
                .build();

        scheduleRepository.save(schedule);
    }

    @Transactional
    public void removeFromCalendar(Long taskId) {
        AsTask task = asTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("AS 업무 없음: " + taskId));

        if (!canRemoveOrMove(task)) {
            throw new IllegalStateException("완료/취소된 업무는 달력에서 제거할 수 없습니다.");
        }

        AsTaskSchedule schedule = scheduleRepository.findByAsTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("달력에 등록되지 않은 업무입니다."));

        LocalDate date = schedule.getScheduledDate();
        scheduleRepository.delete(schedule);

        // ✅ 인덱스 재정렬(빈칸 제거)
        reindexDate(date);
    }

    @Transactional(readOnly = true)
    public List<AsTaskSchedule> getSchedulesByDate(LocalDate date) {
        return scheduleRepository.findByScheduledDateOrderByOrderIndexAsc(date);
    }

    @Transactional
    public void reorderWithinDate(LocalDate date, List<Long> orderedTaskIds) {
        List<AsTaskSchedule> current = scheduleRepository.findByScheduledDateOrderByOrderIndexAsc(date);
        Map<Long, AsTaskSchedule> map = new HashMap<>();
        for (AsTaskSchedule s : current) {
            map.put(s.getAsTask().getId(), s);
        }

        int idx = 0;
        for (Long taskId : orderedTaskIds) {
            AsTaskSchedule s = map.get(taskId);
            if (s == null) continue;
            s.setOrderIndex(idx++);
        }
        scheduleRepository.saveAll(current);
    }

    // ✅ (4) 날짜 → 날짜 이동
    @Transactional
    public void moveToDate(Member actor, Long taskId, LocalDate newDate) {
        AsTask task = asTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("AS 업무 없음: " + taskId));

        if (!canSchedule(task)) {
            throw new IllegalStateException("진행중(IN_PROGRESS) 상태의 업무만 날짜 이동이 가능합니다.");
        }
        if (!canRemoveOrMove(task)) {
            throw new IllegalStateException("완료/취소된 업무는 날짜 이동이 불가능합니다.");
        }

        AsTaskSchedule schedule = scheduleRepository.findByAsTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("달력에 등록되지 않은 업무입니다."));

        LocalDate oldDate = schedule.getScheduledDate();
        if (oldDate != null && oldDate.equals(newDate)) {
            // 같은 날짜면 변경 없음(순서 변경은 reorder로)
            return;
        }

        // 새 날짜의 마지막 인덱스로 붙이기
        Integer max = scheduleRepository.findMaxOrderIndexByDate(newDate);
        int nextIndex = (max == null) ? 0 : (max + 1);

        schedule.setScheduledDate(newDate);
        schedule.setOrderIndex(nextIndex);

        scheduleRepository.save(schedule);

        // 구 날짜는 빈칸 제거
        if (oldDate != null) {
            reindexDate(oldDate);
        }
        // 새 날짜는 이미 마지막에 넣었으니 보통 reindex 불필요하지만,
        // 혹시 기존 데이터가 꼬여있을 경우를 대비해 정렬 보정이 필요하면 아래 라인 주석 해제
        // reindexDate(newDate);
    }

    private void reindexDate(LocalDate date) {
        List<AsTaskSchedule> rest = scheduleRepository.findByScheduledDateOrderByOrderIndexAsc(date);
        int idx = 0;
        for (AsTaskSchedule s : rest) {
            s.setOrderIndex(idx++);
        }
        scheduleRepository.saveAll(rest);
    }

    @Transactional(readOnly = true)
    public List<CalendarEventDto> getCalendarEvents(LocalDate start, LocalDate end) {
        // end는 FullCalendar에서 보통 "exclusive"로 넘어오므로 그대로 사용(>= start, < end)
        List<AsTaskSchedule> list = scheduleRepository.findBetweenDates(start, end);

        List<CalendarEventDto> res = new ArrayList<>();
        for (AsTaskSchedule s : list) {
            AsTask task = s.getAsTask();

            String companyName =
                    (task.getRequestedBy() != null && task.getRequestedBy().getCompany() != null)
                            ? task.getRequestedBy().getCompany().getCompanyName()
                            : "(업체없음)";

            res.add(CalendarEventDto.builder()
                    .taskId(task.getId())
                    .title(companyName)
                    .date(s.getScheduledDate())
                    .status(task.getStatus().name())
                    .build());
        }
        return res;
    }
}
