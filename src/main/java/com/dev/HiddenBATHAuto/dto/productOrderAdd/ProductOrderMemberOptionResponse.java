package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductOrderMemberOptionResponse {
    private Long memberId;
    private String name;
    private String username;
    private String phone;
    private LocalDateTime joinedAt;
}