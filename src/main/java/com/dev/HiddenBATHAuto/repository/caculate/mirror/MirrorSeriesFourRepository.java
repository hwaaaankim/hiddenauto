package com.dev.HiddenBATHAuto.repository.caculate.mirror;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesFour;

@Repository
public interface MirrorSeriesFourRepository extends JpaRepository<MirrorSeriesFour, Long>{
	MirrorSeriesFour findByStandardWidth(int standardWidth);
}
