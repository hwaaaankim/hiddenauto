package com.dev.HiddenBATHAuto.repository.caculate.mirror;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesOneLed;

@Repository
public interface MirrorSeriesOneLedRepository extends JpaRepository<MirrorSeriesOneLed, Long>{

}
