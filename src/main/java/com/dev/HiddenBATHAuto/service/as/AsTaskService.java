package com.dev.HiddenBATHAuto.service.as;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.as.AsTaskCardDto;
import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRegion;
import com.dev.HiddenBATHAuto.model.auth.Province;
import com.dev.HiddenBATHAuto.model.task.AsImage;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.AsTaskSchedule;
import com.dev.HiddenBATHAuto.repository.as.AsImageRepository;
import com.dev.HiddenBATHAuto.repository.as.AsTaskRepository;
import com.dev.HiddenBATHAuto.repository.as.AsTaskScheduleRepository;
import com.dev.HiddenBATHAuto.repository.auth.CityRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRegionRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AsTaskService {

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
    
    @Value("${spring.upload.path}")
    private String uploadPath;

    private static final String AS_TEAM_NAME = "AS팀";

    public Page<AsTask> getFilteredAsListPage(
            Long handlerId,
            AsStatus status,
            String dateType,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    ) {
        if ("processed".equals(dateType)) {
            return asTaskRepository.findByProcessedDateRangePage(handlerId, status, start, end, pageable);
        }
        return asTaskRepository.findByRequestedDateRangePage(handlerId, status, start, end, pageable);
    }

    public List<AsTask> getFilteredAsListAll(
            Long handlerId,
            AsStatus status,
            String dateType,
            LocalDateTime start,
            LocalDateTime end
    ) {
        if ("processed".equals(dateType)) {
            return asTaskRepository.findByProcessedDateRangeList(handlerId, status, start, end);
        }
        return asTaskRepository.findByRequestedDateRangeList(handlerId, status, start, end);
    }
    
    @Transactional(readOnly = true)
    public Page<AsTask> getAsTasks(
            Member handler,
            String dateType,
            LocalDateTime start,
            LocalDateTime end,
            AsStatus status,
            String companyKeyword,
            Long provinceId,
            Long cityId,
            Long districtId,
            Pageable pageable
    ) {
        // ===== 행정구역 name 변환 =====
        String provinceName = regionLookupService.getProvinceName(provinceId);
        String cityName = regionLookupService.getCityName(cityId);
        String districtName = regionLookupService.getDistrictName(districtId);

        List<String> provinceNames = regionLookupService.getProvinceAliases(provinceName);

        // ⚠️ [중요] IN () 방지: 빈 리스트면 null 처리
        if (provinceNames != null && provinceNames.isEmpty()) {
            provinceNames = null;
        }

        // ===== 업무등록일(달력 등록일) 기준 =====
        if ("scheduled".equalsIgnoreCase(dateType)) {

            LocalDate startDate = (start != null) ? start.toLocalDate() : null;
            LocalDate endDate = (end != null) ? end.toLocalDate() : null;
            // end는 컨트롤러에서 이미 +1day 되어 들어오므로 그대로 사용

            return asTaskRepository.findByScheduledDateFlexible(
                    handler.getId(),
                    status,
                    startDate,
                    endDate,
                    companyKeyword,
                    provinceNames,
                    cityName,
                    districtName,
                    pageable
            );
        }

        // ===== 신청일 기준 =====
        if ("requested".equalsIgnoreCase(dateType)) {
            return asTaskRepository.findByRequestedDateFlexible(
                    handler.getId(),
                    status,
                    start,
                    end,
                    companyKeyword,
                    provinceNames,
                    cityName,
                    districtName,
                    pageable
            );
        }

        // ===== 처리일 기준 =====
        return asTaskRepository.findByProcessedDateFlexible(
                handler.getId(),
                status,
                start,
                end,
                companyKeyword,
                provinceNames,
                cityName,
                districtName,
                pageable
        );
    }


    public List<AsTask> getFilteredAsList(Long memberId, AsStatus status, String dateType,
                                          LocalDateTime start, LocalDateTime end) {
        if ("processed".equals(dateType)) {
            return asTaskRepository.findByProcessedDateRangeList(memberId, status, start, end);
        } else {
            return asTaskRepository.findByRequestedDateRangeList(memberId, status, start, end);
        }
    }

    public Page<AsTask> getAsTasks(Member handler, String dateType, LocalDate date, AsStatus status, Pageable pageable) {
        LocalDateTime start = (date != null ? date : LocalDate.now()).atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        if ("requested".equalsIgnoreCase(dateType)) {
            return asTaskRepository.findByRequestedDate(handler.getId(), status, start, end, pageable);
        } else {
            return asTaskRepository.findByProcessedDate(handler.getId(), status, start, end, pageable);
        }
    }

    public Page<AsTask> getFilteredAsList(Long memberId, AsStatus statuses, String dateType,
                                          LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
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

    public AsTask getAsDetail(Long id) {
        return asTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 AS 요청을 찾을 수 없습니다. ID: " + id));
    }

    // ==============================
    // 기존 기능 유지 + 이미지 저장
    // ==============================
    public AsTask submitAsTask(AsTask task, List<MultipartFile> images, Member member) throws IOException {
        task.setRequestedBy(member);
        task.setRequestedAt(LocalDateTime.now());
        task.setStatus(AsStatus.REQUESTED);

        // 주소 파싱(기존)
        refineAddressFromRoad(task);

        // 담당자 자동 배정(포함 매칭/정규화 추가)
        assignAsHandlerIfPossible(task);

        // DB 저장
        AsTask savedTask = asTaskRepository.save(task);

        // 업로드 디렉토리 구성: /{uploadPath}/as/{memberId}/{yyyy-MM-dd}/request
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path saveDir = Paths.get(uploadPath, "as", String.valueOf(member.getId()), dateStr, "request");
        Files.createDirectories(saveDir); // 디렉토리 없으면 생성

        for (MultipartFile file : images) {
            if (file == null || file.isEmpty()) continue;

            String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("image");
            String filename = UUID.randomUUID() + "_" + originalName;
            Path filePath = saveDir.resolve(filename);

            file.transferTo(filePath.toFile());

            // URL은 /upload/as/...
            String url = "/upload/as/" + member.getId() + "/" + dateStr + "/request/" + filename;

            AsImage image = new AsImage();
            image.setAsTask(savedTask);
            image.setFilename(filename);
            image.setPath(filePath.toString());
            image.setUrl(url);
            image.setType("REQUEST");

            asImageRepository.save(image);
        }

        return savedTask;
    }

    // ==============================
    // 주소 파싱 (기존 유지)
    // ==============================
    private void refineAddressFromRoad(AsTask task) {
        String full = task.getRoadAddress();
        if (full == null || full.isBlank()) return;

        String[] tokens = full.trim().split("\\s+");
        String doName = "", siName = "", guName = "";

        if (tokens.length >= 1) doName = tokens[0];

        for (int i = 1; i < tokens.length; i++) {
            String word = tokens[i];
            if (word.endsWith("시") && siName.isBlank()) siName = word;
            else if (word.endsWith("구") && guName.isBlank()) guName = word;
            if (!siName.isBlank() && !guName.isBlank()) break;
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
            System.out.println("✅ 해석된 RegionKey: provinceId=" + key.provinceId
                    + ", cityId=" + key.cityId + ", districtId=" + key.districtId);

            // 2) 포함 매칭 후보 조회 (팀명=AS팀)
            //    👉 주의: 아래 메서드는 앞서 제공한 JPQL(@Query) 메서드명과 시그니처가 일치해야 합니다.
            //       기존에 findDeliveryRegionMatches(...) 로 구현해 두셨다면 동일 시그니처/동일 JPQL로 사용 가능합니다.
            List<MemberRegion> matches = memberRegionRepository.findDeliveryRegionMatches(
                    AS_TEAM_NAME, key.provinceId, key.cityId, key.districtId
            );

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
            List<Member> topCandidates = bestScopePerMember.entrySet().stream()
                    .filter(e -> e.getValue() == topScope)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            System.out.println("🏅 최고 우선순위: " + topScope + ", 후보: " + topCandidates.size());
            if (topCandidates.isEmpty()) {
                System.out.println("❌ 동순위 후보 없음");
                return;
            }

            // 4) 동순위 다수 → 무작위 (원하시면 라운드로빈/최소작업 우선 등으로 교체 가능)
            Member selected = topCandidates.get((int) (Math.random() * topCandidates.size()));
            task.setAssignedHandler(selected);
            task.setAssignedTeam(selected.getTeam());

            System.out.println("✅ AS 담당자 배정 완료 → " + selected.getUsername()
                    + " (scope=" + topScope + ")");

        } catch (Exception e) {
            System.out.println("❌ AS 배정 예외: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** 구(3) > 시(2) > 도(1) */
    private int scopeScore(MemberRegion mr) {
        if (mr.getDistrict() != null) return 3;
        if (mr.getCity() != null) return 2;
        return 1;
    }

    // ==============================
    //        해석/정규화 헬퍼
    // ==============================
    /** 접미사 제거로 베이스명 산출 (특별자치도/광역시/특별시/자치시/자치구/자치군/도/시/군/구 1회 제거) */
    private String normalizeBase(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        String[] suffixes = {"특별자치도", "광역시", "특별시", "자치시", "자치구", "자치군", "도", "시", "군", "구"};
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
        if (province == null) province = pickByRelaxed(provinces, Province::getName, pBase);
        Long provinceId = (province != null ? province.getId() : null);
        if (provinceId == null) return new RegionKey(null, null, null);

        // City (optional)
        Long cityId = null;
        City city = null;
        if (cBase != null && !cBase.isBlank()) {
            List<City> cities = cityRepository.findByProvinceId(provinceId);
            city = pickByBase(cities, City::getName, cBase);
            if (city == null) city = pickByRelaxed(cities, City::getName, cBase);
            cityId = (city != null ? city.getId() : null);
        }

        // District (optional)
        Long districtId = null;
        if (dBase != null && !dBase.isBlank()) {
            List<District> districts = (cityId != null)
                    ? districtRepository.findByCityId(cityId)
                    : districtRepository.findByProvinceId(provinceId); // 서울/세종 등
            District dist = pickByBase(districts, District::getName, dBase);
            if (dist == null) dist = pickByRelaxed(districts, District::getName, dBase);
            districtId = (dist != null ? dist.getId() : null);
        }

        return new RegionKey(provinceId, cityId, districtId);
    }

    /** 베이스명 비교: normalize 후 상호 포함 */
    private <T> T pickByBase(List<T> list, java.util.function.Function<T, String> nameFn, String base) {
        if (base == null || base.isBlank()) return null;
        String b = normalizeBase(base);
        for (T t : list) {
            String n = nameFn.apply(t);
            String nb = normalizeBase(n);
            if (nb != null && (nb.contains(b) || b.contains(nb))) return t;
        }
        return null;
    }

    /** 완화 비교: 공백 제거 후 상호 포함 */
    private <T> T pickByRelaxed(List<T> list, java.util.function.Function<T, String> nameFn, String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String k = keyword.replaceAll("\\s+", "");
        for (T t : list) {
            String n = nameFn.apply(t);
            if (n == null) continue;
            String nn = n.replaceAll("\\s+", "");
            if (nn.contains(k) || k.contains(nn)) return t;
        }
        return null;
    }

    /** provinceId / cityId / districtId 묶음 */
    private record RegionKey(Long provinceId, Long cityId, Long districtId) { }
	
	@Transactional
	public void updateAsTaskByHandler(Long id, AsStatus updatedStatus, List<MultipartFile> resultImages) throws IOException {
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
	            if (file.isEmpty()) continue;

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
    public Page<AsTaskCardDto> getAsTasksForCalendar(
            Member member,
            String dateType,
            LocalDateTime start,
            LocalDateTime end,
            AsStatus status,
            String companyKeyword,
            Long provinceId,
            Long cityId,
            Long districtId,
            Pageable pageable
    ) {
        // =========================
        // 1) 지역 ID -> name 해석
        // =========================
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

        // =========================
        // 1-1) "경기도=경기, 강원=강원도, 서울=서울특별시 ..." 별칭 목록 생성
        // =========================
        List<String> provinceNames = buildRegionAliases(provinceName, RegionLevel.PROVINCE);
        List<String> cityNames = buildRegionAliases(cityName, RegionLevel.CITY);
        List<String> districtNames = buildRegionAliases(districtName, RegionLevel.DISTRICT);

        // 비어있으면 null로 (JPQL IN 파라미터 안전 처리)
        provinceNames = (provinceNames == null || provinceNames.isEmpty()) ? null : provinceNames;
        cityNames = (cityNames == null || cityNames.isEmpty()) ? null : cityNames;
        districtNames = (districtNames == null || districtNames.isEmpty()) ? null : districtNames;

        // =========================
        // 2) 조회 (scheduled vs base)
        // =========================
        Page<AsTask> page;

        if ("scheduled".equals(dateType)) {
            LocalDate s = (start != null) ? start.toLocalDate() : null;
            LocalDate e = (end != null) ? end.toLocalDate() : null;

            // 기존 쿼리가 endDate < :endDate (exclusive) 이므로,
            // "사용자 입력 end를 포함"하려면 e.plusDays(1)로 넘기는게 일반적입니다.
            // 지금 로직을 유지하시려면 아래 줄을 주석 처리하세요.
            if (e != null) e = e.plusDays(1);

            page = asTaskRepository.searchByScheduledDateWithRegion(
                    status,
                    companyKeyword,
                    provinceNames,
                    cityNames,
                    districtNames,
                    s,
                    e,
                    pageable
            );
        } else {
            // requested/processed는 AsTask 기준으로 조회 후, 날짜는 서비스단에서 필터(기존 로직 유지)
            page = asTaskRepository.searchBaseWithRegion(
                    status,
                    companyKeyword,
                    provinceNames,
                    cityNames,
                    districtNames,
                    pageable
            );

            if (start != null || end != null) {
                List<AsTask> filtered = page.getContent().stream().filter(t -> {
                    LocalDateTime base =
                            "processed".equals(dateType) ? t.getAsProcessDate() : t.getRequestedAt();
                    if (base == null) return false;
                    if (start != null && base.isBefore(start)) return false;
                    if (end != null && !base.isBefore(end)) return false; // end exclusive 유지
                    return true;
                }).toList();

                // ⚠️ requested/processed에서 service 필터를 쓰면 "정확한 totalElements"는 깨질 수 있습니다.
                page = new PageImpl<>(filtered, pageable, filtered.size());
            }
        }

        // =========================
        // 3) schedule 정보 합치기
        // =========================
        List<Long> taskIds = page.getContent().stream().map(AsTask::getId).toList();
        Map<Long, LocalDate> scheduledMap = scheduleRepository.findByTaskIds(taskIds).stream()
                .collect(Collectors.toMap(
                        s -> s.getAsTask().getId(),
                        AsTaskSchedule::getScheduledDate,
                        (a, b) -> a // 혹시 중복이 있으면 첫 값 유지
                ));

        // =========================
        // 4) DTO 변환
        // =========================
        List<AsTaskCardDto> dtoList = page.getContent().stream().map(t -> {
            String companyName = (t.getRequestedBy() != null && t.getRequestedBy().getCompany() != null)
                    ? t.getRequestedBy().getCompany().getCompanyName()
                    : "(업체없음)";

            String address = String.join(" ",
                    Optional.ofNullable(t.getDoName()).orElse(""),
                    Optional.ofNullable(t.getSiName()).orElse(""),
                    Optional.ofNullable(t.getGuName()).orElse(""),
                    Optional.ofNullable(t.getRoadAddress()).orElse(""),
                    Optional.ofNullable(t.getDetailAddress()).orElse("")
            ).trim();

            return AsTaskCardDto.builder()
                    .taskId(t.getId())
                    .companyName(companyName)
                    .requestedAt(t.getRequestedAt())
                    .asProcessDate(t.getAsProcessDate())
                    .address(address)
                    .status(t.getStatus().name())
                    .scheduledDate(scheduledMap.get(t.getId()))
                    .build();
        }).toList();

        return new PageImpl<>(dtoList, pageable, page.getTotalElements());
    }

    // =========================
    // 별칭 생성 로직
    // =========================
    private enum RegionLevel { PROVINCE, CITY, DISTRICT }

    /**
     * 예:
     * - "경기도" -> ["경기도", "경기"]
     * - "강원" -> ["강원", "강원도"]
     * - "서울특별시" -> ["서울특별시", "서울"]
     * - "부산광역시" -> ["부산광역시", "부산"]
     */
    private List<String> buildRegionAliases(String input, RegionLevel level) {
        if (input == null) return Collections.emptyList();
        String name = input.trim();
        if (name.isEmpty()) return Collections.emptyList();

        // 원본 포함
        Set<String> set = new LinkedHashSet<>();
        set.add(name);

        // 1) suffix 제거한 "짧은 형태" 생성
        String shortName = stripSuffix(name, level);
        if (!shortName.isBlank()) set.add(shortName);

        // 2) 반대로 "긴 형태"도 보강 (short -> long 후보)
        //    (예: "강원"이면 "강원도"도 추가, "서울"이면 "서울특별시"도 추가 등)
        set.addAll(expandToCommonLongForms(shortName, level));

        // 빈 문자열 제거
        return set.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private String stripSuffix(String name, RegionLevel level) {
        String n = name.trim();

        // 가장 긴 suffix부터 제거 (정확도)
        if (level == RegionLevel.PROVINCE) {
            // 시/도 단위
            String[] suffixes = {
                    "특별자치도", "특별자치시", "특별시", "광역시", "자치도", "도"
            };
            for (String suf : suffixes) {
                if (n.endsWith(suf)) return n.substring(0, n.length() - suf.length()).trim();
            }
            return n;

        } else if (level == RegionLevel.CITY) {
            // 시/군 단위 (필요 시 확장)
            String[] suffixes = {"특별시", "광역시", "특별자치시", "시", "군"};
            for (String suf : suffixes) {
                if (n.endsWith(suf)) return n.substring(0, n.length() - suf.length()).trim();
            }
            return n;

        } else {
            // 구/군 단위
            String[] suffixes = {"구", "군", "시"};
            for (String suf : suffixes) {
                if (n.endsWith(suf)) return n.substring(0, n.length() - suf.length()).trim();
            }
            return n;
        }
    }

    private Set<String> expandToCommonLongForms(String shortName, RegionLevel level) {
        if (shortName == null) return Collections.emptySet();
        String s = shortName.trim();
        if (s.isEmpty()) return Collections.emptySet();

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
