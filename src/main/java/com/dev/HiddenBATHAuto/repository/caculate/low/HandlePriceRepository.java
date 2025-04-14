package com.dev.HiddenBATHAuto.repository.caculate.low;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.low.HandlePrice;

@Repository
public interface HandlePriceRepository extends JpaRepository<HandlePrice, Long> {
	
}
