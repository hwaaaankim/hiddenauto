package com.dev.HiddenBATHAuto.repository.standard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.standard.StandardProduct;

@Repository
public interface StandardProductRepository extends JpaRepository<StandardProduct, Long>{

}
