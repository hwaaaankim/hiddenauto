package com.dev.HiddenBATHAuto.controller.api;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.as.ScheduleCreateRequest;
import com.dev.HiddenBATHAuto.dto.as.ScheduleReorderRequest;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.as.AsScheduleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/team/asSchedule")
@PreAuthorize("hasRole('INTERNAL_EMPLOYEE')")
@RequiredArgsConstructor
public class AsScheduleApiController {

	private final AsScheduleService asScheduleService;

	// ✅ 캘린더 이벤트 조회 (FullCalendar용)
	@GetMapping("/events")
	public List<Map<String, Object>> events(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
		// FullCalendar end는 보통 exclusive라서 그대로 받아도 되지만
		// 여기선 inclusive가 편하므로 end.minusDays(1) 처리 여부는 프론트에서 맞추면 됩니다.
		var list = asScheduleService.getCalendarEvents(start, end);
		List<Map<String, Object>> res = new ArrayList<>();
		for (var e : list) {
			Map<String, Object> m = new HashMap<>();
			m.put("id", e.getTaskId());
			m.put("title", e.getTitle());
			m.put("start", e.getDate().toString()); // yyyy-MM-dd
			m.put("allDay", true);
			res.add(m);
		}
		return res;
	}

	// ✅ 드랍 등록
	@PostMapping("/register")
	public ResponseEntity<?> register(@AuthenticationPrincipal PrincipalDetails principal,
			@RequestBody ScheduleCreateRequest req) {
		asScheduleService.registerToDate(principal.getMember(), req.getTaskId(), req.getScheduledDate());
		return ResponseEntity.ok(Map.of("ok", true));
	}

	// ✅ 제거(모달 x 버튼)
	@DeleteMapping("/remove/{taskId}")
	public ResponseEntity<?> remove(@PathVariable Long taskId) {
		asScheduleService.removeFromCalendar(taskId);
		return ResponseEntity.ok(Map.of("ok", true));
	}

	@GetMapping("/date")
	public List<Map<String, Object>> byDate(@RequestParam String date) {

	    // ✅ "2025-11-30" 또는 "2025-11-30T00:00:00+09:00" 등 모두 대응
	    String d = (date != null && date.length() >= 10) ? date.substring(0, 10) : date;
	    LocalDate localDate = LocalDate.parse(d); // yyyy-MM-dd

	    var list = asScheduleService.getSchedulesByDate(localDate);

	    List<Map<String, Object>> res = new ArrayList<>();
	    for (var s : list) {
	        var task = s.getAsTask();

	        String companyName = (task.getRequestedBy() != null && task.getRequestedBy().getCompany() != null)
	                ? task.getRequestedBy().getCompany().getCompanyName() // 환님 코드 기준
	                : "(업체없음)";

	        String address = String.join(" ",
	                Optional.ofNullable(task.getDoName()).orElse(""),
	                Optional.ofNullable(task.getSiName()).orElse(""),
	                Optional.ofNullable(task.getGuName()).orElse(""),
	                Optional.ofNullable(task.getRoadAddress()).orElse(""),
	                Optional.ofNullable(task.getDetailAddress()).orElse("")
	        ).trim();

	        // 아래 2)에서 설명하는 Map.of NPE 방지용으로 HashMap 사용
	        Map<String, Object> m = new LinkedHashMap<>();
	        m.put("taskId", task.getId());
	        m.put("companyName", companyName);
	        m.put("status", task.getStatus().name());
	        m.put("requestedAt", task.getRequestedAt());     // null 가능
	        m.put("asProcessDate", task.getAsProcessDate()); // null 가능
	        m.put("address", address);
	        m.put("orderIndex", s.getOrderIndex());

	        res.add(m);
	    }
	    return res;
	}
	// ✅ 모달: 순서변경 확정
	@PostMapping("/reorder")
	public ResponseEntity<?> reorder(@RequestBody ScheduleReorderRequest req) {
		asScheduleService.reorderWithinDate(req.getScheduledDate(), req.getTaskIdsInOrder());
		return ResponseEntity.ok(Map.of("ok", true));
	}
}