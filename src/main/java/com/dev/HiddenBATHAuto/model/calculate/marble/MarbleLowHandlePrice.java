package com.dev.HiddenBATHAuto.model.calculate.marble;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "tb_marble_low_handle")
public class MarbleLowHandlePrice {
   
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handle_type")
    private String handleType;

    @Column(name = "price")
    private int price;
}
