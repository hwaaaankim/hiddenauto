package com.dev.HiddenBATHAuto.model.task;

import java.util.Map;

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
import lombok.Data;

@Entity
@Table(name = "tb_order_item")
@Data
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    private String productName;
    private int quantity;

    @Column(columnDefinition = "TEXT")
    private String optionJson;

    /**
     * 생산팀 목록의 "중분류" 정렬 전용 값입니다.
     *
     * - optionJson 안의 "제품시리즈" 값을 DB 정렬에 사용합니다.
     * - 값이 없거나 빈 값이면 "중분류없음"으로 정렬/표시 기준을 맞춥니다.
     * - MariaDB/MySQL JSON 함수 기준입니다.
     */
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
}
