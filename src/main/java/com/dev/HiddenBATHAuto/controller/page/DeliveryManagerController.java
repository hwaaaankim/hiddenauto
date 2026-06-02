package com.dev.HiddenBATHAuto.controller.page;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.dev.HiddenBATHAuto.dto.delivery.DeliveryManagerRowDto;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryManagerSearchCondition;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.delivery.DeliveryManagerService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/team")
@PreAuthorize("hasRole('INTERNAL_EMPLOYEE')")
@RequiredArgsConstructor
public class DeliveryManagerController {

    private final DeliveryManagerService deliveryManagerService;

    @GetMapping("/deliveryManager")
    public String deliveryManager(
            @AuthenticationPrincipal PrincipalDetails principal,

            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "0") int page,

            @RequestParam(required = false) String searchType,
            @RequestParam(required = false) String keyword,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,

            @RequestParam(required = false) String sortKey,
            @RequestParam(required = false) String sortDir,

            Model model
    ) {
        if (principal == null || principal.getMember() == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        Member loginMember = principal.getMember();

        if (loginMember.getTeam() == null || !"배송팀".equals(loginMember.getTeam().getName())) {
            throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
        }

        if (size != 10 && size != 30 && size != 50 && size != 70 && size != 100) {
            size = 10;
        }

        if (page < 0) {
            page = 0;
        }

        LocalDate tomorrow = LocalDate.now().plusDays(1);

        if (fromDate == null) {
            fromDate = tomorrow;
        }

        if (toDate == null) {
            toDate = tomorrow;
        }

        if (toDate.isBefore(fromDate)) {
            toDate = fromDate;
        }

        String normalizedSortKey = StringUtils.hasText(sortKey) ? sortKey.trim() : "orderIndex";
        String normalizedSortDir = StringUtils.hasText(sortDir) ? sortDir.trim().toUpperCase() : "ASC";

        if (!"ASC".equals(normalizedSortDir) && !"DESC".equals(normalizedSortDir)) {
            normalizedSortDir = "ASC";
        }

        DeliveryManagerSearchCondition condition = DeliveryManagerSearchCondition.builder()
                .page(page)
                .size(size)
                .searchType(searchType)
                .keyword(keyword)
                .fromDate(fromDate)
                .toDate(toDate)
                .sortKey(normalizedSortKey)
                .sortDir(normalizedSortDir)
                .build();

        Page<DeliveryManagerRowDto> deliveryPage =
                deliveryManagerService.getMyDeliveryManagerPage(loginMember, condition);

        int totalPages = deliveryPage.getTotalPages();

        int startPage = totalPages == 0 ? 0 : Math.max(0, page - 2);
        int endPage = totalPages == 0 ? 0 : Math.min(totalPages - 1, page + 2);

        model.addAttribute("deliveryPage", deliveryPage);

        model.addAttribute("size", size);
        model.addAttribute("page", page);

        model.addAttribute("searchType", searchType);
        model.addAttribute("keyword", keyword);

        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);

        model.addAttribute("sortKey", normalizedSortKey);
        model.addAttribute("sortDir", normalizedSortDir);

        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);

        return "administration/team/delivery/deliveryManager";
    }
}