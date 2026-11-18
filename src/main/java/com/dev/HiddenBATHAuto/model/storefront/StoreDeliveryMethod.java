package com.dev.HiddenBATHAuto.model.storefront;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tb_store_delivery_method")
@Getter
@Setter
public class StoreDeliveryMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "delivery_fee", nullable = false)
    private int deliveryFee;

    @Column(name = "packing_fee", nullable = false)
    private int packingFee;

    @Column(nullable = false, length = 1000)
    private String description;
}
