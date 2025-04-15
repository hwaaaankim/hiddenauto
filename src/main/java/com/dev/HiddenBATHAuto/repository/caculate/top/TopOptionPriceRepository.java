package com.dev.HiddenBATHAuto.repository.caculate.top;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.top.TopOptionPrice;

@Repository
public interface TopOptionPriceRepository extends JpaRepository<TopOptionPrice, Long> {
    Optional<TopOptionPrice> findByOptionName(String optionName);
}