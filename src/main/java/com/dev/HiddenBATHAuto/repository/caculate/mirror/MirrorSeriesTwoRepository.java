package com.dev.HiddenBATHAuto.repository.caculate.mirror;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesTwo;

@Repository
public interface MirrorSeriesTwoRepository extends JpaRepository<MirrorSeriesTwo, Long>{
	MirrorSeriesTwo findByStandardWidth(int standardWidth);
}
