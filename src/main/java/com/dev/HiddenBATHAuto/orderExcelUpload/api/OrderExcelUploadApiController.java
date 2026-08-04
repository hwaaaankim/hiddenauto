package com.dev.HiddenBATHAuto.orderExcelUpload.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelAddressValidationRequest;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelAddressValidationResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelCompanyAddressLookupResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelDeliveryHandlerAssignmentRequest;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelDeliveryHandlerAssignmentResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelErrorResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelLookupOptionsResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelPreviewResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelSaveRequest;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelSaveResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.service.OrderExcelUploadLookupService;
import com.dev.HiddenBATHAuto.orderExcelUpload.service.OrderExcelUploadService;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.OrderExcelUploadValidationException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/management/api/order-excel-upload")
@RequiredArgsConstructor
public class OrderExcelUploadApiController {

    private final OrderExcelUploadService uploadService;
    private final OrderExcelUploadLookupService lookupService;

    @GetMapping("/options")
    public OrderExcelLookupOptionsResponse options() {
        return lookupService.getOptions();
    }

    @GetMapping("/company-addresses")
    public ResponseEntity<?> companyAddresses(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String businessNumber,
            @RequestParam(required = false) String companyName
    ) {
        try {
            OrderExcelCompanyAddressLookupResponse response = lookupService.getCompanyAddresses(
                    companyId,
                    businessNumber,
                    companyName
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new OrderExcelErrorResponse(false, e.getMessage(), List.of()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    new OrderExcelErrorResponse(false, "등록주소지 조회 중 오류가 발생했습니다.", List.of())
            );
        }
    }

    @PostMapping(value = "/address/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> validateAddress(@RequestBody OrderExcelAddressValidationRequest request) {
        try {
            OrderExcelAddressValidationResponse response = uploadService.validateAddress(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new OrderExcelErrorResponse(false, e.getMessage(), List.of()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    new OrderExcelErrorResponse(false, "주소 검증 중 오류가 발생했습니다.", List.of())
            );
        }
    }

    @PostMapping(value = "/delivery-handler/auto-assign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> autoAssignDeliveryHandler(
            @RequestBody OrderExcelDeliveryHandlerAssignmentRequest request
    ) {
        try {
            OrderExcelDeliveryHandlerAssignmentResponse response = uploadService.autoAssignDeliveryHandler(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new OrderExcelErrorResponse(false, e.getMessage(), List.of()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    new OrderExcelErrorResponse(false, "배송 담당자 자동배정 중 오류가 발생했습니다.", List.of())
            );
        }
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long directDeliveryMethodId,
            @RequestParam(required = false) Long siteDeliveryMethodId
    ) {
        try {
            OrderExcelPreviewResponse response = uploadService.preview(file, directDeliveryMethodId, siteDeliveryMethodId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new OrderExcelErrorResponse(false, e.getMessage(), java.util.List.of()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new OrderExcelErrorResponse(false, "엑셀 미리보기 중 오류가 발생했습니다.", java.util.List.of()));
        }
    }

    @PostMapping(value = "/save", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> saveMultipart(
            @RequestPart("payload") OrderExcelSaveRequest request,
            MultipartHttpServletRequest multipartRequest,
            Authentication authentication
    ) {
        try {
            Map<String, List<MultipartFile>> imageMap = multipartRequest == null
                    ? Map.of()
                    : multipartRequest.getMultiFileMap();
            OrderExcelSaveResponse response = uploadService.save(request, imageMap, resolveUsername(authentication));
            return ResponseEntity.ok(response);
        } catch (OrderExcelUploadValidationException e) {
            return ResponseEntity.badRequest().body(new OrderExcelSaveResponse(false, e.getMessage(), 0, 0, java.util.List.of(), e.getIssues()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new OrderExcelSaveResponse(false, e.getMessage(), 0, 0, java.util.List.of(), java.util.List.of()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new OrderExcelSaveResponse(false, "엑셀 발주 저장 중 오류가 발생했습니다.", 0, 0, java.util.List.of(), java.util.List.of()));
        }
    }

    /**
     * 이미지가 없는 구버전 화면에서도 저장할 수 있도록 JSON 저장 엔드포인트를 같이 유지합니다.
     */
    @PostMapping(value = "/save", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveJson(
            @RequestBody OrderExcelSaveRequest request,
            Authentication authentication
    ) {
        try {
            OrderExcelSaveResponse response = uploadService.save(request, Map.of(), resolveUsername(authentication));
            return ResponseEntity.ok(response);
        } catch (OrderExcelUploadValidationException e) {
            return ResponseEntity.badRequest().body(new OrderExcelSaveResponse(false, e.getMessage(), 0, 0, java.util.List.of(), e.getIssues()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new OrderExcelSaveResponse(false, e.getMessage(), 0, 0, java.util.List.of(), java.util.List.of()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new OrderExcelSaveResponse(false, "엑셀 발주 저장 중 오류가 발생했습니다.", 0, 0, java.util.List.of(), java.util.List.of()));
        }
    }
    private String resolveUsername(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return authentication.getName().trim();
    }

}
