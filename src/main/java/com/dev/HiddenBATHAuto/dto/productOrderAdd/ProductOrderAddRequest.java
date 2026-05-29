package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductOrderAddRequest {

    @NotNull(message = "대리점을 선택해 주세요.")
    private Long companyId;

    @NotNull(message = "배송수단을 선택해 주세요.")
    private Long deliveryMethodId;

    @NotNull(message = "배송 희망일을 선택해 주세요.")
    private LocalDate preferredDeliveryDate;

    /**
     * 배송수단이 직배송인 경우에만 필수입니다.
     * 직배송이 아닌 경우에는 null이어야 하며, 서버에서 저장하지 않습니다.
     */
    private Long deliveryHandlerId;

    private String ordererName;
    private String ordererPhone;

    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;

    private int packingCost = 0;
    private int deliveryCost = 0;

    @Valid
    @NotEmpty(message = "최소 1개의 주문이 필요합니다.")
    private List<ProductOrderCreateRequest> orders = new ArrayList<>();
}
