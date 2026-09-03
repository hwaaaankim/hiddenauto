package com.dev.HiddenBATHAuto.controller.api.productmaster;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.ConfigurationEvaluationRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.ConfigurationEvaluationResponse;
import com.dev.HiddenBATHAuto.service.productmaster.ProductConfigurationEvaluationService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/product-spec/api/{token}")
public class ProductPublicConfigurationApiController {

    private final ProductConfigurationEvaluationService evaluationService;

    @GetMapping("/schema")
    public ApiResponse<ConfigurationEvaluationResponse> schema(
            @PathVariable String token,
            HttpServletResponse response
    ) {
        noStore(response);
        return ApiResponse.ok(evaluationService.evaluatePublicToken(
                token,
                new ConfigurationEvaluationRequest(java.util.List.of())
        ));
    }

    @PostMapping("/evaluate")
    public ApiResponse<ConfigurationEvaluationResponse> evaluate(
            @PathVariable String token,
            @Valid @RequestBody ConfigurationEvaluationRequest request,
            HttpServletResponse response
    ) {
        noStore(response);
        return ApiResponse.ok(evaluationService.evaluatePublicToken(token, request));
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
}
