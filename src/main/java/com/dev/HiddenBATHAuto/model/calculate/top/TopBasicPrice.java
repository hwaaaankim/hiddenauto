package com.dev.HiddenBATHAuto.model.calculate.top;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

//기본 가격
@Entity
@Data
@Table(name = "tb_top_basic_price")
public class TopBasicPrice {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "basic_price")
    private int basicPrice;
    
}