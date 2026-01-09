package com.dev.HiddenBATHAuto.model.task;

import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
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

    @ManyToOne
    private Order order;

    private String productName;
    private int quantity;

    @Column(columnDefinition = "TEXT")
    private String optionJson; // JSON 문자열로 비규격 옵션 저장

    @Transient
    private Map<String, String> parsedOptionMap;

    // ✅ 목록/상세에서 깔끔 출력용(키:값 <br>)
    @Transient
    private String formattedOptionHtml;

    // ✅ "카테고리: ... / 제품명: ... / ..." 한 줄 요약
    @Transient
    private String formattedOptionText;
}