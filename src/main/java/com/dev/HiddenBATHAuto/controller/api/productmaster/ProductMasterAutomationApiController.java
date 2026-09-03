package com.dev.HiddenBATHAuto.controller.api.productmaster;

import java.security.Principal;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.AutomationBootstrapResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.ConfigurationEvaluationRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.ConfigurationEvaluationResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.ConfigurationRuleResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.ConfigurationRuleSaveRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.DynamicPriceRuleResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.DynamicPriceRuleSaveRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.MatrixImportPreview;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.PriceMatrixResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.PriceMatrixSaveRequest;
import com.dev.HiddenBATHAuto.service.productmaster.ProductConfigurationEvaluationService;
import com.dev.HiddenBATHAuto.service.productmaster.ProductMasterAutomationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/api/product-master/automation")
@PreAuthorize("hasRole('ADMIN')")
public class ProductMasterAutomationApiController {

    private final ProductMasterAutomationService automationService;
    private final ProductConfigurationEvaluationService evaluationService;

    @GetMapping("/bootstrap")
    public ApiResponse<AutomationBootstrapResponse> bootstrap() {
        return ApiResponse.ok(automationService.getBootstrap());
    }

    @GetMapping("/rules")
    public ApiResponse<List<ConfigurationRuleResponse>> rules() {
        return ApiResponse.ok(automationService.getRules());
    }

    @PostMapping("/rules")
    public ApiResponse<ConfigurationRuleResponse> createRule(
            @Valid @RequestBody ConfigurationRuleSaveRequest request,
            Principal principal
    ) {
        return ApiResponse.ok("조건 규칙을 등록했습니다.", automationService.createRule(request, actor(principal)));
    }

    @PutMapping("/rules/{ruleId}")
    public ApiResponse<ConfigurationRuleResponse> updateRule(
            @PathVariable Long ruleId,
            @Valid @RequestBody ConfigurationRuleSaveRequest request,
            Principal principal
    ) {
        return ApiResponse.ok("조건 규칙을 수정했습니다.", automationService.updateRule(ruleId, request, actor(principal)));
    }

    @DeleteMapping("/rules/{ruleId}")
    public ApiResponse<Void> deleteRule(@PathVariable Long ruleId) {
        automationService.deleteRule(ruleId);
        return ApiResponse.ok("조건 규칙을 삭제했습니다.", null);
    }

    @GetMapping("/matrices")
    public ApiResponse<List<PriceMatrixResponse>> matrices() {
        return ApiResponse.ok(automationService.getMatrices());
    }

    @PostMapping("/matrices")
    public ApiResponse<PriceMatrixResponse> createMatrix(
            @Valid @RequestBody PriceMatrixSaveRequest request,
            Principal principal
    ) {
        return ApiResponse.ok("가격표를 등록했습니다.", automationService.createMatrix(request, actor(principal)));
    }

    @PutMapping("/matrices/{matrixId}")
    public ApiResponse<PriceMatrixResponse> updateMatrix(
            @PathVariable Long matrixId,
            @Valid @RequestBody PriceMatrixSaveRequest request,
            Principal principal
    ) {
        return ApiResponse.ok("가격표를 수정했습니다.", automationService.updateMatrix(matrixId, request, actor(principal)));
    }

    @DeleteMapping("/matrices/{matrixId}")
    public ApiResponse<Void> deleteMatrix(@PathVariable Long matrixId) {
        automationService.deleteMatrix(matrixId);
        return ApiResponse.ok("가격표를 삭제했습니다.", null);
    }

    @PostMapping(value = "/matrices/import-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MatrixImportPreview> importPreview(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok("가격표 파일을 검증했습니다. 저장 전 미리보기를 확인해 주세요.",
                automationService.previewMatrix(file));
    }

    @GetMapping("/price-rules")
    public ApiResponse<List<DynamicPriceRuleResponse>> priceRules() {
        return ApiResponse.ok(automationService.getPriceRules());
    }

    @PostMapping("/price-rules")
    public ApiResponse<DynamicPriceRuleResponse> createPriceRule(
            @Valid @RequestBody DynamicPriceRuleSaveRequest request,
            Principal principal
    ) {
        return ApiResponse.ok("가격 규칙을 등록했습니다.", automationService.createPriceRule(request, actor(principal)));
    }

    @PutMapping("/price-rules/{ruleId}")
    public ApiResponse<DynamicPriceRuleResponse> updatePriceRule(
            @PathVariable Long ruleId,
            @Valid @RequestBody DynamicPriceRuleSaveRequest request,
            Principal principal
    ) {
        return ApiResponse.ok("가격 규칙을 수정했습니다.", automationService.updatePriceRule(ruleId, request, actor(principal)));
    }

    @DeleteMapping("/price-rules/{ruleId}")
    public ApiResponse<Void> deletePriceRule(@PathVariable Long ruleId) {
        automationService.deletePriceRule(ruleId);
        return ApiResponse.ok("가격 규칙을 삭제했습니다.", null);
    }

    @PostMapping("/products/{productId}/evaluate")
    public ApiResponse<ConfigurationEvaluationResponse> evaluate(
            @PathVariable Long productId,
            @Valid @RequestBody ConfigurationEvaluationRequest request
    ) {
        return ApiResponse.ok(evaluationService.evaluate(productId, request, true));
    }

    private String actor(Principal principal) {
        return principal == null ? "SYSTEM" : principal.getName();
    }
}
