package com.dev.HiddenBATHAuto.model.auth;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tb_province")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Province {
    
	@Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "province", cascade = CascadeType.ALL)
    private List<City> cities = new ArrayList<>();
    
    // 구 목록 (중간에 City 없이 바로 연결되는 경우 포함)
    @OneToMany(mappedBy = "province", cascade = CascadeType.ALL)
    private List<District> districts = new ArrayList<>();
    
}




