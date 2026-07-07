package com.dev.HiddenBATHAuto.orderExcelUpload.api;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

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
            MultipartHttpServletRequest multipartRequest
    ) {
        try {
            Map<String, List<MultipartFile>> imageMap = multipartRequest == null
                    ? Map.of()
                    : multipartRequest.getMultiFileMap();
            OrderExcelSaveResponse response = uploadService.save(request, imageMap);
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
    public ResponseEntity<?> saveJson(@RequestBody OrderExcelSaveRequest request) {
        try {
            OrderExcelSaveResponse response = uploadService.save(request, Map.of());
            return ResponseEntity.ok(response);
        } catch (OrderExcelUploadValidationException e) {
            return ResponseEntity.badRequest().body(new OrderExcelSaveResponse(false, e.getMessage(), 0, 0, java.util.List.of(), e.getIssues()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new OrderExcelSaveResponse(false, e.getMessage(), 0, 0, java.util.List.of(), java.util.List.of()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new OrderExcelSaveResponse(false, "엑셀 발주 저장 중 오류가 발생했습니다.", 0, 0, java.util.List.of(), java.util.List.of()));
        }
    }
}
