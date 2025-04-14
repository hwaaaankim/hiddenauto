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
@Table(name = "tb_marble_low_body")
public class MarbleLowBodyOnly {
    
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "standard_width")
    private int standardWidth;

    @Column(name = "price")
    private int price;
}