package com.dev.HiddenBATHAuto.repository.caculate.top;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.top.TopBasicPrice;

@Repository
public interface TopBasicPriceRepository extends JpaRepository<TopBasicPrice, Long> {
    Optional<TopBasicPrice> findByProductName(String productName);
}
