package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductOrderAddRequest {

    @NotNull
    private Long companyId;

    @NotNull
    private LocalDate preferredDeliveryDate;

    @NotNull
    private Long deliveryHandlerId;

    @NotBlank
    @Size(max = 20)
    private String zipCode;

    @NotBlank
    @Size(max = 100)
    private String doName;

    @Size(max = 100)
    private String siName;

    @Size(max = 100)
    private String guName;

    @NotBlank
    @Size(max = 255)
    private String roadAddress;

    @Size(max = 255)
    private String detailAddress;

    @Valid
    @NotEmpty
    private List<ProductOrderCreateRequest> orders = new ArrayList<>();
}