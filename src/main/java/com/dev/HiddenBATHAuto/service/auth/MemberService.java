package com.dev.HiddenBATHAuto.service.auth;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.MemberSaveDTO;
import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRegion;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.Province;
import com.dev.HiddenBATHAuto.model.auth.Team;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.repository.auth.CityRepository;
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

	@Value("${spring.upload.path}")
	private String uploadPath;

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

	public void registerCustomerRepresentative(Company company, Member member, String role, MultipartFile file) {
		// 1. MemberRole 설정
		MemberRole memberRole = MemberRole.valueOf(role);
		member.setRole(memberRole);

		// 2. registrationKey 생성
		String registrationKey = UUID.randomUUID().toString().substring(0, 8);
		company.setRegistrationKey(registrationKey);
		company.setPoint(0);

		// 3. 주소 필드 가공
		refineAddressFields(company);

		// 4. 주소 유효성 검사
		if (company.getDoName() == null || company.getDoName().isBlank()) {
			throw new IllegalArgumentException("도 이름(doName)이 누락되었습니다.");
		}
		if (company.getRoadAddress() == null || company.getRoadAddress().isBlank()) {
			throw new IllegalArgumentException("주소 정보가 누락되었습니다.");
		}

		// 5. Company 저장 (ID 확보용)
		Company savedCompany = companyRepository.save(company);

		// 6. 사업자등록증 파일 저장
		if (file != null && !file.isEmpty()) {
			try {
				String originalFilename = file.getOriginalFilename();
				String username = member.getUsername();

				// 저장 디렉토리 구성
				String relativePath = username + "/signUp/licence";
				String saveDir = Paths.get(uploadPath, relativePath).toString();

				File dir = new File(saveDir);
				if (!dir.exists()) {
					dir.mkdirs();
				}

				// 저장 파일 경로
				Path filePath = Paths.get(saveDir, originalFilename);
				file.transferTo(filePath.toFile());

				// DB에 저장할 경로 및 URL
				savedCompany.setBusinessLicenseFilename(originalFilename);
				savedCompany.setBusinessLicensePath(filePath.toString()); // 실제 물리 경로
				savedCompany.setBusinessLicenseUrl("/upload/" + relativePath + "/" + originalFilename); // 브라우저 접근 URL

				companyRepository.save(savedCompany);

			} catch (Exception e) {
				throw new RuntimeException("파일 업로드 실패: " + e.getMessage(), e);
			}
		}

		// 7. Member 설정 및 저장
		String encodedPassword = passwordEncoder.encode(member.getPassword());
		member.setPassword(encodedPassword);
		member.setCompany(savedCompany);
		member.setEnabled(true);

		memberRepository.save(member);
	}

	public void registerCustomerEmployee(Member member, String registrationKey) {
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
		if (dto.getRegionJson() != null && !dto.getRegionJson().isBlank()) {
			try {
				System.out.println("▶ Region JSON 수신값:");
				System.out.println(dto.getRegionJson());

				List<MemberRegionDto> regions = objectMapper.readValue(dto.getRegionJson(),
						new TypeReference<List<MemberRegionDto>>() {
						});

				System.out.println("▶ 파싱된 region 개수: " + regions.size());

				List<MemberRegion> addressScopes = new ArrayList<>();
				for (MemberRegionDto r : regions) {
					System.out.printf("⮕ provinceId: %s, cityId: %s, districtId: %s%n", r.getProvinceId(),
							r.getCityId(), r.getDistrictId());

					Province province = provinceRepository.findById(Long.parseLong(r.getProvinceId()))
							.orElseThrow(() -> new IllegalArgumentException("도 없음"));

					City city = (r.getCityId() != null && !r.getCityId().isBlank())
							? cityRepository.findById(Long.parseLong(r.getCityId())).orElse(null)
							: null;

					District district = (r.getDistrictId() != null && !r.getDistrictId().isBlank())
							? districtRepository.findById(Long.parseLong(r.getDistrictId())).orElse(null)
							: null;

					MemberRegion mr = MemberRegion.builder().province(province).city(city).district(district)
							.member(member).build();

					addressScopes.add(mr);

					System.out.printf("✅ 생성된 MemberRegion: province=%s, city=%s, district=%s%n", province.getName(),
							city != null ? city.getName() : "null", district != null ? district.getName() : "null");
				}

				member.setAddressScopes(addressScopes);
				System.out.println("📦 최종 member.addressScopes 수: " + member.getAddressScopes().size());

			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException("지역 JSON 파싱 오류", e);
			}
		}

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
