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
@Table(name = "tb_drawer")
public class Drawer {
    
	@Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "standard_width")
    private int standardWidth;

    @Column(name = "under600")
    private int under600;

    @Column(name = "over600")
    private int over600;
}
