package com.dev.HiddenBATHAuto.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.OrderRequestItemDTO;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.order.OrderProcessingService;

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
        System.out.println("====== 주문 요청 도착 ======");
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            System.out.println("▶ 제품 " + (i + 1));
            System.out.println("수량 : " + item.getQuantity());
            System.out.println("가격 : " + item.getPrice());
            System.out.println("옵션 Json : " + item.getOptionJson());
            System.out.println("----------------------");
        }
        orderProcessingService.createTaskWithOrders(user.getMember(), items);
        return "발주가 완료 되었습니다.";
    }
}








