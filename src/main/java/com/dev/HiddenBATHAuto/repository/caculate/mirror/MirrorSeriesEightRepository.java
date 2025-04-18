package com.dev.HiddenBATHAuto.repository.caculate.mirror;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesEight;

@Repository
public interface MirrorSeriesEightRepository extends JpaRepository<MirrorSeriesEight, Long>{
	MirrorSeriesEight findByStandardWidth(int standardWidth);
}
