package com.dev.HiddenBATHAuto.repository.caculate.slide;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.calculate.slide.SlideHandlePrice;

@Repository
public interface SlideHandlePriceRepository extends JpaRepository<SlideHandlePrice, Long>{

}
