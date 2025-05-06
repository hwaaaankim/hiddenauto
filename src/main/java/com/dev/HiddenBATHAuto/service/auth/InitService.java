package com.dev.HiddenBATHAuto.service.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.Team;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InitService {

	private final MemberRepository memberRepository;
	private final TeamRepository teamRepository;
	private final TeamCategoryRepository teamCategoryRepository;
	private final CompanyRepository companyRepository;
	private final PasswordEncoder passwordEncoder;

	public void createTeam(String name) {
		if (teamRepository.findByName(name) == null) {
			Team team = new Team();
			team.setName(name);
			teamRepository.save(team);
		}
	}

	public void createTeamCategory(String name, String teamName) {
		Team team = teamRepository.findByName(teamName);
		if (team == null)
			return;

		if (teamCategoryRepository.findByNameAndTeam(name, team) == null) {
			TeamCategory category = new TeamCategory();
			category.setName(name);
			category.setTeam(team);
			teamCategoryRepository.save(category);
		}
	}

	public void createCompany(String name, String businessNo) {
		if (companyRepository.findByName(name) == null) {
			Company company = new Company();
			company.setName(name);
			company.setBusinessNo(businessNo);
			company.setAddress("임시주소");
			company.setRegistrationKey("KEY-" + name);
			companyRepository.save(company);
		}
	}

	public void createMember(String username, String name, MemberRole role, String teamName, String teamCategoryName) {
		createMember(username, name, role, teamName, teamCategoryName, null);
	}

	public void createMember(String username, String name, MemberRole role, String teamName, String teamCategoryName,
			String companyName) {
		if (memberRepository.existsByUsername(username))
			return;

		Member member = new Member();
		member.setUsername(username);
		member.setPassword(passwordEncoder.encode("12345"));
		member.setName(name);
		member.setRole(role);
		member.setPhone("010-0000-0000");
		member.setEmail(username + "@test.com");
		member.setTelephone("02-000-0000");
		member.setZipCode("00000");
		member.setRoadAddress("도로명주소");
		member.setJibunAddress("지번주소");
		member.setDetailAddress("상세주소");

		if (teamName != null) {
			Team team = teamRepository.findByName(teamName);
			member.setTeam(team);

			if (teamCategoryName != null) {
				TeamCategory category = teamCategoryRepository.findByNameAndTeam(teamCategoryName, team);
				member.setTeamCategory(category);

				// 🔍 팀 유형에 따른 스코프 세팅
				if ("배송팀".equals(team.getName()) || "AS팀".equals(team.getName())) {
					member.setAddressScope(teamCategoryName); // 예: "서울", "경기"
				} else if ("생산팀".equals(team.getName())) {
					member.setProductCategoryScope(teamCategoryName); // 예: "하부장", "거울"
				}
			}
		}

		if (companyName != null) {
			Company company = companyRepository.findByName(companyName);
			member.setCompany(company);
		}

		memberRepository.save(member);
	}
}
