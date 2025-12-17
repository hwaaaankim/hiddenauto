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

	// ✅ 등록 가능 상태
	private boolean canSchedule(AsTask task) {
		return task.getStatus() == AsStatus.REQUESTED || task.getStatus() == AsStatus.IN_PROGRESS;
	}

	// ✅ 완료/취소면 삭제 불가(등록된 뒤 완료로 바뀐 케이스 포함)
	private boolean canRemove(AsTask task) {
		return task.getStatus() != AsStatus.COMPLETED && task.getStatus() != AsStatus.CANCELED;
	}

	@Transactional
	public void registerToDate(Member actor, Long taskId, LocalDate date) {
		AsTask task = asTaskRepository.findById(taskId)
				.orElseThrow(() -> new IllegalArgumentException("AS 업무 없음: " + taskId));

		if (!canSchedule(task)) {
			throw new IllegalStateException("해당 상태의 업무는 달력에 등록할 수 없습니다: " + task.getStatus());
		}

		// 이미 등록됨
		scheduleRepository.findByAsTaskId(taskId).ifPresent(s -> {
			throw new IllegalStateException("이미 달력에 등록된 업무입니다.");
		});

		Integer max = scheduleRepository.findMaxOrderIndexByDate(date);
		int nextIndex = (max == null) ? 0 : (max + 1);

		AsTaskSchedule schedule = AsTaskSchedule.builder().asTask(task).scheduledDate(date).orderIndex(nextIndex)
				.createdBy(actor).build();

		scheduleRepository.save(schedule);
	}

	@Transactional
	public void removeFromCalendar(Long taskId) {
		AsTask task = asTaskRepository.findById(taskId)
				.orElseThrow(() -> new IllegalArgumentException("AS 업무 없음: " + taskId));

		if (!canRemove(task)) {
			throw new IllegalStateException("완료/취소된 업무는 달력에서 제거할 수 없습니다.");
		}

		AsTaskSchedule schedule = scheduleRepository.findByAsTaskId(taskId)
				.orElseThrow(() -> new IllegalArgumentException("달력에 등록되지 않은 업무입니다."));

		LocalDate date = schedule.getScheduledDate();
		int removedIndex = schedule.getOrderIndex();

		scheduleRepository.delete(schedule);

		// ✅ 인덱스 땡기기(빈칸 제거)
		List<AsTaskSchedule> rest = scheduleRepository.findByScheduledDateOrderByOrderIndexAsc(date);
		int idx = 0;
		for (AsTaskSchedule s : rest) {
			s.setOrderIndex(idx++);
		}
		// saveAll로 한 번에
		scheduleRepository.saveAll(rest);
	}

	@Transactional(readOnly = true)
	public List<AsTaskSchedule> getSchedulesByDate(LocalDate date) {
		return scheduleRepository.findByScheduledDateOrderByOrderIndexAsc(date);
	}

	@Transactional
	public void reorderWithinDate(LocalDate date, List<Long> orderedTaskIds) {
		// 현재 date에 있는 스케줄만 대상으로 안전하게 재정렬
		List<AsTaskSchedule> current = scheduleRepository.findByScheduledDateOrderByOrderIndexAsc(date);
		Map<Long, AsTaskSchedule> map = new HashMap<>();
		for (AsTaskSchedule s : current)
			map.put(s.getAsTask().getId(), s);

		int idx = 0;
		for (Long taskId : orderedTaskIds) {
			AsTaskSchedule s = map.get(taskId);
			if (s == null)
				continue; // 다른 날짜/없는 taskId는 무시
			s.setOrderIndex(idx++);
		}
		scheduleRepository.saveAll(current);
	}

	@Transactional(readOnly = true)
	public List<CalendarEventDto> getCalendarEvents(LocalDate start, LocalDate end) {
		List<AsTaskSchedule> list = scheduleRepository.findBetweenDates(start, end);
		List<CalendarEventDto> res = new ArrayList<>();
		for (AsTaskSchedule s : list) {
			String companyName = (s.getAsTask().getRequestedBy() != null
					&& s.getAsTask().getRequestedBy().getCompany() != null)
							? s.getAsTask().getRequestedBy().getCompany().getCompanyName()
							: "(업체없음)";
			res.add(CalendarEventDto.builder().taskId(s.getAsTask().getId()).title(companyName) // ✅ 업체명만
					.date(s.getScheduledDate()).build());
		}
		return res;
	}
}