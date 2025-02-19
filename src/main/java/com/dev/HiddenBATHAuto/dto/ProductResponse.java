package com.dev.HiddenBATHAuto.dto;

import java.util.List;

import com.dev.HiddenBATHAuto.model.nonstandard.ProductColor;
import com.dev.HiddenBATHAuto.model.nonstandard.ProductOptionAdd;
import com.dev.HiddenBATHAuto.model.nonstandard.ProductOptionPosition;

import lombok.Data;

@Data
public class ProductResponse {
    private Long productId;
    private String productCode;
    private String productName;
    private Boolean productSign;
    private int productRotationNumber;
    private String productRotationExtension;
    private String productTitle;
    private String productSubject;
    private Boolean order;
    private String unit;
    private int productIndex;
    private String productRepImageName;
    private String productRepImageExtension;
    private String productRepImageOriginalName;
    private String productRepImagePath;
    private String productRepImageRoad;
    private Boolean normalLedSign;
    private Boolean tissueAddSign;
    private Boolean dryAddSign;
    private Boolean lowLedAddSign;
    private Boolean outletAddSign;
    private Boolean handleAddSign;
    private Boolean sizeChangeSign;
    private int widthMinLimit;
    private int widthMaxLimit;
    private int heightMinLimit;
    private int heightMaxLimit;
    private int depthMinLimit;
    private int depthMaxLimit;
    private Boolean doorAmountSign;
    private Boolean doorRatioSign;
    private List<ProductColor> productColors;
    private List<ProductOptionAdd> productNormalLedAdds;
    private List<ProductOptionAdd> productTissueAdds;
    private List<ProductOptionPosition> productTissuePositions;
    private List<ProductOptionAdd> productDryAdds;
    private List<ProductOptionPosition> productDryPositions;
    private List<ProductOptionAdd> productLowLedAdds;
    private List<ProductOptionPosition> productLowLedPositions;
    private List<ProductOptionAdd> productOutletAdds;
    private List<ProductOptionPosition> productOutletPositions;
    private List<ProductOptionAdd> productHandleAdds;
    private Long middleId;
    private Long bigId;
}
