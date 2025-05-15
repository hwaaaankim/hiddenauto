package com.dev.HiddenBATHAuto.repository.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Province;

@Repository
public interface ProvinceRepository extends JpaRepository<Province, Long> {
	
    Optional<Province> findByName(String name);
}
