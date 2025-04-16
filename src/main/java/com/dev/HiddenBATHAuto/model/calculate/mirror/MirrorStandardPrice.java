package com.dev.HiddenBATHAuto.model.calculate.mirror;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_mirror_standard_price")
@Data
public class MirrorStandardPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "price_led_off")
    private int priceLedOff;

    @Column(name = "price_led_on")
    private int priceLedOn;
}

