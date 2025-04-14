package com.dev.HiddenBATHAuto.repository.caculate.low;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.low.OptionPrice;

@Repository
public interface OptionPriceRepository extends JpaRepository<OptionPrice, Long> {
	OptionPrice findByOptionName(String name);
}
