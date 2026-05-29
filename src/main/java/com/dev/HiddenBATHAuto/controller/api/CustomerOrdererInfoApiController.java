package com.dev.HiddenBATHAuto.controller.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.auth.CustomerOrdererInfoDtos.OrdererInfoResponse;
import com.dev.HiddenBATHAuto.dto.auth.CustomerOrdererInfoDtos.SaveRequest;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.auth.CustomerOrdererInfoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/customer/api/orderer-infos")
public class CustomerOrdererInfoApiController {

    private final CustomerOrdererInfoService customerOrdererInfoService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getOrdererInfos(
            @AuthenticationPrincipal PrincipalDetails principal) {

        List<OrdererInfoResponse> list = customerOrdererInfoService.getMyCompanyOrdererInfos(principal);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "조회되었습니다.");
        body.put("data", list);

        return ResponseEntity.ok(body);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrdererInfo(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody SaveRequest request) {

        OrdererInfoResponse saved = customerOrdererInfoService.createOrdererInfo(principal, request);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "주문자 정보가 등록되었습니다.");
        body.put("data", saved);

        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteOrdererInfo(
            @AuthenticationPrincipal PrincipalDetails principal,
            @PathVariable Long id) {

        customerOrdererInfoService.deleteOrdererInfo(principal, id);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "주문자 정보가 삭제되었습니다.");

        return ResponseEntity.ok(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", e.getMessage());

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        e.printStackTrace();

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", "주문자 정보 처리 중 서버 오류가 발생했습니다.");

        return ResponseEntity.internalServerError().body(body);
    }
}