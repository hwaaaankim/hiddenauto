package com.dev.HiddenBATHAuto.controller.page;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.dev.HiddenBATHAuto.dto.as.AsTaskCardDto;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;
import com.dev.HiddenBATHAuto.service.as.AsTaskService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/team")
@PreAuthorize("hasRole('INTERNAL_EMPLOYEE')")
@RequiredArgsConstructor
public class AsMANAGEMENTController {

    private final AsTaskService asTaskService;
    private final ProvinceRepository provinceRepository;

    @GetMapping("/asManagement")
    public String asManagement(
            @AuthenticationPrincipal PrincipalDetails principal,

            @RequestParam(required = false, defaultValue = "requested") String dateType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) AsStatus status,

            @RequestParam(required = false) String companyKeyword,

            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) Long districtId,

            Pageable pageable,
            Model model
    ) {
        Member member = principal.getMember();
        if (member.getTeam() == null || !"AS팀".equals(member.getTeam().getName())) {
            throw new AccessDeniedException("AS팀만 접근할 수 있습니다.");
        }

        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime end = (endDate != null) ? endDate.plusDays(1).atStartOfDay() : null;

        // ✅ 오른쪽 카드 리스트 + 캘린더 표시에 필요한 정보까지 포함해서 조회
        Page<AsTaskCardDto> asPage = asTaskService.getAsTasksForCalendar(
                member, dateType, start, end, status,
                companyKeyword, provinceId, cityId, districtId,
                pageable
        );

        model.addAttribute("provinces", provinceRepository.findAll());
        model.addAttribute("asPage", asPage);

        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("dateType", dateType);
        model.addAttribute("selectedStatus", status);

        model.addAttribute("companyKeyword", companyKeyword);
        model.addAttribute("provinceId", provinceId);
        model.addAttribute("cityId", cityId);
        model.addAttribute("districtId", districtId);

        return "administration/team/as/asManagement";
    }
}





















