package com.dev.HiddenBATHAuto.controller.api;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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
import com.dev.HiddenBATHAuto.dto.TaskDetailDTO;
import com.dev.HiddenBATHAuto.dto.employeeDetail.ConflictDTO;
import com.dev.HiddenBATHAuto.dto.employeeDetail.RegionSelectionDTO;
import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.model.auth.Province;
import com.dev.HiddenBATHAuto.model.task.AsTask;
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

    @GetMapping("/validate/businessNumber")
    public ResponseEntity<Map<String, Object>> validateBusinessNumber(@RequestParam("businessNumber") String businessNumber) {
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
    public ResponseEntity<List<ConflictDTO>> checkRegionConflictsForNewMember(@RequestBody NewMemberRegionCheckRequest req) {
        List<ConflictDTO> conflicts = memberManagementService.checkRegionConflictsForNewMember(req.getTeamId(), req.getSelections());
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
	public List<CalendarEventDTO> getCalendarEvents(@AuthenticationPrincipal PrincipalDetails principalDetails) {
		
		Member member = principalDetails.getMember(); // 또는 getUser() 등 실제 메서드명 확인
		log.info("[CalendarEvents] 요청자: {}", member != null ? member.getUsername() : "비로그인");

		List<AsTask> asTasks = asTaskRepository.findByRequestedBy(member);
		List<Task> tasks = taskRepository.findByRequestedBy(member);

		log.info("조회된 AS 태스크 수: {}", asTasks.size());
		log.info("조회된 주문 태스크 수: {}", tasks.size());

		Map<LocalDate, List<AsTask>> asMap = asTasks.stream()
				.collect(Collectors.groupingBy(task -> task.getRequestedAt().toLocalDate()));

		Map<LocalDate, List<Task>> taskMap = tasks.stream()
				.collect(Collectors.groupingBy(task -> task.getCreatedAt().toLocalDate()));

		Set<LocalDate> allDates = new HashSet<>();
		allDates.addAll(asMap.keySet());
		allDates.addAll(taskMap.keySet());

		log.info("모든 날짜 수: {}", allDates.size());

		List<CalendarEventDTO> result = new ArrayList<>();
		for (LocalDate date : allDates) {
			int asCount = asMap.getOrDefault(date, List.of()).size();
			int taskCount = taskMap.getOrDefault(date, List.of()).size();
			result.add(new CalendarEventDTO(date.toString(), asCount, taskCount));

			log.info("📅 {}: AS {}건 / 주문 {}건", date, asCount, taskCount);
		}

		return result;
	}

	@GetMapping("/calendar/tasks")
	@ResponseBody
	public List<TaskDetailDTO> getTasksByDate(@AuthenticationPrincipal PrincipalDetails principalDetails, @RequestParam String date) {
		
		Member member = principalDetails.getMember(); // 또는 getUser() 등 실제 메서드명 확인
		log.info("[TaskDetail] 날짜: {}, 요청자: {}", date, member != null ? member.getUsername() : "비로그인");

		LocalDate targetDate = LocalDate.parse(date);

		List<AsTask> asTasks = asTaskRepository.findByRequestedByAndRequestedAtBetween(member,
				targetDate.atStartOfDay(), targetDate.atTime(LocalTime.MAX));

		List<Task> tasks = taskRepository.findByRequestedByAndCreatedAtBetween(member, targetDate.atStartOfDay(),
				targetDate.atTime(LocalTime.MAX));

		log.info("해당 날짜의 AS 태스크 수: {}", asTasks.size());
		log.info("해당 날짜의 주문 태스크 수: {}", tasks.size());

		List<TaskDetailDTO> result = new ArrayList<>();
		for (AsTask task : asTasks) {
			TaskDetailDTO dto = TaskDetailDTO.fromAsTask(task);
			log.debug("AS → {}", dto);
			result.add(dto);
		}

		for (Task task : tasks) {
			TaskDetailDTO dto = TaskDetailDTO.fromTask(task);
			log.debug("TASK → {}", dto);
			result.add(dto);
		}

		return result;
	}

}
