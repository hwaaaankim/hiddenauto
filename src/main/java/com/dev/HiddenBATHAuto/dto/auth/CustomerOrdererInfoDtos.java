package com.dev.HiddenBATHAuto.dto.auth;

import com.dev.HiddenBATHAuto.model.auth.CompanyOrdererInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class CustomerOrdererInfoDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SaveRequest {
        private String name;
        private String phone;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class OrdererInfoResponse {
        private Long id;
        private String name;
        private String phone;

        public static OrdererInfoResponse from(CompanyOrdererInfo entity) {
            return OrdererInfoResponse.builder()
                    .id(entity.getId())
                    .name(entity.getOrdererName())
                    .phone(entity.getPhone())
                    .build();
        }
    }
}