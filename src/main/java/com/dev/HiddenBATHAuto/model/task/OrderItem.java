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

    /**
     * 엑셀 업로드 파일의 "품목명" 값을 가공하지 않고 그대로 저장합니다.
     *
     * 기존 productName은 현재 제품명 가공/표시 로직을 그대로 유지하고,
     * itemName은 엑셀 원문 보존 용도로만 사용합니다.
     */
    @Column(name = "item_name", columnDefinition = "TEXT")
    private String itemName;

    private int quantity;

    @Column(columnDefinition = "TEXT")
    private String optionJson;

    /**
     * 생산팀 목록의 "중분류" 정렬 전용 값입니다.
     *
     * - optionJson 안의 "제품시리즈" 값을 DB 정렬에 사용합니다.
     * - 값이 없거나 빈 값이면 "중분류없음"으로 정렬/표시 기준을 맞춥니다.
     * - MariaDB/MySQL JSON 함수 기준입니다.
     *
     * 주의:
     * 이 필드는 조회 전용 Formula이므로 DB 컬럼이 새로 생기지 않습니다.
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

    /**
     * 배송팀 화면 전용 카테고리 표시값입니다.
     */
    @Transient
    private String deliveryCategoryText;

    /**
     * 배송팀 화면 전용 상품명 표시값입니다.
     */
    @Transient
    private String deliveryProductName;

    /**
     * 배송팀 화면 전용 사이즈 표시값입니다.
     */
    @Transient
    private String deliverySizeText;

    /**
     * 배송팀 화면 전용 색상 표시값입니다.
     */
    @Transient
    private String deliveryColorText;

    /**
     * 배송팀 화면 전용 수량 표시값입니다.
     */
    @Transient
    private String deliveryQuantityText;

    /**
     * 배송팀 화면 전용 옵션 표시값입니다.
     * optionJson 중 "옵션", "옵션2", "옵션3" ... 계열만 표시합니다.
     *
     * 예)
     * 원도어 좌경첩 / 하단 오픈 / 미니HW / **샘플**
     */
    @Transient
    private String deliveryOptionText;

    /**
     * 배송팀 모달/요약 표시용 한 줄 텍스트입니다.
     *
     * 예)
     * 카테고리 상부장 / 제품명 모듈 / 사이즈 500*800 / 색상 HC (히든 크림) / 수량 1개 / 옵션 원도어 좌경첩 / 하단 오픈 / 미니HW / **샘플**
     */
    @Transient
    private String deliveryProductSummaryText;
}
