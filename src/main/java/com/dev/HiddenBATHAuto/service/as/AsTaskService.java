package com.dev.HiddenBATHAuto.service.as;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.as.AsTaskCardDto;
import com.dev.HiddenBATHAuto.dto.as.AsTaskScheduleSummaryProjection;
import com.dev.HiddenBATHAuto.dto.as.CompanySearchItemDto;
import com.dev.HiddenBATHAuto.dto.as.CustomerAsUpdateRequest;
import com.dev.HiddenBATHAuto.dto.as.TeamAsDetailModalResponse;
import com.dev.HiddenBATHAuto.enums.AsBillingTarget;
import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRegion;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.Province;
import com.dev.HiddenBATHAuto.model.task.AsImage;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.AsTaskSchedule;
import com.dev.HiddenBATHAuto.model.task.as.AsVideo;
import com.dev.HiddenBATHAuto.repository.as.AsImageRepository;
import com.dev.HiddenBATHAuto.repository.as.AsTaskRepository;
import com.dev.HiddenBATHAuto.repository.as.AsTaskScheduleRepository;
import com.dev.HiddenBATHAuto.repository.as.AsVideoRepository;
import com.dev.HiddenBATHAuto.repository.auth.CityRepository;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRegionRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AsTaskService {

	private final AsVideoRepository asVideoRepository;
	private final AsTaskRepository asTaskRepository;
	private final AsImageRepository asImageRepository;
	private final AsTaskScheduleRepository scheduleRepository;

	// ===== 기존 주입 =====
	private final DistrictRepository districtRepository;
	private final MemberRegionRepository memberRegionRepository;
	private final MemberRepository memberRepository;

	// ===== 추가 주입: 유연한 도/시/구 해석을 위한 Repository =====
	private final ProvinceRepository provinceRepository;
	private final CityRepository cityRepository;

	private final RegionLookupService regionLookupService;

	private final AsTaskScheduleRepository asTaskScheduleRepository;
	private final CompanyRepository companyRepository;

	@Value("${spring.upload.path}")
	private String uploadPath;

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

	private static final String AS_TEAM_NAME = "AS팀";

	private static final String REQUEST_TYPE = "REQUEST";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

	@Transactional(readOnly = true)
	public LocalDate getVisitPlannedDate(Long asTaskId) {
		return asTaskScheduleRepository.findByAsTaskId(asTaskId).map(AsTaskSchedule::getScheduledDate).orElse(null);
	}

	@Transactional
	public void updateCustomerAsTask(Long id, CustomerAsUpdateRequest req, List<MultipartFile> newImages,
			List<Long> deleteImageIds, List<MultipartFile> newVideos, List<Long> deleteVideoIds, Member loginMember) {

		if (loginMember == null) {
			throw new IllegalStateException("로그인 정보가 올바르지 않습니다.");
		}
		if (loginMember.getCompany() == null || loginMember.getCompany().getId() == null) {
			throw new IllegalStateException("회사 정보가 없는 계정은 AS 수정을 진행할 수 없습니다.");
		}

		AsTask task = asTaskRepository.findByIdAndRequestedBy_Company_Id(id, loginMember.getCompany().getId())
				.orElseThrow(() -> new IllegalArgumentException("수정할 AS 신청 정보를 찾을 수 없습니다."));

		if (task.getStatus() != AsStatus.REQUESTED) {
			throw new IllegalStateException("접수 상태의 AS만 수정할 수 있습니다.");
		}

		validateUpdateRequest(req);

		task.setCustomerName(normalizeRequired(req.getCustomerName(), "고객 성함"));
		task.setZipCode(normalize(req.getZipCode()));
		task.setDoName(normalize(req.getDoName()));
		task.setSiName(normalize(req.getSiName()));
		task.setGuName(normalize(req.getGuName()));
		task.setRoadAddress(normalizeRequired(req.getRoadAddress(), "주소"));
		task.setDetailAddress(normalize(req.getDetailAddress()));
		task.setOnsiteContact(formatPhone(req.getOnsiteContact()));

		task.setProductName(normalizeRequired(req.getProductName(), "제품명"));
		task.setProductSize(normalizeRequired(req.getProductSize(), "제품 사이즈"));
		task.setProductColor(normalizeRequired(req.getProductColor(), "제품 컬러"));
		task.setProductOptions(normalizeRequired(req.getProductOptions(), "제품 옵션 여부"));

		task.setSubject(normalizeRequired(req.getSubject(), "AS 증상"));
		task.setReason(normalize(req.getReason()));

		// ✅ 추가 필드
		task.setPurchaseDate(req.getPurchaseDate());
		task.setApplicantName(normalize(req.getApplicantName()));
		task.setApplicantPhone(formatOptionalPhone(req.getApplicantPhone()));
		task.setApplicantEmail(normalizeEmail(req.getApplicantEmail()));
		task.setBillingTarget(req.getBillingTarget());

		deleteRequestImages(task, deleteImageIds);
		deleteRequestVideos(task, deleteVideoIds);

		saveRequestImages(task, newImages);
		saveRequestVideos(task, newVideos);

		task.setUpdatedAt(LocalDateTime.now());

		asTaskRepository.save(task);
	}

	private void validateUpdateRequest(CustomerAsUpdateRequest req) {
		normalizeRequired(req.getCustomerName(), "고객 성함");
		normalizeRequired(req.getRoadAddress(), "주소");
		normalizeRequired(req.getOnsiteContact(), "현장 연락처");
		normalizeRequired(req.getProductName(), "제품명");
		normalizeRequired(req.getProductSize(), "제품 사이즈");
		normalizeRequired(req.getProductColor(), "제품 컬러");
		normalizeRequired(req.getProductOptions(), "제품 옵션 여부");
		normalizeRequired(req.getSubject(), "AS 증상");

		String onsitePhone = formatPhone(req.getOnsiteContact());
		if (!isValidPhone(onsitePhone)) {
			throw new IllegalArgumentException("현장 연락처 형식이 올바르지 않습니다.");
		}

		if (StringUtils.hasText(req.getApplicantPhone())) {
			String applicantPhone = formatOptionalPhone(req.getApplicantPhone());
			if (!isValidPhone(applicantPhone)) {
				throw new IllegalArgumentException("접수 담당자 연락처 형식이 올바르지 않습니다.");
			}
		}

		if (StringUtils.hasText(req.getApplicantEmail())
				&& !EMAIL_PATTERN.matcher(req.getApplicantEmail().trim()).matches()) {
			throw new IllegalArgumentException("접수 담당자 이메일 형식이 올바르지 않습니다.");
		}
	}

	private void saveRequestImages(AsTask task, List<MultipartFile> files) {
		if (files == null || files.isEmpty())
			return;

		for (MultipartFile file : files) {
			if (file == null || file.isEmpty())
				continue;

			String contentType = Optional.ofNullable(file.getContentType()).orElse("");
			if (!contentType.startsWith("image/")) {
				throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
			}

			StoredFile stored = storeMultipartFile(task.getId(), file, "images");

			AsImage image = new AsImage();
			image.setAsTask(task);
			image.setType(REQUEST_TYPE);
			image.setFilename(stored.originalFilename());
			image.setPath(stored.absolutePath());
			image.setUrl(stored.url());
			image.setUploadedAt(LocalDateTime.now());

			task.getImages().add(image);
		}
	}

	private void saveRequestVideos(AsTask task, List<MultipartFile> files) {
		if (files == null || files.isEmpty())
			return;

		for (MultipartFile file : files) {
			if (file == null || file.isEmpty())
				continue;

			String contentType = Optional.ofNullable(file.getContentType()).orElse("");
			if (!contentType.startsWith("video/")) {
				throw new IllegalArgumentException("동영상 파일만 업로드할 수 있습니다.");
			}

			StoredFile stored = storeMultipartFile(task.getId(), file, "videos");

			AsVideo video = new AsVideo();
			video.setAsTask(task);
			video.setType(REQUEST_TYPE);
			video.setFilename(stored.originalFilename());
			video.setPath(stored.absolutePath());
			video.setUrl(stored.url());
			video.setContentType(contentType);
			video.setFileSize(file.getSize());
			video.setUploadedAt(LocalDateTime.now());

			task.getVideos().add(video);
		}
	}

	private StoredFile storeMultipartFile(Long taskId, MultipartFile file, String leafDir) {
		try {
			String originalFilename = StringUtils
					.cleanPath(Optional.ofNullable(file.getOriginalFilename()).orElse("file"));

			String ext = getExtension(originalFilename);
			String savedFilename = UUID.randomUUID() + ext;

			String dateFolder = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

			Path root = Paths.get(uploadPath).toAbsolutePath().normalize();
			Path dir = root.resolve(Paths.get("as", String.valueOf(taskId), dateFolder, "request", leafDir))
					.normalize();

			Files.createDirectories(dir);

			Path target = dir.resolve(savedFilename).normalize();
			file.transferTo(target.toFile());

			Path relative = root.relativize(target);

			String url = "/upload/" + relative.toString().replace("\\", "/");

			return new StoredFile(originalFilename, target.toString(), url);
		} catch (IOException e) {
			throw new IllegalStateException("파일 저장 중 오류가 발생했습니다.", e);
		}
	}

	private String normalizeRequired(String value, String fieldName) {
		String normalized = normalize(value);
		if (!StringUtils.hasText(normalized)) {
			throw new IllegalArgumentException(fieldName + "을(를) 입력해 주세요.");
		}
		return normalized;
	}

	private String normalizeEmail(String value) {
		String normalized = normalize(value);
		return normalized == null ? null : normalized;
	}

	private String formatPhone(String value) {
		String digits = Optional.ofNullable(value).orElse("").replaceAll("\\D", "");
		if (digits.startsWith("02")) {
			if (digits.length() == 9)
				return digits.replaceFirst("(02)(\\d{3})(\\d{4})", "$1-$2-$3");
			if (digits.length() >= 10)
				return digits.replaceFirst("(02)(\\d{4})(\\d{4}).*", "$1-$2-$3");
		}
		if (digits.length() == 10)
			return digits.replaceFirst("(\\d{3})(\\d{3})(\\d{4})", "$1-$2-$3");
		if (digits.length() >= 11)
			return digits.replaceFirst("(\\d{3})(\\d{4})(\\d{4}).*", "$1-$2-$3");
		return digits;
	}

	private String formatOptionalPhone(String value) {
		String normalized = normalize(value);
		if (normalized == null)
			return null;
		return formatPhone(normalized);
	}

	private boolean isValidPhone(String value) {
		if (!StringUtils.hasText(value))
			return false;
		String digits = value.replaceAll("\\D", "");
		return digits.startsWith("0") && digits.length() >= 9 && digits.length() <= 11;
	}

	private String getExtension(String filename) {
		int idx = filename.lastIndexOf('.');
		return (idx >= 0) ? filename.substring(idx) : "";
	}

	private record StoredFile(String originalFilename, String absolutePath, String url) {
	}

	private String onlyDigits(String value) {
		return String.valueOf(value == null ? "" : value).replaceAll("\\D", "");
	}

	private boolean isValidPhoneByDigits(String digits) {
		if (!StringUtils.hasText(digits))
			return false;
		if (!digits.startsWith("0"))
			return false;
		return digits.length() >= 9 && digits.length() <= 11;
	}

	private String formatKoreanPhone(String digits) {
		digits = onlyDigits(digits);

		if (digits.length() > 11) {
			digits = digits.substring(0, 11);
		}

		if (digits.length() <= 3) {
			return digits;
		}

		if (digits.startsWith("02")) {
			if (digits.length() <= 5) {
				return digits.substring(0, 2) + "-" + digits.substring(2);
			}
			if (digits.length() == 9) {
				return digits.substring(0, 2) + "-" + digits.substring(2, 5) + "-" + digits.substring(5);
			}
			if (digits.length() >= 10) {
				return digits.substring(0, 2) + "-" + digits.substring(2, 6) + "-" + digits.substring(6, 10);
			}
		}

		if (digits.length() == 10) {
			return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
		}

		if (digits.length() >= 11) {
			return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7, 11);
		}

		return digits.substring(0, 3) + "-" + digits.substring(3);
	}

	public Page<AsTask> getFilteredAsListPage(
	        Long handlerId,
	        AsStatus status,
	        String dateType,
	        LocalDateTime start,
	        LocalDateTime end,
	        String priceFilter,
	        Boolean paymentCollected,
	        String keywordType,
	        String keyword,
	        Pageable pageable
	) {
	    if ("processed".equals(dateType)) {
	        return asTaskRepository.findByProcessedDateRangePage(
	                handlerId,
	                status,
	                start,
	                end,
	                priceFilter,
	                paymentCollected,
	                keywordType,
	                keyword,
	                pageable
	        );
	    }

	    return asTaskRepository.findByRequestedDateRangePage(
	            handlerId,
	            status,
	            start,
	            end,
	            priceFilter,
	            paymentCollected,
	            keywordType,
	            keyword,
	            pageable
	    );
	}

	public List<AsTask> getFilteredAsListAll(
	        Long handlerId,
	        AsStatus status,
	        String dateType,
	        LocalDateTime start,
	        LocalDateTime end,
	        String priceFilter,
	        Boolean paymentCollected,
	        String keywordType,
	        String keyword,
	        Sort sort
	) {
	    if ("processed".equals(dateType)) {
	        return asTaskRepository.findByProcessedDateRangeAll(
	                handlerId,
	                status,
	                start,
	                end,
	                priceFilter,
	                paymentCollected,
	                keywordType,
	                keyword,
	                sort
	        );
	    }

	    return asTaskRepository.findByRequestedDateRangeAll(
	            handlerId,
	            status,
	            start,
	            end,
	            priceFilter,
	            paymentCollected,
	            keywordType,
	            keyword,
	            sort
	    );
	}

	public List<AsTask> getFilteredAsListAll(
	        Long handlerId,
	        AsStatus status,
	        String dateType,
	        LocalDateTime start,
	        LocalDateTime end,
	        String priceFilter,
	        Boolean paymentCollected,
	        Sort sort
	) {
	    if ("processed".equals(dateType)) {
	        return asTaskRepository.findByProcessedDateRangeAll(
	                handlerId,
	                status,
	                start,
	                end,
	                priceFilter,
	                paymentCollected,
	                sort
	        );
	    }

	    return asTaskRepository.findByRequestedDateRangeAll(
	            handlerId,
	            status,
	            start,
	            end,
	            priceFilter,
	            paymentCollected,
	            sort
	    );
	}

	@Transactional(readOnly = true)
	public Page<AsTask> getAsTasks(Member handler, String dateType, LocalDateTime start, LocalDateTime end,
	        AsStatus status, String companyKeyword, Long provinceId, Long cityId, Long districtId,
	        String visitTimeSort, String scheduledDateSort, Pageable pageable) {

	    return getAsTasks(
	            handler,
	            dateType,
	            start,
	            end,
	            status,
	            companyKeyword,
	            provinceId,
	            cityId,
	            districtId,
	            visitTimeSort,
	            scheduledDateSort,
	            null,
	            pageable
	    );
	}
	
	@Transactional(readOnly = true)
	public Page<AsTask> getAsTasks(Member handler, String dateType, LocalDateTime start, LocalDateTime end,
	        AsStatus status, String companyKeyword, Long provinceId, Long cityId, Long districtId,
	        String visitTimeSort, Pageable pageable) {

	    return getAsTasks(
	            handler,
	            dateType,
	            start,
	            end,
	            status,
	            companyKeyword,
	            provinceId,
	            cityId,
	            districtId,
	            visitTimeSort,
	            null,
	            null,
	            pageable
	    );
	}

	@Transactional(readOnly = true)
	public Page<AsTask> getAsTasks(Member handler, String dateType, LocalDateTime start, LocalDateTime end,
	        AsStatus status, String companyKeyword, Long provinceId, Long cityId, Long districtId,
	        String visitTimeSort, String scheduledDateSort, String addressSort, Pageable pageable) {

	    String normalizedCompanyKeyword = normalizeBlankToNull(companyKeyword);
	    String normalizedVisitTimeSort = normalizeVisitTimeSort(visitTimeSort);
	    String normalizedScheduledDateSort = normalizeScheduledDateSort(scheduledDateSort);
	    String normalizedAddressSort = normalizeAddressSort(addressSort);

	    String provinceName = regionLookupService.getProvinceName(provinceId);
	    String cityName = regionLookupService.getCityName(cityId);
	    String districtName = regionLookupService.getDistrictName(districtId);

	    List<String> provinceNames = regionLookupService.getProvinceAliases(provinceName);

	    if (provinceNames != null && provinceNames.isEmpty()) {
	        provinceNames = null;
	    }

	    if ("scheduled".equalsIgnoreCase(dateType)) {
	        LocalDate startDate = (start != null) ? start.toLocalDate() : null;
	        LocalDate endDate = (end != null) ? end.toLocalDate() : null;

	        return asTaskRepository.findByScheduledDateFlexible(
	                handler.getId(),
	                status,
	                startDate,
	                endDate,
	                normalizedCompanyKeyword,
	                provinceNames,
	                cityName,
	                districtName,
	                normalizedVisitTimeSort,
	                normalizedScheduledDateSort,
	                normalizedAddressSort,
	                pageable
	        );
	    }

	    if ("requested".equalsIgnoreCase(dateType)) {
	        return asTaskRepository.findByRequestedDateFlexible(
	                handler.getId(),
	                status,
	                start,
	                end,
	                normalizedCompanyKeyword,
	                provinceNames,
	                cityName,
	                districtName,
	                normalizedVisitTimeSort,
	                normalizedAddressSort,
	                pageable
	        );
	    }

	    return asTaskRepository.findByProcessedDateFlexible(
	            handler.getId(),
	            status,
	            start,
	            end,
	            normalizedCompanyKeyword,
	            provinceNames,
	            cityName,
	            districtName,
	            normalizedVisitTimeSort,
	            normalizedAddressSort,
	            pageable
	    );
	}
	
	private String normalizeAddressSort(String raw) {
	    if (!StringUtils.hasText(raw)) {
	        return null;
	    }

	    String normalized = raw.trim().toLowerCase(Locale.ROOT);
	    if (!"asc".equals(normalized) && !"desc".equals(normalized)) {
	        return null;
	    }

	    return normalized;
	}

	@Transactional(readOnly = true)
	public Map<Long, String> getAddressGroupClassMap(List<AsTask> tasks) {
	    if (tasks == null || tasks.isEmpty()) {
	        return Collections.emptyMap();
	    }

	    List<String> palette = List.of(
	            "as-list-added-address-group-1",
	            "as-list-added-address-group-2",
	            "as-list-added-address-group-3",
	            "as-list-added-address-group-4",
	            "as-list-added-address-group-5",
	            "as-list-added-address-group-6"
	    );

	    Map<Long, String> result = new LinkedHashMap<>();
	    Map<String, String> classByAddress = new LinkedHashMap<>();

	    for (AsTask task : tasks) {
	        if (task == null || task.getId() == null) {
	            continue;
	        }

	        String addressKey = buildAddressGroupKey(task);
	        if (!StringUtils.hasText(addressKey)) {
	            result.put(task.getId(), "");
	            continue;
	        }

	        String cssClass = classByAddress.get(addressKey);
	        if (cssClass == null) {
	            cssClass = palette.get(classByAddress.size() % palette.size());
	            classByAddress.put(addressKey, cssClass);
	        }

	        result.put(task.getId(), cssClass);
	    }

	    return result;
	}

	private String buildAddressGroupKey(AsTask task) {
	    String roadAddress = normalizeBlankToNull(task.getRoadAddress());
	    String detailAddress = normalizeBlankToNull(task.getDetailAddress());

	    String merged = ((roadAddress != null ? roadAddress : "") + " " + (detailAddress != null ? detailAddress : ""))
	            .replaceAll("\\s+", " ")
	            .trim();

	    if (!StringUtils.hasText(merged)) {
	        return null;
	    }

	    return merged.toLowerCase(Locale.ROOT);
	}
	
	@Transactional(readOnly = true)
	public Map<Long, String> getScheduleDisplayMap(List<AsTask> tasks) {
		if (tasks == null || tasks.isEmpty()) {
			return Collections.emptyMap();
		}

		List<Long> taskIds = tasks.stream().map(AsTask::getId).filter(Objects::nonNull).collect(Collectors.toList());

		if (taskIds.isEmpty()) {
			return Collections.emptyMap();
		}

		List<AsTaskScheduleSummaryProjection> schedules = asTaskScheduleRepository.findSummariesByTaskIdIn(taskIds);

		Map<Long, String> result = new HashMap<>();

		for (AsTaskScheduleSummaryProjection schedule : schedules) {
			if (schedule.getTaskId() == null || schedule.getScheduledDate() == null) {
				continue;
			}

			int displayOrder = schedule.getOrderIndex() + 1;
			String text = schedule.getScheduledDate().format(DateTimeFormatter.ISO_LOCAL_DATE) + "(" + displayOrder
					+ "번째)";

			result.put(schedule.getTaskId(), text);
		}

		return result;
	}

	private String normalizeScheduledDateSort(String scheduledDateSort) {
		if (!StringUtils.hasText(scheduledDateSort)) {
			return null;
		}

		String v = scheduledDateSort.trim().toLowerCase();

		if ("asc".equals(v) || "desc".equals(v)) {
			return v;
		}

		return null;
	}

	@Transactional
	public void updateAsTaskByHandler(Long id, Member handler, AsStatus updatedStatus, String handlerMemo,
			LocalDate visitPlannedDate, LocalTime visitPlannedTime, List<MultipartFile> resultImages)
			throws IOException {

		AsTask task = asTaskRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("AS 요청이 존재하지 않습니다."));

		if (handler == null || handler.getId() == null) {
			throw new AccessDeniedException("잘못된 접근입니다.");
		}

		if (task.getAssignedHandler() == null || !Objects.equals(task.getAssignedHandler().getId(), handler.getId())) {
			throw new AccessDeniedException("본인에게 배정된 AS만 수정할 수 있습니다.");
		}

		if (task.getStatus() != AsStatus.IN_PROGRESS) {
			throw new IllegalStateException("진행중(IN_PROGRESS) 상태의 AS만 수정할 수 있습니다.");
		}

		boolean shouldSave = false;

		boolean scheduleChanged = syncVisitPlannedDate(task, handler, visitPlannedDate);
		if (scheduleChanged) {
			shouldSave = true;
		}

		String normalizedHandlerMemo = normalizeBlankToNull(handlerMemo);

		if (!Objects.equals(task.getHandlerMemo(), normalizedHandlerMemo)) {
			task.setHandlerMemo(normalizedHandlerMemo);
			shouldSave = true;
		}

		if (!Objects.equals(task.getVisitPlannedTime(), visitPlannedTime)) {
			task.setVisitPlannedTime(visitPlannedTime);
			shouldSave = true;
		}

		if (updatedStatus != null && updatedStatus == AsStatus.COMPLETED) {
			task.setStatus(AsStatus.COMPLETED);
			task.setAsProcessDate(LocalDateTime.now());
			shouldSave = true;
		}

		if (resultImages != null && !resultImages.isEmpty()) {
			if (task.getRequestedBy() == null || task.getRequestedBy().getId() == null) {
				throw new IllegalStateException("요청자 정보가 없어 결과 이미지를 저장할 수 없습니다.");
			}

			Long requesterId = task.getRequestedBy().getId();
			String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

			Path saveDir = Paths.get(uploadPath, "as", String.valueOf(requesterId), dateStr, "result");
			Files.createDirectories(saveDir);

			for (MultipartFile file : resultImages) {
				if (file == null || file.isEmpty()) {
					continue;
				}

				String originalFilename = file.getOriginalFilename();
				String filename = UUID.randomUUID() + "_"
						+ (StringUtils.hasText(originalFilename) ? originalFilename : "image");
				Path filePath = saveDir.resolve(filename);

				file.transferTo(filePath.toFile());

				AsImage image = new AsImage();
				image.setAsTask(task);
				image.setFilename(filename);
				image.setPath(filePath.toString());
				image.setUrl("/upload/as/" + requesterId + "/" + dateStr + "/result/" + filename);
				image.setType("RESULT");

				asImageRepository.save(image);
			}

			shouldSave = true;
		}

		if (shouldSave) {
			task.setUpdatedAt(LocalDateTime.now());
			asTaskRepository.save(task);
		}
	}

	private boolean syncVisitPlannedDate(AsTask task, Member handler, LocalDate visitPlannedDate) {
		AsTaskSchedule existingSchedule = asTaskScheduleRepository.findByAsTaskId(task.getId()).orElse(null);

		// 1) 화면에서 날짜를 비운 경우 -> 기존 스케줄이 있으면 삭제
		if (visitPlannedDate == null) {
			if (existingSchedule != null) {
				asTaskScheduleRepository.delete(existingSchedule);
				asTaskScheduleRepository.flush();
				return true;
			}
			return false;
		}

		// 2) 기존 스케줄이 있고 날짜가 그대로면 -> 유지 (orderIndex 변경 없음)
		if (existingSchedule != null && visitPlannedDate.equals(existingSchedule.getScheduledDate())) {
			return false;
		}

		// 3) 기존 스케줄이 있는데 날짜가 바뀌면 -> 기존 스케줄 삭제
		if (existingSchedule != null) {
			asTaskScheduleRepository.delete(existingSchedule);
			asTaskScheduleRepository.flush();
		}

		// 4) 새 날짜 기준으로 최대 orderIndex + 1 계산 후 신규 등록
		Integer maxOrderIndex = asTaskScheduleRepository.findMaxOrderIndexByScheduledDate(visitPlannedDate);
		int nextOrderIndex = (maxOrderIndex == null ? -1 : maxOrderIndex) + 1;

		AsTaskSchedule newSchedule = AsTaskSchedule.builder().asTask(task).scheduledDate(visitPlannedDate)
				.orderIndex(nextOrderIndex).createdBy(handler).build();

		asTaskScheduleRepository.save(newSchedule);
		return true;
	}

	@Transactional(readOnly = true)
	public TeamAsDetailModalResponse getAsTaskDetailModal(Long id, Member handler) {
		AsTask task = asTaskRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("AS 요청이 존재하지 않습니다."));

		if (handler == null || handler.getId() == null) {
			throw new AccessDeniedException("잘못된 접근입니다.");
		}

		if (task.getAssignedHandler() == null || !Objects.equals(task.getAssignedHandler().getId(), handler.getId())) {
			throw new AccessDeniedException("본인에게 배정된 AS만 조회할 수 있습니다.");
		}

		TeamAsDetailModalResponse response = new TeamAsDetailModalResponse();
		response.setId(task.getId());
		response.setStatus(task.getStatus() != null ? task.getStatus().name() : null);
		response.setCompanyName(task.getRequestedCompanyNameSafe());
		response.setRequesterName(task.getRequestedBy() != null ? safeText(task.getRequestedBy().getName()) : "-");
		response.setFullAddress(buildFullAddress(task));
		response.setReason(safeText(task.getReason()));
		response.setAdminMemo(task.getAdminMemoSafe());
		response.setHandlerMemo(task.getHandlerMemoSafe());
		response.setVisitPlannedTime(
				task.getVisitPlannedTime() != null ? task.getVisitPlannedTime().format(TIME_FORMATTER) : "-");
		response.setProductName(safeText(task.getProductName()));
		response.setProductSize(safeText(task.getProductSize()));
		response.setProductColor(safeText(task.getProductColor()));
		response.setOnsiteContact(safeText(task.getOnsiteContact()));
		response.setRequestedAt(
				task.getRequestedAt() != null ? task.getRequestedAt().format(DATE_TIME_FORMATTER) : "-");

		task.getResultImages().stream()
				.sorted(Comparator.comparing(AsImage::getUploadedAt, Comparator.nullsLast(Comparator.naturalOrder())))
				.forEach(img -> {
					TeamAsDetailModalResponse.ImageItem item = new TeamAsDetailModalResponse.ImageItem();
					item.setId(img.getId());
					item.setFilename(img.getFilename());
					item.setUrl(img.getUrl());
					response.getResultImages().add(item);
				});

		return response;
	}

	private String normalizeBlankToNull(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	private String normalizeVisitTimeSort(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String normalized = value.trim().toLowerCase();
		if ("asc".equals(normalized) || "desc".equals(normalized)) {
			return normalized;
		}
		return null;
	}

	private String safeText(String value) {
		if (!StringUtils.hasText(value)) {
			return "-";
		}
		return value.trim();
	}

	private String buildFullAddress(AsTask task) {
		List<String> parts = new ArrayList<>();

		if (StringUtils.hasText(task.getZipCode())) {
			parts.add("(" + task.getZipCode().trim() + ")");
		}
		if (StringUtils.hasText(task.getDoName())) {
			parts.add(task.getDoName().trim());
		}
		if (StringUtils.hasText(task.getSiName())) {
			parts.add(task.getSiName().trim());
		}
		if (StringUtils.hasText(task.getGuName())) {
			parts.add(task.getGuName().trim());
		}
		if (StringUtils.hasText(task.getRoadAddress())) {
			parts.add(task.getRoadAddress().trim());
		}
		if (StringUtils.hasText(task.getDetailAddress())) {
			parts.add(task.getDetailAddress().trim());
		}

		if (parts.isEmpty()) {
			return "-";
		}
		return String.join(" ", parts);
	}

	public List<AsTask> getFilteredAsList(Long memberId, AsStatus status, String dateType, LocalDateTime start,
			LocalDateTime end) {
		if ("processed".equals(dateType)) {
			return asTaskRepository.findByProcessedDateRangeList(memberId, status, start, end);
		} else {
			return asTaskRepository.findByRequestedDateRangeList(memberId, status, start, end);
		}
	}

	public Page<AsTask> getAsTasks(Member handler, String dateType, LocalDate date, AsStatus status,
			Pageable pageable) {
		LocalDateTime start = (date != null ? date : LocalDate.now()).atStartOfDay();
		LocalDateTime end = start.plusDays(1);

		if ("requested".equalsIgnoreCase(dateType)) {
			return asTaskRepository.findByRequestedDate(handler.getId(), status, start, end, pageable);
		} else {
			return asTaskRepository.findByProcessedDate(handler.getId(), status, start, end, pageable);
		}
	}

	public Page<AsTask> getFilteredAsList(Long memberId, AsStatus statuses, String dateType, LocalDateTime startDate,
			LocalDateTime endDate, Pageable pageable) {
		if ("processed".equals(dateType)) {
			return asTaskRepository.findByProcessedDateRange(memberId, statuses, startDate, endDate, pageable);
		} else {
			return asTaskRepository.findByRequestedDateRange(memberId, statuses, startDate, endDate, pageable);
		}
	}

	public List<AsTask> getFilteredAsList(Long handlerId, AsStatus status, LocalDate date) {
		LocalDateTime start = date.atStartOfDay();
		LocalDateTime end = date.plusDays(1).atStartOfDay();
		return asTaskRepository.findByFilterWithDateRangeNonPageable(handlerId, status, start, end);
	}

	@Transactional
	public void updateAsTask(Long id, Integer price, String statusStr, Long assignedHandlerId) {
		AsTask asTask = getAsDetail(id);

		AsStatus status = AsStatus.valueOf(statusStr);
		asTask.setPrice(price == null ? 0 : price);

		if (assignedHandlerId == null) {
			throw new IllegalArgumentException("담당자를 반드시 지정해야 합니다.");
		}

		Member handler = memberRepository.findById(assignedHandlerId)
				.orElseThrow(() -> new IllegalArgumentException("유효하지 않은 담당자입니다."));

		asTask.setAssignedHandler(handler);
		asTask.setStatus(status);
		asTask.setUpdatedAt(LocalDateTime.now());

		asTaskRepository.save(asTask);
	}

	// =========================================================
	// 1) 회사 검색(대리점 검색)
	// =========================================================
	@Transactional(readOnly = true)
	public List<CompanySearchItemDto> searchCompaniesForAs(String keyword, int limit) {
		String kw = normalize(keyword);
		if (kw.isBlank())
			return Collections.emptyList();

		int safeLimit = Math.max(1, Math.min(limit, 50));

		List<Company> companies = companyRepository.findTop50ByCompanyNameContainingOrderByCompanyNameAsc(kw);

		List<CompanySearchItemDto> out = new ArrayList<>();
		for (Company c : companies) {
			if (c == null)
				continue;

			Optional<Member> repOpt = memberRepository.findFirstByCompanyIdAndRoleOrderByIdAsc(c.getId(),
					MemberRole.CUSTOMER_REPRESENTATIVE);

			boolean hasRep = repOpt.isPresent();
			String repName = hasRep ? nvl(repOpt.get().getName(), "(대표)") : "(대표 없음)";

			String address = buildCompanyAddress(c);

			out.add(CompanySearchItemDto.builder().companyId(c.getId()).companyName(nvl(c.getCompanyName(), "-"))
					.address(address).representativeName(repName).hasRepresentative(hasRep).build());
		}

		if (out.size() > safeLimit)
			return out.subList(0, safeLimit);
		return out;
	}

	private String buildCompanyAddress(Company c) {
		List<String> parts = new ArrayList<>();
		addIfText(parts, c.getDoName());
		addIfText(parts, c.getSiName());
		addIfText(parts, c.getGuName());
		addIfText(parts, c.getRoadAddress());
		addIfText(parts, c.getDetailAddress());

		String s = String.join(" ", parts).trim();
		if (!s.isBlank())
			return s;

		// fallback
		addIfText(parts, c.getOriginAddress());
		addIfText(parts, c.getJibunAddress());
		s = String.join(" ", parts).trim();
		return s.isBlank() ? "-" : s;
	}

	private void addIfText(List<String> list, String v) {
		if (v == null)
			return;
		String t = v.trim();
		if (!t.isEmpty())
			list.add(t);
	}

	@Transactional
	public void updateAsTaskThird(Long id, String priceStr, String statusStr, Long assignedHandlerId,

	        Long companyId,

	        String zipCode, String doName, String siName, String guName, String roadAddress, String detailAddress,

	        String customerName, String productName, String productSize, String productColor, String productOptions,
	        String onsiteContact,

	        String applicantName, String applicantPhone, String applicantEmail, String purchaseDateStr,
	        String billingTargetStr, Boolean paymentCollected,

	        String subject, String adminMemo,

	        String deleteRequestImageIds, List<MultipartFile> newRequestImages,

	        String deleteRequestVideoIds, List<MultipartFile> newRequestVideos) {

	    AsTask asTask = getAsDetail(id);

	    // 완료 상태였는지 먼저 기억
	    boolean wasCompleted = AsStatus.COMPLETED.equals(asTask.getStatus());

	    // 0) 상태
	    if (StringUtils.hasText(statusStr)) {
	        AsStatus status = AsStatus.valueOf(statusStr.trim());
	        asTask.setStatus(status);
	    }

	    // 1) 담당자
	    if (assignedHandlerId == null) {
	        throw new IllegalArgumentException("담당자를 반드시 지정해야 합니다.");
	    }

	    Member handler = memberRepository.findById(assignedHandlerId)
	            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 담당자입니다."));
	    asTask.setAssignedHandler(handler);
	    asTask.setAssignedTeam(handler.getTeam());

	    // 2) 가격
	    // 이미 완료된 건은 백엔드에서도 가격 변경 막음
	    if (!wasCompleted) {
	        int price = parsePriceOrZero(priceStr);
	        asTask.setPrice(price);
	    }

	    // 3) 비용 수납 여부
	    asTask.setPaymentCollected(Boolean.TRUE.equals(paymentCollected));

	    // 4) 회사 변경 시 requestedBy 교체
	    if (companyId != null) {
	        Long currentCompanyId = (asTask.getRequestedBy() != null && asTask.getRequestedBy().getCompany() != null)
	                ? asTask.getRequestedBy().getCompany().getId()
	                : null;

	        if (currentCompanyId == null || !currentCompanyId.equals(companyId)) {

	            companyRepository.findById(companyId)
	                    .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 대리점입니다."));

	            Member representative = memberRepository
	                    .findFirstByCompanyIdAndRoleOrderByIdAsc(companyId, MemberRole.CUSTOMER_REPRESENTATIVE)
	                    .orElseThrow(() -> new IllegalArgumentException(
	                            "해당 대리점에 대표(CUSTOMER_REPRESENTATIVE) 회원이 없습니다."));

	            asTask.setRequestedBy(representative);
	        }
	    }

	    // 5) 주소
	    asTask.setZipCode(normalizeText(zipCode));
	    asTask.setDoName(normalizeText(doName));
	    asTask.setSiName(normalizeText(siName));
	    asTask.setGuName(normalizeText(guName));
	    asTask.setRoadAddress(normalizeText(roadAddress));
	    asTask.setDetailAddress(normalizeText(detailAddress));

	    // 6) 고객/제품/현장
	    asTask.setCustomerName(normalizeText(customerName));
	    asTask.setProductName(normalizeText(productName));
	    asTask.setProductSize(normalizeText(productSize));
	    asTask.setProductColor(normalizeText(productColor));
	    asTask.setProductOptions(normalizeText(productOptions));
	    asTask.setOnsiteContact(formatOptionalPhone(onsiteContact, "현장 연락처"));

	    // 7) 접수 담당자 / 납품일자 / 청구주체
	    asTask.setApplicantName(normalizeText(applicantName));
	    asTask.setApplicantPhone(formatOptionalPhone(applicantPhone, "접수 담당자 연락처"));
	    asTask.setApplicantEmail(normalizeOptionalEmail(applicantEmail));
	    asTask.setPurchaseDate(parseLocalDateOrNull(purchaseDateStr, "납품일자"));
	    asTask.setBillingTarget(parseBillingTargetOrNull(billingTargetStr));

	    // 8) subject
	    String normalizedSubject = normalizeText(subject);
	    if (isValidTwoDepthSubject(normalizedSubject)) {
	        asTask.setSubject(normalizedSubject);
	    }

	    // 9) 관리자 메모
	    asTask.setAdminMemo(normalizeText(adminMemo));

	    // 10) 요청 이미지 삭제
	    List<Long> deleteImageIdList = parseIdCsv(deleteRequestImageIds);
	    if (!deleteImageIdList.isEmpty()) {
	        deleteRequestImages(asTask, deleteImageIdList);
	    }

	    // 11) 요청 비디오 삭제
	    List<Long> deleteVideoIdList = parseIdCsv(deleteRequestVideoIds);
	    if (!deleteVideoIdList.isEmpty()) {
	        deleteRequestVideos(asTask, deleteVideoIdList);
	    }

	    // 12) 요청 이미지 추가
	    if (newRequestImages != null && !newRequestImages.isEmpty()) {
	        saveNewRequestImages(asTask, newRequestImages);
	    }

	    // 13) 요청 비디오 추가
	    if (newRequestVideos != null && !newRequestVideos.isEmpty()) {
	        saveNewRequestVideos(asTask, newRequestVideos);
	    }

	    asTask.setUpdatedAt(LocalDateTime.now());
	    asTaskRepository.save(asTask);
	}

	private void deleteRequestImages(AsTask asTask, List<Long> deleteIds) {
		List<AsImage> deleteTargets = asImageRepository.findByAsTaskIdAndIdInAndType(asTask.getId(), deleteIds,
				"REQUEST");

		if (deleteTargets.isEmpty()) {
			return;
		}

		Set<Long> deleteIdSet = deleteTargets.stream().map(AsImage::getId).collect(Collectors.toSet());

		for (AsImage image : deleteTargets) {
			deletePhysicalFileSafe(image.getPath());
		}

		asImageRepository.deleteAll(deleteTargets);

		if (asTask.getImages() != null) {
			asTask.getImages().removeIf(img -> img != null && deleteIdSet.contains(img.getId()));
		}
	}

	private void deleteRequestVideos(AsTask asTask, List<Long> deleteIds) {
		List<AsVideo> deleteTargets = asVideoRepository.findByAsTaskIdAndIdInAndType(asTask.getId(), deleteIds,
				"REQUEST");

		if (deleteTargets.isEmpty()) {
			return;
		}

		Set<Long> deleteIdSet = deleteTargets.stream().map(AsVideo::getId).collect(Collectors.toSet());

		for (AsVideo video : deleteTargets) {
			deletePhysicalFileSafe(video.getPath());
		}

		asVideoRepository.deleteAll(deleteTargets);

		if (asTask.getVideos() != null) {
			asTask.getVideos().removeIf(video -> video != null && deleteIdSet.contains(video.getId()));
		}
	}

	private void saveNewRequestImages(AsTask asTask, List<MultipartFile> files) {
		Member owner = asTask.getRequestedBy();
		if (owner == null || owner.getId() == null) {
			throw new IllegalStateException("요청자 정보가 없어 이미지 저장 경로를 구성할 수 없습니다.");
		}

		String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		Path saveDir = Paths.get(uploadPath, "as", String.valueOf(owner.getId()), dateStr, "request");

		try {
			Files.createDirectories(saveDir);
		} catch (IOException e) {
			throw new RuntimeException("이미지 저장 폴더 생성 실패: " + saveDir, e);
		}

		for (MultipartFile file : files) {
			if (file == null || file.isEmpty()) {
				continue;
			}

			if (!isImageFile(file)) {
				throw new IllegalArgumentException("이미지 업로드 항목에는 이미지 파일만 첨부할 수 있습니다.");
			}

			String originalName = getSafeOriginalFilename(file);
			String filename = UUID.randomUUID() + "_" + originalName;
			Path filePath = saveDir.resolve(filename);

			try {
				file.transferTo(filePath.toFile());
			} catch (IOException e) {
				throw new RuntimeException("이미지 저장 실패: " + filename, e);
			}

			String url = "/upload/as/" + owner.getId() + "/" + dateStr + "/request/" + filename;

			AsImage image = new AsImage();
			image.setAsTask(asTask);
			image.setFilename(filename);
			image.setPath(filePath.toString());
			image.setUrl(url);
			image.setType("REQUEST");

			asImageRepository.save(image);
		}
	}

	private void saveNewRequestVideos(AsTask asTask, List<MultipartFile> files) {
		Member owner = asTask.getRequestedBy();
		if (owner == null || owner.getId() == null) {
			throw new IllegalStateException("요청자 정보가 없어 비디오 저장 경로를 구성할 수 없습니다.");
		}

		String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		Path saveDir = Paths.get(uploadPath, "as", String.valueOf(owner.getId()), dateStr, "request");

		try {
			Files.createDirectories(saveDir);
		} catch (IOException e) {
			throw new RuntimeException("비디오 저장 폴더 생성 실패: " + saveDir, e);
		}

		for (MultipartFile file : files) {
			if (file == null || file.isEmpty()) {
				continue;
			}

			if (!isVideoFile(file)) {
				throw new IllegalArgumentException("비디오 업로드 항목에는 동영상 파일만 첨부할 수 있습니다.");
			}

			String originalName = getSafeOriginalFilename(file);
			String filename = UUID.randomUUID() + "_" + originalName;
			Path filePath = saveDir.resolve(filename);

			try {
				file.transferTo(filePath.toFile());
			} catch (IOException e) {
				throw new RuntimeException("비디오 저장 실패: " + filename, e);
			}

			String url = "/upload/as/" + owner.getId() + "/" + dateStr + "/request/" + filename;

			AsVideo video = new AsVideo();
			video.setAsTask(asTask);
			video.setType("REQUEST");
			video.setFilename(filename);
			video.setPath(filePath.toString());
			video.setUrl(url);
			video.setContentType(file.getContentType());
			video.setFileSize(file.getSize());

			asVideoRepository.save(video);
		}
	}

	private String formatOptionalPhone(String rawValue, String fieldLabel) {
		String value = normalizeText(rawValue);

		if (!StringUtils.hasText(value)) {
			return null;
		}

		String digits = onlyDigits(value);
		if (!isValidPhoneByDigits(digits)) {
			throw new IllegalArgumentException(fieldLabel + " 형식이 올바르지 않습니다.");
		}

		return formatKoreanPhone(digits);
	}

	private LocalDate parseLocalDateOrNull(String rawValue, String fieldLabel) {
		String value = normalizeText(rawValue);

		if (!StringUtils.hasText(value)) {
			return null;
		}

		try {
			return LocalDate.parse(value);
		} catch (Exception e) {
			throw new IllegalArgumentException(fieldLabel + " 형식이 올바르지 않습니다. (yyyy-MM-dd)");
		}
	}

	private AsBillingTarget parseBillingTargetOrNull(String rawValue) {
		String value = normalizeText(rawValue);

		if (!StringUtils.hasText(value)) {
			return null;
		}

		try {
			return AsBillingTarget.valueOf(value);
		} catch (Exception e) {
			throw new IllegalArgumentException("청구주체 값이 올바르지 않습니다.");
		}
	}

	// =========================================================
	// 3) 삭제 - 일정 + 이미지 + AsTask
	// =========================================================
	@Transactional
	public void deleteAsTaskCascade(Long asTaskId) {
		AsTask asTask = getAsDetail(asTaskId);

		// 1) 일정 삭제
		List<AsTaskSchedule> schedules = asTaskScheduleRepository.findByAsTask_Id(asTaskId);
		if (!schedules.isEmpty()) {
			asTaskScheduleRepository.deleteAll(schedules);
		}

		// 2) 이미지 파일 + DB 삭제
		List<AsImage> images = asImageRepository.findByAsTask_Id(asTaskId);
		for (AsImage img : images) {
			if (img == null)
				continue;
			deletePhysicalFileSafe(img.getPath());
		}
		if (!images.isEmpty()) {
			asImageRepository.deleteAll(images);
		}

		// 3) 동영상 파일 + DB 삭제
		List<AsVideo> videos = asVideoRepository.findByAsTask_Id(asTaskId);
		for (AsVideo video : videos) {
			if (video == null)
				continue;
			deletePhysicalFileSafe(video.getPath());
		}
		if (!videos.isEmpty()) {
			asVideoRepository.deleteAll(videos);
		}

		// 4) AS 삭제
		asTaskRepository.delete(asTask);
	}

	// ---------------------------------------------------------
	// Utils
	// ---------------------------------------------------------
	private int parsePriceOrZero(String s) {
		String v = normalize(s);
		if (v.isBlank())
			return 0;
		try {
			return Integer.parseInt(v);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private List<Long> parseIdCsv(String csv) {
		String v = normalize(csv);
		if (v.isBlank())
			return Collections.emptyList();

		List<Long> out = new ArrayList<>();
		for (String token : v.split(",")) {
			String t = token.trim();
			if (t.isEmpty())
				continue;
			try {
				out.add(Long.parseLong(t));
			} catch (NumberFormatException ignore) {
			}
		}
		return out;
	}

	private void deletePhysicalFileSafe(String pathStr) {
		try {
			if (pathStr == null || pathStr.isBlank())
				return;
			Path p = Paths.get(pathStr);
			Files.deleteIfExists(p);
		} catch (Exception ignore) {
			// 파일 삭제 실패는 저장/삭제 전체를 막지 않도록 "조용히" 처리(원하시면 로그로 변경 가능)
		}
	}

	private boolean isValidTwoDepthSubject(String subject) {
		if (subject == null)
			return false;
		String s = subject.trim();
		if (s.isEmpty())
			return false;

		// "상부장 - 도색 벗겨짐" 형태만 허용
		String[] parts = s.split(" - ");
		if (parts.length != 2)
			return false;

		String a = parts[0].trim();
		String b = parts[1].trim();
		return !a.isEmpty() && !b.isEmpty();
	}

	private String normalize(String v) {
		if (v == null)
			return "";
		return v.trim();
	}

	private String nvl(String v, String def) {
		if (v == null)
			return def;
		String t = v.trim();
		return t.isEmpty() ? def : t;
	}

	public AsTask getAsDetail(Long id) {
		return asTaskRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("해당 AS 요청을 찾을 수 없습니다. ID: " + id));
	}

	@Transactional
	public AsTask submitAsTask(AsTask task, List<MultipartFile> attachments, Member member) throws IOException {

		if (member == null || member.getId() == null) {
			throw new IllegalArgumentException("로그인 정보가 없습니다.");
		}

		if (task == null) {
			throw new IllegalArgumentException("잘못된 요청입니다.");
		}

		List<MultipartFile> safeAttachments = (attachments == null) ? Collections.emptyList()
				: attachments.stream().filter(file -> file != null && !file.isEmpty()).collect(Collectors.toList());

		// 요청자
		task.setRequestedBy(member);

		// 기본 문자열 정리
		task.setCustomerName(normalizeText(task.getCustomerName()));
		task.setRoadAddress(normalizeText(task.getRoadAddress()));
		task.setDetailAddress(normalizeText(task.getDetailAddress()));
		task.setDoName(normalizeText(task.getDoName()));
		task.setSiName(normalizeText(task.getSiName()));
		task.setGuName(normalizeText(task.getGuName()));
		task.setZipCode(normalizeText(task.getZipCode()));

		task.setOnsiteContact(formatRequiredPhone(task.getOnsiteContact(), "현장 연락처"));

		task.setApplicantName(normalizeText(task.getApplicantName()));
		task.setApplicantPhone(formatRequiredPhone(task.getApplicantPhone(), "신청 담당자 연락처"));
		task.setApplicantEmail(normalizeOptionalEmail(task.getApplicantEmail()));

		task.setProductName(normalizeText(task.getProductName()));
		task.setProductSize(normalizeText(task.getProductSize()));
		task.setProductColor(normalizeText(task.getProductColor()));
		task.setProductOptions(normalizeText(task.getProductOptions()));
		task.setSubject(normalizeText(task.getSubject()));
		task.setReason(normalizeText(task.getReason()));

		// 상태/시간
		task.setRequestedAt(LocalDateTime.now());
		task.setUpdatedAt(null);
		task.setStatus(AsStatus.REQUESTED);

		validateCustomerSubmitTask(task, safeAttachments);

		// hidden 주소값이 비어 있는 경우만 도/시/구 보정
		if (!StringUtils.hasText(task.getDoName()) && !StringUtils.hasText(task.getSiName())
				&& !StringUtils.hasText(task.getGuName())) {
			refineAddressFromRoad(task);
		}

		// 담당자 자동 배정
		assignAsHandlerIfPossible(task);

		// DB 저장
		AsTask savedTask = asTaskRepository.save(task);

		// 첨부 저장
		saveRequestAttachments(savedTask, safeAttachments, member.getId());

		return savedTask;
	}

	private void validateCustomerSubmitTask(AsTask task, List<MultipartFile> attachments) {
		if (!StringUtils.hasText(task.getCustomerName())) {
			throw new IllegalArgumentException("고객 성함을 입력해 주세요.");
		}

		if (!StringUtils.hasText(task.getRoadAddress())) {
			throw new IllegalArgumentException("주소를 입력해 주세요.");
		}

		if (!StringUtils.hasText(task.getOnsiteContact())) {
			throw new IllegalArgumentException("현장 연락처를 입력해 주세요.");
		}

		if (!StringUtils.hasText(task.getApplicantName())) {
			throw new IllegalArgumentException("신청 담당자 이름을 입력해 주세요.");
		}

		if (!StringUtils.hasText(task.getApplicantPhone())) {
			throw new IllegalArgumentException("신청 담당자 연락처를 입력해 주세요.");
		}

		if (!StringUtils.hasText(task.getProductName())) {
			throw new IllegalArgumentException("제품명을 입력해 주세요.");
		}

		if (!StringUtils.hasText(task.getProductSize())) {
			throw new IllegalArgumentException("제품 사이즈를 입력해 주세요.");
		}

		if (!StringUtils.hasText(task.getProductColor())) {
			throw new IllegalArgumentException("제품 색상을 입력해 주세요.");
		}

		if (!StringUtils.hasText(task.getProductOptions())) {
			throw new IllegalArgumentException("제품 옵션을 입력해 주세요.");
		}

		if (!StringUtils.hasText(task.getSubject()) || !isValidTwoDepthSubject(task.getSubject())) {
			throw new IllegalArgumentException("AS 증상을 올바르게 선택해 주세요.");
		}

		if (task.getBillingTarget() == null) {
			throw new IllegalArgumentException("비용 청구 주체를 선택해 주세요.");
		}

		if (attachments == null || attachments.isEmpty()) {
			throw new IllegalArgumentException("사진 또는 동영상을 1개 이상 첨부해 주세요.");
		}
	}

	private String formatRequiredPhone(String rawValue, String fieldLabel) {
		String digits = onlyDigits(rawValue);

		if (!isValidPhoneByDigits(digits)) {
			throw new IllegalArgumentException(fieldLabel + " 형식이 올바르지 않습니다.");
		}

		return formatKoreanPhone(digits);
	}

	private String normalizeOptionalEmail(String email) {
		String value = normalizeText(email);

		if (!StringUtils.hasText(value)) {
			return null;
		}

		String regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
		if (!value.matches(regex)) {
			throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
		}

		return value;
	}

	private void saveRequestAttachments(AsTask savedTask, List<MultipartFile> attachments, Long ownerMemberId)
			throws IOException {
		if (attachments == null || attachments.isEmpty()) {
			return;
		}

		String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		Path saveDir = Paths.get(uploadPath, "as", String.valueOf(ownerMemberId), dateStr, "request");
		Files.createDirectories(saveDir);

		for (MultipartFile file : attachments) {
			if (file == null || file.isEmpty()) {
				continue;
			}

			String originalName = getSafeOriginalFilename(file);
			String filename = UUID.randomUUID() + "_" + originalName;
			Path filePath = saveDir.resolve(filename);

			if (!isSupportedAttachment(file)) {
				throw new IllegalArgumentException("지원하지 않는 첨부파일 형식입니다. 이미지 또는 동영상만 업로드해 주세요.");
			}

			file.transferTo(filePath.toFile());

			String url = "/upload/as/" + ownerMemberId + "/" + dateStr + "/request/" + filename;

			if (isVideoFile(file)) {
				AsVideo video = new AsVideo();
				video.setAsTask(savedTask);
				video.setType("REQUEST");
				video.setFilename(filename);
				video.setPath(filePath.toString());
				video.setUrl(url);
				video.setContentType(file.getContentType());
				video.setFileSize(file.getSize());
				asVideoRepository.save(video);
			} else {
				AsImage image = new AsImage();
				image.setAsTask(savedTask);
				image.setFilename(filename);
				image.setPath(filePath.toString());
				image.setUrl(url);
				image.setType("REQUEST");
				asImageRepository.save(image);
			}
		}
	}

	private boolean isSupportedAttachment(MultipartFile file) {
		return isImageFile(file) || isVideoFile(file);
	}

	private boolean isImageFile(MultipartFile file) {
		String contentType = normalizeContentType(file.getContentType());
		if (contentType.startsWith("image/")) {
			return true;
		}

		String ext = getFileExtension(getSafeOriginalFilename(file));
		return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("gif") || ext.equals("webp")
				|| ext.equals("bmp") || ext.equals("heic") || ext.equals("heif");
	}

	private boolean isVideoFile(MultipartFile file) {
		String contentType = normalizeContentType(file.getContentType());
		if (contentType.startsWith("video/")) {
			return true;
		}

		String ext = getFileExtension(getSafeOriginalFilename(file));
		return ext.equals("mp4") || ext.equals("mov") || ext.equals("avi") || ext.equals("m4v") || ext.equals("wmv")
				|| ext.equals("webm") || ext.equals("mkv");
	}

	private String normalizeContentType(String contentType) {
		return contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
	}

	private String getSafeOriginalFilename(MultipartFile file) {
		String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("file");
		return Paths.get(originalName).getFileName().toString();
	}

	private String getFileExtension(String filename) {
		if (!StringUtils.hasText(filename)) {
			return "";
		}

		int idx = filename.lastIndexOf('.');
		if (idx < 0 || idx == filename.length() - 1) {
			return "";
		}

		return filename.substring(idx + 1).trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeText(String v) {
		if (v == null)
			return null;
		String t = v.trim();
		return t.isEmpty() ? null : t;
	}

	// ==============================
	// 주소 파싱 (기존 유지)
	// ==============================
	private void refineAddressFromRoad(AsTask task) {
		String full = task.getRoadAddress();
		if (full == null || full.isBlank())
			return;

		String[] tokens = full.trim().split("\\s+");
		String doName = "", siName = "", guName = "";

		if (tokens.length >= 1)
			doName = tokens[0];

		for (int i = 1; i < tokens.length; i++) {
			String word = tokens[i];
			if (word.endsWith("시") && siName.isBlank())
				siName = word;
			else if (word.endsWith("구") && guName.isBlank())
				guName = word;
			if (!siName.isBlank() && !guName.isBlank())
				break;
		}

		if (siName.isBlank() && guName.isBlank() && tokens.length >= 2)
			guName = tokens[1];

		task.setDoName(doName);
		task.setSiName(siName);
		task.setGuName(guName);
	}

	// ==============================
	// AS 담당자 자동 배정 (업그레이드)
	// - 도/시/구 유연 해석(명칭 정규화 포함)
	// - 포함 매칭(구→시→도) 우선순위
	// - 동순위 다수 시 무작위
	// ==============================
	private void assignAsHandlerIfPossible(AsTask task) {
		final String doName = task.getDoName();
		final String siName = task.getSiName();
		final String guName = task.getGuName();

		System.out.println("🛠 [AS 주소 파싱]");
		System.out.println("- 도 : " + doName);
		System.out.println("- 시 : " + siName);
		System.out.println("- 구 : " + guName);

		if (doName == null || doName.isBlank()) {
			System.out.println("❌ 도 정보 부족. AS 배정 중단");
			return;
		}

		try {
			// 1) 도/시/구를 유연하게 해석해 키 산출 (구 없어도 진행)
			RegionKey key = resolveRegionKey(doName, siName, guName);
			if (key.provinceId == null) {
				System.out.println("❌ Province 매칭 실패. AS 배정 중단");
				return;
			}
			System.out.println("✅ 해석된 RegionKey: provinceId=" + key.provinceId + ", cityId=" + key.cityId
					+ ", districtId=" + key.districtId);

			// 2) 포함 매칭 후보 조회 (팀명=AS팀)
			// 👉 주의: 아래 메서드는 앞서 제공한 JPQL(@Query) 메서드명과 시그니처가 일치해야 합니다.
			// 기존에 findDeliveryRegionMatches(...) 로 구현해 두셨다면 동일 시그니처/동일 JPQL로 사용 가능합니다.
			List<MemberRegion> matches = memberRegionRepository.findDeliveryRegionMatches(AS_TEAM_NAME, key.provinceId,
					key.cityId, key.districtId);

			System.out.println("🔎 AS 포함 매칭 후보 수: " + matches.size());
			if (matches.isEmpty()) {
				System.out.println("❌ AS 담당자 후보 없음");
				return;
			}

			// 3) 우선순위 스코어링 (구=3, 시=2, 도=1)
			Map<Member, Integer> bestScopePerMember = new HashMap<>();
			for (MemberRegion mr : matches) {
				Member m = mr.getMember();
				int scope = scopeScore(mr);
				bestScopePerMember.merge(m, scope, Math::max);
			}

			int topScope = bestScopePerMember.values().stream().mapToInt(i -> i).max().orElse(1);
			List<Member> topCandidates = bestScopePerMember.entrySet().stream().filter(e -> e.getValue() == topScope)
					.map(Map.Entry::getKey).collect(Collectors.toList());

			System.out.println("🏅 최고 우선순위: " + topScope + ", 후보: " + topCandidates.size());
			if (topCandidates.isEmpty()) {
				System.out.println("❌ 동순위 후보 없음");
				return;
			}

			// 4) 동순위 다수 → 무작위 (원하시면 라운드로빈/최소작업 우선 등으로 교체 가능)
			Member selected = topCandidates.get((int) (Math.random() * topCandidates.size()));
			task.setAssignedHandler(selected);
			task.setAssignedTeam(selected.getTeam());

			System.out.println("✅ AS 담당자 배정 완료 → " + selected.getUsername() + " (scope=" + topScope + ")");

		} catch (Exception e) {
			System.out.println("❌ AS 배정 예외: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/** 구(3) > 시(2) > 도(1) */
	private int scopeScore(MemberRegion mr) {
		if (mr.getDistrict() != null)
			return 3;
		if (mr.getCity() != null)
			return 2;
		return 1;
	}

	// ==============================
	// 해석/정규화 헬퍼
	// ==============================
	/** 접미사 제거로 베이스명 산출 (특별자치도/광역시/특별시/자치시/자치구/자치군/도/시/군/구 1회 제거) */
	private String normalizeBase(String s) {
		if (s == null)
			return null;
		String trimmed = s.trim();
		String[] suffixes = { "특별자치도", "광역시", "특별시", "자치시", "자치구", "자치군", "도", "시", "군", "구" };
		for (String suf : suffixes) {
			if (trimmed.endsWith(suf)) {
				trimmed = trimmed.substring(0, trimmed.length() - suf.length());
				break;
			}
		}
		return trimmed;
	}

	/** provinceId/cityId/districtId를 유연하게 산출 (구 없어도 OK) */
	private RegionKey resolveRegionKey(String doName, String siName, String guName) {
		String pBase = normalizeBase(doName);
		String cBase = (siName != null ? normalizeBase(siName) : null);
		String dBase = (guName != null ? normalizeBase(guName) : null);

		// Province
		List<Province> provinces = provinceRepository.findAll();
		Province province = pickByBase(provinces, Province::getName, pBase);
		if (province == null)
			province = pickByRelaxed(provinces, Province::getName, pBase);
		Long provinceId = (province != null ? province.getId() : null);
		if (provinceId == null)
			return new RegionKey(null, null, null);

		// City (optional)
		Long cityId = null;
		City city = null;
		if (cBase != null && !cBase.isBlank()) {
			List<City> cities = cityRepository.findByProvinceId(provinceId);
			city = pickByBase(cities, City::getName, cBase);
			if (city == null)
				city = pickByRelaxed(cities, City::getName, cBase);
			cityId = (city != null ? city.getId() : null);
		}

		// District (optional)
		Long districtId = null;
		if (dBase != null && !dBase.isBlank()) {
			List<District> districts = (cityId != null) ? districtRepository.findByCityId(cityId)
					: districtRepository.findByProvinceId(provinceId); // 서울/세종 등
			District dist = pickByBase(districts, District::getName, dBase);
			if (dist == null)
				dist = pickByRelaxed(districts, District::getName, dBase);
			districtId = (dist != null ? dist.getId() : null);
		}

		return new RegionKey(provinceId, cityId, districtId);
	}

	/** 베이스명 비교: normalize 후 상호 포함 */
	private <T> T pickByBase(List<T> list, java.util.function.Function<T, String> nameFn, String base) {
		if (base == null || base.isBlank())
			return null;
		String b = normalizeBase(base);
		for (T t : list) {
			String n = nameFn.apply(t);
			String nb = normalizeBase(n);
			if (nb != null && (nb.contains(b) || b.contains(nb)))
				return t;
		}
		return null;
	}

	/** 완화 비교: 공백 제거 후 상호 포함 */
	private <T> T pickByRelaxed(List<T> list, java.util.function.Function<T, String> nameFn, String keyword) {
		if (keyword == null || keyword.isBlank())
			return null;
		String k = keyword.replaceAll("\\s+", "");
		for (T t : list) {
			String n = nameFn.apply(t);
			if (n == null)
				continue;
			String nn = n.replaceAll("\\s+", "");
			if (nn.contains(k) || k.contains(nn))
				return t;
		}
		return null;
	}

	/** provinceId / cityId / districtId 묶음 */
	private record RegionKey(Long provinceId, Long cityId, Long districtId) {
	}

	@Transactional
	public void updateAsTaskByHandler(Long id, AsStatus updatedStatus, List<MultipartFile> resultImages)
			throws IOException {
		AsTask task = asTaskRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("AS 요청이 존재하지 않습니다."));

		boolean shouldSave = false;

		// ✅ 상태 변경 조건
		if (updatedStatus != null && task.getStatus() == AsStatus.IN_PROGRESS && updatedStatus == AsStatus.COMPLETED) {
			task.setStatus(AsStatus.COMPLETED);
			task.setAsProcessDate(LocalDateTime.now());
			shouldSave = true;
		}

		// ✅ 이미지 업로드 처리
		if (resultImages != null && !resultImages.isEmpty()) {
			Long requesterId = task.getRequestedBy().getId();
			String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

			Path saveDir = Paths.get(uploadPath, "as", String.valueOf(requesterId), dateStr, "result");
			Files.createDirectories(saveDir);

			for (MultipartFile file : resultImages) {
				if (file.isEmpty())
					continue;

				String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
				Path filePath = saveDir.resolve(filename);
				file.transferTo(filePath.toFile());

				AsImage image = new AsImage();
				image.setAsTask(task);
				image.setFilename(filename);
				image.setPath(filePath.toString());
				image.setUrl("/upload/as/" + requesterId + "/" + dateStr + "/result/" + filename);
				image.setType("RESULT");

				asImageRepository.save(image);
			}

			shouldSave = true;
		}

		if (shouldSave) {
			task.setUpdatedAt(LocalDateTime.now());
			asTaskRepository.save(task);
		}
	}

	@Transactional(readOnly = true)
	public Page<AsTaskCardDto> getAsTasksForCalendar(Member member, String dateType, LocalDateTime start,
			LocalDateTime end, // ✅ end는 exclusive(컨트롤러에서 endDate+1일 처리)
			AsStatus status, String companyKeyword, Long provinceId, Long cityId, Long districtId, Pageable pageable) {
		// 0) dateType normalize
		String dt = (dateType == null || dateType.isBlank()) ? "requested" : dateType.trim().toLowerCase();

		// 1) companyKeyword normalize
		String kw = (companyKeyword == null) ? null : companyKeyword.trim();
		if (kw != null && kw.isEmpty())
			kw = null;

		// 2) 지역 ID -> name 해석
		String provinceName = null;
		String cityName = null;
		String districtName = null;

		if (provinceId != null) {
			provinceName = provinceRepository.findById(provinceId).map(Province::getName).orElse(null);
		}
		if (cityId != null) {
			cityName = cityRepository.findById(cityId).map(City::getName).orElse(null);
		}
		if (districtId != null) {
			districtName = districtRepository.findById(districtId).map(District::getName).orElse(null);
		}

		// 3) 별칭 목록 생성(기존 로직 유지)
		List<String> provinceNames = buildRegionAliases(provinceName, RegionLevel.PROVINCE);
		List<String> cityNames = buildRegionAliases(cityName, RegionLevel.CITY);
		List<String> districtNames = buildRegionAliases(districtName, RegionLevel.DISTRICT);

		provinceNames = (provinceNames == null || provinceNames.isEmpty()) ? null : provinceNames;
		cityNames = (cityNames == null || cityNames.isEmpty()) ? null : cityNames;
		districtNames = (districtNames == null || districtNames.isEmpty()) ? null : districtNames;

		// 4) dateType별 조회를 Repository에서 “정렬+날짜필터”까지 처리
		Page<AsTask> page;

		if ("scheduled".equals(dt)) {
			LocalDate s = (start != null) ? start.toLocalDate() : null;
			LocalDate e = (end != null) ? end.toLocalDate() : null; // ✅ end는 이미 exclusive 상태

			page = asTaskRepository.searchScheduledForCalendar(status, kw, provinceNames, cityNames, districtNames, s,
					e, pageable);

		} else if ("processed".equals(dt)) {
			page = asTaskRepository.searchProcessedForCalendar(status, kw, provinceNames, cityNames, districtNames,
					start, end, pageable);

		} else {
			// default: requested
			page = asTaskRepository.searchRequestedForCalendar(status, kw, provinceNames, cityNames, districtNames,
					start, end, pageable);
		}

		// 5) schedule 정보 합치기(기존 유지)
		List<Long> taskIds = page.getContent().stream().map(AsTask::getId).toList();
		Map<Long, LocalDate> scheduledMap = scheduleRepository.findByTaskIds(taskIds).stream()
				.collect(Collectors.toMap(s -> s.getAsTask().getId(), AsTaskSchedule::getScheduledDate, (a, b) -> a));

		// 6) DTO 변환(기존 유지)
		List<AsTaskCardDto> dtoList = page.getContent().stream().map(t -> {
			String companyName = (t.getRequestedBy() != null && t.getRequestedBy().getCompany() != null)
					? t.getRequestedBy().getCompany().getCompanyName()
					: "(업체없음)";

			String address = String.join(" ", Optional.ofNullable(t.getDoName()).orElse(""),
					Optional.ofNullable(t.getSiName()).orElse(""), Optional.ofNullable(t.getGuName()).orElse(""),
					Optional.ofNullable(t.getRoadAddress()).orElse(""),
					Optional.ofNullable(t.getDetailAddress()).orElse("")).trim();

			return AsTaskCardDto.builder().taskId(t.getId()).companyName(companyName).requestedAt(t.getRequestedAt())
					.asProcessDate(t.getAsProcessDate()).address(address).status(t.getStatus().name())
					.scheduledDate(scheduledMap.get(t.getId())).build();
		}).toList();

		return new PageImpl<>(dtoList, pageable, page.getTotalElements());
	}

	// =========================
	// 별칭 생성 로직
	// =========================
	private enum RegionLevel {
		PROVINCE, CITY, DISTRICT
	}

	/**
	 * 예: - "경기도" -> ["경기도", "경기"] - "강원" -> ["강원", "강원도"] - "서울특별시" -> ["서울특별시",
	 * "서울"] - "부산광역시" -> ["부산광역시", "부산"]
	 */
	private List<String> buildRegionAliases(String input, RegionLevel level) {
		if (input == null)
			return Collections.emptyList();
		String name = input.trim();
		if (name.isEmpty())
			return Collections.emptyList();

		// 원본 포함
		Set<String> set = new LinkedHashSet<>();
		set.add(name);

		// 1) suffix 제거한 "짧은 형태" 생성
		String shortName = stripSuffix(name, level);
		if (!shortName.isBlank())
			set.add(shortName);

		// 2) 반대로 "긴 형태"도 보강 (short -> long 후보)
		// (예: "강원"이면 "강원도"도 추가, "서울"이면 "서울특별시"도 추가 등)
		set.addAll(expandToCommonLongForms(shortName, level));

		// 빈 문자열 제거
		return set.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
	}

	private String stripSuffix(String name, RegionLevel level) {
		String n = name.trim();

		// 가장 긴 suffix부터 제거 (정확도)
		if (level == RegionLevel.PROVINCE) {
			// 시/도 단위
			String[] suffixes = { "특별자치도", "특별자치시", "특별시", "광역시", "자치도", "도" };
			for (String suf : suffixes) {
				if (n.endsWith(suf))
					return n.substring(0, n.length() - suf.length()).trim();
			}
			return n;

		} else if (level == RegionLevel.CITY) {
			// 시/군 단위 (필요 시 확장)
			String[] suffixes = { "특별시", "광역시", "특별자치시", "시", "군" };
			for (String suf : suffixes) {
				if (n.endsWith(suf))
					return n.substring(0, n.length() - suf.length()).trim();
			}
			return n;

		} else {
			// 구/군 단위
			String[] suffixes = { "구", "군", "시" };
			for (String suf : suffixes) {
				if (n.endsWith(suf))
					return n.substring(0, n.length() - suf.length()).trim();
			}
			return n;
		}
	}

	private Set<String> expandToCommonLongForms(String shortName, RegionLevel level) {
		if (shortName == null)
			return Collections.emptySet();
		String s = shortName.trim();
		if (s.isEmpty())
			return Collections.emptySet();

		Set<String> out = new LinkedHashSet<>();

		if (level == RegionLevel.PROVINCE) {
			// 흔한 케이스만 안전하게 추가
			// (DB 값이 "서울특별시"로 저장되어 있을 수도 있고, "서울"로 저장되어 있을 수도 있어서 둘 다 지원)
			switch (s) {
			case "서울" -> out.add("서울특별시");
			case "부산" -> out.add("부산광역시");
			case "대구" -> out.add("대구광역시");
			case "인천" -> out.add("인천광역시");
			case "광주" -> out.add("광주광역시");
			case "대전" -> out.add("대전광역시");
			case "울산" -> out.add("울산광역시");
			case "세종" -> out.add("세종특별자치시");
			case "제주" -> {
				out.add("제주특별자치도");
				out.add("제주도");
			}
			default -> {
				// 일반 도 단위: short + "도"
				out.add(s + "도");
			}
			}
		}
		// CITY / DISTRICT는 long 확장은 지역마다 케이스가 많아(예: ~시, ~군)
		// 무리하게 붙이면 오탐 가능성이 있어서 strip 정도만으로 두는 것이 안전합니다.
		return out;
	}
}
