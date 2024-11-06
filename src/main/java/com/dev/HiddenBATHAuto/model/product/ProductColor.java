package com.dev.HiddenBATHAuto.model.product;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name="tb_product_color")
@Data
public class ProductColor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="PRODUCT_COLOR_ID")
    private Long id;
    
    @Column(name="PRODUCT_COLOR_SUBJECT")
    private String productColorSubject;

    @Column(name="PRODUCT_COLOR_ROAD")
    private String productColorRoad;
    
    @Column(name="PRODUCT_COLOR_PATH")
    private String productColorPath;
    
    @ManyToMany(mappedBy = "productColors", fetch = FetchType.EAGER)
    @JsonBackReference // 순환 참조 방지
    @JsonIgnore
    private List<Product> products;
}
