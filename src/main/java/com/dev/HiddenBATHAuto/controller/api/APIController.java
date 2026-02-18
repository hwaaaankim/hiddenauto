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
import com.dev.HiddenBATHAuto.dto.calendar.CalendarTaskDetailDTO.OrderBriefDTO;
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
	public List<CalendarEventDTO> getCalendarEvents(
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            @RequestParam(value = "basis", required = false) String basisParam
    ) {
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
                .map(t -> new AbstractMap.SimpleEntry<>(extractAsDate(t, basis), t))
                .filter(e -> e.getKey() != null) // ✅ 처리일 기준에서 null 제거
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
                .map(t -> new AbstractMap.SimpleEntry<>(extractTaskDate(t, basis), t))
                .filter(e -> e.getKey() != null) // ✅ 처리일 기준에서 null 제거
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

        // 날짜 정렬(오름차순) 원하시면 반대로도 가능
        result.sort(Comparator.comparing(CalendarEventDTO::getDate));
        return result;
    }

    /**
     * ✅ 모달 상세
     * - basis=REQUEST(기본): date는 신청일 기준으로 필터
     * - basis=PROCESS: date는 처리일 기준으로 필터 (NULL 제외)
     */
    @GetMapping("/calendar/tasks")
    @ResponseBody
    public List<CalendarTaskDetailDTO> getCalendarTasks(
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            @RequestParam("date") String dateStr,
            @RequestParam(value = "basis", required = false) String basisParam
    ) {
        CalendarDateBasis basis = CalendarDateBasis.from(basisParam);
        LocalDate target = LocalDate.parse(dateStr);

        Member member = principalDetails.getMember();
        log.info("[CalendarTasks] basis={}, date={}, requester={}", basis, dateStr, member != null ? member.getUsername() : "비로그인");

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

        asTasks.stream()
                .filter(t -> {
                    LocalDate d = extractAsDate(t, basis);
                    return d != null && d.equals(target);
                })
                .forEach(t -> out.add(toAsDetailDTO(t, basis)));

        // -------------------------
        // TASK
        // -------------------------
        List<Task> tasks;
        if (basis == CalendarDateBasis.PROCESS) {
            tasks = taskRepository.findByRequestedByAndPreferredDeliveryNotNullFetchOrders(member);
        } else {
            tasks = taskRepository.findByRequestedByFetchOrders(member);
        }

        tasks.stream()
                .filter(t -> {
                    LocalDate d = extractTaskDate(t, basis);
                    return d != null && d.equals(target);
                })
                .forEach(t -> out.add(toTaskDetailDTO(t, basis)));

        // 보기 좋게: AS 먼저, TASK 다음 (원하시면 변경 가능)
        out.sort(Comparator.comparing(CalendarTaskDetailDTO::getType));
        return out;
    }

    // =========================================================
    // ✅ Date 추출 규칙
    // =========================================================
    private LocalDate extractAsDate(AsTask t, CalendarDateBasis basis) {
        if (t == null) return null;
        if (basis == CalendarDateBasis.PROCESS) {
            // ✅ 처리일 기준: asProcessDate (NULL이면 표시 안 함)
            LocalDateTime p = t.getAsProcessDate();
            return (p != null) ? p.toLocalDate() : null;
        } else {
            // ✅ 신청일 기준: requestedAt
            LocalDateTime r = t.getRequestedAt();
            return (r != null) ? r.toLocalDate() : null;
        }
    }

    private LocalDate extractTaskDate(Task t, CalendarDateBasis basis) {
        if (t == null) return null;

        if (basis == CalendarDateBasis.PROCESS) {
            // ✅ 처리일 기준: 주문들의 preferredDeliveryDate (전부 동일하다는 전제)
            LocalDateTime pref = getTaskPreferredDeliveryDate(t);
            return (pref != null) ? pref.toLocalDate() : null;
        } else {
            // ✅ 신청일 기준: task.createdAt
            LocalDateTime c = t.getCreatedAt();
            return (c != null) ? c.toLocalDate() : null;
        }
    }

    /**
     * ✅ Task 내 orders가 여러 개여도 배송희망일은 동일하므로,
     *    첫 번째 유효값을 대표로 사용합니다.
     *    (혹시 데이터가 섞일 가능성이 있으면 min/max 검증 로직 추가 권장)
     */
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
    private CalendarTaskDetailDTO toAsDetailDTO(AsTask t, CalendarDateBasis basis) {
        CalendarTaskDetailDTO dto = new CalendarTaskDetailDTO();
        dto.setType("AS");
        dto.setId(t.getId());

        // title: subject 우선, 없으면 productName
        String title = (t.getSubject() != null && !t.getSubject().isBlank()) ? t.getSubject() : t.getProductName();
        dto.setTitle(title);

        LocalDate date = extractAsDate(t, basis);
        dto.setDate(date != null ? date.toString() : null);

        dto.setAddress(buildAddress(t.getDoName(), t.getSiName(), t.getGuName(), t.getRoadAddress(), t.getDetailAddress()));
        return dto;
    }

    private CalendarTaskDetailDTO toTaskDetailDTO(Task t, CalendarDateBasis basis) {
        CalendarTaskDetailDTO dto = new CalendarTaskDetailDTO();
        dto.setType("TASK");
        dto.setId(t.getId());

        LocalDate date = extractTaskDate(t, basis);
        dto.setDate(date != null ? date.toString() : null);

        // orders
        List<Order> orders = (t.getOrders() != null) ? t.getOrders() : List.of();
        for (Order o : orders) {
            if (o == null) continue;

            OrderBriefDTO ob = new OrderBriefDTO();
            ob.setOrderId(o.getId());
            ob.setCreatedAt(o.getCreatedAt() != null ? o.getCreatedAt().format(DT) : null);
            ob.setPreferredDeliveryDate(o.getPreferredDeliveryDate() != null ? o.getPreferredDeliveryDate().format(DT) : null);
            ob.setAddress(buildAddress(o.getDoName(), o.getSiName(), o.getGuName(), o.getRoadAddress(), o.getDetailAddress()));
            ob.setQuantity(o.getQuantity());
            ob.setPrice(o.getProductCost());
            ob.setCategoryName(o.getProductCategory() != null ? o.getProductCategory().getName() : null);

            dto.getOrders().add(ob);
        }

        // title은 JS가 TASK는 orders를 찍으므로 굳이 필요 없지만, 혹시 대비해서 설정
        dto.setTitle("TASK_" + t.getId());
        dto.setAddress(dto.getOrders().isEmpty() ? "-" : dto.getOrders().get(0).getAddress());

        return dto;
    }

    private String buildAddress(String doName, String siName, String guName, String roadAddress, String detailAddress) {
        // roadAddress가 이미 전체 주소라면 roadAddress 위주로
        String base = (roadAddress != null && !roadAddress.isBlank())
                ? roadAddress
                : String.join(" ",
                    safe(doName),
                    safe(siName),
                    safe(guName)
                ).trim();

        if (detailAddress != null && !detailAddress.isBlank()) {
            if (base.isBlank()) return detailAddress;
            return base + " " + detailAddress;
        }
        return base.isBlank() ? "-" : base;
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }

}
