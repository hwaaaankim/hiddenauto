package com.dev.HiddenBATHAuto.repository.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.Province;

@Repository
public interface CityRepository extends JpaRepository<City, Long> {
	
	List<City> findByProvinceId(Long provinceId);
	Optional<City> findByNameAndProvince(String name, Province province);
	
	List<City> findByProvinceIdOrderByNameAsc(Long provinceId);
}
