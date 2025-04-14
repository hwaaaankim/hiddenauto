package com.dev.HiddenBATHAuto.repository.caculate.marble;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowOptionPrice;

@Repository
public interface MarbleLowOptionPriceRepository extends JpaRepository<MarbleLowOptionPrice, Long> {
}
