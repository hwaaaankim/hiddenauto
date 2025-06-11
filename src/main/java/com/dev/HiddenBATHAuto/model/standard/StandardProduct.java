package com.dev.HiddenBATHAuto.model.standard;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_standard_product")
@Data
public class StandardProduct {
    
	@Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productCode;   // 제품고유코드
    private String name;          // 제품명

    private Integer indexOrder;   // 인덱스
    private Integer viewCount;    // 조회수
    private Integer orderCount;   // 주문수

    private Boolean hasTissueCap;
    private Boolean hasDryHolder;
    private Boolean hasOutlet;
    private Boolean hasLed;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private StandardCategory category;

    @ManyToOne
    @JoinColumn(name = "series_id")
    private StandardProductSeries productSeries;

    // 사이즈 ManyToMany
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "tb_standard_product_size_map",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "size_id")
    )
    private List<StandardProductSize> sizes;

    // 색상 ManyToMany
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "tb_standard_product_color_map",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "color_id")
    )
    private List<StandardProductColor> colors;

    // 티슈 위치
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "tb_standard_product_and_tissue_position",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "position_id")
    )
    private List<StandardProductOptionPosition> productTissuePositions;

    // 드라이 위치
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "tb_standard_product_and_dry_position",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "position_id")
    )
    private List<StandardProductOptionPosition> productDryPositions;

    // 콘센트 위치
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "tb_standard_product_and_outlet_position",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "position_id")
    )
    private List<StandardProductOptionPosition> productOutletPositions;

    // LED 위치
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "tb_standard_product_and_led_position",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "position_id")
    )
    private List<StandardProductOptionPosition> productLedPositions;

}

