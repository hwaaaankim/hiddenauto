package com.dev.HiddenBATHAuto.controller.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.delivery.DeliveryStatementExcelRequest;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryStatementLayoutDtos.LayoutRequest;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryStatementLayoutDtos.LayoutResponse;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryStatementExcelService;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryStatementLayoutService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/internal/delivery-statement")
@RequiredArgsConstructor
public class DeliveryStatementExcelController {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final DeliveryStatementExcelService deliveryStatementExcelService;
    private final DeliveryStatementLayoutService deliveryStatementLayoutService;

    /**
     * 기존 현장/택배 템플릿 ZIP 다운로드 API입니다.
     * 다른 관리자 화면의 기존 호출부 보호를 위해 경로와 기존 동작을 유지합니다.
     */
    @PostMapping("/excel")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGEMENT', 'ROLE_INTERNAL_EMPLOYEE')")
    public ResponseEntity<?> downloadStatementExcel(
            @RequestBody DeliveryStatementExcelRequest request,
            @AuthenticationPrincipal PrincipalDetails principal
    ) {
        try {
            Member loginMember = resolveLoginMember(principal);
            byte[] bytes = deliveryStatementExcelService.buildZip(request, loginMember);

            String normalizedType = deliveryStatementExcelService.normalizeStatementType(
                    request != null ? request.getStatementType() : null
            );

            String prefix = "SITE".equals(normalizedType) ? "현장명세서" : "택배명세서";
            String filename = prefix + "_" + LocalDate.now(KOREA_ZONE) + ".zip";

            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(filename, StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    e.getMessage() != null ? e.getMessage() : "명세서 ZIP 생성 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * 출고팀 바로 출력용 데이터 API입니다.
     *
     * SITE   : 배송수단이 현장배송, 화물, 방문인 선택 주문만 Task 단위로 출력
     * PARCEL : 배송수단이 택배인 선택 주문만 Task 단위로 출력
     */
    @PostMapping("/layout/data")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGEMENT', 'ROLE_INTERNAL_EMPLOYEE')")
    public ResponseEntity<?> getLayoutStatementData(
            @RequestBody LayoutRequest request,
            @AuthenticationPrincipal PrincipalDetails principal
    ) {
        try {
            Member loginMember = resolveLoginMember(principal);
            LayoutResponse response = deliveryStatementLayoutService.buildLayoutResponse(
                    request,
                    loginMember
            );

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    e.getMessage() != null ? e.getMessage() : "명세서 출력 데이터 생성 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * 배송팀 업체별 배송 화면 전용 바로 출력 데이터 API입니다.
     *
     * 출고팀의 선택형 API와 분리하여 주문 ID가 아니라 조회 날짜를 받습니다.
     * 해당 날짜의 전체 주문을 조회한 뒤 SITE/PARCEL 배송수단 기준으로 필터링합니다.
     */
    @PostMapping("/layout/date/data")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGEMENT', 'ROLE_INTERNAL_EMPLOYEE')")
    public ResponseEntity<?> getDateLayoutStatementData(
            @RequestBody DeliveryDateLayoutRequest request,
            @AuthenticationPrincipal PrincipalDetails principal
    ) {
        try {
            Member loginMember = resolveLoginMember(principal);
            validateDateLayoutRequest(request);

            LayoutResponse response = deliveryStatementLayoutService.buildLayoutResponseForDeliveryDate(
                    request.deliveryDate(),
                    request.layoutType(),
                    request.statementType(),
                    loginMember
            );

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    e.getMessage() != null
                            ? e.getMessage()
                            : "날짜 전체 명세서 출력 데이터 생성 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * 출고팀 A4 2분할 명세서 XLSX 다운로드 API입니다.
     * 가로형은 A4 가로 중앙 절취, 세로형은 A4 세로 중앙 절취로 생성합니다.
     */
    @PostMapping("/layout/excel")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGEMENT', 'ROLE_INTERNAL_EMPLOYEE')")
    public ResponseEntity<?> downloadLayoutStatementExcel(
            @RequestBody LayoutRequest request,
            @AuthenticationPrincipal PrincipalDetails principal
    ) {
        try {
            Member loginMember = resolveLoginMember(principal);
            byte[] bytes = deliveryStatementLayoutService.buildLayoutExcel(
                    request,
                    loginMember
            );

            String layoutType = deliveryStatementLayoutService.normalizeLayoutType(
                    request != null ? request.getLayoutType() : null
            );
            String statementType = deliveryStatementLayoutService.normalizeStatementType(
                    request != null ? request.getStatementType() : null
            );
            String statementLabel = deliveryStatementLayoutService.statementTypeLabel(statementType);
            String layoutLabel = DeliveryStatementLayoutService.LAYOUT_HORIZONTAL.equals(layoutType)
                    ? "가로형"
                    : "세로형";
            String filename = statementLabel + "_" + layoutLabel + "_" + LocalDate.now(KOREA_ZONE) + ".xlsx";

            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(filename, StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .contentType(XLSX_MEDIA_TYPE)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    e.getMessage() != null ? e.getMessage() : "명세서 엑셀 생성 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * 배송팀 업체별 배송 화면 전용 날짜 전체 XLSX 다운로드 API입니다.
     * 조회 날짜의 모든 업체를 명세서 종류에 맞춰 한 XLSX의 여러 시트로 생성합니다.
     */
    @PostMapping("/layout/date/excel")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGEMENT', 'ROLE_INTERNAL_EMPLOYEE')")
    public ResponseEntity<?> downloadDateLayoutStatementExcel(
            @RequestBody DeliveryDateLayoutRequest request,
            @AuthenticationPrincipal PrincipalDetails principal
    ) {
        try {
            Member loginMember = resolveLoginMember(principal);
            validateDateLayoutRequest(request);

            byte[] bytes = deliveryStatementLayoutService.buildLayoutExcelForDeliveryDate(
                    request.deliveryDate(),
                    request.layoutType(),
                    request.statementType(),
                    loginMember
            );

            String layoutType = deliveryStatementLayoutService.normalizeLayoutType(
                    request.layoutType()
            );
            String statementType = deliveryStatementLayoutService.normalizeStatementType(
                    request.statementType()
            );
            String statementLabel = deliveryStatementLayoutService.statementTypeLabel(statementType);
            String layoutLabel = DeliveryStatementLayoutService.LAYOUT_HORIZONTAL.equals(layoutType)
                    ? "가로형"
                    : "세로형";
            String filename = statementLabel
                    + "_"
                    + layoutLabel
                    + "_"
                    + request.deliveryDate()
                    + ".xlsx";

            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(filename, StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .contentType(XLSX_MEDIA_TYPE)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    e.getMessage() != null
                            ? e.getMessage()
                            : "날짜 전체 명세서 엑셀 생성 중 오류가 발생했습니다."
            );
        }
    }

    private void validateDateLayoutRequest(DeliveryDateLayoutRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("날짜 전체 명세서 요청이 없습니다.");
        }

        if (request.deliveryDate() == null) {
            throw new IllegalArgumentException("명세서를 출력할 배송일이 없습니다.");
        }
    }

    private Member resolveLoginMember(PrincipalDetails principal) {
        if (principal == null || principal.getMember() == null) {
            throw new AccessDeniedException("로그인 사용자 정보를 확인할 수 없습니다.");
        }

        return principal.getMember();
    }

    public record DeliveryDateLayoutRequest(
            LocalDate deliveryDate,
            String layoutType,
            String statementType
    ) {
    }
}
