package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductOrderCreateRequest {

    @NotNull
    private Boolean standard;

    private Long standardCategoryId;

    private Long standardProductSeriesId;

    private Long productionCategoryId;

    @NotBlank(message = "제품명은 필수입니다.")
    private String productName;

    @NotBlank(message = "사이즈는 필수입니다.")
    private String productSize;

    @NotBlank(message = "색상은 필수입니다.")
    private String productColor;

    @Min(value = 0, message = "제품가격은 0원 이상이어야 합니다.")
    private int productCost;

    @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
    private int quantity;

    private String orderComment;

    @Valid
    private List<ProductOrderOptionEntryRequest> optionEntries = new ArrayList<>();
}