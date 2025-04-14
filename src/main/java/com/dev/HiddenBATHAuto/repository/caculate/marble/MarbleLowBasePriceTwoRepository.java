package com.dev.HiddenBATHAuto.repository.caculate.marble;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowBasePriceTwo;

@Repository
public interface MarbleLowBasePriceTwoRepository extends JpaRepository<MarbleLowBasePriceTwo, Long> {
	
}
