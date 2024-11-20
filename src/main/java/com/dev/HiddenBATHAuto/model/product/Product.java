package com.dev.HiddenBATHAuto.model.product;

import java.util.List;

import org.springframework.lang.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;

@Entity
@Table(name="tb_product")
@Data
public class Product {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="PRODUCT_ID")
    private Long id;
    
    @Column(name="PRODUCT_CODE")
    private String productCode;

    @Column(name="PRODUCT_NAME")
    private String name;
    
    @Column(name="PRODUCT_SIGN")
    private Boolean productSign;
    
    @Column(name="PRODUCT_ROTATION_NUMBER")
    private int productRotationNumber;
    
    @Column(name="PRODUCT_ROTATION_EXTENSION")
    private String productRotationExtension;
    
    @Column(name="PRODUCT_TITLE")
    private String title;
    
    @Column(name="PRODUCT_SUBJECT")
    private String subject;
    
    @Column(name="PRODUCT_ORDER")
    private Boolean order;
    
    @Column(name="PRODUCT_UNIT")
    private String unit;
    
    @Column(name="PRODUCT_INDEX")
    private int productIndex;
    
    @Column(name="PRODUCT_REP_IMAGE_NAME")
    private String productRepImageName;
    
    @Column(name="PRODUCT_REP_IMAGE_EXTENSION")
    private String productRepImageExtension;
    
    @Column(name="PRODUCT_REP_IMAGE_ORIGINAL_NAME")
    private String productRepImageOriginalName;
    
    @Column(name="PRODUCT_REP_IMAGE_PATH")
    private String productRepImagePath;
    
    @Column(name="PRODUCT_REP_IMAGE_ROAD")
    private String productRepImageRoad;
    
    @Transient
    private String randomImage;
   
    @Column(name="NORMALLED_ADD_SIGN")
    private Boolean normalLedSign;
    
    @Column(name="TISSUE_ADD_SIGN")
    private Boolean tissueAddSign;
    
    @Column(name="DRY_ADD_SIGN")
    private Boolean dryAddSign;
    
    @Column(name="LOWLED_ADD_SIGN")
    private Boolean lowLedAddSign;
    
    @Column(name="CONCENT_ADD_SIGN")
    private Boolean concentAddSign;
    
    @Column(name="HANDLE_ADD_SIGN")
    private Boolean handleAddSign;
    
    @Column(name="SIZE_CHANGE_SIGN")
    private Boolean sizeChangeSign;
    
    @Column(name="WIDTH_MIN_LIMIT")
    private int widthMinLimit;
    
    @Column(name="WIDTH_MAX_LIMIT")
    private int widthMaxLimit;
    
    @Column(name="HEIGHT_MIN_LIMIT")
    private int heightMinLimit;
    
    @Column(name="HEIGHT_MAX_LIMIT")
    private int heightMaxLimit;
    
    @Column(name="DEPTH_MIN_LIMIT")
    private int depthMinLimit;
    
    @Column(name="DEPTH_MAX_LIMIT")
    private int depthMaxLimit;
    
    @Column(name="DOOR_AMOUNT_SIGN")
    private Boolean doorAmountSign;
    
    @Column(name="DOOR_RATIO_SIGN")
    private Boolean doorRatioSign;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @Nullable
    @JoinTable(
        name="tb_product_and_color", 
        joinColumns = @JoinColumn(name="PC_PRODUCT_ID"),
        inverseJoinColumns = @JoinColumn(name="PC_COLOR_ID")
    )
    @JsonManagedReference
    private List<ProductColor> productColors;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @Nullable
    @JoinTable(
        name="tb_product_and_size", 
        joinColumns = @JoinColumn(name="PS_PRODUCT_ID"),
        inverseJoinColumns = @JoinColumn(name="PS_SIZE_ID")
    )
    @JsonManagedReference
    private List<ProductSize> productSizes;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @Nullable
    @JoinTable(
        name="tb_product_and_tag", 
        joinColumns = @JoinColumn(name="PT_PRODUCT_ID"),
        inverseJoinColumns = @JoinColumn(name="PT_TAG_ID")
    )
    private List<ProductTag> productTags;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @Nullable
    @JoinTable(
        name="tb_product_and_option", 
        joinColumns = @JoinColumn(name="PO_PRODUCT_ID"),
        inverseJoinColumns = @JoinColumn(name="PO_OPTION_ID")
    )
    private List<ProductOption> productOptions;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @Nullable
    @JoinTable(
        name="tb_product_and_normalled_add", 
        joinColumns = @JoinColumn(name="PNA_PRODUCT_ID"),
        inverseJoinColumns = @JoinColumn(name="PNA_ADD_ID")
    )
    private List<ProductOptionAdd> productNormalLedAdds;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @Nullable
    @JoinTable(
        name="tb_product_and_tissue_add", 
        joinColumns = @JoinColumn(name="PTA_PRODUCT_ID"),
        inverseJoinColumns = @JoinColumn(name="PTA_ADD_ID")
    )
    private List<ProductOptionAdd> productTissueAdds;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @Nullable
    @JoinTable(
        name="tb_product_and_tissue_position", 
        joinColumns = @JoinColumn(name="PTP_PRODUCT_ID"),
        inverseJoinColumns = @JoinColumn(name="PTP_POSITION_ID")
    )
    private List<ProductOptionPosition> productTissuePositions;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @Nullable
    @JoinTable(
        name="tb_product_and_dry_add", 
        joinColumns = @JoinColumn(name="PDA_PRODUCT_ID"),
        inverseJoinColumns = @JoinColumn(name="PDA_ADD_ID")
    )
    private List<ProductOptionAdd> productDryAdds;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @Nullable
    @JoinTable(
        name="tb_product_and_dry_position", 
        joinColumns = @JoinColumn(name="PDP_PRODUCT_ID"),
        inverseJoinColumns = @JoinColumn(name="PDP_POSITION_ID")
    )
    private List<ProductOptionPosition> productDryPositions;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @Nullable
    @JoinTable(
        name="tb_product_and_lowled_add", 
        joinColumns = @JoinColumn(name="PLA_PRODUCT_ID"),
        inverseJoinColumns = @JoinColumn(name="PLA_ADD_ID")
    )
    private List<ProductOptionAdd> productLowLedAdds;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @Nullable
    @JoinTable(
        name="tb_product_and_lowled_position", 
        joinColumns = @JoinColumn(name="PLP_PRODUCT_ID"),
        inverseJoinColumns = @JoinColumn(name="PLP_POSITION_ID")
    )
    private List<ProductOptionPosition> productLowLedPositions;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @Nullable
    @JoinTable(
        name="tb_product_and_concent_add", 
        joinColumns = @JoinColumn(name="PCA_PRODUCT_ID"),
        inverseJoinColumns = @JoinColumn(name="PCA_ADD_ID")
    )
    private List<ProductOptionAdd> productConcentAdds;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @Nullable
    @JoinTable(
        name="tb_product_and_concent_position", 
        joinColumns = @JoinColumn(name="PCP_PRODUCT_ID"),
        inverseJoinColumns = @JoinColumn(name="PCP_POSITION_ID")
    )
    private List<ProductOptionPosition> productConcentPositions;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @Nullable
    @JoinTable(
        name="tb_product_and_handle_add", 
        joinColumns = @JoinColumn(name="PHA_PRODUCT_ID"),
        inverseJoinColumns = @JoinColumn(name="PHA_ADD_ID")
    )
    private List<ProductOptionAdd> productHandleAdds;
    
    @Transient
    private Long middleId;
    
    @Transient
    private Long bigId;
        
    @OneToMany(
        fetch = FetchType.LAZY, 
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        mappedBy = "productId"
    )
    private List<ProductImage> images;
    
    @OneToMany(
        fetch = FetchType.LAZY, 
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        mappedBy = "productId"
    )
    private List<ProductFile> files;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name="PRODUCT_MIDDLE_REFER_ID", referencedColumnName="MIDDLE_SORT_ID"
    )
    @JsonIgnore
    private MiddleSort middleSort;
    
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(
        name="PRODUCT_BIG_REFER_ID", referencedColumnName="BIG_SORT_ID"
    )
    @JsonIgnore
    private BigSort bigSort;
}
