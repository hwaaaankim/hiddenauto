package com.dev.HiddenBATHAuto.controller.api;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.CalendarEventDTO;
import com.dev.HiddenBATHAuto.dto.calendar.CalendarOverviewDTO;
import com.dev.HiddenBATHAuto.dto.calendar.CalendarWorkWindowDTO;
import com.dev.HiddenBATHAuto.dto.calendar.CalendarTaskDetailDTO;
import com.dev.HiddenBATHAuto.dto.employeeDetail.ConflictDTO;
import com.dev.HiddenBATHAuto.dto.employeeDetail.RegionSelectionDTO;
import com.dev.HiddenBATHAuto.enums.CalendarDateBasis;
import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.model.auth.Province;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.AsTaskSchedule;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.as.AsTaskRepository;
import com.dev.HiddenBATHAuto.repository.as.AsTaskScheduleRepository;
import com.dev.HiddenBATHAuto.repository.auth.CityRepository;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductColorRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductOptionPositionRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductSeriesRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.dev.HiddenBATHAuto.service.auth.MemberManagementService;
import com.dev.HiddenBATHAuto.service.auth.MemberService;
import com.dev.HiddenBATHAuto.service.auth.MemberValidationService;
import com.dev.HiddenBATHAuto.service.auth.RegionExcelService;
import com.dev.HiddenBATHAuto.service.calculate.excel.FlapExcelUploadService;
import com.dev.HiddenBATHAuto.service.calculate.excel.LowCalculateExcelService;
import com.dev.HiddenBATHAuto.service.calculate.excel.MarbleLowCalculateExcelService;
import com.dev.HiddenBATHAuto.service.calculate.excel.MirrorStandardPriceExcelService;
import com.dev.HiddenBATHAuto.service.calculate.excel.MirrorUnstandardExcelUploadService;
import com.dev.HiddenBATHAuto.service.calculate.excel.SlideExcelUploadService;
import com.dev.HiddenBATHAuto.service.calculate.excel.TopExcelUploadService;
import com.dev.HiddenBATHAuto.service.nonstandard.ExcelUploadService;
import com.dev.HiddenBATHAuto.utils.OptionTranslator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class APIController {

	private final MemberService memberService;
	private final ExcelUploadService excelUploadService;
	private final RegionExcelService regionExcelService;
	private final MemberValidationService memberValidationService;
	private final TopExcelUploadService topExcelUploadService;
	private final LowCalculateExcelService excelService;
	private final MarbleLowCalculateExcelService marbleExcelService;
	private final FlapExcelUploadService flapExcelUploadService;
	private final SlideExcelUploadService slideExcelUplaodService;
	private final MirrorStandardPriceExcelService mirrorStandardPriceExcelService;
	private final MirrorUnstandardExcelUploadService mirrorSeriesExcelUploadService;
	private final ProductSeriesRepository seriesRepo;
	private final ProductRepository productRepo;
	private final ProductColorRepository colorRepo;
	private final ProductOptionPositionRepository optionRepo;
	private final CityRepository cityRepository;
	private final DistrictRepository districtRepository;
	private final AsTaskRepository asTaskRepository;
	private final TaskRepository taskRepository;
	private final AsTaskScheduleRepository asTaskScheduleRepository;

	private final MemberManagementService memberManagementService;
	// ✅ 신규: province 목록 조회용
	private final ProvinceRepository provinceRepository;
	private final CompanyRepository companyRepository;

	private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final ObjectMapper CALENDAR_JSON_MAPPER = new ObjectMapper();

	@GetMapping("/validate/businessNumber")
	public ResponseEntity<Map<String, Object>> validateBusinessNumber(
			@RequestParam("businessNumber") String businessNumber) {
		String digits = (businessNumber == null) ? "" : businessNumber.replaceAll("\\D", "");

		Map<String, Object> result = new HashMap<>();
		// ✅ 형식이 아예 틀리면 duplicate=false로 내려주고 프론트에서 길이검증
		if (digits.length() != 10) {
			result.put("duplicate", false);
			result.put("normalized", digits);
			return ResponseEntity.ok(result);
		}

		boolean duplicate = companyRepository.existsByBusinessNumber(digits);
		result.put("duplicate", duplicate);
		result.put("normalized", digits);
		return ResponseEntity.ok(result);
	}

	@PostMapping("/region/conflicts/check-new")
	public ResponseEntity<List<ConflictDTO>> checkRegionConflictsForNewMember(
			@RequestBody NewMemberRegionCheckRequest req) {
		List<ConflictDTO> conflicts = memberManagementService.checkRegionConflictsForNewMember(req.getTeamId(),
				req.getSelections());
		return ResponseEntity.ok(conflicts);
	}

	@Data
	public static class NewMemberRegionCheckRequest {
		private Long teamId;
		private List<RegionSelectionDTO> selections;
	}

	// ✅✅ (신규) Province 전체 목록
	@GetMapping("/provinces")
	@ResponseBody
	public List<Province> getProvinces() {
		// 정렬이 필요하면 findAllByOrderByNameAsc() 사용
		return provinceRepository.findAllByOrderByNameAsc();
	}

	@GetMapping("/province/{provinceId}/cities")
	@ResponseBody
	public List<City> getCitiesByProvince(@PathVariable Long provinceId) {
		return cityRepository.findByProvinceId(provinceId);
	}

	@GetMapping("/province/{provinceId}/districts")
	@ResponseBody
	public List<District> getDistrictsByProvince(@PathVariable Long provinceId) {
		return districtRepository.findByProvinceIdAndCityIsNull(provinceId);
	}

	@GetMapping("/city/{cityId}/districts")
	@ResponseBody
	public List<District> getDistrictsByCity(@PathVariable Long cityId) {
		return districtRepository.findByCityId(cityId);
	}

	@PostMapping("/mirrorSeriesExcelUpload")
	public ResponseEntity<String> uploadMirrorSeriesExcel(@RequestParam("file") MultipartFile file) {
		try {
			mirrorSeriesExcelUploadService.uploadExcel(file);
			return ResponseEntity.ok("✅ 거울 시리즈 엑셀 업로드 성공");
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(500).body("❌ 업로드 실패: " + e.getMessage());
		}
	}

	@PostMapping("/mirrorStandardExcelUpload")
	public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
		try {
			mirrorStandardPriceExcelService.uploadStandardPriceExcel(file);
			return ResponseEntity.ok("✅ 거울 규격 가격표 업로드 성공");
		} catch (Exception e) {
			return ResponseEntity.status(500).body("❌ 업로드 실패: " + e.getMessage());
		}
	}

	@PostMapping("/slideExcelUpload")
	public ResponseEntity<Map<String, Object>> uploadSlideExcel(@RequestParam("file") MultipartFile file) {
		Map<String, Object> result = new HashMap<>();
		try {
			slideExcelUplaodService.uploadSlideExcel(file);
			result.put("success", true);
			result.put("message", "✅ 슬라이드장 엑셀 업로드 및 DB 저장 완료");
		} catch (Exception e) {
			e.printStackTrace();
			result.put("success", false);
			result.put("message", "❌ 업로드 실패: " + e.getMessage());
		}

		return ResponseEntity.ok(result);
	}

	@PostMapping("/flapExcelUpload")
	public ResponseEntity<Map<String, Object>> uploadFlapExcel(@RequestParam("file") MultipartFile file) {
		Map<String, Object> result = new HashMap<>();
		try {
			flapExcelUploadService.uploadFlapExcel(file);
			result.put("success", true);
			result.put("message", "✅ 플랩장 엑셀 업로드 및 DB 저장 완료");
		} catch (Exception e) {
			e.printStackTrace();
			result.put("success", false);
			result.put("message", "❌ 업로드 실패: " + e.getMessage());
		}

		return ResponseEntity.ok(result);
	}

	@PostMapping("/marbleLowExcelUpload")
	public ResponseEntity<String> marbleLowExcelUpload(@RequestParam("file") MultipartFile file) {
		try (InputStream inputStream = file.getInputStream()) {
			marbleExcelService.uploadExcel(inputStream);
			return ResponseEntity.ok("✅ 마블 하부장 엑셀 업로드 및 DB 저장 완료");
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(500).body("❌ 업로드 실패: " + e.getMessage());
		}
	}

	@PostMapping("/topExcelUpload")
	public ResponseEntity<Map<String, Object>> uploadTopExcel(@RequestParam("file") MultipartFile file) {
		Map<String, Object> result = new HashMap<>();
		try {
			topExcelUploadService.uploadTopExcel(file);
			result.put("success", true);
			result.put("message", "엑셀 업로드가 성공적으로 완료되었습니다.");
		} catch (IOException e) {
			result.put("success", false);
			result.put("message", "엑셀 파일 처리 중 오류가 발생했습니다: " + e.getMessage());
		}

		return ResponseEntity.ok(result);
	}

	@PostMapping("/lowExcelUpload")
	public ResponseEntity<String> uploadExcel(@RequestParam("file") MultipartFile file) {
		try (InputStream inputStream = file.getInputStream()) {
			excelService.uploadExcel(inputStream);
			return ResponseEntity.ok("✅ 엑셀 업로드 및 DB 저장 완료");
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(500).body("❌ 업로드 실패: " + e.getMessage());
		}
	}

	@PostMapping("/join")
	@ResponseBody
	public String adminJoin(Member member) {
		memberService.insertMember(member);
		return "success";
	}

	@PostMapping("/resetExcelUpload")
	@ResponseBody
	public List<String> addExcelUpload(MultipartFile file, Model model) throws IOException {
		return excelUploadService.uploadExcel(file);
	}

	@PostMapping("/regionExcelUpload")
	public ResponseEntity<String> regionExcelUpload(@RequestParam("file") MultipartFile file) {
		try {
			regionExcelService.uploadRegionExcel(file);
			return ResponseEntity.ok("엑셀 업로드 및 저장 완료");
		} catch (Exception e) {
			return ResponseEntity.status(500).body("업로드 실패: " + e.getMessage());
		}
	}

	@PostMapping("/translate")
	@ResponseBody
	public Map<String, String> translateOption(@RequestBody Map<String, Object> optionJson)
			throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		String json = mapper.writeValueAsString(optionJson);

		return OptionTranslator.getLocalizedOptionMap(json, seriesRepo, productRepo, colorRepo, optionRepo);
	}

	@GetMapping("/validate/username")
	@ResponseBody
	public Map<String, Boolean> checkUsernameDuplicate(@RequestParam String username) {
		boolean duplicate = memberValidationService.isUsernameDuplicate(username);
		Map<String, Boolean> response = new HashMap<>();
		response.put("duplicate", duplicate);
		return response;
	}

	@GetMapping("/validate/phone")
	@ResponseBody
	public Map<String, Boolean> checkPhoneDuplicate(@RequestParam String phone) {
		boolean duplicate = memberValidationService.isPhoneDuplicate(phone);
		Map<String, Boolean> response = new HashMap<>();
		response.put("duplicate", duplicate);
		return response;
	}

	@GetMapping("/calendar/events")
	@ResponseBody
	public List<CalendarEventDTO> getCalendarEvents(
			@AuthenticationPrincipal PrincipalDetails principalDetails,
			@RequestParam(value = "basis", required = false) String basisParam,
			@RequestParam(value = "start", required = false) String startParam,
			@RequestParam(value = "end", required = false) String endParam) {

		CalendarDateBasis basis = CalendarDateBasis.from(basisParam);
		Member member = principalDetails.getMember();

		LocalDate start = parseDateOrNull(startParam);
		LocalDate endExclusive = parseDateOrNull(endParam);
		boolean hasRange = isValidRange(start, endExclusive);

		log.info("[CalendarEvents] basis={}, start={}, end={}, requester={}",
				basis, startParam, endParam, (member != null ? member.getUsername() : "비로그인"));

		List<AsTask> asTasks = hasRange
				? loadAsTasksForCalendarRange(member, basis, start, endExclusive)
				: loadAllAsTasksForCalendar(member, basis);

		List<Task> tasks = hasRange
				? loadTasksForCalendarRange(member, basis, start, endExclusive)
				: loadAllTasksForCalendar(member, basis);

		Map<LocalDate, List<AsTask>> asMap = asTasks.stream()
				.map(t -> new AbstractMap.SimpleEntry<>(extractAsDate(t, basis), t))
				.filter(e -> e.getKey() != null)
				.filter(e -> !hasRange || isDateInRange(e.getKey(), start, endExclusive))
				.collect(Collectors.groupingBy(Map.Entry::getKey,
						Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

		Map<LocalDate, List<Task>> taskMap = tasks.stream()
				.map(t -> new AbstractMap.SimpleEntry<>(extractTaskDate(t, basis), t))
				.filter(e -> e.getKey() != null)
				.filter(e -> !hasRange || isDateInRange(e.getKey(), start, endExclusive))
				.collect(Collectors.groupingBy(Map.Entry::getKey,
						Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

		Set<LocalDate> allDates = new HashSet<>();
		allDates.addAll(asMap.keySet());
		allDates.addAll(taskMap.keySet());

		List<CalendarEventDTO> result = new ArrayList<>();
		for (LocalDate date : allDates) {
			int asCount = asMap.getOrDefault(date, List.of()).size();
			int taskCount = taskMap.getOrDefault(date, List.of()).size();
			result.add(new CalendarEventDTO(date.toString(), asCount, taskCount));
		}

		result.sort(Comparator.comparing(CalendarEventDTO::getDate));
		return result;
	}

	/**
	 * index 메인 오버뷰.
	 *
	 * <p>start는 inclusive, end는 exclusive입니다.
	 * FullCalendar의 현재 표시 기간을 그대로 받아 달력과 동일한 범위만 집계합니다.</p>
	 */
	@GetMapping("/calendar/overview")
	@ResponseBody
	public CalendarOverviewDTO getCalendarOverview(
			@AuthenticationPrincipal PrincipalDetails principalDetails,
			@RequestParam(value = "basis", required = false) String basisParam,
			@RequestParam("start") String startParam,
			@RequestParam("end") String endParam) {

		CalendarDateBasis basis = CalendarDateBasis.from(basisParam);
		LocalDate start = LocalDate.parse(startParam);
		LocalDate endExclusive = LocalDate.parse(endParam);

		if (!isValidRange(start, endExclusive)) {
			throw new IllegalArgumentException("달력 조회 기간이 올바르지 않습니다.");
		}

		Member member = principalDetails.getMember();
		log.info("[CalendarOverview] basis={}, start={}, end={}, requester={}",
				basis, start, endExclusive, (member != null ? member.getUsername() : "비로그인"));

		List<AsTask> asTasks = loadAsTasksForCalendarRange(member, basis, start, endExclusive);
		List<Task> tasks = loadTasksForCalendarRange(member, basis, start, endExclusive).stream()
				.filter(t -> isDateInRange(extractTaskDate(t, basis), start, endExclusive))
				.toList();

		CalendarOverviewDTO dto = new CalendarOverviewDTO();
		dto.setBasis(basis.name());
		dto.setStartDate(start.toString());
		dto.setEndDate(endExclusive.minusDays(1).toString());
		dto.setOrder(buildOrderOverview(tasks));
		dto.setAs(buildAsOverview(asTasks));
		return dto;
	}


	/**
	 * index 메인 최근/예정 7일 업무 흐름.
	 *
	 * <p>최근 처리완료는 오늘 포함 과거 7일입니다.
	 * - 발주: DELIVERY_DONE 상태이며 배송완료 처리 시각으로 저장되는 Order.updatedAt 기준
	 * - AS: COMPLETED 상태이며 AsTask.asProcessDate 기준</p>
	 *
	 * <p>앞으로 처리예정은 오늘 포함 향후 7일입니다.
	 * - 발주: preferredDeliveryDate 기준, DELIVERY_DONE/CANCELED 제외
	 * - AS: AsTaskSchedule.scheduledDate 기준, COMPLETED/CANCELED 제외</p>
	 */
	@GetMapping("/calendar/work-window")
	@ResponseBody
	public CalendarWorkWindowDTO getCalendarWorkWindow(
			@AuthenticationPrincipal PrincipalDetails principalDetails) {

		Member member = principalDetails.getMember();
		LocalDate today = LocalDate.now();

		LocalDate recentStart = today.minusDays(6);
		LocalDate recentEndExclusive = today.plusDays(1);

		LocalDate upcomingStart = today;
		LocalDate upcomingEndExclusive = today.plusDays(7);

		log.info("[CalendarWorkWindow] recent={}~{}, upcoming={}~{}, requester={}",
				recentStart, recentEndExclusive.minusDays(1),
				upcomingStart, upcomingEndExclusive.minusDays(1),
				(member != null ? member.getUsername() : "비로그인"));

		List<CalendarWorkWindowDTO.WorkItemDTO> recentItems = new ArrayList<>();
		List<CalendarWorkWindowDTO.WorkItemDTO> upcomingItems = new ArrayList<>();

		LocalDateTime recentStartAt = recentStart.atStartOfDay();
		LocalDateTime recentEndAt = recentEndExclusive.atStartOfDay();

		List<Task> recentDeliveredTasks = taskRepository.findIndexRecentDeliveryCompletedRange(
				member,
				OrderStatus.DELIVERY_DONE,
				recentStartAt,
				recentEndAt);

		for (Task task : recentDeliveredTasks) {
			if (task == null || task.getOrders() == null) {
				continue;
			}
			for (Order order : task.getOrders()) {
				if (order == null
						|| order.getStatus() != OrderStatus.DELIVERY_DONE
						|| order.getUpdatedAt() == null
						|| order.getUpdatedAt().isBefore(recentStartAt)
						|| !order.getUpdatedAt().isBefore(recentEndAt)) {
					continue;
				}
				recentItems.add(toRecentOrderWorkItem(task, order));
			}
		}

		LocalDateTime recentAsEndInclusive = recentEndAt.minusNanos(1);
		asTaskRepository.findByRequestedByAndAsProcessDateBetween(member, recentStartAt, recentAsEndInclusive)
				.stream()
				.filter(t -> t != null && t.getStatus() == AsStatus.COMPLETED && t.getAsProcessDate() != null)
				.map(this::toRecentAsWorkItem)
				.forEach(recentItems::add);

		LocalDateTime upcomingStartAt = upcomingStart.atStartOfDay();
		LocalDateTime upcomingEndAt = upcomingEndExclusive.atStartOfDay();

		List<Task> upcomingOrderTasks = taskRepository.findIndexUpcomingDeliveryRange(
				member,
				upcomingStartAt,
				upcomingEndAt,
				List.of(OrderStatus.DELIVERY_DONE, OrderStatus.CANCELED));

		for (Task task : upcomingOrderTasks) {
			if (task == null || task.getOrders() == null) {
				continue;
			}
			for (Order order : task.getOrders()) {
				LocalDateTime preferred = order != null ? order.getPreferredDeliveryDate() : null;
				if (order == null
						|| preferred == null
						|| preferred.isBefore(upcomingStartAt)
						|| !preferred.isBefore(upcomingEndAt)
						|| order.getStatus() == OrderStatus.DELIVERY_DONE
						|| order.getStatus() == OrderStatus.CANCELED) {
					continue;
				}
				upcomingItems.add(toUpcomingOrderWorkItem(task, order));
			}
		}

		List<AsTaskSchedule> upcomingAsSchedules = asTaskScheduleRepository.findIndexUpcomingSchedules(
				member,
				upcomingStart,
				upcomingEndExclusive,
				List.of(AsStatus.COMPLETED, AsStatus.CANCELED));

		for (AsTaskSchedule schedule : upcomingAsSchedules) {
			if (schedule == null || schedule.getAsTask() == null || schedule.getScheduledDate() == null) {
				continue;
			}
			upcomingItems.add(toUpcomingAsWorkItem(schedule));
		}

		recentItems.sort(Comparator
				.comparing(CalendarWorkWindowDTO.WorkItemDTO::getDateTime,
						Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
				.reversed()
				.thenComparing(CalendarWorkWindowDTO.WorkItemDTO::getType,
						Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

		upcomingItems.sort(Comparator
				.comparing(CalendarWorkWindowDTO.WorkItemDTO::getDateTime,
						Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
				.thenComparing(CalendarWorkWindowDTO.WorkItemDTO::getType,
						Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

		long recentOrderCount = recentItems.stream().filter(i -> "ORDER".equals(i.getType())).count();
		long recentAsCount = recentItems.stream().filter(i -> "AS".equals(i.getType())).count();
		long upcomingOrderCount = upcomingItems.stream().filter(i -> "ORDER".equals(i.getType())).count();
		long upcomingAsCount = upcomingItems.stream().filter(i -> "AS".equals(i.getType())).count();

		CalendarWorkWindowDTO out = new CalendarWorkWindowDTO();
		out.setToday(today.toString());
		out.setRecentStartDate(recentStart.toString());
		out.setRecentEndDate(recentEndExclusive.minusDays(1).toString());
		out.setUpcomingStartDate(upcomingStart.toString());
		out.setUpcomingEndDate(upcomingEndExclusive.minusDays(1).toString());

		out.setRecentCompletedCount(recentItems.size());
		out.setRecentOrderCount(recentOrderCount);
		out.setRecentAsCount(recentAsCount);
		out.setUpcomingCount(upcomingItems.size());
		out.setUpcomingOrderCount(upcomingOrderCount);
		out.setUpcomingAsCount(upcomingAsCount);

		out.setRecentCompleted(recentItems);
		out.setUpcoming(upcomingItems);
		return out;
	}

	/**
	 * ✅ 모달 상세
	 * - basis=REQUEST(기본): date는 신청일 기준
	 * - basis=PROCESS: date는 처리일 기준 (NULL 제외)
	 *
	 * ✅ 기존 DTO 구조와 필드명을 유지하여 기존 기능을 깨지 않습니다.
	 */
	@GetMapping("/calendar/tasks")
	@ResponseBody
	public List<CalendarTaskDetailDTO> getCalendarTasks(
			@AuthenticationPrincipal PrincipalDetails principalDetails,
			@RequestParam("date") String dateStr,
			@RequestParam(value = "basis", required = false) String basisParam) {

		CalendarDateBasis basis = CalendarDateBasis.from(basisParam);
		LocalDate target = LocalDate.parse(dateStr);
		LocalDate endExclusive = target.plusDays(1);

		Member member = principalDetails.getMember();
		log.info("[CalendarTasks] basis={}, date={}, requester={}", basis, dateStr,
				member != null ? member.getUsername() : "비로그인");

		List<CalendarTaskDetailDTO> out = new ArrayList<>();

		List<AsTask> asOnDate = loadAsTasksForCalendarRange(member, basis, target, endExclusive).stream()
				.filter(t -> target.equals(extractAsDate(t, basis)))
				.toList();

		Map<Long, LocalDate> scheduleMap = new HashMap<>();
		if (!asOnDate.isEmpty()) {
			List<Long> ids = asOnDate.stream().map(AsTask::getId).toList();
			asTaskScheduleRepository.findSimpleByAsTaskIdIn(ids).forEach(v ->
					scheduleMap.put(v.getAsTaskId(), v.getScheduledDate()));
		}

		asOnDate.forEach(t -> out.add(toAsDetailDTO(t, basis, scheduleMap.get(t.getId()))));

		loadTasksForCalendarRange(member, basis, target, endExclusive).stream()
				.filter(t -> target.equals(extractTaskDate(t, basis)))
				.forEach(t -> out.add(toTaskDetailDTO(t, basis)));

		// 기존 순서 유지: AS -> TASK
		out.sort(Comparator.comparing(CalendarTaskDetailDTO::getType));
		return out;
	}

	// =========================================================
	// index 달력 범위 조회
	// =========================================================
	private List<AsTask> loadAsTasksForCalendarRange(
			Member member,
			CalendarDateBasis basis,
			LocalDate start,
			LocalDate endExclusive) {

		LocalDateTime startAt = start.atStartOfDay();
		LocalDateTime endInclusive = endExclusive.atStartOfDay().minusNanos(1);

		if (basis == CalendarDateBasis.PROCESS) {
			return asTaskRepository.findByRequestedByAndAsProcessDateBetween(member, startAt, endInclusive);
		}
		return asTaskRepository.findByRequestedByAndRequestedAtBetween(member, startAt, endInclusive);
	}

	private List<AsTask> loadAllAsTasksForCalendar(Member member, CalendarDateBasis basis) {
		if (basis == CalendarDateBasis.PROCESS) {
			return asTaskRepository.findByRequestedByAndAsProcessDateNotNull(member);
		}
		return asTaskRepository.findByRequestedBy(member);
	}

	private List<Task> loadTasksForCalendarRange(
			Member member,
			CalendarDateBasis basis,
			LocalDate start,
			LocalDate endExclusive) {

		LocalDateTime startAt = start.atStartOfDay();
		LocalDateTime endAt = endExclusive.atStartOfDay();

		List<Task> tasks;
		if (basis == CalendarDateBasis.PROCESS) {
			tasks = taskRepository.findCalendarProcessedRangeCandidates(member, startAt, endAt);
		} else {
			tasks = taskRepository.findCalendarRequestedRange(member, startAt, endAt);
		}

		// PROCESS 후보 조회는 같은 Task 안 다른 Order 날짜로 포함될 수 있으므로
		// 기존 extractTaskDate 규칙으로 최종 범위를 다시 맞춥니다.
		return tasks.stream()
				.filter(t -> isDateInRange(extractTaskDate(t, basis), start, endExclusive))
				.toList();
	}

	private List<Task> loadAllTasksForCalendar(Member member, CalendarDateBasis basis) {
		if (basis == CalendarDateBasis.PROCESS) {
			return taskRepository.findByRequestedByAndPreferredDeliveryNotNullFetchOrders(member);
		}
		return taskRepository.findByRequestedByFetchOrders(member);
	}

	private LocalDate parseDateOrNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value.substring(0, Math.min(value.length(), 10)));
		} catch (Exception e) {
			return null;
		}
	}

	private boolean isValidRange(LocalDate start, LocalDate endExclusive) {
		return start != null && endExclusive != null && endExclusive.isAfter(start);
	}

	private boolean isDateInRange(LocalDate date, LocalDate start, LocalDate endExclusive) {
		return date != null && !date.isBefore(start) && date.isBefore(endExclusive);
	}

	// =========================================================
	// 오버뷰 집계
	// =========================================================
	private CalendarOverviewDTO.OrderOverviewDTO buildOrderOverview(List<Task> tasks) {
		CalendarOverviewDTO.OrderOverviewDTO out = new CalendarOverviewDTO.OrderOverviewDTO();
		List<Task> safeTasks = tasks != null ? tasks : List.of();

		out.setTaskCount(safeTasks.size());

		Map<String, Long> categoryCounts = new HashMap<>();
		Map<String, Long> regionCounts = new HashMap<>();
		Map<String, Long> deliveryMethodCounts = new HashMap<>();
		Map<OrderStatus, Long> statusCounts = new HashMap<>();
		long unknownStatusCount = 0L;
		long orderCount = 0L;
		long totalAmount = 0L;

		for (Task task : safeTasks) {
			if (task == null || task.getOrders() == null) {
				continue;
			}

			for (Order order : task.getOrders()) {
				if (order == null) {
					continue;
				}

				orderCount++;
				totalAmount += order.getTotalAmount();

				if (order.getStatus() != null) {
					statusCounts.merge(order.getStatus(), 1L, Long::sum);
				} else {
					unknownStatusCount++;
				}

				incrementCount(categoryCounts, resolveOrderOptionCategory(order));
				incrementCount(regionCounts, resolveOrderDeliveryRegion(order));

				String deliveryMethodName = (order.getDeliveryMethod() != null)
						? safeText(order.getDeliveryMethod().getMethodName())
						: null;
				incrementCount(deliveryMethodCounts, deliveryMethodName != null ? deliveryMethodName : "미지정");
			}
		}

		out.setOrderCount(orderCount);
		out.setTotalAmount(totalAmount);

		List<CalendarOverviewDTO.CountItemDTO> statusItems = new ArrayList<>();
		for (OrderStatus status : OrderStatus.values()) {
			statusItems.add(new CalendarOverviewDTO.CountItemDTO(
					status.name(), status.getLabel(), statusCounts.getOrDefault(status, 0L)));
		}
		if (unknownStatusCount > 0) {
			statusItems.add(new CalendarOverviewDTO.CountItemDTO("UNKNOWN", "미지정", unknownStatusCount));
		}

		out.setStatusCounts(statusItems);
		out.setCategoryCounts(toSortedCountItems(categoryCounts));
		out.setRegionCounts(toSortedCountItems(regionCounts));
		out.setDeliveryMethodCounts(toSortedCountItems(deliveryMethodCounts));
		return out;
	}

	private CalendarOverviewDTO.AsOverviewDTO buildAsOverview(List<AsTask> asTasks) {
		CalendarOverviewDTO.AsOverviewDTO out = new CalendarOverviewDTO.AsOverviewDTO();
		List<AsTask> safeTasks = asTasks != null ? asTasks : List.of();

		Map<AsStatus, Long> statusCounts = new HashMap<>();
		Map<String, Long> productCounts = new HashMap<>();
		Map<String, Long> regionCounts = new HashMap<>();
		Map<String, Long> billingTargetCounts = new HashMap<>();
		long unknownStatusCount = 0L;

		long chargedCount = 0L;
		long zeroPriceCount = 0L;
		long totalAmount = 0L;
		long collectedAmount = 0L;
		long uncollectedAmount = 0L;
		long maxChargedAmount = 0L;

		for (AsTask task : safeTasks) {
			if (task == null) {
				continue;
			}

			if (task.getStatus() != null) {
				statusCounts.merge(task.getStatus(), 1L, Long::sum);
			} else {
				unknownStatusCount++;
			}

			String productName = safeText(task.getProductName());
			incrementCount(productCounts, productName != null ? productName : "미지정");
			incrementCount(regionCounts, buildRegionLabel(task.getDoName(), task.getSiName()));

			String billingTarget = safeText(task.getBillingTargetLabelSafe());
			incrementCount(billingTargetCounts,
					billingTarget != null && !"-".equals(billingTarget) ? billingTarget : "미지정");

			int price = task.getPrice();
			if (price > 0) {
				chargedCount++;
				totalAmount += price;
				maxChargedAmount = Math.max(maxChargedAmount, price);

				if (task.isPaymentCollected()) {
					collectedAmount += price;
				} else {
					uncollectedAmount += price;
				}
			} else {
				// 현재 기존 AS 리스트 화면과 동일하게 0원은 "무상/미정"으로 취급합니다.
				zeroPriceCount++;
			}
		}

		out.setTotalCount(safeTasks.size());
		out.setChargedCount(chargedCount);
		out.setZeroPriceCount(zeroPriceCount);
		out.setTotalAmount(totalAmount);
		out.setCollectedAmount(collectedAmount);
		out.setUncollectedAmount(uncollectedAmount);
		out.setAverageChargedAmount(chargedCount > 0 ? totalAmount / chargedCount : 0L);
		out.setMaxChargedAmount(maxChargedAmount);

		List<CalendarOverviewDTO.CountItemDTO> statusItems = new ArrayList<>();
		for (AsStatus status : AsStatus.values()) {
			statusItems.add(new CalendarOverviewDTO.CountItemDTO(
					status.name(), status.getLabelKr(), statusCounts.getOrDefault(status, 0L)));
		}
		if (unknownStatusCount > 0) {
			statusItems.add(new CalendarOverviewDTO.CountItemDTO("UNKNOWN", "미지정", unknownStatusCount));
		}

		out.setStatusCounts(statusItems);
		out.setProductCounts(toSortedCountItems(productCounts));
		out.setRegionCounts(toSortedCountItems(regionCounts));
		out.setBillingTargetCounts(toSortedCountItems(billingTargetCounts));
		return out;
	}

	private void incrementCount(Map<String, Long> target, String label) {
		String normalized = safeText(label);
		target.merge(normalized != null ? normalized : "미지정", 1L, Long::sum);
	}

	private List<CalendarOverviewDTO.CountItemDTO> toSortedCountItems(Map<String, Long> counts) {
		return counts.entrySet().stream()
				.map(e -> new CalendarOverviewDTO.CountItemDTO(e.getKey(), e.getKey(), e.getValue()))
				.sorted(Comparator
						.comparingLong(CalendarOverviewDTO.CountItemDTO::getCount).reversed()
						.thenComparing(CalendarOverviewDTO.CountItemDTO::getLabel, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private String resolveOrderOptionCategory(Order order) {
		if (order == null || order.getOrderItem() == null) {
			return "미분류";
		}

		String optionJson = safeText(order.getOrderItem().getOptionJson());
		if (optionJson == null) {
			return "미분류";
		}

		try {
			JsonNode root = CALENDAR_JSON_MAPPER.readTree(optionJson);
			JsonNode categoryNode = root != null ? root.get("카테고리") : null;
			if (categoryNode == null || categoryNode.isNull()) {
				return "미분류";
			}

			String category = safeText(categoryNode.isTextual() ? categoryNode.asText() : categoryNode.toString());
			return category != null ? category : "미분류";
		} catch (Exception ignored) {
			return "미분류";
		}
	}

	private String buildRegionLabel(String province, String city) {
		String p = safeText(province);
		String c = safeText(city);

		if (p == null && c == null) {
			return "지역 미지정";
		}
		if (p == null) {
			return c;
		}
		if (c == null || p.equals(c)) {
			return p;
		}
		return p + " " + c;
	}


	// =========================================================
	// 최근/예정 7일 카드 변환
	// =========================================================
	private CalendarWorkWindowDTO.WorkItemDTO toRecentOrderWorkItem(Task task, Order order) {
		LocalDateTime completedAt = order.getUpdatedAt();
		String methodName = order.getDeliveryMethod() != null
				? safeText(order.getDeliveryMethod().getMethodName())
				: null;

		return new CalendarWorkWindowDTO.WorkItemDTO(
				"ORDER",
				order.getId(),
				task != null ? task.getId() : null,
				completedAt != null ? completedAt.toLocalDate().toString() : null,
				completedAt != null ? completedAt.format(DT) : null,
				resolveOrderWorkTitle(order),
				joinWorkDescription(methodName, "배송 완료"),
				resolveOrderDeliveryRegion(order),
				order.getStatus() != null ? order.getStatus().name() : "UNKNOWN",
				order.getStatus() != null ? order.getStatus().getLabel() : "상태 미지정",
				order.getTotalAmount(),
				safeText(order.getOrdererName()),
				safeText(order.getOrdererPhone())
		);
	}

	private CalendarWorkWindowDTO.WorkItemDTO toRecentAsWorkItem(AsTask task) {
		LocalDateTime completedAt = task.getAsProcessDate();
		return new CalendarWorkWindowDTO.WorkItemDTO(
				"AS",
				task.getId(),
				null,
				completedAt != null ? completedAt.toLocalDate().toString() : null,
				completedAt != null ? completedAt.format(DT) : null,
				resolveAsWorkTitle(task),
				joinWorkDescription(safeText(task.getSubject()), "AS 처리 완료"),
				buildRegionLabel(task.getDoName(), task.getSiName()),
				task.getStatus() != null ? task.getStatus().name() : "UNKNOWN",
				task.getStatus() != null ? task.getStatus().getLabelKr() : "상태 미지정",
				task.getPrice(),
				safeText(task.getCustomerName()),
				safeText(task.getOnsiteContact())
		);
	}

	private CalendarWorkWindowDTO.WorkItemDTO toUpcomingOrderWorkItem(Task task, Order order) {
		LocalDateTime preferred = order.getPreferredDeliveryDate();
		String methodName = order.getDeliveryMethod() != null
				? safeText(order.getDeliveryMethod().getMethodName())
				: null;

		return new CalendarWorkWindowDTO.WorkItemDTO(
				"ORDER",
				order.getId(),
				task != null ? task.getId() : null,
				preferred != null ? preferred.toLocalDate().toString() : null,
				preferred != null ? preferred.format(DT) : null,
				resolveOrderWorkTitle(order),
				joinWorkDescription(methodName, "배송 예정"),
				resolveOrderDeliveryRegion(order),
				order.getStatus() != null ? order.getStatus().name() : "UNKNOWN",
				order.getStatus() != null ? order.getStatus().getLabel() : "상태 미지정",
				order.getTotalAmount(),
				safeText(order.getOrdererName()),
				safeText(order.getOrdererPhone())
		);
	}

	private CalendarWorkWindowDTO.WorkItemDTO toUpcomingAsWorkItem(AsTaskSchedule schedule) {
		AsTask task = schedule.getAsTask();
		LocalDate scheduledDate = schedule.getScheduledDate();
		String dateTime = scheduledDate != null ? scheduledDate.toString() : null;
		if (scheduledDate != null && task.getVisitPlannedTime() != null) {
			dateTime = scheduledDate.atTime(task.getVisitPlannedTime()).format(DT);
		}

		return new CalendarWorkWindowDTO.WorkItemDTO(
				"AS",
				task.getId(),
				null,
				scheduledDate != null ? scheduledDate.toString() : null,
				dateTime,
				resolveAsWorkTitle(task),
				joinWorkDescription(safeText(task.getSubject()), "AS 방문 예정"),
				buildRegionLabel(task.getDoName(), task.getSiName()),
				task.getStatus() != null ? task.getStatus().name() : "UNKNOWN",
				task.getStatus() != null ? task.getStatus().getLabelKr() : "상태 미지정",
				task.getPrice(),
				safeText(task.getCustomerName()),
				safeText(task.getOnsiteContact())
		);
	}

	private String resolveOrderWorkTitle(Order order) {
		if (order == null) {
			return "발주";
		}

		if (order.getOrderItem() != null) {
			String productName = safeText(order.getOrderItem().getProductName());
			if (productName != null) {
				return productName;
			}
		}

		if (order.getProductCategory() != null) {
			String categoryName = safeText(order.getProductCategory().getName());
			if (categoryName != null) {
				return categoryName;
			}
		}

		return order.getId() != null ? "ORDER #" + order.getId() : "발주";
	}

	private String resolveAsWorkTitle(AsTask task) {
		if (task == null) {
			return "AS";
		}
		String productName = safeText(task.getProductName());
		if (productName != null) {
			return productName;
		}
		String subject = safeText(task.getSubject());
		return subject != null ? subject : (task.getId() != null ? "AS #" + task.getId() : "AS");
	}

	private String joinWorkDescription(String first, String second) {
		String a = safeText(first);
		String b = safeText(second);
		if (a == null) {
			return b;
		}
		if (b == null || a.equals(b)) {
			return a;
		}
		return a + " · " + b;
	}

	private String resolveOrderDeliveryRegion(Order order) {
		if (order == null) {
			return "지역 미지정";
		}

		boolean hasSiteAddress = safeText(order.getSiteDoName()) != null
				|| safeText(order.getSiteSiName()) != null
				|| safeText(order.getSiteRoadAddress()) != null;

		if (hasSiteAddress) {
			return buildRegionLabel(order.getSiteDoName(), order.getSiteSiName());
		}
		return buildRegionLabel(order.getDoName(), order.getSiName());
	}

	// =========================================================
	// ✅ Date 추출 규칙 - 기존 로직 유지
	// =========================================================
	private LocalDate extractAsDate(AsTask t, CalendarDateBasis basis) {
		if (t == null) {
			return null;
		}
		if (basis == CalendarDateBasis.PROCESS) {
			LocalDateTime p = t.getAsProcessDate();
			return (p != null) ? p.toLocalDate() : null;
		}
		LocalDateTime r = t.getRequestedAt();
		return (r != null) ? r.toLocalDate() : null;
	}

	private LocalDate extractTaskDate(Task t, CalendarDateBasis basis) {
		if (t == null) {
			return null;
		}

		if (basis == CalendarDateBasis.PROCESS) {
			LocalDateTime pref = getTaskPreferredDeliveryDate(t);
			return (pref != null) ? pref.toLocalDate() : null;
		}

		LocalDateTime c = t.getCreatedAt();
		return (c != null) ? c.toLocalDate() : null;
	}

	private LocalDateTime getTaskPreferredDeliveryDate(Task t) {
		if (t.getOrders() == null || t.getOrders().isEmpty()) {
			return null;
		}
		for (Order o : t.getOrders()) {
			if (o != null && o.getPreferredDeliveryDate() != null) {
				return o.getPreferredDeliveryDate();
			}
		}
		return null;
	}

	// =========================================================
	// ✅ DTO 변환 - 기존 필드/호환 유지
	// =========================================================
	private CalendarTaskDetailDTO toAsDetailDTO(AsTask t, CalendarDateBasis basis, LocalDate scheduledDate) {
		CalendarTaskDetailDTO dto = new CalendarTaskDetailDTO();
		dto.setType("AS");
		dto.setId(t.getId());

		String title = (t.getSubject() != null && !t.getSubject().isBlank()) ? t.getSubject() : t.getProductName();
		dto.setTitle(safeText(title));

		LocalDate date = extractAsDate(t, basis);
		dto.setDate(date != null ? date.toString() : null);

		dto.setAddress(
				buildAddress(t.getDoName(), t.getSiName(), t.getGuName(), t.getRoadAddress(), t.getDetailAddress()));

		dto.setScheduledDate(scheduledDate != null ? scheduledDate.toString() : null);

		Member handler = t.getAssignedHandler();
		dto.setHandlerName(handler != null ? safeText(handler.getName()) : null);
		dto.setHandlerContact(resolveContact(handler));

		dto.setProductName(safeText(t.getProductName()));
		dto.setProductSize(safeText(t.getProductSize()));
		dto.setProductColor(safeText(t.getProductColor()));
		dto.setProductOptions(safeText(t.getProductOptions()));
		dto.setSymptom(safeText(t.getSubject()));

		dto.setCustomerName(safeText(t.getCustomerName()));
		dto.setOnsiteContact(safeText(t.getOnsiteContact()));
		dto.setRequestedAt(t.getRequestedAt() != null ? t.getRequestedAt().format(DT) : null);

		return dto;
	}

	private CalendarTaskDetailDTO toTaskDetailDTO(Task t, CalendarDateBasis basis) {
		CalendarTaskDetailDTO dto = new CalendarTaskDetailDTO();
		dto.setType("TASK");
		dto.setId(t.getId());

		LocalDate date = extractTaskDate(t, basis);
		dto.setDate(date != null ? date.toString() : null);

		List<Order> orders = (t.getOrders() != null) ? t.getOrders() : List.of();
		for (Order o : orders) {
			if (o == null) {
				continue;
			}

			CalendarTaskDetailDTO.OrderBriefDTO ob = new CalendarTaskDetailDTO.OrderBriefDTO();
			ob.setOrderId(o.getId());
			ob.setCreatedAt(o.getCreatedAt() != null ? o.getCreatedAt().format(DT) : null);
			ob.setPreferredDeliveryDate(
					o.getPreferredDeliveryDate() != null ? o.getPreferredDeliveryDate().format(DT) : null);
			ob.setAddress(
					buildAddress(o.getDoName(), o.getSiName(), o.getGuName(), o.getRoadAddress(), o.getDetailAddress()));
			ob.setQuantity(o.getQuantity());
			ob.setPrice(o.getProductCost());
			ob.setCategoryName(o.getProductCategory() != null ? o.getProductCategory().getName() : null);

			dto.getOrders().add(ob);
		}

		dto.setTitle("TASK_" + t.getId());
		dto.setAddress(dto.getOrders().isEmpty() ? "-" : dto.getOrders().get(0).getAddress());
		return dto;
	}

	private String buildAddress(String doName, String siName, String guName, String roadAddress, String detailAddress) {
		String base = (roadAddress != null && !roadAddress.isBlank())
				? roadAddress
				: String.join(" ", safe(doName), safe(siName), safe(guName)).trim();

		if (detailAddress != null && !detailAddress.isBlank()) {
			if (base.isBlank()) {
				return detailAddress;
			}
			return base + " " + detailAddress;
		}
		return base.isBlank() ? "-" : base;
	}

	private String safe(String s) {
		return (s == null) ? "" : s;
	}

	private String safeText(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	/**
	 * ✅ 연락처: phone 우선, 없으면 telephone, 둘 다 없으면 null
	 */
	private String resolveContact(Member m) {
		if (m == null) {
			return null;
		}
		String phone = safeText(m.getPhone());
		if (phone != null) {
			return phone;
		}
		return safeText(m.getTelephone());
	}
}
