package com.dev.HiddenBATHAuto.model.standard;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_standard_category")
@Data
public class StandardCategory {
   
	@Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // 대분류명

    @OneToMany(mappedBy = "category")
    private List<StandardProductSeries> productSeriesList;

    @OneToMany(mappedBy = "category")
    private List<StandardProduct> products;
}

