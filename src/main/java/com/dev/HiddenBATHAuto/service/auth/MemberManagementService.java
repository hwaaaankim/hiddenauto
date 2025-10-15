package com.dev.HiddenBATHAuto.service.auth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	// ===== 멤버 업데이트 =====
	@Transactional
	public void updateEmployee(EmployeeUpdateRequest req) {
		Member m = memberRepository.findById(req.getMemberId())
				.orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다."));

		// 필수 검증 (유선/이메일 제외)
		if (isBlank(req.getName()) || isBlank(req.getPhone()) || isBlank(req.getRole()) || req.getTeamId() == null
				|| req.getTeamCategoryId() == null) {
			throw new IllegalArgumentException("필수 항목이 누락되었습니다.");
		}

		Team team = teamRepository.findById(req.getTeamId())
				.orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다."));
		TeamCategory teamCategory = teamCategoryRepository.findById(req.getTeamCategoryId())
				.orElseThrow(() -> new IllegalArgumentException("팀 카테고리를 찾을 수 없습니다."));
		if (!Objects.equals(teamCategory.getTeam().getId(), team.getId())) {
			throw new IllegalArgumentException("선택한 팀과 팀 카테고리가 일치하지 않습니다.");
		}

		m.setName(req.getName());
		m.setPhone(req.getPhone());
		m.setTelephone(req.getTelephone());
		m.setEmail(req.getEmail());
		m.setRole(MemberRole.valueOf(req.getRole()));
		m.setTeam(team);
		m.setTeamCategory(teamCategory);
		m.setUpdatedAt(java.time.LocalDateTime.now());
		memberRepository.save(m);
	}

	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
	
	@Transactional(readOnly = true)
	public List<MemberRegionSimpleDTO> getMemberRegionsSimple(Long memberId) {
	    List<MemberRegion> list = memberRegionRepository.findByMemberIdFetchAll(memberId);
	    return list.stream().map(mr -> MemberRegionSimpleDTO.builder()
	            .id(mr.getId())
	            .provinceName(mr.getProvince() != null ? mr.getProvince().getName() : null)
	            .cityName(mr.getCity() != null ? mr.getCity().getName() : null)
	            .districtName(mr.getDistrict() != null ? mr.getDistrict().getName() : null)
	            .build())
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
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다."));
		final String myTeamName = (team != null ? team.getName() : null);

		List<ConflictDTO> conflicts = new ArrayList<>();

		// 1) 요청 selections → 엔티티 후보로 매핑
		List<MemberRegion> candidates = new ArrayList<>();
		for (RegionSelectionDTO sel : selections) {
			Province p = provinceRepository.findById(sel.getProvinceId())
					.orElseThrow(() -> new IllegalArgumentException("Province 미존재"));
			City c = (sel.getCityId() != null)
					? cityRepository.findById(sel.getCityId())
							.orElseThrow(() -> new IllegalArgumentException("City 미존재"))
					: null;
			District d = (sel.getDistrictId() != null)
					? districtRepository.findById(sel.getDistrictId())
							.orElseThrow(() -> new IllegalArgumentException("District 미존재"))
					: null;

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
					conflicts.add(ConflictDTO.builder()
							.conflictMemberId(null)
							.conflictMemberName("요청내 중복")
							.conflictPath(toPath(candidates.get(j)))
							.build());
				}
			}
		}
		if (!conflicts.isEmpty()) return conflicts;

		// 3) 다른 멤버(동일 팀 이름만)과의 충돌
		for (MemberRegion cand : candidates) {
			// 기존 쿼리 활용: 타 멤버 전부 가져오고 서비스 단에서 팀 이름으로 필터
			List<MemberRegion> othersSameProvince =
					memberRegionRepository.findOthersInSameProvince(-1L, cand.getProvince().getId()); // -1L: 모두 포함

			List<MemberRegion> othersSameTeam = othersSameProvince.stream()
					.filter(o -> {
						String otherTeamName =
								(o.getMember() != null && o.getMember().getTeam() != null)
										? o.getMember().getTeam().getName()
										: null;
						return Objects.equals(myTeamName, otherTeamName);
					})
					.toList();

			for (MemberRegion o : othersSameTeam) {
				if (isOverlap(cand, o)) {
					conflicts.add(ConflictDTO.builder()
							.conflictMemberId(o.getMember() != null ? o.getMember().getId() : null)
							.conflictMemberName(o.getMember() != null ? o.getMember().getName() : "(알수없음)")
							.conflictPath(toPath(o))
							.build());
				}
			}
		}

		return conflicts;
	}

	@Transactional
	public List<ConflictDTO> saveMemberRegions(RegionBulkSaveRequest req) {
	    Member me = memberRepository.findById(req.getMemberId())
	        .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다."));

	    List<ConflictDTO> conflicts = new ArrayList<>();

	    // === 0) 내 팀 이름 확보 (null 허용: null 팀이면 null 팀끼리만 비교)
	    final String myTeamName = (me.getTeam() != null ? me.getTeam().getName() : null);

	    // === 1) 요청 selections → 엔티티 후보로 매핑
	    List<MemberRegion> candidates = new ArrayList<>();
	    for (RegionSelectionDTO sel : req.getSelections()) {
	        Province p = provinceRepository.findById(sel.getProvinceId())
	            .orElseThrow(() -> new IllegalArgumentException("Province 미존재"));
	        City c = (sel.getCityId() != null)
	            ? cityRepository.findById(sel.getCityId())
	                .orElseThrow(() -> new IllegalArgumentException("City 미존재"))
	            : null;
	        District d = (sel.getDistrictId() != null)
	            ? districtRepository.findById(sel.getDistrictId())
	                .orElseThrow(() -> new IllegalArgumentException("District 미존재"))
	            : null;

	        MemberRegion mr = new MemberRegion();
	        mr.setMember(me);
	        mr.setProvince(p);
	        mr.setCity(c);
	        mr.setDistrict(d);
	        candidates.add(mr);
	    }

	    // === 2) 본인 보유 구역과의 '포함' 충돌 금지
	    List<MemberRegion> mine = memberRegionRepository.findByMemberId(me.getId());
	    for (MemberRegion cand : candidates) {
	        for (MemberRegion own : mine) {
	            if (isOverlap(cand, own)) {
	                conflicts.add(ConflictDTO.builder()
	                    .conflictMemberId(me.getId())
	                    .conflictMemberName(me.getName() + " (본인)")
	                    .conflictPath(toPath(own))
	                    .build());
	            }
	        }
	    }

	    // === 3) 요청 안에서의 상호 포함 충돌 금지
	    for (int i = 0; i < candidates.size(); i++) {
	        for (int j = i + 1; j < candidates.size(); j++) {
	            if (isOverlap(candidates.get(i), candidates.get(j))) {
	                conflicts.add(ConflictDTO.builder()
	                    .conflictMemberId(me.getId())
	                    .conflictMemberName(me.getName() + " (요청내 중복)")
	                    .conflictPath(toPath(candidates.get(j)))
	                    .build());
	            }
	        }
	    }

	    // === 4) 다른 멤버와의 충돌 (동일 '팀 이름'끼리만 검사)
	    for (MemberRegion cand : candidates) {
	        List<MemberRegion> othersSameProvince =
	            memberRegionRepository.findOthersInSameProvince(me.getId(), cand.getProvince().getId());

	        List<MemberRegion> othersSameTeam = othersSameProvince.stream()
	            .filter(o -> {
	                String otherTeamName =
	                    (o.getMember() != null && o.getMember().getTeam() != null)
	                        ? o.getMember().getTeam().getName()
	                        : null;
	                return Objects.equals(myTeamName, otherTeamName);
	            })
	            .toList();

	        for (MemberRegion o : othersSameTeam) {
	            if (isOverlap(cand, o)) {
	                conflicts.add(ConflictDTO.builder()
	                    .conflictMemberId(o.getMember().getId())
	                    .conflictMemberName(o.getMember().getName())
	                    .conflictPath(toPath(o))
	                    .build());
	            }
	        }
	    }

	    if (!conflicts.isEmpty()) return conflicts;

	    for (MemberRegion cand : candidates) {
	        boolean dupSamePath = mine.stream().anyMatch(mr -> samePath(mr, cand));
	        if (!dupSamePath) {
	            memberRegionRepository.save(cand);
	        }
	    }
	    return Collections.emptyList();
	}

	/**
	 * 포함(상하위) 관계일 때만 true.
	 */
	private boolean isOverlap(MemberRegion a, MemberRegion b) {
	    if (a.getProvince() == null || b.getProvince() == null) return false;
	    if (!Objects.equals(a.getProvince().getId(), b.getProvince().getId())) return false;
	    return contains(a, b) || contains(b, a);
	}

	/** A가 B를 포함하면 true */
	private boolean contains(MemberRegion a, MemberRegion b) {
	    if (a.getCity() == null && a.getDistrict() == null) {
	        return true;
	    }
	    if (a.getCity() == null && a.getDistrict() != null) {
	        return (b.getDistrict() != null) &&
	               Objects.equals(a.getDistrict().getId(), b.getDistrict().getId());
	    }
	    if (a.getCity() != null && a.getDistrict() == null) {
	        if (b.getCity() != null) {
	            return Objects.equals(a.getCity().getId(), b.getCity().getId());
	        }
	        if (b.getDistrict() != null) {
	            return b.getDistrict().getCity() != null &&
	                   Objects.equals(a.getCity().getId(), b.getDistrict().getCity().getId());
	        }
	        return false;
	    }
	    if (a.getCity() != null && a.getDistrict() != null) {
	        return (b.getDistrict() != null) &&
	               Objects.equals(a.getDistrict().getId(), b.getDistrict().getId());
	    }
	    return false;
	}

	private boolean samePath(MemberRegion x, MemberRegion y) {
		if (!Objects.equals(id(x.getProvince()), id(y.getProvince())))
			return false;
		if (id(x.getCity()) == null && id(y.getCity()) == null)
			return true;
		if (!Objects.equals(id(x.getCity()), id(y.getCity())))
			return false;
		if (id(x.getDistrict()) == null && id(y.getDistrict()) == null)
			return true;
		return Objects.equals(id(x.getDistrict()), id(y.getDistrict()));
	}

	private Long id(Object e) {
		if (e == null)
			return null;
		try {
			return (Long) e.getClass().getMethod("getId").invoke(e);
		} catch (Exception ex) {
			return null;
		}
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