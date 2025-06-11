package com.dev.HiddenBATHAuto.model.standard;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_standard_product_series")
@Data
public class StandardProductSeries {
    
	@Id 
	@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // 중분류명

    @ManyToOne
    @JoinColumn(name = "category_id")
    private StandardCategory category;

    @OneToMany(mappedBy = "productSeries")
    private List<StandardProduct> products;
}

