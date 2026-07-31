package com.dev.HiddenBATHAuto.service.auth;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.CompanyDeliveryAddressRequest;
import com.dev.HiddenBATHAuto.dto.MemberSaveDTO;
import com.dev.HiddenBATHAuto.dto.employeeDetail.ConflictDTO;
import com.dev.HiddenBATHAuto.dto.employeeDetail.RegionSelectionDTO;
import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.CompanyDeliveryAddress;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRegion;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.Province;
import com.dev.HiddenBATHAuto.model.auth.Team;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.repository.auth.CityRepository;
import com.dev.HiddenBATHAuto.repository.auth.CompanyDeliveryAddressRepository;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Service
@RequiredArgsConstructor
public class MemberService {

	private final MemberRepository memberRepository;
	private final CompanyRepository companyRepository;
	private final PasswordEncoder passwordEncoder;

	private final TeamRepository teamRepository;
	private final TeamCategoryRepository teamCategoryRepository;
	private final ProvinceRepository provinceRepository;
	private final CityRepository cityRepository;
	private final DistrictRepository districtRepository;
	private final ObjectMapper objectMapper;
	private final AddressRegionResolver addressRegionResolver;
	private final MemberManagementService memberManagementService;
	private final CompanyDeliveryAddressRepository companyDeliveryAddressRepository;
	
	@Value("${spring.upload.path}")
	private String uploadPath;

	 /**
     * ✅ "1,2,3" 문자열을 Long 리스트로 파싱 (순서 유지)
     * - 공백/빈값 제거
     * - 숫자 아닌 값은 제외
     * - 중복은 최초 등장만 유지(순서 유지)
     */
    public List<Long> parseIdListKeepOrder(String ids) {
        if (ids == null || ids.trim().isEmpty()) return Collections.emptyList();

        String[] parts = ids.split(",");
        LinkedHashSet<Long> set = new LinkedHashSet<>();

        for (String p : parts) {
            if (p == null) continue;
            String s = p.trim();
            if (s.isEmpty()) continue;
            try {
                set.add(Long.parseLong(s));
            } catch (NumberFormatException ignore) {
                // 숫자 아닌 값은 무시
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * ✅ 체크된 직원만 조회 + 요청 ids 순서대로 정렬하여 반환
     * - EntityGraph로 연관관계 로딩 (N+1 방지)
     */
    @Transactional(readOnly = true)
    public List<Member> findEmployeesForExcelByIdsOrdered(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) return Collections.emptyList();

        List<MemberRole> roles = List.of(MemberRole.INTERNAL_EMPLOYEE, MemberRole.MANAGEMENT);

        List<Member> fetched = memberRepository.searchEmployeesForExcelByIds(orderedIds, roles);

        // DB 반환 순서는 IN 절 특성상 보장되지 않으므로, 요청 순서대로 재정렬
        Map<Long, Member> map = fetched.stream()
                .collect(Collectors.toMap(Member::getId, m -> m, (a, b) -> a));

        List<Member> ordered = new ArrayList<>();
        for (Long id : orderedIds) {
            Member m = map.get(id);
            if (m != null) ordered.add(m);
        }
        return ordered;
    }

	
	public Page<Member> searchEmployees(String name, Long teamId, Pageable pageable) {
        // 직원만(관리직/현장직)
        List<MemberRole> roles = List.of(MemberRole.INTERNAL_EMPLOYEE, MemberRole.MANAGEMENT);
        return memberRepository.searchEmployees(name, teamId, roles, pageable);
    }

    /**
     * ✅ 엑셀용: 페이징 없이 전체 리스트 + 팀/카테고리/지역까지 조회(성능 위해 EntityGraph)
     */
    @Transactional(readOnly = true)
    public List<Member> findEmployeesForExcel(String name, Long teamId, Sort sort) {
        List<MemberRole> roles = List.of(MemberRole.INTERNAL_EMPLOYEE, MemberRole.MANAGEMENT);
        return memberRepository.searchEmployeesForExcel(name, teamId, roles, sort);
    }

    /**
     * ✅ 담당구역 텍스트 생성 (줄바꿈 자연스럽게)
     */
    @Transactional(readOnly = true)
    public String buildRegionText(Member m) {
        if (m.getAddressScopes() == null || m.getAddressScopes().isEmpty()) return "";

        return m.getAddressScopes().stream()
                .map(r -> {
                    String p = (r.getProvince() != null ? r.getProvince().getName() : "");
                    String c = (r.getCity() != null ? r.getCity().getName() : "");
                    String d = (r.getDistrict() != null ? r.getDistrict().getName() : "");

                    // 공백 정리
                    String combined = (p + " " + c + " " + d).trim().replaceAll("\\s{2,}", " ");
                    return combined;
                })
                .filter(s -> s != null && !s.isBlank())
                // 엑셀 셀 줄바꿈
                .collect(Collectors.joining("\n"));
    }
	
	public Page<Member> searchEmployees(String name, String team, Pageable pageable) {
		List<MemberRole> roles = List.of(MemberRole.INTERNAL_EMPLOYEE, MemberRole.MANAGEMENT);
		return memberRepository.searchByRolesAndNameAndTeam(roles, name == null || name.isBlank() ? null : name,
				team == null || team.isBlank() ? null : team, pageable);
	}

	public Optional<Member> findById(Long id) {
		return memberRepository.findById(id);
	}

	public Member insertMember(Member member) {
		String encodedPassword = passwordEncoder.encode(member.getPassword());
		member.setPassword(encodedPassword);
		member.setRole(member.getRole());
		return memberRepository.save(member);

	}

	@Transactional(rollbackFor = Exception.class)
	public void registerCustomerRepresentative(
            Company company,
            Member member,
            String role,
            MultipartFile file,
            String deliveryAddressesJson
    ) {
        MemberRole memberRole = MemberRole.valueOf(role);
        member.setRole(memberRole);

        String registrationKey = UUID.randomUUID().toString().substring(0, 8);
        company.setRegistrationKey(registrationKey);
        company.setPoint(0);

        String bizNo = (company.getBusinessNumber() == null)
                ? ""
                : company.getBusinessNumber().replaceAll("\\D", "");

        if (bizNo.isBlank()) {
            throw new IllegalArgumentException("사업자등록번호를 입력해야 합니다.");
        }
        if (bizNo.length() != 10) {
            throw new IllegalArgumentException("사업자등록번호는 숫자 10자리로 입력해야 합니다.");
        }
        if (companyRepository.existsByBusinessNumber(bizNo)) {
            throw new IllegalArgumentException("이미 등록된 사업자등록번호입니다.");
        }
        company.setBusinessNumber(bizNo);

        if (company.getRoadAddress() == null || company.getRoadAddress().isBlank()) {
            throw new IllegalArgumentException("주소 정보가 누락되었습니다.");
        }

        // JSON 오류 또는 행정구역 DB 불일치는 회사/회원 저장 전에 먼저 검증합니다.
        List<NormalizedDeliveryAddress> normalizedDeliveryAddresses =
                parseAndNormalizeDeliveryAddresses(deliveryAddressesJson);

        normalizeCompanyAddress(company);

        Company savedCompany = companyRepository.save(company);

        if (file != null && !file.isEmpty()) {
            try {
                String originalFilename = file.getOriginalFilename();
                String username = member.getUsername();

                String relativePath = username + "/signUp/licence";
                String saveDir = Paths.get(uploadPath, relativePath).toString();

                File dir = new File(saveDir);
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("사업자등록증 저장 폴더를 생성할 수 없습니다.");
                }

                Path filePath = Paths.get(saveDir, originalFilename);
                file.transferTo(filePath.toFile());

                savedCompany.setBusinessLicenseFilename(originalFilename);
                savedCompany.setBusinessLicensePath(filePath.toString());
                savedCompany.setBusinessLicenseUrl("/upload/" + relativePath + "/" + originalFilename);

                companyRepository.save(savedCompany);

            } catch (Exception e) {
                throw new RuntimeException("파일 업로드 실패: " + e.getMessage(), e);
            }
        }

        String encodedPassword = passwordEncoder.encode(member.getPassword());
        member.setPassword(encodedPassword);
        member.setCompany(savedCompany);
        member.setEnabled(true);

        memberRepository.save(member);
        saveDeliveryAddresses(savedCompany, normalizedDeliveryAddresses);
    }

    @Transactional(rollbackFor = Exception.class)
    public void registerCustomerEmployee(Member member, String registrationKey, String deliveryAddressesJson) {
        if (registrationKey == null || registrationKey.isBlank()) {
            throw new IllegalArgumentException("업체코드를 입력해야 합니다.");
        }

        Company company = companyRepository.findByRegistrationKey(registrationKey)
                .orElseThrow(() -> new IllegalArgumentException("입력한 업체코드에 해당하는 회사가 존재하지 않습니다."));

        // 회원 저장 전에 추가 배송지 JSON과 행정구역 매칭을 먼저 검증합니다.
        List<NormalizedDeliveryAddress> normalizedDeliveryAddresses =
                parseAndNormalizeDeliveryAddresses(deliveryAddressesJson);

        String encodedPassword = passwordEncoder.encode(member.getPassword());
        member.setPassword(encodedPassword);
        member.setCompany(company);
        member.setRole(MemberRole.CUSTOMER_EMPLOYEE);
        member.setEnabled(true);

        memberRepository.save(member);
        saveDeliveryAddresses(company, normalizedDeliveryAddresses);
    }

    private void normalizeCompanyAddress(Company company) {
        AddressRegionResolver.ResolvedRegion region = addressRegionResolver.resolve(
                company.getDoName(),
                company.getSiName(),
                company.getGuName(),
                company.getRoadAddress()
        );

        company.setDoName(region.doName());
        company.setSiName(region.siName());
        company.setGuName(region.guName());
    }

    private List<NormalizedDeliveryAddress> parseAndNormalizeDeliveryAddresses(String deliveryAddressesJson) {
        if (deliveryAddressesJson == null || deliveryAddressesJson.isBlank()) {
            return Collections.emptyList();
        }

        final List<CompanyDeliveryAddressRequest> requests;
        try {
            requests = objectMapper.readValue(
                    deliveryAddressesJson,
                    new TypeReference<List<CompanyDeliveryAddressRequest>>() {}
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("추가 배송지 데이터 처리에 실패했습니다. (JSON 형식 오류)", e);
        }

        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> seen = new LinkedHashSet<>();
        List<NormalizedDeliveryAddress> normalized = new ArrayList<>();

        for (CompanyDeliveryAddressRequest request : requests) {
            if (request == null) {
                continue;
            }

            String roadAddress = safe(request.getRoadAddress());
            String detailAddress = safe(request.getDetailAddress());
            if (roadAddress.isBlank()) {
                continue;
            }

            String duplicateKey = deliveryAddressKey(roadAddress, detailAddress);
            if (!seen.add(duplicateKey)) {
                continue;
            }

            AddressRegionResolver.ResolvedRegion region = addressRegionResolver.resolve(
                    request.getDoName(),
                    request.getSiName(),
                    request.getGuName(),
                    roadAddress
            );

            normalized.add(new NormalizedDeliveryAddress(
                    safe(request.getZipCode()),
                    region.doName(),
                    region.siName(),
                    region.guName(),
                    roadAddress,
                    detailAddress
            ));
        }

        return normalized;
    }

    private void saveDeliveryAddresses(Company company, List<NormalizedDeliveryAddress> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return;
        }

        Set<String> existingKeys = company.getDeliveryAddresses() == null
                ? new LinkedHashSet<>()
                : company.getDeliveryAddresses().stream()
                        .map(item -> deliveryAddressKey(item.getRoadAddress(), item.getDetailAddress()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        List<CompanyDeliveryAddress> entities = new ArrayList<>();

        for (NormalizedDeliveryAddress value : addresses) {
            String key = deliveryAddressKey(value.roadAddress(), value.detailAddress());
            if (!existingKeys.add(key)) {
                continue;
            }

            CompanyDeliveryAddress address = new CompanyDeliveryAddress();
            address.setCompany(company);
            address.setZipCode(value.zipCode());
            address.setDoName(value.doName());
            address.setSiName(value.siName());
            address.setGuName(value.guName());
            address.setRoadAddress(value.roadAddress());
            address.setDetailAddress(value.detailAddress());
            address.setCreatedAt(LocalDateTime.now());
            entities.add(address);
        }

        if (!entities.isEmpty()) {
            companyDeliveryAddressRepository.saveAll(entities);
        }
    }

    private String deliveryAddressKey(String roadAddress, String detailAddress) {
        return safe(roadAddress) + "||" + safe(detailAddress);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private record NormalizedDeliveryAddress(
            String zipCode,
            String doName,
            String siName,
            String guName,
            String roadAddress,
            String detailAddress
    ) {
    }

	@Transactional
	public void saveMember(MemberSaveDTO dto) {

	    Team team = teamRepository.findById(dto.getTeamId())
	            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 팀"));

	    TeamCategory category = resolveTeamCategoryForEmployeeSave(team, dto.getTeamCategoryId());

	    Member member = new Member();
	    member.setUsername(dto.getUsername());
	    member.setPassword(passwordEncoder.encode(dto.getPassword()));
	    member.setName(dto.getName());
	    member.setPhone(dto.getPhone());
	    member.setEmail(dto.getEmail());
	    member.setRole(dto.getRole());
	    member.setTeam(team);
	    member.setTeamCategory(category);
	    member.setEnabled(true);
	    member.setCreatedAt(LocalDateTime.now());

	    List<MemberRegion> addressScopes = new ArrayList<>();

	    if (dto.getRegionJson() != null && !dto.getRegionJson().isBlank()) {
	        try {
	            List<MemberRegionDto> regions = objectMapper.readValue(
	                    dto.getRegionJson(),
	                    new TypeReference<List<MemberRegionDto>>() {}
	            );

	            for (MemberRegionDto r : regions) {
	                Province province = provinceRepository.findById(Long.parseLong(r.getProvinceId()))
	                        .orElseThrow(() -> new IllegalArgumentException("도 없음"));

	                City city = (r.getCityId() != null && !r.getCityId().isBlank())
	                        ? cityRepository.findById(Long.parseLong(r.getCityId())).orElse(null)
	                        : null;

	                District district = (r.getDistrictId() != null && !r.getDistrictId().isBlank())
	                        ? districtRepository.findById(Long.parseLong(r.getDistrictId())).orElse(null)
	                        : null;

	                MemberRegion mr = MemberRegion.builder()
	                        .province(province)
	                        .city(city)
	                        .district(district)
	                        .member(member)
	                        .build();

	                addressScopes.add(mr);
	            }

	        } catch (Exception e) {
	            throw new RuntimeException("지역 JSON 파싱 오류", e);
	        }
	    }

	    if ("배송팀".equals(team.getName()) || "AS팀".equals(team.getName())) {
	        List<RegionSelectionDTO> selections = addressScopes.stream()
	                .map(mr -> {
	                    RegionSelectionDTO s = new RegionSelectionDTO();
	                    s.setProvinceId(mr.getProvince() != null ? mr.getProvince().getId() : null);
	                    s.setCityId(mr.getCity() != null ? mr.getCity().getId() : null);
	                    s.setDistrictId(mr.getDistrict() != null ? mr.getDistrict().getId() : null);
	                    return s;
	                })
	                .toList();

	        List<ConflictDTO> conflicts =
	                memberManagementService.checkRegionConflictsForNewMember(team.getId(), selections);

	        if (!conflicts.isEmpty()) {
	            String msg = conflicts.stream()
	                    .map(c -> "[" + c.getConflictMemberName() + "] " + c.getConflictPath())
	                    .collect(Collectors.joining(", "));

	            throw new IllegalStateException("담당구역 충돌: " + msg);
	        }
	    }

	    member.setAddressScopes(addressScopes);
	    memberRepository.save(member);
	}

	private TeamCategory resolveTeamCategoryForEmployeeSave(Team team, Long teamCategoryId) {
	    String teamName = team.getName() != null ? team.getName().trim() : "";

	    Set<String> categoryRequiredTeams = Set.of("생산팀", "출고팀");

	    if (teamCategoryId != null) {
	        TeamCategory selectedCategory = teamCategoryRepository.findById(teamCategoryId)
	                .orElseThrow(() -> new IllegalArgumentException("카테고리 없음"));

	        if (selectedCategory.getTeam() == null ||
	                !Objects.equals(selectedCategory.getTeam().getId(), team.getId())) {
	            throw new IllegalArgumentException("선택한 카테고리가 해당 팀 소속이 아닙니다.");
	        }

	        return selectedCategory;
	    }

	    if (categoryRequiredTeams.contains(teamName)) {
	        throw new IllegalArgumentException(teamName + "은 카테고리 선택이 필수입니다.");
	    }

	    return teamCategoryRepository.findFirstByTeam_IdOrderByIdAsc(team.getId())
	            .orElseThrow(() -> new IllegalArgumentException(teamName + "에 등록된 기본 카테고리가 없습니다."));
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class MemberRegionDto {
		private String provinceId;
		private String cityId;
		private String districtId;
		// 아래는 없어도 되고, 있으면 더 활용 가능
		private String provinceName;
		private String cityName;
		private String districtName;
	}


	public List<Member> getCompanyEmployees(Company company) {
		return memberRepository.findByCompanyAndRole(company, MemberRole.CUSTOMER_EMPLOYEE);
	}

	public Member getMemberById(Long id) {
		return memberRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("해당 직원을 찾을 수 없습니다."));
	}
}
