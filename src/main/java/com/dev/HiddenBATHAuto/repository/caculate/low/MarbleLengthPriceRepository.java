package com.dev.HiddenBATHAuto.repository.caculate.low;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.low.MarbleLengthPrice;

@Repository
public interface MarbleLengthPriceRepository extends JpaRepository<MarbleLengthPrice, Long> {
	MarbleLengthPrice findByStandardWidth(int width);
}
