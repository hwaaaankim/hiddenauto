package com.dev.HiddenBATHAuto.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.service.auth.InitService;
import com.dev.HiddenBATHAuto.service.auth.RegionTeamInitService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/init")
@RequiredArgsConstructor
public class InitController {

	private final InitService initService;
	private final RegionTeamInitService regionTeamInitService;
	
	@GetMapping("/regionMember")
    public ResponseEntity<String> initRegionMembers() {
        String result = regionTeamInitService.initRegionMembers();
        return ResponseEntity.ok(result);
    }
	
	@GetMapping("/teams")
    public void createTeams() {
        initService.createTeam("관리팀");
        initService.createTeam("생산팀");
        initService.createTeam("배송팀");
        initService.createTeam("AS팀");
    }

    @GetMapping("/team-categories")
    public void createTeamCategories() {
        initService.createTeamCategory("관리팀직원", "관리팀");

        initService.createTeamCategory("하부장", "생산팀");
        initService.createTeamCategory("상부장", "생산팀");
        initService.createTeamCategory("슬라이드장", "생산팀");
        initService.createTeamCategory("플랩장", "생산팀");
        initService.createTeamCategory("거울", "생산팀");
        initService.createTeamCategory("LED거울", "생산팀");

        initService.createTeamCategory("서울", "배송팀");
        initService.createTeamCategory("경기", "배송팀");

        initService.createTeamCategory("서울", "AS팀");
        initService.createTeamCategory("경기", "AS팀");
    }

    @GetMapping("/companies")
    public void createCompanies() {
        initService.createCompany("고객사A", "111-11-11111");
        initService.createCompany("고객사B", "222-22-22222");
    }

    @GetMapping("/members")
    public void createMembers() {
        initService.createMember("admin", "최고관리자", MemberRole.ADMIN, null, null);
        initService.createMember("manager", "관리팀직원", MemberRole.MANAGEMENT, "관리팀", "관리팀직원");

        initService.createMember("production_low", "하부장 담당", MemberRole.INTERNAL_EMPLOYEE, "생산팀", "하부장");
        initService.createMember("production_top", "상부장 담당", MemberRole.INTERNAL_EMPLOYEE, "생산팀", "상부장");
        initService.createMember("production_slide", "슬라이드 담당", MemberRole.INTERNAL_EMPLOYEE, "생산팀", "슬라이드장");
        initService.createMember("production_flap", "플랩 담당", MemberRole.INTERNAL_EMPLOYEE, "생산팀", "플랩장");
        initService.createMember("production_mirror", "거울 담당", MemberRole.INTERNAL_EMPLOYEE, "생산팀", "거울");
        initService.createMember("production_led", "LED거울 담당", MemberRole.INTERNAL_EMPLOYEE, "생산팀", "LED거울");

        initService.createMember("delivery_seoul", "배송 서울", MemberRole.INTERNAL_EMPLOYEE, "배송팀", "서울");
        initService.createMember("delivery_gg", "배송 경기", MemberRole.INTERNAL_EMPLOYEE, "배송팀", "경기");

        initService.createMember("as_seoul", "AS 서울", MemberRole.INTERNAL_EMPLOYEE, "AS팀", "서울");
        initService.createMember("as_gg", "AS 경기", MemberRole.INTERNAL_EMPLOYEE, "AS팀", "경기");

        initService.createMember("client_a", "고객사A대표", MemberRole.CUSTOMER_REPRESENTATIVE, null, null, "고객사A");
        initService.createMember("client_a_emp", "고객사A직원", MemberRole.CUSTOMER_EMPLOYEE, null, null, "고객사A");
        initService.createMember("client_b", "고객사B대표", MemberRole.CUSTOMER_REPRESENTATIVE, null, null, "고객사B");
        initService.createMember("client_b_emp", "고객사B직원", MemberRole.CUSTOMER_EMPLOYEE, null, null, "고객사B");
    }
}
