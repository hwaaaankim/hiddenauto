package com.dev.HiddenBATHAuto.controller.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.delivery.DeliveryStatementExcelRequest;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryStatementExcelService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/internal/delivery-statement")
@RequiredArgsConstructor
public class DeliveryStatementExcelController {

    private final DeliveryStatementExcelService deliveryStatementExcelService;

    @PostMapping("/excel")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGEMENT', 'ROLE_INTERNAL_EMPLOYEE')")
    public ResponseEntity<?> downloadStatementExcel(
            @RequestBody DeliveryStatementExcelRequest request,
            @AuthenticationPrincipal PrincipalDetails principal
    ) {
        try {
            Member loginMember = principal != null ? principal.getMember() : null;
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
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    e.getMessage() != null ? e.getMessage() : "명세서 ZIP 생성 중 오류가 발생했습니다."
            );
        }
    }
}
