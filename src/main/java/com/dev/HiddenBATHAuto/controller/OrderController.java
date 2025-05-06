package com.dev.HiddenBATHAuto.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.OrderRequestItemDTO;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.auth.OrderProcessingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

	private final OrderProcessingService orderProcessingService;

    @PostMapping("/submit")
    public String submitOrder(
        @AuthenticationPrincipal PrincipalDetails user,
        @RequestBody List<OrderRequestItemDTO> items
    ) {
        orderProcessingService.createTaskWithOrders(user.getMember(), items);
        return "OK";
    }
}








