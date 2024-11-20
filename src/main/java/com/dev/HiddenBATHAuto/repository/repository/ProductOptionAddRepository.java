package com.dev.HiddenBATHAuto.repository.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.product.ProductOptionAdd;

@Repository
public interface ProductOptionAddRepository extends JpaRepository<ProductOptionAdd, Long> {
}