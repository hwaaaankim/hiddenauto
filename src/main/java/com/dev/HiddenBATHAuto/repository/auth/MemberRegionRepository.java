package com.dev.HiddenBATHAuto.repository.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.MemberRegion;

@Repository
public interface MemberRegionRepository extends JpaRepository<MemberRegion, Long>{

	List<MemberRegion> findByDistrict(District district);

}
