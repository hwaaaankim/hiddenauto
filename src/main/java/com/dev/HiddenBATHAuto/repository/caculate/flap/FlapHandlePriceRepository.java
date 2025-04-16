package com.dev.HiddenBATHAuto.repository.caculate.flap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.flap.FlapHandlePrice;

@Repository
public interface FlapHandlePriceRepository extends JpaRepository<FlapHandlePrice, Long> {
	
}

