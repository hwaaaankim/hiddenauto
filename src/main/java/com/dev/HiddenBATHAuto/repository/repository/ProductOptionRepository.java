package com.dev.HiddenBATHAuto.repository.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.nonstandard.ProductOption;

@Repository
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long>{

}
