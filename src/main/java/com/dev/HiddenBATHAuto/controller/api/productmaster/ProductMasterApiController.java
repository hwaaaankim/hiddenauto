package com.dev.HiddenBATHAuto.controller.api.productmaster;

import java.security.Principal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.PageResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ProductDetailResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ProductListItemResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ProductSaveRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.StockAdjustmentRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.VoidStockMovementRequest;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductMasterStatus;
import com.dev.HiddenBATHAuto.service.productmaster.ProductInventoryService;
import com.dev.HiddenBATHAuto.service.productmaster.ProductMasterService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/api/product-master/products")
@PreAuthorize("hasRole('ADMIN')")
public class ProductMasterApiController {

    private final ProductMasterService productMasterService;
    private final ProductInventoryService inventoryService;

    @GetMapping
    public ApiResponse<PageResponse<ProductListItemResponse>> products(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "30") int size,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) ProductMasterStatus status,
            @RequestParam(name = "stockStatus", required = false) String stockStatus,
            @RequestParam(name = "attribute", required = false) List<String> attributes,
            @RequestParam(name = "numberAttribute", required = false) List<String> numberAttributes,
            @RequestParam(name = "textAttribute", required = false) List<String> textAttributes,
            @RequestParam(name = "sort", required = false) List<String> sorts,
            @RequestParam(name = "widthMin", required = false) Integer widthMin,
            @RequestParam(name = "widthMax", required = false) Integer widthMax,
            @RequestParam(name = "depthMin", required = false) Integer depthMin,
            @RequestParam(name = "depthMax", required = false) Integer depthMax,
            @RequestParam(name = "heightMin", required = false) Integer heightMin,
            @RequestParam(name = "heightMax", required = false) Integer heightMax
    ) {
        return ApiResponse.ok(productMasterService.search(
                page,
                size,
                keyword,
                status,
                stockStatus,
                attributes,
                numberAttributes,
                textAttributes,
                sorts,
                widthMin,
                widthMax,
                depthMin,
                depthMax,
                heightMin,
                heightMax
        ));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> product(@PathVariable Long productId) {
        return ApiResponse.ok(productMasterService.getProduct(productId));
    }

    @GetMapping("/resolve")
    public ApiResponse<ProductDetailResponse> resolve(@RequestParam("code") String code) {
        return ApiResponse.ok(productMasterService.resolveCode(code));
    }

    @PostMapping
    public ApiResponse<ProductDetailResponse> create(
            @Valid @RequestBody ProductSaveRequest request,
            Principal principal
    ) {
        return ApiResponse.ok(
                "제품을 등록했습니다.",
                productMasterService.create(request, actor(principal))
        );
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> update(
            @PathVariable Long productId,
            @Valid @RequestBody ProductSaveRequest request,
            Principal principal
    ) {
        return ApiResponse.ok(
                "제품을 수정했습니다.",
                productMasterService.update(productId, request, actor(principal))
        );
    }

    @PostMapping("/{productId}/stock-movements")
    public ApiResponse<ProductDetailResponse> adjustStock(
            @PathVariable Long productId,
            @Valid @RequestBody StockAdjustmentRequest request,
            Principal principal
    ) {
        return ApiResponse.ok(
                "재고를 변경했습니다.",
                inventoryService.adjust(productId, request, actor(principal))
        );
    }

    @PostMapping("/{productId}/stock-movements/{movementId}/void")
    public ApiResponse<ProductDetailResponse> voidStockMovement(
            @PathVariable Long productId,
            @PathVariable Long movementId,
            @Valid @RequestBody VoidStockMovementRequest request,
            Principal principal
    ) {
        return ApiResponse.ok(
                "재고 이력을 삭제(취소)했습니다.",
                inventoryService.voidMovement(productId, movementId, request.reason(), actor(principal))
        );
    }

    private String actor(Principal principal) {
        return principal == null ? "SYSTEM" : principal.getName();
    }
}
