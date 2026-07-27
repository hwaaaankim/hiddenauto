package com.dev.HiddenBATHAuto.controller.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

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

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final DeliveryStatementExcelService deliveryStatementExcelService;
    private final DeliveryStatementLayoutService deliveryStatementLayoutService;

    /**
     * 기존 현장/택배 명세서 ZIP 다운로드 API입니다.
     * 관리자 화면 등 기존 호출부 보호를 위해 경로와 동작을 그대로 유지합니다.
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
            String filename = prefix + "_" + LocalDate.now() + ".zip";

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
     * 출고팀의 가로형/세로형 바로 출력용 데이터 API입니다.
     *
     * 배송수단 필터는 적용하지 않습니다.
     * - 배송수단명에 '택배'가 포함된 주문: 기존 택배명세서 항목 체계
     * - 그 외 모든 배송수단: 기존 현장명세서 항목 체계
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
     * 출고팀의 가로형/세로형 A4 2분할 엑셀 다운로드 API입니다.
     * 한 개의 XLSX 안에 실제 출력 페이지별로 시트를 생성합니다.
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
            String layoutLabel = DeliveryStatementLayoutService.LAYOUT_HORIZONTAL.equals(layoutType)
                    ? "가로형"
                    : "세로형";
            String filename = layoutLabel + "명세서_" + LocalDate.now() + ".xlsx";

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

    private Member resolveLoginMember(PrincipalDetails principal) {
        if (principal == null || principal.getMember() == null) {
            throw new AccessDeniedException("로그인 사용자 정보를 확인할 수 없습니다.");
        }

        return principal.getMember();
    }
}
