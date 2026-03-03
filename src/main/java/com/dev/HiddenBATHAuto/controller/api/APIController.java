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
import com.dev.HiddenBATHAuto.dto.calendar.CalendarTaskDetailDTO;
import com.dev.HiddenBATHAuto.dto.employeeDetail.ConflictDTO;
import com.dev.HiddenBATHAuto.dto.employeeDetail.RegionSelectionDTO;
import com.dev.HiddenBATHAuto.enums.CalendarDateBasis;
import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.model.auth.Province;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.Order;
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
	public List<CalendarEventDTO> getCalendarEvents(@AuthenticationPrincipal PrincipalDetails principalDetails,
			@org.springframework.web.bind.annotation.RequestParam(value = "basis", required = false) String basisParam) {
		CalendarDateBasis basis = CalendarDateBasis.from(basisParam);

		Member member = principalDetails.getMember();
		log.info("[CalendarEvents] basis={}, requester={}", basis, (member != null ? member.getUsername() : "비로그인"));

		// =========================
		// AS 조회/그룹핑
		// =========================
		List<AsTask> asTasks;
		if (basis == CalendarDateBasis.PROCESS) {
			asTasks = asTaskRepository.findByRequestedByAndAsProcessDateNotNull(member);
		} else {
			asTasks = asTaskRepository.findByRequestedBy(member);
		}

		Map<LocalDate, List<AsTask>> asMap = asTasks.stream()
				.map(t -> new AbstractMap.SimpleEntry<>(extractAsDate(t, basis), t)).filter(e -> e.getKey() != null)
				.collect(Collectors.groupingBy(Map.Entry::getKey,
						Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

		// =========================
		// TASK 조회/그룹핑
		// =========================
		List<Task> tasks;
		if (basis == CalendarDateBasis.PROCESS) {
			tasks = taskRepository.findByRequestedByAndPreferredDeliveryNotNullFetchOrders(member);
		} else {
			tasks = taskRepository.findByRequestedByFetchOrders(member);
		}

		Map<LocalDate, List<Task>> taskMap = tasks.stream()
				.map(t -> new AbstractMap.SimpleEntry<>(extractTaskDate(t, basis), t)).filter(e -> e.getKey() != null)
				.collect(Collectors.groupingBy(Map.Entry::getKey,
						Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

		// =========================
		// 합치기
		// =========================
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
	 * ✅ 모달 상세
	 * - basis=REQUEST(기본): date는 신청일 기준
	 * - basis=PROCESS: date는 처리일 기준 (NULL 제외)
	 *
	 * ✅ 포함:
	 * - AS 방문예정일(scheduledDate)
	 * - AS 담당자 이름/연락처
	 * - AS 제품정보/고객정보(AS 신청 시 입력한 값)
	 */
	@GetMapping("/calendar/tasks")
	@ResponseBody
	public List<CalendarTaskDetailDTO> getCalendarTasks(
			@AuthenticationPrincipal PrincipalDetails principalDetails,
			@org.springframework.web.bind.annotation.RequestParam("date") String dateStr,
			@org.springframework.web.bind.annotation.RequestParam(value = "basis", required = false) String basisParam
	) {
		CalendarDateBasis basis = CalendarDateBasis.from(basisParam);
		LocalDate target = LocalDate.parse(dateStr);

		Member member = principalDetails.getMember();
		log.info("[CalendarTasks] basis={}, date={}, requester={}", basis, dateStr,
				member != null ? member.getUsername() : "비로그인");

		List<CalendarTaskDetailDTO> out = new ArrayList<>();

		// -------------------------
		// AS
		// -------------------------
		List<AsTask> asTasks;
		if (basis == CalendarDateBasis.PROCESS) {
			asTasks = asTaskRepository.findByRequestedByAndAsProcessDateNotNull(member);
		} else {
			asTasks = asTaskRepository.findByRequestedBy(member);
		}

		List<AsTask> asOnDate = asTasks.stream().filter(t -> {
			LocalDate d = extractAsDate(t, basis);
			return d != null && d.equals(target);
		}).toList();

		// ✅ scheduledDate 맵(필요한 asTask들만)
		Map<Long, LocalDate> scheduleMap = new HashMap<>();
		if (!asOnDate.isEmpty()) {
			List<Long> ids = asOnDate.stream().map(AsTask::getId).toList();
			asTaskScheduleRepository.findSimpleByAsTaskIdIn(ids).forEach(v -> {
				scheduleMap.put(v.getAsTaskId(), v.getScheduledDate());
			});
		}

		asOnDate.forEach(t -> out.add(toAsDetailDTO(t, basis, scheduleMap.get(t.getId()))));

		// -------------------------
		// TASK
		// -------------------------
		List<Task> tasks;
		if (basis == CalendarDateBasis.PROCESS) {
			tasks = taskRepository.findByRequestedByAndPreferredDeliveryNotNullFetchOrders(member);
		} else {
			tasks = taskRepository.findByRequestedByFetchOrders(member);
		}

		tasks.stream().filter(t -> {
			LocalDate d = extractTaskDate(t, basis);
			return d != null && d.equals(target);
		}).forEach(t -> out.add(toTaskDetailDTO(t, basis)));

		// 보기 좋게: AS 먼저, TASK 다음
		out.sort(Comparator.comparing(CalendarTaskDetailDTO::getType));
		return out;
	}

	// =========================================================
	// ✅ Date 추출 규칙
	// =========================================================
	private LocalDate extractAsDate(AsTask t, CalendarDateBasis basis) {
		if (t == null) return null;
		if (basis == CalendarDateBasis.PROCESS) {
			LocalDateTime p = t.getAsProcessDate();
			return (p != null) ? p.toLocalDate() : null;
		} else {
			LocalDateTime r = t.getRequestedAt();
			return (r != null) ? r.toLocalDate() : null;
		}
	}

	private LocalDate extractTaskDate(Task t, CalendarDateBasis basis) {
		if (t == null) return null;

		if (basis == CalendarDateBasis.PROCESS) {
			LocalDateTime pref = getTaskPreferredDeliveryDate(t);
			return (pref != null) ? pref.toLocalDate() : null;
		} else {
			LocalDateTime c = t.getCreatedAt();
			return (c != null) ? c.toLocalDate() : null;
		}
	}

	private LocalDateTime getTaskPreferredDeliveryDate(Task t) {
		if (t.getOrders() == null || t.getOrders().isEmpty()) return null;
		for (Order o : t.getOrders()) {
			if (o != null && o.getPreferredDeliveryDate() != null) {
				return o.getPreferredDeliveryDate();
			}
		}
		return null;
	}

	// =========================================================
	// ✅ DTO 변환
	// =========================================================
	private CalendarTaskDetailDTO toAsDetailDTO(AsTask t, CalendarDateBasis basis, LocalDate scheduledDate) {
		CalendarTaskDetailDTO dto = new CalendarTaskDetailDTO();
		dto.setType("AS");
		dto.setId(t.getId());

		// (title은 이제 모달에서 사용하지 않지만, 기존 호환 위해 유지)
		String title = (t.getSubject() != null && !t.getSubject().isBlank()) ? t.getSubject() : t.getProductName();
		dto.setTitle(safeText(title));

		// 달력 기준 date (yyyy-MM-dd)
		LocalDate date = extractAsDate(t, basis);
		dto.setDate(date != null ? date.toString() : null);

		dto.setAddress(
			buildAddress(t.getDoName(), t.getSiName(), t.getGuName(), t.getRoadAddress(), t.getDetailAddress())
		);

		// ✅ 방문예정일
		dto.setScheduledDate(scheduledDate != null ? scheduledDate.toString() : null);

		// ✅ 담당자 이름/연락처
		Member handler = t.getAssignedHandler();
		dto.setHandlerName(handler != null ? safeText(handler.getName()) : null);
		dto.setHandlerContact(resolveContact(handler));

		// =================================================
		// ✅ 추가: 제품정보/고객정보 (AS 신청 시 입력한 값)
		// =================================================
		dto.setProductName(safeText(t.getProductName()));
		dto.setProductSize(safeText(t.getProductSize()));
		dto.setProductColor(safeText(t.getProductColor()));
		dto.setProductOptions(safeText(t.getProductOptions()));
		dto.setSymptom(safeText(t.getSubject()));           // ✅ 증상 = subject

		dto.setCustomerName(safeText(t.getCustomerName())); // ✅ 고객 성함(신규)
		dto.setOnsiteContact(safeText(t.getOnsiteContact())); // ✅ 현장 연락처
		dto.setRequestedAt(t.getRequestedAt() != null ? t.getRequestedAt().format(DT) : null); // ✅ 신청일(고정)

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
			if (o == null) continue;

			CalendarTaskDetailDTO.OrderBriefDTO ob = new CalendarTaskDetailDTO.OrderBriefDTO();
			ob.setOrderId(o.getId());
			ob.setCreatedAt(o.getCreatedAt() != null ? o.getCreatedAt().format(DT) : null);
			ob.setPreferredDeliveryDate(o.getPreferredDeliveryDate() != null ? o.getPreferredDeliveryDate().format(DT) : null);
			ob.setAddress(buildAddress(o.getDoName(), o.getSiName(), o.getGuName(), o.getRoadAddress(), o.getDetailAddress()));
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
			if (base.isBlank()) return detailAddress;
			return base + " " + detailAddress;
		}
		return base.isBlank() ? "-" : base;
	}

	private String safe(String s) {
		return (s == null) ? "" : s;
	}

	private String safeText(String s) {
		if (s == null) return null;
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	/**
	 * ✅ 연락처: phone 우선, 없으면 telephone, 둘 다 없으면 null
	 */
	private String resolveContact(Member m) {
		if (m == null) return null;
		String phone = safeText(m.getPhone());
		if (phone != null) return phone;
		String tel = safeText(m.getTelephone());
		return tel;
	}
}
