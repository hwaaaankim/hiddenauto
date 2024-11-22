package com.dev.HiddenBATHAuto.dto;

import java.util.List;

import com.dev.HiddenBATHAuto.model.product.ProductColor;
import com.dev.HiddenBATHAuto.model.product.ProductFile;
import com.dev.HiddenBATHAuto.model.product.ProductImage;
import com.dev.HiddenBATHAuto.model.product.ProductOption;
import com.dev.HiddenBATHAuto.model.product.ProductOptionAdd;
import com.dev.HiddenBATHAuto.model.product.ProductOptionPosition;
import com.dev.HiddenBATHAuto.model.product.ProductSize;
import com.dev.HiddenBATHAuto.model.product.ProductTag;

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
    private List<ProductSize> productSizes;
    private List<ProductTag> productTags;
    private List<ProductOption> productOptions;
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
    private List<ProductImage> images;
    private List<ProductFile> files;
}
