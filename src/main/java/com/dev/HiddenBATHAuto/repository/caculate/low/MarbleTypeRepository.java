package com.dev.HiddenBATHAuto.repository.caculate.low;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.low.MarbleType;

@Repository
public interface MarbleTypeRepository extends JpaRepository<MarbleType, Long> {
	MarbleType findByMarbleName(String name);
}