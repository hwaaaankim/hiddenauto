package com.dev.HiddenBATHAuto.repository.caculate.low;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.low.WashPrice;

@Repository
public interface WashPriceRepository extends JpaRepository<WashPrice, Long> {
	WashPrice findByBasinType(String type);
}
