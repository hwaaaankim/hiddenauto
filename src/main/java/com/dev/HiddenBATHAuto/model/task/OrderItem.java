package com.dev.HiddenBATHAuto.model.task;

import java.util.Map;

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

    @Transient
    private Map<String, String> parsedOptionMap;

    @Transient
    private String formattedOptionHtml;

    @Transient
    private String formattedOptionText;
}