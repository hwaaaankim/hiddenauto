package com.dev.HiddenBATHAuto.repository.caculate;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;

@Repository
public interface DeliveryMethodRepository extends JpaRepository<DeliveryMethod, Long> {
	List<DeliveryMethod> findAllByOrderByMethodNameAsc();

}
