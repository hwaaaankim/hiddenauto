package com.dev.HiddenBATHAuto.dto.auth;

import com.dev.HiddenBATHAuto.model.auth.CompanyOrdererInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyOrdererInfoViewDTO {

    private Long id;
    private String ordererName;
    private String phone;

    public static CompanyOrdererInfoViewDTO fromEntity(CompanyOrdererInfo entity) {
        if (entity == null) {
            return null;
        }

        return CompanyOrdererInfoViewDTO.builder()
                .id(entity.getId())
                .ordererName(entity.getOrdererName())
                .phone(entity.getPhone())
                .build();
    }
}