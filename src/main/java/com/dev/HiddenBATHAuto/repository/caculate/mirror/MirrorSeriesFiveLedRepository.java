package com.dev.HiddenBATHAuto.repository.caculate.mirror;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesFiveLed;

@Repository
public interface MirrorSeriesFiveLedRepository extends JpaRepository<MirrorSeriesFiveLed, Long>{
	MirrorSeriesFiveLed findByStandardWidth(int standardWidth);
}
