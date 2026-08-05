package com.dev.HiddenBATHAuto.model.task;

import java.util.Map;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Formula;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "tb_order_item")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @OneToOne
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    private String productName;

    /**
     * 엑셀 업로드 파일의 "품목명" 값을 가공하지 않고 그대로 저장합니다.
     */
    @Column(name = "item_name", columnDefinition = "TEXT")
    private String itemName;

    private int quantity;

    @Column(columnDefinition = "TEXT")
    private String optionJson;

    @Formula("case when option_json is not null and json_valid(option_json) = 1 then coalesce(nullif(json_unquote(json_extract(option_json, '$.\"제품시리즈\"')), ''), '중분류없음') else '중분류없음' end")
    private String productionProductSeriesSortValue;

    @Transient
    private Map<String, String> parsedOptionMap;

    @Transient
    private String formattedOptionHtml;

    @Transient
    private String formattedOptionText;

    @Transient
    private String productionProductName;

    @Transient
    private String productionProductSeries;

    @Transient
    private String productionColor;

    @Transient
    private String productionSize;

    @Transient
    private String productionCategory;

    @Transient
    private String deliveryCategoryText;

    @Transient
    private String deliveryProductName;

    @Transient
    private String deliverySizeText;

    @Transient
    private String deliveryColorText;

    @Transient
    private String deliveryQuantityText;

    @Transient
    private String deliveryOptionText;

    @Transient
    private String deliveryProductSummaryText;

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        OrderItem that = (OrderItem) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
