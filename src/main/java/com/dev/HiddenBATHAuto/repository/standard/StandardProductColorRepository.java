package com.dev.HiddenBATHAuto.repository.standard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.standard.StandardProductColor;

@Repository
public interface StandardProductColorRepository extends JpaRepository<StandardProductColor, Long>{

}
