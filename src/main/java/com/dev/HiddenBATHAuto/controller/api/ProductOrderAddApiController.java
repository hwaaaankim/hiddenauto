package com.dev.HiddenBATHAuto.controller.api;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderAddRequest;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderAddSaveResponse;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderCompanyOptionResponse;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderMemberOptionResponse;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderSimpleOptionResponse;
import com.dev.HiddenBATHAuto.service.productOrderAdd.ProductOrderAddCommandService;
import com.dev.HiddenBATHAuto.service.productOrderAdd.ProductOrderAddQueryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/management/api/product-order-add")
@RequiredArgsConstructor
@Validated
public class ProductOrderAddApiController {

    private final ProductOrderAddQueryService queryService;
    private final ProductOrderAddCommandService commandService;

    @GetMapping("/companies")
    public List<ProductOrderCompanyOptionResponse> searchCompanies(
            @RequestParam(required = false) String keyword
    ) {
        return queryService.searchCompanies(keyword);
    }

    @GetMapping("/delivery-handlers")
    public List<ProductOrderMemberOptionResponse> searchDeliveryHandlers(
            @RequestParam(required = false) String keyword
    ) {
        return queryService.searchDeliveryHandlers(keyword);
    }

    @GetMapping("/standard-categories")
    public List<ProductOrderSimpleOptionResponse> getStandardCategories() {
        return queryService.getStandardCategories();
    }

    @GetMapping("/standard-series")
    public List<ProductOrderSimpleOptionResponse> getStandardSeries(
            @RequestParam Long categoryId
    ) {
        return queryService.getStandardSeries(categoryId);
    }

    @GetMapping("/production-categories")
    public List<ProductOrderSimpleOptionResponse> getProductionCategories(
            @RequestParam(required = false) String keyword
    ) {
        return queryService.getProductionCategories(keyword);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductOrderAddSaveResponse> createProductOrder(
            @Valid @RequestPart("request") ProductOrderAddRequest request,
            MultipartHttpServletRequest multipartRequest
    ) {
        try {
            ProductOrderAddSaveResponse response = commandService.create(request, multipartRequest.getMultiFileMap());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ProductOrderAddSaveResponse(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ProductOrderAddSaveResponse(false, "발주 등록 중 오류가 발생했습니다.", null));
        }
    }
}