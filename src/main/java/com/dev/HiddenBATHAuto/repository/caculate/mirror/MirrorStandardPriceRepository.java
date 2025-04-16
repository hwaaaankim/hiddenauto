package com.dev.HiddenBATHAuto.repository.caculate.mirror;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorStandardPrice;

@Repository
public interface MirrorStandardPriceRepository extends JpaRepository<MirrorStandardPrice, Long> {
    Optional<MirrorStandardPrice> findByProductName(String productName);
}

