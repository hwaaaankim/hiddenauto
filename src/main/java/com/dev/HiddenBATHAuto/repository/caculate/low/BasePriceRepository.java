package com.dev.HiddenBATHAuto.repository.caculate.low;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.low.BasePrice;

@Repository
public interface BasePriceRepository extends JpaRepository<BasePrice, Long> {
	BasePrice findByStandardWidth(int width);
}
