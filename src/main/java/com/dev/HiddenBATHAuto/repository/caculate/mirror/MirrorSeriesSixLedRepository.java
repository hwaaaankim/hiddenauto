package com.dev.HiddenBATHAuto.repository.caculate.mirror;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesSixLed;

@Repository
public interface MirrorSeriesSixLedRepository extends JpaRepository<MirrorSeriesSixLed, Long>{
	MirrorSeriesSixLed findByStandardWidth(int standardWidth);
}
