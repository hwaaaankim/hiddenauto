package com.dev.HiddenBATHAuto.repository.nonstandard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.nonstandard.ProductColor;

@Repository
public interface ProductColorRepository extends JpaRepository<ProductColor, Long>{

}
