package com.dev.HiddenBATHAuto.repository.caculate.top;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.top.TopHandlePrice;

@Repository
public interface TopHandlePriceRepository extends JpaRepository<TopHandlePrice, Long> {
    Optional<TopHandlePrice> findByHandleName(String handleName);
}
