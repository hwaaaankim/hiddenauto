package com.dev.HiddenBATHAuto.repository.caculate.marble;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowHandlePrice;

@Repository
public interface MarbleLowHandlePriceRepository extends JpaRepository<MarbleLowHandlePrice, Long> {
}
