package com.dev.HiddenBATHAuto.repository.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.product.ProductColor;

@Repository
public interface ProductColorRepository extends JpaRepository<ProductColor, Long>{

}
