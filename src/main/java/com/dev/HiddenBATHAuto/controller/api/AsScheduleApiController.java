package com.dev.HiddenBATHAuto.controller.api;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.dev.HiddenBATHAuto.dto.as.ScheduleMoveRequest;
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

    @GetMapping("/events")
    public List<Map<String, Object>> events(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        var list = asScheduleService.getCalendarEvents(start, end);

        List<Map<String, Object>> res = new ArrayList<>();
        for (var e : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getTaskId());
            m.put("title", e.getTitle());
            m.put("start", e.getDate().toString());
            m.put("allDay", true);

            String status = e.getStatus(); // "REQUESTED" ...
            List<String> classNames = new ArrayList<>();
            classNames.add("as-management-added-evt");
            classNames.add("as-management-added-evt-" + status);
            m.put("classNames", classNames);

            // ✅ eventContent에서 status badge 그리기용
            m.put("extendedProps", Map.of("status", status));

            res.add(m);
        }
        return res;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody ScheduleCreateRequest req) {

        asScheduleService.registerToDate(principal.getMember(), req.getTaskId(), req.getScheduledDate());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/remove/{taskId}")
    public ResponseEntity<?> remove(@PathVariable Long taskId) {
        asScheduleService.removeFromCalendar(taskId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/date")
    public List<Map<String, Object>> byDate(@RequestParam String date) {

        String d = (date != null && date.length() >= 10) ? date.substring(0, 10) : date;
        LocalDate localDate = LocalDate.parse(d);

        var list = asScheduleService.getSchedulesByDate(localDate);

        List<Map<String, Object>> res = new ArrayList<>();
        for (var s : list) {
            var task = s.getAsTask();

            String companyName =
                    (task.getRequestedBy() != null && task.getRequestedBy().getCompany() != null)
                            ? task.getRequestedBy().getCompany().getCompanyName()
                            : "(업체없음)";

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", task.getId());
            m.put("companyName", companyName);
            m.put("status", task.getStatus().name());
            m.put("requestedAt", task.getRequestedAt());
            m.put("asProcessDate", task.getAsProcessDate());
            m.put("orderIndex", s.getOrderIndex());
            res.add(m);
        }
        return res;
    }

    @PostMapping("/reorder")
    public ResponseEntity<?> reorder(@RequestBody ScheduleReorderRequest req) {
        asScheduleService.reorderWithinDate(req.getScheduledDate(), req.getTaskIdsInOrder());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ✅ (4) 날짜 → 날짜 이동
    @PostMapping("/move")
    public ResponseEntity<?> move(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody ScheduleMoveRequest req) {

        asScheduleService.moveToDate(principal.getMember(), req.getTaskId(), req.getScheduledDate());
        return ResponseEntity.ok(Map.of("ok", true));
    }
}