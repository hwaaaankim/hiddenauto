package com.dev.HiddenBATHAuto.model.standard;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_standard_product_price")
@Data
public class StandardProductPrice {
    
	@Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private StandardProduct product;

    @ManyToOne
    @JoinColumn(name = "size_id", nullable = true) // 명시적
    private StandardProductSize size;

    @ManyToOne
    @JoinColumn(name = "color_id", nullable = true) // 명시적
    private StandardProductColor color;

    private Integer price;
}

