package com.dev.HiddenBATHAuto.repository.order;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.task.OrderImage;

@Repository
public interface OrderImageRepository extends JpaRepository<OrderImage, Long>{
	List<OrderImage> findByOrderIsNullAndTypeAndPathContaining(String type, String pathKeyword);
}
