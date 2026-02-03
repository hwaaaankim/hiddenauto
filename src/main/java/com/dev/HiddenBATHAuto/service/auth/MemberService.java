package com.dev.HiddenBATHAuto.service.auth;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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

@Configuration
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
	private final MemberManagementService memberManagementService;
	private final CompanyDeliveryAddressRepository companyDeliveryAddressRepository;
	
	@Value("${spring.upload.path}")
	private String uploadPath;

	 public Page<Member> searchEmployees(String name, Long teamId, Pageable pageable) {
        // 직원만(관리직/현장직)
        List<MemberRole> roles = List.of(MemberRole.INTERNAL_EMPLOYEE, MemberRole.MANAGEMENT);
        return memberRepository.searchEmployees(name, teamId, roles, pageable);
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

	public void registerCustomerRepresentative(
            Company company,
            Member member,
            String role,
            MultipartFile file,
            String deliveryAddressesJson
    ) {
        // 1) Role
        MemberRole memberRole = MemberRole.valueOf(role);
        member.setRole(memberRole);

        // 2) registrationKey 생성
        String registrationKey = UUID.randomUUID().toString().substring(0, 8);
        company.setRegistrationKey(registrationKey);
        company.setPoint(0);

        // ✅ 2-1) 사업자등록번호 검증/중복
        String bizNo = (company.getBusinessNumber() == null) ? "" : company.getBusinessNumber().replaceAll("\\D", "");
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

        // 3) 주소 필드 가공
        refineAddressFields(company);

        // 4) 주소 유효성 검사
        if (company.getDoName() == null || company.getDoName().isBlank()) {
            throw new IllegalArgumentException("도 이름(doName)이 누락되었습니다.");
        }
        if (company.getRoadAddress() == null || company.getRoadAddress().isBlank()) {
            throw new IllegalArgumentException("주소 정보가 누락되었습니다.");
        }

        // 5) Company 저장
        Company savedCompany = companyRepository.save(company);

        // 6) 사업자등록증 파일 저장
        if (file != null && !file.isEmpty()) {
            try {
                String originalFilename = file.getOriginalFilename();
                String username = member.getUsername();

                String relativePath = username + "/signUp/licence";
                String saveDir = Paths.get(uploadPath, relativePath).toString();

                File dir = new File(saveDir);
                if (!dir.exists()) dir.mkdirs();

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

        // 7) Member 저장
        String encodedPassword = passwordEncoder.encode(member.getPassword());
        member.setPassword(encodedPassword);
        member.setCompany(savedCompany);
        member.setEnabled(true);

        memberRepository.save(member);

        // ✅ 8) 추가 배송지 저장(0개여도 가능)
        saveDeliveryAddressesIfAny(savedCompany, deliveryAddressesJson);
    }

    public void registerCustomerEmployee(Member member, String registrationKey, String deliveryAddressesJson) {
        if (registrationKey == null || registrationKey.isBlank()) {
            throw new IllegalArgumentException("업체코드를 입력해야 합니다.");
        }

        Company company = companyRepository.findByRegistrationKey(registrationKey)
                .orElseThrow(() -> new IllegalArgumentException("입력한 업체코드에 해당하는 회사가 존재하지 않습니다."));

        String encodedPassword = passwordEncoder.encode(member.getPassword());
        member.setPassword(encodedPassword);
        member.setCompany(company);
        member.setRole(MemberRole.CUSTOMER_EMPLOYEE);
        member.setEnabled(true);

        memberRepository.save(member);

        // ✅ 직원도 추가 배송지 저장 가능(0개 가능)
        saveDeliveryAddressesIfAny(company, deliveryAddressesJson);
    }

    private void saveDeliveryAddressesIfAny(Company company, String deliveryAddressesJson) {
        if (deliveryAddressesJson == null || deliveryAddressesJson.isBlank()) return;

        try {
            List<CompanyDeliveryAddressRequest> list = objectMapper.readValue(
                    deliveryAddressesJson,
                    new TypeReference<List<CompanyDeliveryAddressRequest>>() {}
            );

            if (list == null || list.isEmpty()) return;

            // 간단 중복 제거(road+detail 기준)
            Set<String> seen = new HashSet<>();
            for (CompanyDeliveryAddressRequest req : list) {
                if (req == null) continue;

                String road = (req.getRoadAddress() == null) ? "" : req.getRoadAddress().trim();
                String detail = (req.getDetailAddress() == null) ? "" : req.getDetailAddress().trim();
                if (road.isBlank()) continue;

                String key = road + "||" + detail;
                if (seen.contains(key)) continue;
                seen.add(key);

                CompanyDeliveryAddress addr = new CompanyDeliveryAddress();
                addr.setCompany(company);
                addr.setZipCode(safe(req.getZipCode()));
                addr.setDoName(safe(req.getDoName()));
                addr.setSiName(safe(req.getSiName()));
                addr.setGuName(safe(req.getGuName()));
                addr.setRoadAddress(road);
                addr.setDetailAddress(detail);
                addr.setCreatedAt(LocalDateTime.now());

                companyDeliveryAddressRepository.save(addr);
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("추가 배송지 데이터 처리에 실패했습니다. (형식 오류)");
        }
    }

    private String safe(String v) {
        return (v == null) ? "" : v.trim();
    }

	/**
	 * 주소에서 siName/guName을 분리하거나 보정함
	 */
	private void refineAddressFields(Company company) {
		String si = company.getSiName();
		if (si != null && !si.isBlank()) {
			String[] parts = si.trim().split(" ");
			if (parts.length == 2) {
				// 예: "용인시 수지구"
				company.setSiName(parts[0]);
				company.setGuName(parts[1]);
			} else if (parts.length == 1) {
				// 예: "관악구"
				company.setGuName(parts[0]);
				company.setSiName(""); // 시 없음
			} else if (parts.length > 2) {
				// 예외 처리: 가장 앞은 시, 그다음은 구로
				company.setSiName(parts[0]);
				company.setGuName(parts[1]);
			}
		}
	}

	public void saveMember(MemberSaveDTO dto) {

		Team team = teamRepository.findById(dto.getTeamId())
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 팀"));

		// 생산팀이면 사용자가 선택한 카테고리
		TeamCategory category;
		if ("생산팀".equals(team.getName())) {
			category = teamCategoryRepository.findById(dto.getTeamCategoryId())
					.orElseThrow(() -> new IllegalArgumentException("카테고리 없음"));
		} else {
			// 그 외 팀이면 고정된 값 사용
			Long forcedCategoryId = switch (team.getName()) {
			case "관리팀" -> 1L;
			case "배송팀" -> 8L;
			case "AS팀" -> 10L;
			default -> throw new IllegalArgumentException("지원하지 않는 팀");
			};
			category = teamCategoryRepository.findById(forcedCategoryId)
					.orElseThrow(() -> new IllegalArgumentException("고정된 카테고리 없음"));
		}

		// 기본 Member 생성
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

		// 지역 등록 처리
		List<MemberRegion> addressScopes = new ArrayList<>();
		if (dto.getRegionJson() != null && !dto.getRegionJson().isBlank()) {
			try {
				List<MemberRegionDto> regions = objectMapper.readValue(dto.getRegionJson(),
						new TypeReference<List<MemberRegionDto>>() {});

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

		// === 서버측 2차 방어: 팀 기준 충돌 검사 (배송/AS만)
		if ("배송팀".equals(team.getName()) || "AS팀".equals(team.getName())) {
			// RegionSelectionDTO 목록으로 변환
			List<RegionSelectionDTO> selections = addressScopes.stream().map(mr -> {
				RegionSelectionDTO s = new RegionSelectionDTO();
				s.setProvinceId(mr.getProvince() != null ? mr.getProvince().getId() : null);
				s.setCityId(mr.getCity() != null ? mr.getCity().getId() : null);
				s.setDistrictId(mr.getDistrict() != null ? mr.getDistrict().getId() : null);
				return s;
			}).toList();

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
