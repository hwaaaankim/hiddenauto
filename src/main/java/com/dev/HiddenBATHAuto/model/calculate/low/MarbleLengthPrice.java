package com.dev.HiddenBATHAuto.model.calculate.low;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "tb_marble_length_price")
public class MarbleLengthPrice {
    
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "standard_width")
    private int standardWidth;

    @Column(name = "price1")
    private int price1;

    @Column(name = "price2")
    private int price2;

    @Column(name = "price3")
    private int price3;

    @Column(name = "additional_fee")
    private int additionalFee;
}