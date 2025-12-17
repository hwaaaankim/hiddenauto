package com.dev.HiddenBATHAuto.repository.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Province;

@Repository
public interface DistrictRepository extends JpaRepository<District, Long> {

	List<District> findByProvinceIdAndCityIsNull(Long provinceId);
    List<District> findByCityId(Long cityId);
    List<District> findByProvinceId(Long provinceId);
    
	Optional<District> findByNameAndProvinceAndCity(String name, Province province, City city);
	Optional<District> findByNameAndProvince_NameAndCity_Name(String guName, String doName, String siName);
	Optional<District> findByNameAndProvince_Name(String guName, String doName); // 시 없는 경우

	@Query(value = """
	        SELECT d.* FROM tb_district d
	        JOIN tb_province p ON d.province_id = p.id
	        LEFT JOIN tb_city c ON d.city_id = c.id
	        WHERE d.name = :guName
	          AND p.name LIKE CONCAT('%', :doKeyword, '%')
	          AND (
	            (:siKeyword IS NULL AND d.city_id IS NULL)
	            OR (:siKeyword IS NOT NULL AND c.name LIKE CONCAT('%', :siKeyword, '%'))
	          )
	        LIMIT 1
	        """, nativeQuery = true)
    Optional<District> findByAddressPartsSingleNative(
        @Param("guName") String guName,
        @Param("doKeyword") String doKeyword,
        @Param("siKeyword") String siKeyword
    );
	
	List<District> findByProvinceIdAndCityIsNullOrderByNameAsc(Long provinceId); // 서울/세종 등
    List<District> findByCityIdOrderByNameAsc(Long cityId); // 일반 케이스


}
