package com.dev.HiddenBATHAuto.dto;

import lombok.Data;

@Data
public class CompanyDeliveryAddressViewDTO {
    private Long id;
    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;

    public static CompanyDeliveryAddressViewDTO fromEntity(com.dev.HiddenBATHAuto.model.auth.CompanyDeliveryAddress e) {
        CompanyDeliveryAddressViewDTO dto = new CompanyDeliveryAddressViewDTO();
        dto.setId(e.getId());
        dto.setZipCode(e.getZipCode());
        dto.setDoName(e.getDoName());
        dto.setSiName(e.getSiName());
        dto.setGuName(e.getGuName());
        dto.setRoadAddress(e.getRoadAddress());
        dto.setDetailAddress(e.getDetailAddress());
        return dto;
    }
}