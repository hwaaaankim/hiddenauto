package com.dev.HiddenBATHAuto.controller.page;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.model.auth.Team;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamRepository;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/team")
@RequiredArgsConstructor
public class TeamController {

	private final MemberRepository memberRepository;
	private final TeamRepository teamRepository;

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
    
    @GetMapping("/{memberId}")
    @ResponseBody
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE')")
    public String accessTeamByMemberId(@PathVariable Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new RuntimeException("해당 멤버를 찾을 수 없습니다."));

        String teamName = member.getTeam() != null ? member.getTeam().getName() : "팀 없음";
        String categoryName = member.getTeamCategory() != null ? member.getTeamCategory().getName() : "카테고리 없음";

        return String.format("회원 ID: %d, 팀: %s, 카테고리: %s", memberId, teamName, categoryName);
    }
    
    @GetMapping("/team/asList/{teamId}/{teamCategoryId}")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE')")
    public String accessAsTeamPage(@PathVariable Long teamId,
                                   @PathVariable Long teamCategoryId,
                                   @AuthenticationPrincipal PrincipalDetails principalDetails,
                                   Model model) {

    	Member member = principalDetails.getMember();

        // 팀 존재 여부 확인
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 팀입니다."));

        // 팀 이름 검사
        if (!"AS팀".equals(team.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AS팀이 아닌 팀입니다.");
        }

        // 현재 로그인한 사용자의 팀 일치 여부 검사
        if (!member.getTeam().getId().equals(teamId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근하려는 팀이 사용자 팀과 일치하지 않습니다.");
        }

        if (member.getTeamCategory() == null || !member.getTeamCategory().getId().equals(teamCategoryId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근하려는 카테고리가 사용자 정보와 일치하지 않습니다.");
        }

        // 통과했으면 해당 뷰로 이동 (또는 필요한 데이터 추가)
        model.addAttribute("teamName", team.getName());
        model.addAttribute("teamCategory", member.getTeamCategory().getName());
        return "administration/team/as/asList"; // Thymeleaf 템플릿 이름
    }
    
    @GetMapping("/deliveryList/{teamId}/{teamCategoryId}")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE')")
    public String accessDeliveryTeamPage(@PathVariable Long teamId,
                                   @PathVariable Long teamCategoryId,
                                   @AuthenticationPrincipal PrincipalDetails principalDetails,
                                   Model model) {

    	Member member = principalDetails.getMember();

        // 팀 존재 여부 확인
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 팀입니다."));

        // 팀 이름 검사
        if (!"배송팀".equals(team.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AS팀이 아닌 팀입니다.");
        }

        // 현재 로그인한 사용자의 팀 일치 여부 검사
        if (!member.getTeam().getId().equals(teamId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근하려는 팀이 사용자 팀과 일치하지 않습니다.");
        }

        if (member.getTeamCategory() == null || !member.getTeamCategory().getId().equals(teamCategoryId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근하려는 카테고리가 사용자 정보와 일치하지 않습니다.");
        }

        // 통과했으면 해당 뷰로 이동 (또는 필요한 데이터 추가)
        model.addAttribute("teamName", team.getName());
        model.addAttribute("teamCategory", member.getTeamCategory().getName());
        return "administration/team/delivery/deliveryList"; // Thymeleaf 템플릿 이름
    }
    
    @GetMapping("/deliveryDetail/{teamId}/{teamCategoryId}")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE')")
    public String deliveryDetail(@PathVariable Long teamId,
                                   @PathVariable Long teamCategoryId,
                                   @AuthenticationPrincipal PrincipalDetails principalDetails,
                                   Model model) {

    	Member member = principalDetails.getMember();

        // 팀 존재 여부 확인
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 팀입니다."));

        // 팀 이름 검사
        if (!"배송팀".equals(team.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AS팀이 아닌 팀입니다.");
        }

        // 현재 로그인한 사용자의 팀 일치 여부 검사
        if (!member.getTeam().getId().equals(teamId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근하려는 팀이 사용자 팀과 일치하지 않습니다.");
        }

        if (member.getTeamCategory() == null || !member.getTeamCategory().getId().equals(teamCategoryId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근하려는 카테고리가 사용자 정보와 일치하지 않습니다.");
        }

        // 통과했으면 해당 뷰로 이동 (또는 필요한 데이터 추가)
        model.addAttribute("teamName", team.getName());
        model.addAttribute("teamCategory", member.getTeamCategory().getName());
        return "administration/team/delivery/deliveryDetail"; // Thymeleaf 템플릿 이름
    }
    
    @GetMapping("/productionListLegacy/{teamId}/{teamCategoryId}")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE')")
    public String productionListLegacy(@PathVariable Long teamId,
                                   @PathVariable Long teamCategoryId,
                                   @AuthenticationPrincipal PrincipalDetails principalDetails,
                                   Model model) {

    	Member member = principalDetails.getMember();

        // 팀 존재 여부 확인
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 팀입니다."));

        // 팀 이름 검사
        if (!"생산팀".equals(team.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AS팀이 아닌 팀입니다.");
        }

        // 현재 로그인한 사용자의 팀 일치 여부 검사
        if (!member.getTeam().getId().equals(teamId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근하려는 팀이 사용자 팀과 일치하지 않습니다.");
        }

        if (member.getTeamCategory() == null || !member.getTeamCategory().getId().equals(teamCategoryId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근하려는 카테고리가 사용자 정보와 일치하지 않습니다.");
        }

        // 통과했으면 해당 뷰로 이동 (또는 필요한 데이터 추가)
        model.addAttribute("teamName", team.getName());
        model.addAttribute("teamCategory", member.getTeamCategory().getName());
        return "administration/team/production/productionList"; // Thymeleaf 템플릿 이름
    }
    
    @GetMapping("/productionDetailLegacy/{teamId}/{teamCategoryId}")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE')")
    public String productionDetailLegacy(@PathVariable Long teamId,
                                   @PathVariable Long teamCategoryId,
                                   @AuthenticationPrincipal PrincipalDetails principalDetails,
                                   Model model) {

    	Member member = principalDetails.getMember();

        // 팀 존재 여부 확인
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 팀입니다."));

        // 팀 이름 검사
        if (!"생산팀".equals(team.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AS팀이 아닌 팀입니다.");
        }

        // 현재 로그인한 사용자의 팀 일치 여부 검사
        if (!member.getTeam().getId().equals(teamId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근하려는 팀이 사용자 팀과 일치하지 않습니다.");
        }

        if (member.getTeamCategory() == null || !member.getTeamCategory().getId().equals(teamCategoryId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근하려는 카테고리가 사용자 정보와 일치하지 않습니다.");
        }

        // 통과했으면 해당 뷰로 이동 (또는 필요한 데이터 추가)
        model.addAttribute("teamName", team.getName());
        model.addAttribute("teamCategory", member.getTeamCategory().getName());
        return "administration/team/production/productionDetail"; // Thymeleaf 템플릿 이름
    }
    
    @GetMapping("/productionListNew/{teamId}/{teamCategoryId}")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE')")
    public String productionListNew(@PathVariable Long teamId,
                                   @PathVariable Long teamCategoryId,
                                   @AuthenticationPrincipal PrincipalDetails principalDetails,
                                   Model model) {

    	Member member = principalDetails.getMember();

        // 팀 존재 여부 확인
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 팀입니다."));

        // 팀 이름 검사
        if (!"생산팀".equals(team.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AS팀이 아닌 팀입니다.");
        }

        // 현재 로그인한 사용자의 팀 일치 여부 검사
        if (!member.getTeam().getId().equals(teamId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근하려는 팀이 사용자 팀과 일치하지 않습니다.");
        }

        if (member.getTeamCategory() == null || !member.getTeamCategory().getId().equals(teamCategoryId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근하려는 카테고리가 사용자 정보와 일치하지 않습니다.");
        }

        // 통과했으면 해당 뷰로 이동 (또는 필요한 데이터 추가)
        model.addAttribute("teamName", team.getName());
        model.addAttribute("teamCategory", member.getTeamCategory().getName());
        return "administration/team/production/productionList"; // Thymeleaf 템플릿 이름
    }
    
    @GetMapping("/team/{teamId}/{teamCategoryId}")
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_EMPLOYEE')")
    public String accessTeamWithCategory(@PathVariable Long teamId,
                                         @PathVariable Long teamCategoryId,
                                         @AuthenticationPrincipal Member member) {

        if (!member.getTeam().getId().equals(teamId)) {
            throw new AccessDeniedException("팀 접근 권한이 없습니다.");
        }

        if (member.getTeamCategory() == null || !member.getTeamCategory().getId().equals(teamCategoryId)) {
            throw new AccessDeniedException("카테고리 접근 권한이 없습니다.");
        }

        return String.format("접속한 팀: %s (%d), 카테고리: %s (%d)",
                member.getTeam().getName(), teamId,
                member.getTeamCategory().getName(), teamCategoryId);
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
