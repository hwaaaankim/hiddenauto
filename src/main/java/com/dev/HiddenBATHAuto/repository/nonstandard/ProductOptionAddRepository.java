package com.dev.HiddenBATHAuto.repository.nonstandard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.nonstandard.ProductOptionAdd;

@Repository
public interface ProductOptionAddRepository extends JpaRepository<ProductOptionAdd, Long> {
}