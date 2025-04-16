package com.dev.HiddenBATHAuto.repository.caculate.flap;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.flap.FlapOptionPrice;

@Repository
public interface FlapOptionPriceRepository extends JpaRepository<FlapOptionPrice, Long> {
	Optional<FlapOptionPrice> findByOptionName(String optionName);
}