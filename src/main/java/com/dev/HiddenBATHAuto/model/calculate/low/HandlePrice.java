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
@Table(name = "tb_handle_price")
public class HandlePrice {
    
	@Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handle_type")
    private String handleType;

    @Column(name = "price")
    private int price;
}