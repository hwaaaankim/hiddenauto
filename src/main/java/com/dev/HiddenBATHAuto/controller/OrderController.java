package com.dev.HiddenBATHAuto.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.OrderSubmitRequestDTO;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.order.OrderProcessingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

	private final OrderProcessingService orderProcessingService;

    @PostMapping("/submit")
    public ResponseEntity<String> submitOrder(
        @AuthenticationPrincipal PrincipalDetails user,
        @RequestBody OrderSubmitRequestDTO request
    ) {
    	System.out.println("====== 주문 요청 도착 ======");
        for (int i = 0; i < request.getItems().size(); i++) {
            var item = request.getItems().get(i);
            System.out.println("▶ 제품 " + (i + 1));
            System.out.println("수량 : " + item.getQuantity());
            System.out.println("가격 : " + item.getPrice());
            System.out.println("배송비 : " + item.getDeliveryPrice());
            System.out.println("주소 : " + item.getMainAddress() + " " + item.getDetailAddress());
            System.out.println("우편번호 : " + item.getZipCode());
            System.out.println("배송수단 : " + item.getDeliveryMethodId());
            System.out.println("행정구역 : " + item.getDoName() + " " + item.getSiName() + " " + item.getGuName());
            System.out.println("옵션 Json : " + item.getOptionJson());
            System.out.println("----------------------");
        }
        try {
            String result = orderProcessingService.createTaskWithOrders(user.getMember(), request.getItems(), request.getPointUsed());
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("서버 오류: " + e.getMessage());
        }
    }
}








