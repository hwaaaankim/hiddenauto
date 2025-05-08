package com.dev.HiddenBATHAuto.controller.page;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/team")
@RequiredArgsConstructor
public class TeamController {

	private final MemberRepository memberRepository;

    // 방식 1: PathVariable
    @GetMapping("/check-role/{memberId}")
    public String checkTeamInfoByPath(@PathVariable Long memberId) {
        return checkTeamInfo(memberId);
    }

    // 방식 2: RequestBody
    @PostMapping("/check-role")
    public String checkTeamInfoByBody(@RequestBody Map<String, Long> body) {
        Long memberId = body.get("memberId");
        return checkTeamInfo(memberId);
    }

    private String checkTeamInfo(Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new RuntimeException("해당 회원이 존재하지 않습니다."));

        if (member.getRole() != MemberRole.INTERNAL_EMPLOYEE) {
            return "내부 직원이 아닙니다.";
        }

        String team = member.getTeam() != null ? member.getTeam().getName() : "팀 없음";
        String category = member.getTeamCategory() != null ? member.getTeamCategory().getName() : "부서 없음";

        return String.format("팀: %s, 부서: %s", team, category);
    }
    
 // ✅ 팀별 접근 제한 (Team ID 기준)
    @GetMapping("/team1")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamId == 1")
    public String accessTeam1() {
        return "1팀 전용 페이지";
    }

    @GetMapping("/team2")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamId == 2")
    public String accessTeam2() {
        return "2팀 전용 페이지";
    }

    @GetMapping("/team3")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamId == 3")
    public String accessTeam3() {
        return "3팀 전용 페이지";
    }

    @GetMapping("/team4")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamId == 4")
    public String accessTeam4() {
        return "4팀 전용 페이지";
    }

    // ✅ 팀 카테고리별 접근 제한

    // 1팀: 1개 카테고리 (예: 설계1팀)
    @GetMapping("/team1/cat1")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamCategoryId == 1")
    public String accessTeam1Cat1() {
        return "1팀 - 1번 카테고리 접근";
    }

    // 2팀: 6개 카테고리 (예: 하부장, 상부장, 슬라이드 등)
    @GetMapping("/team2/cat1")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamCategoryId == 2")
    public String accessTeam2Cat1() {
        return "2팀 - 1번 카테고리 접근";
    }

    @GetMapping("/team2/cat2")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamCategoryId == 3")
    public String accessTeam2Cat2() {
        return "2팀 - 2번 카테고리 접근";
    }

    @GetMapping("/team2/cat3")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamCategoryId == 4")
    public String accessTeam2Cat3() {
        return "2팀 - 3번 카테고리 접근";
    }

    @GetMapping("/team2/cat4")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamCategoryId == 5")
    public String accessTeam2Cat4() {
        return "2팀 - 4번 카테고리 접근";
    }

    @GetMapping("/team2/cat5")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamCategoryId == 6")
    public String accessTeam2Cat5() {
        return "2팀 - 5번 카테고리 접근";
    }

    @GetMapping("/team2/cat6")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamCategoryId == 7")
    public String accessTeam2Cat6() {
        return "2팀 - 6번 카테고리 접근";
    }

    // 3팀: 2개 카테고리
    @GetMapping("/team3/cat1")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamCategoryId == 8")
    public String accessTeam3Cat1() {
        return "3팀 - 1번 카테고리 접근";
    }

    @GetMapping("/team3/cat2")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamCategoryId == 9")
    public String accessTeam3Cat2() {
        return "3팀 - 2번 카테고리 접근";
    }

    // 4팀: 2개 카테고리
    @GetMapping("/team4/cat1")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamCategoryId == 10")
    public String accessTeam4Cat1() {
        return "4팀 - 1번 카테고리 접근";
    }

    @GetMapping("/team4/cat2")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE') and #authentication.principal.teamCategoryId == 11")
    public String accessTeam4Cat2() {
        return "4팀 - 2번 카테고리 접근";
    }
	
}
