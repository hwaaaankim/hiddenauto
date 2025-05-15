package com.dev.HiddenBATHAuto.model.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_district")
@Data
public class District {
    
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    private Province province;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    private City city; // optional for 서울특별시, 세종 등
}

