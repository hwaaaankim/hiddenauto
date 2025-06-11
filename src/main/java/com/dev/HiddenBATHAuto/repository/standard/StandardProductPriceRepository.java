package com.dev.HiddenBATHAuto.repository.standard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.standard.StandardProductPrice;

@Repository
public interface StandardProductPriceRepository extends JpaRepository<StandardProductPrice, Long>{

}
