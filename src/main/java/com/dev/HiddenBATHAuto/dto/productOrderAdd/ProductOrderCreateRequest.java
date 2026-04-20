package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @Valid
    @NotEmpty
    private List<ProductOrderOptionEntryRequest> optionEntries = new ArrayList<>();

    @Size(max = 3000)
    private String orderComment;

    @NotNull
    @Min(0)
    private Integer productCost;

    @NotNull
    @Min(1)
    private Integer quantity;
}