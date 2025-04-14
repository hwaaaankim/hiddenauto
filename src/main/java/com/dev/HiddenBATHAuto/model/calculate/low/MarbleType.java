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
@Table(name = "tb_marble_type")
public class MarbleType {
    
	@Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "marble_name")
    private String marbleName;

    @Column(name = "unit_price")
    private int unitPrice;
}