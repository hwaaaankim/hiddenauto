package com.dev.HiddenBATHAuto.service.auth;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.employee.EmployeeUpdateResult;
import com.dev.HiddenBATHAuto.dto.employeeDetail.ConflictDTO;
import com.dev.HiddenBATHAuto.dto.employeeDetail.EmployeeUpdateRequest;
import com.dev.HiddenBATHAuto.dto.employeeDetail.MemberRegionSimpleDTO;
import com.dev.HiddenBATHAuto.dto.employeeDetail.RegionBulkSaveRequest;
import com.dev.HiddenBATHAuto.dto.employeeDetail.RegionSelectionDTO;
import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRegion;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.Province;
import com.dev.HiddenBATHAuto.model.auth.Team;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.repository.auth.CityRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRegionRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class MemberManagementService {

	private final MemberRepository memberRepository;
	private final TeamRepository teamRepository;
	private final TeamCategoryRepository teamCategoryRepository;
	private final ProvinceRepository provinceRepository;
	private final CityRepository cityRepository;
	private final DistrictRepository districtRepository;
	private final MemberRegionRepository memberRegionRepository;

	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private boolean isRegionTeam(String teamName) {
		return "배송팀".equals(teamName) || "AS팀".equals(teamName);
	}

	private boolean isProductionTeam(String teamName) {
		return "생산팀".equals(teamName);
	}

	private Long forcedCategoryIdByTeamName(String teamName) {
		return switch (teamName) {
		case "관리팀" -> 1L;
		case "배송팀" -> 8L;
		case "AS팀" -> 10L;
		default -> null;
		};
	}

	@Transactional
	public void clearMemberRegions(Long memberId) {
		memberRegionRepository.deleteByMember_Id(memberId);
	}

	// ===== 멤버 업데이트 =====
	@Transactional
	public EmployeeUpdateResult updateEmployee(EmployeeUpdateRequest req) {

		Member m = memberRepository.findById(req.getMemberId())
				.orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다."));

		if (isBlank(req.getName()) || isBlank(req.getPhone()) || isBlank(req.getRole()) || req.getTeamId() == null) {
			throw new IllegalArgumentException("필수 항목이 누락되었습니다.");
		}

		Team newTeam = teamRepository.findById(req.getTeamId())
				.orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다."));

		String oldTeamName = (m.getTeam() != null) ? m.getTeam().getName() : null;
		String newTeamName = newTeam.getName();

		boolean oldIsRegion = oldTeamName != null && isRegionTeam(oldTeamName);
		boolean newIsRegion = isRegionTeam(newTeamName);

		boolean regionsCleared = false;

		// ✅ 팀 변경에 따른 담당구역 정리 규칙
		// 1) 배송/AS -> 다른팀 : 담당구역 삭제
		// 2) 배송 <-> AS : 담당구역 삭제(팀 바뀌면 기존 지역 무효)
		if (oldTeamName != null && !Objects.equals(oldTeamName, newTeamName)) {
			if (oldIsRegion && (!newIsRegion || (newIsRegion && !Objects.equals(oldTeamName, newTeamName)))) {
				// 실제 삭제는 컨트롤러의 DELETE API에서 confirm 후 호출하도록 되어있지만,
				// 서버도 일관성 유지를 위해 최종적으로 한번 더 정리합니다(이미 비었으면 무해).
				clearMemberRegions(m.getId());
				regionsCleared = true;
			}
		}

		// ✅ 팀카테고리 정책
		TeamCategory resolvedCategory;

		if (isProductionTeam(newTeamName)) {
			if (req.getTeamCategoryId() == null) {
				throw new IllegalArgumentException("생산팀은 카테고리 선택이 필수입니다.");
			}
			resolvedCategory = teamCategoryRepository.findById(req.getTeamCategoryId())
					.orElseThrow(() -> new IllegalArgumentException("팀 카테고리를 찾을 수 없습니다."));

			if (!Objects.equals(resolvedCategory.getTeam().getId(), newTeam.getId())) {
				throw new IllegalArgumentException("선택한 팀과 팀 카테고리가 일치하지 않습니다.");
			}
		} else {
			Long forcedId = forcedCategoryIdByTeamName(newTeamName);
			if (forcedId == null) {
				throw new IllegalArgumentException("지원하지 않는 팀입니다. 팀카테고리 강제 매핑이 필요합니다: " + newTeamName);
			}
			resolvedCategory = teamCategoryRepository.findById(forcedId)
					.orElseThrow(() -> new IllegalArgumentException("고정된 팀 카테고리를 찾을 수 없습니다."));
		}

		// ✅ 기본 필드 업데이트
		m.setName(req.getName());
		m.setPhone(req.getPhone());
		m.setTelephone(req.getTelephone());
		m.setEmail(req.getEmail());
		m.setRole(MemberRole.valueOf(req.getRole()));
		m.setTeam(newTeam);
		m.setTeamCategory(resolvedCategory);
		m.setUpdatedAt(LocalDateTime.now());

		memberRepository.save(m);

		String msg = regionsCleared ? "저장되었습니다. 팀 변경으로 인해 기존 담당구역이 초기화되었습니다." : "저장되었습니다.";

		return new EmployeeUpdateResult(regionsCleared, msg);
	}

	@Transactional(readOnly = true)
	public List<MemberRegionSimpleDTO> getMemberRegionsSimple(Long memberId) {
		List<MemberRegion> list = memberRegionRepository.findByMemberIdFetchAll(memberId);
		return list.stream()
				.map(mr -> MemberRegionSimpleDTO.builder().id(mr.getId())
						.provinceName(mr.getProvince() != null ? mr.getProvince().getName() : null)
						.cityName(mr.getCity() != null ? mr.getCity().getName() : null)
						.districtName(mr.getDistrict() != null ? mr.getDistrict().getName() : null).build())
				.toList();
	}

	// ===== 선택지 조회 =====
	@Transactional(readOnly = true)
	public List<Team> getTeams() {
		return teamRepository.findAll();
	}

	@Transactional(readOnly = true)
	public List<TeamCategory> getTeamCategories(Long teamId) {
		return teamCategoryRepository.findByTeamId(teamId);
	}

	@Transactional(readOnly = true)
	public List<String> getMemberRoles() {
		return Arrays.stream(MemberRole.values()).map(Enum::name).collect(Collectors.toList());
	}

	// ===== 행정구역 조회 =====
	@Transactional(readOnly = true)
	public List<Province> getProvinces() {
		return provinceRepository.findAll();
	}

	@Transactional(readOnly = true)
	public List<City> getCities(Long provinceId) {
		return cityRepository.findByProvinceId(provinceId);
	}

	@Transactional(readOnly = true)
	public List<District> getDistricts(Long provinceId, Long cityId) {
		if (cityId != null)
			return districtRepository.findByCityId(cityId);
		return districtRepository.findByProvinceId(provinceId);
	}

	// ===== 담당구역: 조회/삭제 =====
	@Transactional(readOnly = true)
	public List<MemberRegion> getMemberRegions(Long memberId) {
		return memberRegionRepository.findByMemberId(memberId);
	}

	@Transactional
	public void deleteMemberRegion(Long memberId, Long memberRegionId) {
		MemberRegion mr = memberRegionRepository.findById(memberRegionId)
				.orElseThrow(() -> new IllegalArgumentException("담당 구역을 찾을 수 없습니다."));
		if (!Objects.equals(mr.getMember().getId(), memberId)) {
			throw new IllegalArgumentException("해당 멤버의 담당 구역이 아닙니다.");
		}
		memberRegionRepository.delete(mr);
	}

	// ===== 신규 멤버 등록 전: 팀 기준 담당구역 충돌 검사 =====
	@Transactional(readOnly = true)
	public List<ConflictDTO> checkRegionConflictsForNewMember(Long teamId, List<RegionSelectionDTO> selections) {
		Team team = teamRepository.findById(teamId).orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다."));
		final String myTeamName = (team != null ? team.getName() : null);

		List<ConflictDTO> conflicts = new ArrayList<>();

		// 1) 요청 selections → 엔티티 후보로 매핑
		List<MemberRegion> candidates = new ArrayList<>();
		for (RegionSelectionDTO sel : selections) {
			Province p = provinceRepository.findById(sel.getProvinceId())
					.orElseThrow(() -> new IllegalArgumentException("Province 미존재"));
			City c = (sel.getCityId() != null) ? cityRepository.findById(sel.getCityId())
					.orElseThrow(() -> new IllegalArgumentException("City 미존재")) : null;
			District d = (sel.getDistrictId() != null) ? districtRepository.findById(sel.getDistrictId())
					.orElseThrow(() -> new IllegalArgumentException("District 미존재")) : null;

			MemberRegion mr = new MemberRegion();
			mr.setMember(null); // 아직 멤버 미생성
			mr.setProvince(p);
			mr.setCity(c);
			mr.setDistrict(d);
			candidates.add(mr);
		}

		// 2) 요청 안에서의 상호 포함 충돌 금지
		for (int i = 0; i < candidates.size(); i++) {
			for (int j = i + 1; j < candidates.size(); j++) {
				if (isOverlap(candidates.get(i), candidates.get(j))) {
					conflicts.add(ConflictDTO.builder().conflictMemberId(null).conflictMemberName("요청내 중복")
							.conflictPath(toPath(candidates.get(j))).build());
				}
			}
		}
		if (!conflicts.isEmpty())
			return conflicts;

		// 3) 다른 멤버(동일 팀 이름만)과의 충돌
		for (MemberRegion cand : candidates) {
			// 기존 쿼리 활용: 타 멤버 전부 가져오고 서비스 단에서 팀 이름으로 필터
			List<MemberRegion> othersSameProvince = memberRegionRepository.findOthersInSameProvince(-1L,
					cand.getProvince().getId()); // -1L: 모두 포함

			List<MemberRegion> othersSameTeam = othersSameProvince.stream().filter(o -> {
				String otherTeamName = (o.getMember() != null && o.getMember().getTeam() != null)
						? o.getMember().getTeam().getName()
						: null;
				return Objects.equals(myTeamName, otherTeamName);
			}).toList();

			for (MemberRegion o : othersSameTeam) {
				if (isOverlap(cand, o)) {
					conflicts.add(
							ConflictDTO.builder().conflictMemberId(o.getMember() != null ? o.getMember().getId() : null)
									.conflictMemberName(o.getMember() != null ? o.getMember().getName() : "(알수없음)")
									.conflictPath(toPath(o)).build());
				}
			}
		}

		return conflicts;
	}

	/**
	 * 기존 서비스 호출부 보호용입니다.
	 *
	 * 기존 호출부는 별도의 확인 UI가 없으므로, 본인 하위 구역을 상위 구역으로
	 * 통합해야 하는 경우 자동 승인하여 저장합니다. 직원 상세 화면은
	 * saveMemberRegionsWithHierarchy(..., false)를 먼저 호출하여 사용자 확인을 받습니다.
	 */
	@Transactional
	public List<ConflictDTO> saveMemberRegions(RegionBulkSaveRequest req) {
		return saveMemberRegionsWithHierarchy(req, true).conflicts();
	}

	public record RegionSaveResult(boolean success, boolean requiresConfirmation, int savedCount, int removedCount,
			List<ConflictDTO> conflicts, List<String> messages) {

		public RegionSaveResult {
			conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
			messages = messages == null ? List.of() : List.copyOf(messages);
		}
	}

	/**
	 * 배송팀/AS팀 담당구역 저장 규칙
	 *
	 * 1. 같은 팀의 다른 멤버가 상위/동일/하위 구역을 담당하면 저장하지 않습니다.
	 * 2. 본인이 이미 상위 구역을 담당하면 더 하위 구역은 중복 저장하지 않습니다.
	 * 3. 본인이 담당 중인 하위 구역보다 상위 구역을 새로 등록하면, 확인 후 하위 구역을
	 *    삭제하고 상위 구역 하나로 통합합니다.
	 * 4. 요청 안에 상위/하위 구역이 함께 있으면 상위 구역 하나로 정규화합니다.
	 */
	@Transactional
	public RegionSaveResult saveMemberRegionsWithHierarchy(RegionBulkSaveRequest req,
			boolean confirmConsolidation) {

		if (req == null || req.getMemberId() == null) {
			throw new IllegalArgumentException("멤버 정보가 누락되었습니다.");
		}
		if (req.getSelections() == null || req.getSelections().isEmpty()) {
			return new RegionSaveResult(true, false, 0, 0, List.of(),
					List.of("추가할 담당 구역이 없습니다."));
		}

		Member me = memberRepository.findById(req.getMemberId())
				.orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다."));

		String myTeamName = me.getTeam() != null ? me.getTeam().getName() : null;
		if (!isRegionTeam(myTeamName)) {
			throw new IllegalArgumentException("배송팀 또는 AS팀 멤버만 담당 구역을 등록할 수 있습니다.");
		}

		List<String> messages = new ArrayList<>();
		List<MemberRegion> rawCandidates = buildRegionCandidates(me, req.getSelections());
		List<MemberRegion> candidates = normalizeRequestedCandidates(rawCandidates, messages);

		// 다른 멤버와의 충돌을 먼저 전부 검증합니다. 충돌이 하나라도 있으면 삭제/저장을 전혀 하지 않습니다.
		List<ConflictDTO> conflicts = new ArrayList<>();
		Set<String> conflictKeys = new LinkedHashSet<>();

		for (MemberRegion candidate : candidates) {
			List<MemberRegion> othersSameProvince = memberRegionRepository.findOthersInSameProvince(me.getId(),
					candidate.getProvince().getId());

			for (MemberRegion other : othersSameProvince) {
				String otherTeamName = other.getMember() != null && other.getMember().getTeam() != null
						? other.getMember().getTeam().getName()
						: null;

				if (!Objects.equals(myTeamName, otherTeamName) || !isOverlap(candidate, other)) {
					continue;
				}

				Long otherMemberId = other.getMember() != null ? other.getMember().getId() : null;
				String key = otherMemberId + ":" + other.getId() + ":" + toPath(candidate);
				if (!conflictKeys.add(key)) {
					continue;
				}

				String otherMemberName = other.getMember() != null ? other.getMember().getName() : "(알 수 없음)";
				conflicts.add(ConflictDTO.builder().conflictMemberId(otherMemberId)
						.conflictMemberName(otherMemberName).conflictPath(toPath(other)).build());
				messages.add("등록 요청 지역 [" + toPath(candidate) + "]은(는) " + otherMemberName + "님의 담당 지역 ["
						+ toPath(other) + "]과 상·하위 범위가 중복되어 등록할 수 없습니다.");
			}
		}

		if (!conflicts.isEmpty()) {
			return new RegionSaveResult(false, false, 0, 0, conflicts, messages);
		}

		List<MemberRegion> mine = memberRegionRepository.findByMemberIdFetchAll(me.getId());
		List<MemberRegion> regionsToSave = new ArrayList<>();
		List<MemberRegion> regionsToDelete = new ArrayList<>();
		Set<Long> deleteIds = new LinkedHashSet<>();

		for (MemberRegion candidate : candidates) {
			MemberRegion exact = mine.stream().filter(own -> samePath(own, candidate)).findFirst().orElse(null);
			if (exact != null) {
				messages.add("[" + toPath(candidate) + "]은(는) 이미 등록된 담당 지역이므로 추가 저장하지 않았습니다.");
				continue;
			}

			MemberRegion ownParent = mine.stream().filter(own -> contains(own, candidate)).findFirst().orElse(null);
			if (ownParent != null) {
				messages.add("[" + toPath(candidate) + "]은(는) 이미 담당 중인 상위 지역 [" + toPath(ownParent)
						+ "]에 포함되므로 별도로 추가하지 않았습니다.");
				continue;
			}

			List<MemberRegion> ownChildren = mine.stream().filter(own -> contains(candidate, own)).toList();
			for (MemberRegion child : ownChildren) {
				if (child.getId() != null && deleteIds.add(child.getId())) {
					regionsToDelete.add(child);
					messages.add("기존 하위 담당 지역 [" + toPath(child) + "]을(를) 삭제하고 상위 지역 ["
							+ toPath(candidate) + "]으로 통합합니다.");
				}
			}

			regionsToSave.add(candidate);
		}

		if (!regionsToDelete.isEmpty() && !confirmConsolidation) {
			return new RegionSaveResult(false, true, 0, regionsToDelete.size(), List.of(), messages);
		}

		if (!regionsToDelete.isEmpty()) {
			memberRegionRepository.deleteAll(regionsToDelete);
		}

		for (MemberRegion region : regionsToSave) {
			memberRegionRepository.save(region);
		}

		if (regionsToSave.isEmpty() && regionsToDelete.isEmpty()) {
			messages.add("변경할 담당 구역이 없습니다.");
		} else {
			messages.add("담당 구역 저장 완료: 신규 " + regionsToSave.size() + "개, 하위 구역 정리 "
					+ regionsToDelete.size() + "개");
		}

		return new RegionSaveResult(true, false, regionsToSave.size(), regionsToDelete.size(), List.of(), messages);
	}

	private List<MemberRegion> buildRegionCandidates(Member member, List<RegionSelectionDTO> selections) {
		List<MemberRegion> candidates = new ArrayList<>();

		for (RegionSelectionDTO selection : selections) {
			if (selection == null || selection.getProvinceId() == null) {
				throw new IllegalArgumentException("광역시/도 선택값이 누락되었습니다.");
			}

			Province province = provinceRepository.findById(selection.getProvinceId())
					.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 광역시/도입니다."));
			City city = selection.getCityId() != null
					? cityRepository.findById(selection.getCityId())
							.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 시/군/구입니다."))
					: null;
			District district = selection.getDistrictId() != null
					? districtRepository.findById(selection.getDistrictId())
							.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구/읍/면/동입니다."))
					: null;

			validateRegionHierarchy(province, city, district);

			MemberRegion candidate = new MemberRegion();
			candidate.setMember(member);
			candidate.setProvince(province);
			candidate.setCity(city);
			candidate.setDistrict(district);
			candidates.add(candidate);
		}

		return candidates;
	}

	private void validateRegionHierarchy(Province province, City city, District district) {
		if (city != null && (city.getProvince() == null
				|| !Objects.equals(city.getProvince().getId(), province.getId()))) {
			throw new IllegalArgumentException("선택한 시/군/구가 광역시/도에 속하지 않습니다.");
		}

		if (district != null && (district.getProvince() == null
				|| !Objects.equals(district.getProvince().getId(), province.getId()))) {
			throw new IllegalArgumentException("선택한 구/읍/면/동이 광역시/도에 속하지 않습니다.");
		}

		if (city == null && district != null && district.getCity() != null) {
			throw new IllegalArgumentException("해당 하위 지역을 등록하려면 시/군/구를 먼저 선택해야 합니다.");
		}

		if (city != null && district != null
				&& (district.getCity() == null || !Objects.equals(district.getCity().getId(), city.getId()))) {
			throw new IllegalArgumentException("선택한 구/읍/면/동이 시/군/구에 속하지 않습니다.");
		}
	}

	private List<MemberRegion> normalizeRequestedCandidates(List<MemberRegion> source, List<String> messages) {
		List<MemberRegion> normalized = new ArrayList<>();

		for (MemberRegion candidate : source) {
			MemberRegion exact = normalized.stream().filter(saved -> samePath(saved, candidate)).findFirst().orElse(null);
			if (exact != null) {
				messages.add("요청 내 동일 지역 [" + toPath(candidate) + "]이(가) 중복되어 한 번만 처리합니다.");
				continue;
			}

			MemberRegion requestedParent = normalized.stream().filter(saved -> contains(saved, candidate)).findFirst()
					.orElse(null);
			if (requestedParent != null) {
				messages.add("요청 지역 [" + toPath(candidate) + "]은(는) 함께 요청된 상위 지역 ["
						+ toPath(requestedParent) + "]에 포함되어 제외합니다.");
				continue;
			}

			List<MemberRegion> requestedChildren = normalized.stream().filter(saved -> contains(candidate, saved)).toList();
			if (!requestedChildren.isEmpty()) {
				normalized.removeAll(requestedChildren);
				for (MemberRegion child : requestedChildren) {
					messages.add("요청된 하위 지역 [" + toPath(child) + "]을(를) 상위 지역 [" + toPath(candidate)
							+ "]으로 통합하여 처리합니다.");
				}
			}

			normalized.add(candidate);
		}

		return normalized;
	}

	/**
	 * 포함(상하위) 관계일 때만 true.
	 */
	private boolean isOverlap(MemberRegion a, MemberRegion b) {
		if (a.getProvince() == null || b.getProvince() == null)
			return false;
		if (!Objects.equals(a.getProvince().getId(), b.getProvince().getId()))
			return false;
		return contains(a, b) || contains(b, a);
	}

	/** A가 B를 포함하면 true */
	private boolean contains(MemberRegion a, MemberRegion b) {
		if (a == null || b == null || a.getProvince() == null || b.getProvince() == null) {
			return false;
		}
		if (!Objects.equals(a.getProvince().getId(), b.getProvince().getId())) {
			return false;
		}

		if (a.getCity() == null && a.getDistrict() == null) {
			return true;
		}
		if (a.getCity() == null && a.getDistrict() != null) {
			return (b.getDistrict() != null) && Objects.equals(a.getDistrict().getId(), b.getDistrict().getId());
		}
		if (a.getCity() != null && a.getDistrict() == null) {
			if (b.getCity() != null) {
				return Objects.equals(a.getCity().getId(), b.getCity().getId());
			}
			if (b.getDistrict() != null) {
				return b.getDistrict().getCity() != null
						&& Objects.equals(a.getCity().getId(), b.getDistrict().getCity().getId());
			}
			return false;
		}
		if (a.getCity() != null && a.getDistrict() != null) {
			return (b.getDistrict() != null) && Objects.equals(a.getDistrict().getId(), b.getDistrict().getId());
		}
		return false;
	}

	private boolean samePath(MemberRegion x, MemberRegion y) {
		if (x == null || y == null) {
			return false;
		}

		Long xProvinceId = x.getProvince() != null ? x.getProvince().getId() : null;
		Long yProvinceId = y.getProvince() != null ? y.getProvince().getId() : null;
		Long xCityId = x.getCity() != null ? x.getCity().getId() : null;
		Long yCityId = y.getCity() != null ? y.getCity().getId() : null;
		Long xDistrictId = x.getDistrict() != null ? x.getDistrict().getId() : null;
		Long yDistrictId = y.getDistrict() != null ? y.getDistrict().getId() : null;

		return Objects.equals(xProvinceId, yProvinceId) && Objects.equals(xCityId, yCityId)
				&& Objects.equals(xDistrictId, yDistrictId);
	}

	private String toPath(MemberRegion mr) {
		StringBuilder sb = new StringBuilder();
		sb.append(mr.getProvince().getName());
		if (mr.getCity() != null)
			sb.append(" ").append(mr.getCity().getName());
		if (mr.getDistrict() != null)
			sb.append(" ").append(mr.getDistrict().getName());
		return sb.toString();
	}
}