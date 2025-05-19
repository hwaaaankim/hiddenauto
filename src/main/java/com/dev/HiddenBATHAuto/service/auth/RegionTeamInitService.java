package com.dev.HiddenBATHAuto.service.auth;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRegion;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.Team;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRegionRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegionTeamInitService {

	private final MemberRepository memberRepository;
	private final MemberRegionRepository memberRegionRepository;
	private final DistrictRepository districtRepository;
	private final TeamRepository teamRepository;
	private final TeamCategoryRepository teamCategoryRepository;
	private final PasswordEncoder passwordEncoder;

	public String initRegionMembers() {
		// ✅ 팀 조회
		Team asTeam = teamRepository.findByName("AS팀").orElseThrow(() -> new RuntimeException("AS팀을 찾을 수 없습니다"));
		Team deliveryTeam = teamRepository.findByName("배송팀").orElseThrow(() -> new RuntimeException("배송팀을 찾을 수 없습니다"));

		// ✅ 팀별 카테고리 조회 (서울/경기 각각 팀에 소속된 것만)
		TeamCategory asSeoul = teamCategoryRepository.findByNameAndTeam("서울", asTeam)
				.orElseThrow(() -> new RuntimeException("AS팀의 서울 카테고리를 찾을 수 없습니다"));
		TeamCategory asGyeonggi = teamCategoryRepository.findByNameAndTeam("경기", asTeam)
				.orElseThrow(() -> new RuntimeException("AS팀의 경기 카테고리를 찾을 수 없습니다"));
		TeamCategory deliverySeoul = teamCategoryRepository.findByNameAndTeam("서울", deliveryTeam)
				.orElseThrow(() -> new RuntimeException("배송팀의 서울 카테고리를 찾을 수 없습니다"));
		TeamCategory deliveryGyeonggi = teamCategoryRepository.findByNameAndTeam("경기", deliveryTeam)
				.orElseThrow(() -> new RuntimeException("배송팀의 경기 카테고리를 찾을 수 없습니다"));

		// ✅ 지역 무작위 추출
		List<District> allDistricts = districtRepository.findAll();
		Collections.shuffle(allDistricts);

		int pointer = 0;
		createMemberWithRegions("asteam_01", "AS팀 서울담당", asTeam, asSeoul, allDistricts.subList(pointer, pointer + 10));
		pointer += 10;
		createMemberWithRegions("asteam_02", "AS팀 경기담당", asTeam, asGyeonggi,
				allDistricts.subList(pointer, pointer + 10));
		pointer += 10;
		createMemberWithRegions("delivery_01", "배송팀 서울담당", deliveryTeam, deliverySeoul,
				allDistricts.subList(pointer, pointer + 10));
		pointer += 10;
		createMemberWithRegions("delivery_02", "배송팀 경기담당", deliveryTeam, deliveryGyeonggi,
				allDistricts.subList(pointer, pointer + 10));

		return "✅ 지역 담당 직원 생성 완료";
	}

	private void createMemberWithRegions(String username, String name, Team team, TeamCategory category,
			List<District> districtList) {
		if (memberRepository.existsByUsername(username)) {
			System.out.println("⚠️ 이미 존재하는 유저: " + username);
			return;
		}

		Member member = new Member();
		member.setUsername(username);
		member.setPassword(passwordEncoder.encode("12345"));
		member.setName(name);
		member.setRole(MemberRole.INTERNAL_EMPLOYEE);
		member.setTeam(team);
		member.setTeamCategory(category);
		member.setEnabled(true);
		member.setCreatedAt(LocalDateTime.now());

		member = memberRepository.save(member);

		for (District district : districtList) {
			MemberRegion region = MemberRegion.builder().member(member).province(district.getProvince())
					.city(district.getCity()) // null 허용
					.district(district).build();
			memberRegionRepository.save(region);
		}

		System.out.println("✅ 생성 완료: " + username + " (" + category.getName() + ")");
	}
}