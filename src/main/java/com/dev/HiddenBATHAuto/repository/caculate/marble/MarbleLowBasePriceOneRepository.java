package com.dev.HiddenBATHAuto.repository.caculate.marble;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowBasePriceOne;

@Repository
public interface MarbleLowBasePriceOneRepository extends JpaRepository<MarbleLowBasePriceOne, Long> {
	MarbleLowBasePriceOne findByStandardWidth(int standardWidth);
}