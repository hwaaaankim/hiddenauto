package com.dev.HiddenBATHAuto.controller.page;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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

            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "requested") String dateType,
            @org.springframework.web.bind.annotation.RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false) AsStatus status,

            @org.springframework.web.bind.annotation.RequestParam(required = false) String companyKeyword,

            @org.springframework.web.bind.annotation.RequestParam(required = false) Long provinceId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long cityId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long districtId,

            @PageableDefault(size = 200) Pageable pageable,
            Model model
    ) {
        if (principal == null || principal.getMember() == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        Member member = principal.getMember();
        if (member.getTeam() == null || !"AS팀".equals(member.getTeam().getName())) {
            throw new AccessDeniedException("AS팀만 접근할 수 있습니다.");
        }

        AsStatus normalizedStatus = normalizeVisibleCalendarStatus(status);

        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime end = (endDate != null) ? endDate.plusDays(1).atStartOfDay() : null;

        Page<AsTaskCardDto> asPage = asTaskService.getAsTasksForCalendar(
                member,
                dateType,
                start,
                end,
                normalizedStatus,
                companyKeyword,
                provinceId,
                cityId,
                districtId,
                pageable
        );

        model.addAttribute("provinces", provinceRepository.findAll());
        model.addAttribute("asPage", asPage);

        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("dateType", dateType);
        model.addAttribute("selectedStatus", normalizedStatus);

        model.addAttribute("companyKeyword", companyKeyword);
        model.addAttribute("provinceId", provinceId);
        model.addAttribute("cityId", cityId);
        model.addAttribute("districtId", districtId);

        model.addAttribute("asStatusLabels", AsStatus.labelMap());

        return "administration/team/as/asManagement";
    }

    private AsStatus normalizeVisibleCalendarStatus(AsStatus status) {
        if (status == null) {
            return null;
        }
        if (status == AsStatus.IN_PROGRESS || status == AsStatus.COMPLETED) {
            return status;
        }
        return null;
    }
}