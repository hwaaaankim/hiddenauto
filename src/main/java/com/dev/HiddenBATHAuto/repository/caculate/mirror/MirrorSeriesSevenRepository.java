package com.dev.HiddenBATHAuto.repository.caculate.mirror;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesSeven;

@Repository
public interface MirrorSeriesSevenRepository extends JpaRepository<MirrorSeriesSeven, Long>{
	MirrorSeriesSeven findByStandardWidth(int standardWidth);
}
