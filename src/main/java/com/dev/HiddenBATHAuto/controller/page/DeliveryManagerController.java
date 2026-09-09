package com.dev.HiddenBATHAuto.controller.page;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.dev.HiddenBATHAuto.dto.delivery.DeliveryManagerRowDto;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryManagerSearchCondition;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryStatementLayoutDtos.LayoutResponse;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.service.delivery.DeliveryManagerService;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryManagerStatementService;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryTeamSiteStatementService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/team")
@PreAuthorize("hasAnyRole('MANAGEMENT', 'INTERNAL_EMPLOYEE') and principal.teamName == '배송팀'")
@RequiredArgsConstructor
public class DeliveryManagerController {

    private final DeliveryManagerService deliveryManagerService;
    private final DeliveryManagerStatementService deliveryManagerStatementService;
    private final DeliveryTeamSiteStatementService deliveryTeamSiteStatementService;

    @GetMapping("/deliveryManager")
    public String deliveryManager(
            @AuthenticationPrincipal PrincipalDetails principal,

            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "0") int page,

            @RequestParam(required = false) String searchType,
            @RequestParam(required = false) String keyword,

            @RequestParam(required = false) Long deliveryMethodId,

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
                .deliveryMethodId(deliveryMethodId)
                .fromDate(fromDate)
                .toDate(toDate)
                .sortKey(normalizedSortKey)
                .sortDir(normalizedSortDir)
                .build();

        Page<DeliveryManagerRowDto> deliveryPage =
                deliveryManagerService.getMyDeliveryManagerPage(loginMember, condition);

        List<DeliveryMethod> deliveryMethods = deliveryManagerService.getDeliveryMethodsForFilter();

        int totalPages = deliveryPage.getTotalPages();

        int startPage = totalPages == 0 ? 0 : Math.max(0, page - 2);
        int endPage = totalPages == 0 ? 0 : Math.min(totalPages - 1, page + 2);

        model.addAttribute("deliveryPage", deliveryPage);
        model.addAttribute("deliveryMethods", deliveryMethods);

        model.addAttribute("size", size);
        model.addAttribute("page", page);

        model.addAttribute("searchType", searchType);
        model.addAttribute("keyword", keyword);
        model.addAttribute("deliveryMethodId", deliveryMethodId);

        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);

        model.addAttribute("sortKey", normalizedSortKey);
        model.addAttribute("sortDir", normalizedSortDir);

        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);

        model.addAttribute("deliveryHandlerId", loginMember.getId());
        model.addAttribute(
                "isDeliveryTeamStatementManager",
                deliveryTeamSiteStatementService.isTeamStatementManager(loginMember)
        );

        return "administration/team/delivery/deliveryManager";
    }

    /**
     * 배송관리 체크 선택 현장명세서 사전 검증입니다.
     * 비대상 주문은 실패시키지 않고 Order ID별 제외 사유를 반환합니다.
     */
    @PostMapping("/deliveryManager/site-statement/preview")
    @ResponseBody
    public ResponseEntity<?> previewSelectedSiteStatement(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody(required = false) DeliveryManagerStatementRequest request
    ) {
        try {
            Member loginMember = requireDeliveryTeamMember(principal);
            validateStatementRequest(request, false);

            return ResponseEntity.ok(
                    deliveryManagerStatementService.preview(
                            loginMember,
                            request.orderIds()
                    )
            );
        } catch (AccessDeniedException e) {
            return errorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage() != null
                            ? e.getMessage()
                            : "현장명세서 대상 확인 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * 배송관리 체크 선택 현장명세서 A4 출력 데이터입니다.
     * 선택값을 서버에서 다시 검증하며 현장배송/화물만 포함합니다.
     */
    @PostMapping("/deliveryManager/site-statement/data")
    @ResponseBody
    public ResponseEntity<?> buildSelectedSiteStatementData(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody(required = false) DeliveryManagerStatementRequest request
    ) {
        try {
            Member loginMember = requireDeliveryTeamMember(principal);
            validateStatementRequest(request, true);

            LayoutResponse response = deliveryManagerStatementService.buildLayoutResponse(
                    loginMember,
                    request.orderIds(),
                    request.layoutType()
            );

            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return errorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage() != null
                            ? e.getMessage()
                            : "현장명세서 출력 데이터 생성 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * 배송관리 체크 선택 현장명세서 XLSX 다운로드입니다.
     */
    @PostMapping("/deliveryManager/site-statement/excel")
    @ResponseBody
    public ResponseEntity<?> downloadSelectedSiteStatementExcel(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody(required = false) DeliveryManagerStatementRequest request
    ) {
        try {
            Member loginMember = requireDeliveryTeamMember(principal);
            validateStatementRequest(request, true);

            byte[] bytes = deliveryManagerStatementService.buildLayoutExcel(
                    loginMember,
                    request.orderIds(),
                    request.layoutType()
            );

            String filename = "현장명세서_가로형_선택건.xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(filename, StandardCharsets.UTF_8)
                    .build());
            headers.setContentLength(bytes.length);

            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (AccessDeniedException e) {
            return errorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage() != null
                            ? e.getMessage()
                            : "현장명세서 엑셀 생성 중 오류가 발생했습니다."
            );
        }
    }

    private void validateStatementRequest(
            DeliveryManagerStatementRequest request,
            boolean requireLayoutType
    ) {
        if (request == null) {
            throw new IllegalArgumentException("현장명세서 요청이 없습니다.");
        }

        if (request.orderIds() == null || request.orderIds().isEmpty()) {
            throw new IllegalArgumentException("현장명세서로 출력할 배송건을 하나 이상 선택해 주세요.");
        }

        if (requireLayoutType
                && (request.layoutType() == null || request.layoutType().isBlank())) {
            throw new IllegalArgumentException("현장명세서 레이아웃 구분이 없습니다.");
        }
    }

    private Member requireDeliveryTeamMember(PrincipalDetails principal) {
        if (principal == null || principal.getMember() == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        Member member = principal.getMember();
        if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
            throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
        }

        return member;
    }

    private ResponseEntity<Map<String, Object>> errorResponse(
            HttpStatus status,
            String message
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message != null ? message : "요청 처리 중 오류가 발생했습니다.");
        return ResponseEntity.status(status).body(body);
    }

    public record DeliveryManagerStatementRequest(
            List<Long> orderIds,
            String layoutType
    ) {
    }
}
