package com.dev.HiddenBATHAuto.model.amount;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tb_amount_item_master", indexes = {
        @Index(name = "idx_tb_amount_item_master_code", columnList = "item_code"),
        @Index(name = "idx_tb_amount_item_master_name", columnList = "item_name"),
        @Index(name = "idx_tb_amount_item_master_updated", columnList = "updated_at")
})
public class AmountItemMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "division", length = 255)
    private String division;
    @Column(name = "item_code", length = 255)
    private String itemCode;
    @Column(name = "item_name", length = 255)
    private String itemName;
    @Column(name = "purchase_price", length = 255)
    private String purchasePrice;
    @Column(name = "sales_price", length = 255)
    private String salesPrice;
    @Column(name = "opening_stock_qty", length = 255)
    private String openingStockQty;
    @Column(name = "opening_stock_unit_price", length = 255)
    private String openingStockUnitPrice;
    @Column(name = "unit_name", length = 255)
    private String unit;
    @Column(name = "specification", length = 255)
    private String specification;
    @Column(name = "barcode", length = 255)
    private String barcode;
    @Column(name = "category_name", length = 255)
    private String categoryName;
    @Column(name = "brand_name", length = 255)
    private String brandName;
    @Column(name = "model_name", length = 255)
    private String modelName;
    @Column(name = "tax_type", length = 255)
    private String taxType;
    @Column(name = "item_registered_date", length = 255)
    private String itemRegisteredDate;
    @Column(name = "liquor_item_yn", length = 255)
    private String liquorItemYn;
    @Column(name = "usage_type", length = 255)
    private String usageType;
    @Column(name = "liquor_type", length = 255)
    private String liquorType;
    @Column(name = "dedicated_warehouse_no", length = 255)
    private String dedicatedWarehouseNo;
    @Column(name = "purchase_base_qty", length = 255)
    private String purchaseBaseQty;
    @Column(name = "proper_stock", length = 255)
    private String properStock;
    @Column(name = "outsource_production_price", length = 255)
    private String outsourceProductionPrice;
    @Column(name = "grade1_price", length = 255)
    private String grade1Price;
    @Column(name = "grade1_qty", length = 255)
    private String grade1Qty;
    @Column(name = "grade2_price", length = 255)
    private String grade2Price;
    @Column(name = "grade2_qty", length = 255)
    private String grade2Qty;
    @Column(name = "grade3_price", length = 255)
    private String grade3Price;
    @Column(name = "grade3_qty", length = 255)
    private String grade3Qty;
    @Column(name = "grade4_price", length = 255)
    private String grade4Price;
    @Column(name = "grade4_qty", length = 255)
    private String grade4Qty;
    @Column(name = "grade5_price", length = 255)
    private String grade5Price;
    @Column(name = "grade5_qty", length = 255)
    private String grade5Qty;
    @Column(name = "use_status", length = 255)
    private String useStatus;
    @Column(name = "stock_calculation_yn", length = 255)
    private String stockCalculationYn;
    @Column(name = "origin_display_type", length = 255)
    private String originDisplayType;
    @Column(name = "procurement_identifier_code", length = 255)
    private String procurementIdentifierCode;
    @Lob
    @Column(name = "note")
    private String note;
    @Column(name = "udi_use_yn", length = 255)
    private String udiUseYn;
    @Lob
    @Column(name = "search_text")
    private String searchText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
