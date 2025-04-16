package com.dev.HiddenBATHAuto.repository.caculate.flap;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.flap.FlapBasicPrice;

@Repository
public interface FlapBasicPriceRepository extends JpaRepository<FlapBasicPrice, Long> {
	Optional<FlapBasicPrice> findByProductName(String productName);
}
