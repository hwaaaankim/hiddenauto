package com.dev.HiddenBATHAuto.repository.caculate.low;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.low.BodyOnly;

@Repository
public interface BodyOnlyRepository extends JpaRepository<BodyOnly, Long> {
	
}
