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
@Table(name = "tb_body_only")
public class BodyOnly {
   
	@Id 
	@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "standard_width")
    private int standardWidth;

    @Column(name = "price")
    private int price;
}