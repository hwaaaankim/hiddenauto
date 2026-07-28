package com.dev.HiddenBATHAuto.controller.page;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.delivery.DeliveryStatementLayoutDtos.LayoutResponse;
import com.dev.HiddenBATHAuto.dto.delivery.route.DeliveryRouteDtos.Page;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.order.DeliveryCompletionService;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryRouteService;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryTeamSiteStatementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/team/deliveryRoute")
@PreAuthorize("hasRole('INTERNAL_EMPLOYEE')")
@RequiredArgsConstructor
public class DeliveryRouteController {

    private final DeliveryRouteService deliveryRouteService;
    private final DeliveryCompletionService deliveryCompletionService;
    private final DeliveryTeamSiteStatementService deliveryTeamSiteStatementService;

    @GetMapping
    public String getDeliveryRoutePage(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            Model model
    ) {
        Member loginMember = requireLoginMember(principal);
        LocalDate selectedDate = deliveryDate == null ? LocalDate.now() : deliveryDate;
        Page routePage = deliveryRouteService.getRoutePage(loginMember, selectedDate);

        model.addAttribute("routePage", routePage);
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("previousDate", selectedDate.minusDays(1));
        model.addAttribute("nextDate", selectedDate.plusDays(1));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isToday", selectedDate.equals(LocalDate.now()));
        model.addAttribute(
                "isDeliveryTeamStatementLeader",
                deliveryTeamSiteStatementService.isTeamStatementLeader(loginMember)
        );

        return "administration/team/delivery/deliveryRoute";
    }

    /**
     * 업체별 배송 화면에서 현재 조회된 주문을 화면 표시 순서대로 엑셀로 내려받습니다.
     * deliveryList.html의 일반 배송리스트 엑셀과 동일한 DeliveryExcelService를 사용하므로
     * 디자인, 열 구성, A4 가로 레이아웃과 출력 항목이 동일합니다.
     * 반품/회수용 음수 수량도 숫자 셀로 그대로 보존합니다.
     */
    @PostMapping("/excel")
    @ResponseBody
    public ResponseEntity<?> downloadRouteExcel(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody(required = false) DeliveryRouteExcelRequest request
    ) {
        try {
            Member loginMember = requireLoginMember(principal);

            if (request == null) {
                throw new IllegalArgumentException("엑셀 출력 요청이 없습니다.");
            }

            if (request.deliveryHandlerId() != null
                    && !request.deliveryHandlerId().equals(loginMember.getId())) {
                throw new AccessDeniedException("현재 로그인한 배송 담당자의 데이터만 출력할 수 있습니다.");
            }

            LocalDate fromDate = request.fromDate();
            LocalDate toDate = request.toDate();

            if (fromDate == null && toDate == null) {
                throw new IllegalArgumentException("엑셀로 출력할 배송일이 없습니다.");
            }

            LocalDate deliveryDate = fromDate != null ? fromDate : toDate;

            if (fromDate != null && toDate != null && !fromDate.equals(toDate)) {
                throw new IllegalArgumentException("업체별 배송 화면 엑셀은 같은 날짜 범위만 출력할 수 있습니다.");
            }

            /*
             * DeliveryRouteService.createRouteExcel 내부에서 기존 deliveryList.html과
             * 동일한 DeliveryExcelService를 사용합니다.
             */
            byte[] excelBytes = deliveryRouteService.createRouteExcel(
                    loginMember,
                    deliveryDate,
                    request.orderedOrderIds()
            );

            String filename = "배송리스트_" + deliveryDate + ".xlsx";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(filename, StandardCharsets.UTF_8)
                    .build());
            headers.setContentLength(excelBytes.length);

            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);

        } catch (AccessDeniedException e) {
            return errorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("업체별 배송 엑셀 생성 중 오류가 발생했습니다.", e);
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage() != null ? e.getMessage() : "엑셀 생성 중 오류가 발생했습니다."
            );
        }
    }


    /**
     * 배송팀 팀장 전용 현장명세서 프리뷰입니다.
     * 활성 배송팀 멤버를 member.id 오름차순으로 반환하며,
     * 각 멤버별 묶음 수와 주문 수를 실제 출력과 동일한 기준으로 계산합니다.
     */
    @PostMapping("/team-site-statement/preview")
    @ResponseBody
    public ResponseEntity<?> previewDeliveryTeamSiteStatement(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody(required = false) DeliveryTeamSiteStatementRequest request
    ) {
        try {
            Member loginMember = requireLoginMember(principal);
            validateTeamSiteStatementRequest(request);

            return ResponseEntity.ok(
                    deliveryTeamSiteStatementService.buildPreview(
                            loginMember,
                            request.deliveryDate()
                    )
            );
        } catch (AccessDeniedException e) {
            return errorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("배송팀 전체 현장명세서 프리뷰 생성 중 오류가 발생했습니다.", e);
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage() != null
                            ? e.getMessage()
                            : "배송팀 전체 현장명세서 프리뷰 생성 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * deli001 배송팀장 전용 배송팀 전체 현장명세서 출력 데이터입니다.
     *
     * 대상 멤버:
     * - team.name = 배송팀
     * - enabled = true
     * - member.id 오름차순
     *
     * 대상 주문:
     * - 선택 날짜의 DeliveryOrderIndex에 등록된 현장배송/화물 주문
     * - 담당자별로 분리한 뒤 업체/실제 배송지/배송수단/배송일 기준으로 묶습니다.
     */
    @PostMapping("/team-site-statement/data")
    @ResponseBody
    public ResponseEntity<?> buildDeliveryTeamSiteStatementData(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody(required = false) DeliveryTeamSiteStatementRequest request
    ) {
        try {
            Member loginMember = requireLoginMember(principal);
            validateTeamSiteStatementRequest(request);

            LayoutResponse response = deliveryTeamSiteStatementService.buildLayoutResponse(
                    loginMember,
                    request.deliveryDate(),
                    request.layoutType()
            );

            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return errorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("배송팀 전체 현장명세서 출력 데이터 생성 중 오류가 발생했습니다.", e);
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage() != null
                            ? e.getMessage()
                            : "배송팀 전체 현장명세서 출력 데이터 생성 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * deli001 배송팀장 전용 배송팀 전체 현장명세서 엑셀 다운로드입니다.
     */
    @PostMapping("/team-site-statement/excel")
    @ResponseBody
    public ResponseEntity<?> downloadDeliveryTeamSiteStatementExcel(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody(required = false) DeliveryTeamSiteStatementRequest request
    ) {
        try {
            Member loginMember = requireLoginMember(principal);
            validateTeamSiteStatementRequest(request);

            String normalizedLayoutType = deliveryTeamSiteStatementService.normalizeLayoutType(
                    request.layoutType()
            );

            byte[] excelBytes = deliveryTeamSiteStatementService.buildLayoutExcel(
                    loginMember,
                    request.deliveryDate(),
                    normalizedLayoutType
            );

            String layoutLabel = "HORIZONTAL".equals(normalizedLayoutType)
                    ? "가로형"
                    : "세로형";
            String filename = "배송팀현장명세서_"
                    + layoutLabel
                    + "_"
                    + request.deliveryDate()
                    + ".xlsx";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(filename, StandardCharsets.UTF_8)
                    .build());
            headers.setContentLength(excelBytes.length);

            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
        } catch (AccessDeniedException e) {
            return errorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("배송팀 전체 현장명세서 엑셀 생성 중 오류가 발생했습니다.", e);
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage() != null
                            ? e.getMessage()
                            : "배송팀 전체 현장명세서 엑셀 생성 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * 업체별 오늘 배송 화면에서 같은 묶음의 선택 주문을 한 번에 배송완료 처리합니다.
     * 업로드한 모든 이미지는 선택된 모든 주문에 각각 독립 파일/OrderImage로 저장됩니다.
     *
     * 완료 직후 페이지 전체 새로고침 없이 화면을 정확히 재배치할 수 있도록
     * 처리된 묶음의 최신 완료 상태도 함께 반환합니다.
     */
    @PostMapping("/complete")
    @ResponseBody
    public ResponseEntity<?> completeSelectedRouteOrders(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam("deliveryDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam("orderIds") List<Long> orderIds,
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ) {
        try {
            Member loginMember = requireLoginMember(principal);

            List<Long> completedOrderIds = deliveryCompletionService.completeRouteSelection(
                    loginMember,
                    deliveryDate,
                    orderIds,
                    files
            );

            int uploadedImageCount = countValidImageFiles(files);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("completedOrderIds", completedOrderIds);
            body.put("completedCount", completedOrderIds.size());
            body.put("uploadedImageCount", uploadedImageCount);
            body.put("message", completedOrderIds.size() + "건을 배송완료 처리했습니다.");

            try {
                deliveryRouteService.findCompletionSnapshot(
                        loginMember,
                        deliveryDate,
                        completedOrderIds
                ).ifPresent(snapshot -> body.put("completionSnapshot", snapshot));
            } catch (RuntimeException snapshotException) {
                /*
                 * 배송완료 트랜잭션은 이미 정상 커밋되었습니다.
                 * 화면 보조 스냅샷 조회 실패를 완료 API 실패로 응답하면 사용자가 재시도하여
                 * "이미 배송완료" 오류를 보게 되므로 성공 응답은 유지하고 프론트 기본 갱신으로 대체합니다.
                 */
                log.warn(
                        "배송완료 후 화면 갱신 스냅샷 조회에 실패했습니다. deliveryDate={}, orderIds={}",
                        deliveryDate,
                        completedOrderIds,
                        snapshotException
                );
                body.put("requiresReload", true);
            }

            return ResponseEntity.ok(body);

        } catch (AccessDeniedException e) {
            return errorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage() != null ? e.getMessage() : "배송완료 처리 중 오류가 발생했습니다."
            );
        }
    }

    private void validateTeamSiteStatementRequest(DeliveryTeamSiteStatementRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("배송팀 현장명세서 요청이 없습니다.");
        }

        if (request.deliveryDate() == null) {
            throw new IllegalArgumentException("배송팀 현장명세서로 출력할 배송일이 없습니다.");
        }

        if (request.layoutType() == null || request.layoutType().isBlank()) {
            throw new IllegalArgumentException("배송팀 현장명세서 레이아웃 구분이 없습니다.");
        }
    }

    private int countValidImageFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return 0;
        }

        return (int) files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .filter(file -> file.getContentType() != null)
                .filter(file -> file.getContentType().toLowerCase(Locale.ROOT).startsWith("image/"))
                .count();
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message != null ? message : "요청 처리 중 오류가 발생했습니다.");
        return ResponseEntity.status(status).body(body);
    }

    private Member requireLoginMember(PrincipalDetails principal) {
        if (principal == null || principal.getMember() == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        Member member = principal.getMember();

        if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
            throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
        }

        return member;
    }

    public record DeliveryTeamSiteStatementRequest(
            LocalDate deliveryDate,
            String layoutType
    ) {
    }

    public record DeliveryRouteExcelRequest(
            Long deliveryHandlerId,
            LocalDate fromDate,
            LocalDate toDate,
            List<Long> orderedOrderIds
    ) {
        public DeliveryRouteExcelRequest {
            orderedOrderIds = orderedOrderIds == null
                    ? List.of()
                    : orderedOrderIds.stream()
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
        }
    }
}
