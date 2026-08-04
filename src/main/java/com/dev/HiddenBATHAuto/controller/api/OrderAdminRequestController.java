package com.dev.HiddenBATHAuto.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.ordernotification.OrderAdminRequestDto;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderAdminRequestResponse;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.ordernotification.OrderAdminRequestService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/internal/orders")
@RequiredArgsConstructor
public class OrderAdminRequestController {

    private final OrderAdminRequestService adminRequestService;

    @PostMapping("/{orderId}/admin-request")
    public ResponseEntity<OrderAdminRequestResponse> requestAdmin(
            @AuthenticationPrincipal PrincipalDetails principal,
            @PathVariable Long orderId,
            @RequestBody(required = false) OrderAdminRequestDto request
    ) {
        return ResponseEntity.ok(adminRequestService.request(
                orderId,
                principal != null ? principal.getMember() : null,
                request
        ));
    }
}
