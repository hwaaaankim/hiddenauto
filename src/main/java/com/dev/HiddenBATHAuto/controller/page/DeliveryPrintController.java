package com.dev.HiddenBATHAuto.controller.page;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.dev.HiddenBATHAuto.dto.delivery.route.DeliveryRouteDtos.PrintRow;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryRouteService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/team/deliveryPrint")
@PreAuthorize("hasRole('INTERNAL_EMPLOYEE')")
@RequiredArgsConstructor
public class DeliveryPrintController {

    private static final int MAX_PRINT_ORDER_COUNT = 1000;
    private static final DateTimeFormatter GENERATED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DeliveryRouteService deliveryRouteService;

    /**
     * URL 직접 접근 및 구버전 JS 호환용 GET입니다.
     */
    @GetMapping
    public String printDeliveryListByGet(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam(required = false) String orderIds,
            Model model
    ) {
        return renderPrintPage(
                requireLoginMember(principal),
                deliveryDate,
                parseOrderIds(orderIds),
                model
        );
    }

    /**
     * 기존 배송리스트의 인쇄 버튼이 사용하는 방식입니다.
     * 주문 수가 많아도 URL 길이 제한을 받지 않도록 form POST로 전달합니다.
     */
    @PostMapping
    public String printDeliveryListByPost(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam("orderIds") List<Long> orderIds,
            Model model
    ) {
        return renderPrintPage(
                requireLoginMember(principal),
                deliveryDate,
                normalizeOrderIds(orderIds),
                model
        );
    }

    private String renderPrintPage(
            Member loginMember,
            LocalDate deliveryDate,
            List<Long> orderedOrderIds,
            Model model
    ) {
        LocalDate selectedDate = deliveryDate == null ? LocalDate.now() : deliveryDate;
        List<PrintRow> rows = deliveryRouteService.getPrintRows(
                loginMember,
                selectedDate,
                orderedOrderIds
        );

        model.addAttribute("deliveryDate", selectedDate);
        model.addAttribute("handlerName", resolveMemberName(loginMember));
        model.addAttribute("rows", rows);
        model.addAttribute("rowCount", rows.size());
        model.addAttribute("generatedAt", LocalDateTime.now().format(GENERATED_AT_FORMATTER));

        return "administration/team/delivery/deliveryPrint";
    }

    private List<Long> parseOrderIds(String orderIds) {
        if (!StringUtils.hasText(orderIds)) {
            throw new IllegalArgumentException("인쇄할 주문 ID가 없습니다.");
        }

        LinkedHashSet<Long> parsed = new LinkedHashSet<>();

        for (String token : Arrays.asList(orderIds.split(","))) {
            if (!StringUtils.hasText(token)) {
                continue;
            }

            try {
                long value = Long.parseLong(token.trim());

                if (value > 0) {
                    parsed.add(value);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("올바르지 않은 주문 ID가 포함되어 있습니다: " + token);
            }
        }

        return validateCountAndCopy(parsed);
    }

    private List<Long> normalizeOrderIds(List<Long> orderIds) {
        LinkedHashSet<Long> normalized = orderIds == null
                ? new LinkedHashSet<>()
                : orderIds.stream()
                        .filter(Objects::nonNull)
                        .filter(orderId -> orderId > 0)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return validateCountAndCopy(normalized);
    }

    private List<Long> validateCountAndCopy(LinkedHashSet<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new IllegalArgumentException("인쇄할 주문 ID가 없습니다.");
        }

        if (orderIds.size() > MAX_PRINT_ORDER_COUNT) {
            throw new IllegalArgumentException(
                    "한 번에 인쇄할 수 있는 주문은 최대 " + MAX_PRINT_ORDER_COUNT + "건입니다."
            );
        }

        return List.copyOf(orderIds);
    }

    private Member requireLoginMember(PrincipalDetails principal) {
        if (principal == null || principal.getMember() == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        return principal.getMember();
    }

    private String resolveMemberName(Member member) {
        if (member == null) {
            return "-";
        }

        if (StringUtils.hasText(member.getName())) {
            return member.getName().trim();
        }

        return StringUtils.hasText(member.getUsername()) ? member.getUsername().trim() : "-";
    }
}
