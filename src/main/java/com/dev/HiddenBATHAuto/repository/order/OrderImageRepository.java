package com.dev.HiddenBATHAuto.repository.order;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.task.OrderImage;

@Repository
public interface OrderImageRepository extends JpaRepository<OrderImage, Long>{
	List<OrderImage> findByOrderIsNullAndTypeAndPathContaining(String type, String pathKeyword);
	
	List<OrderImage> findByOrder_IdInAndTypeIgnoreCase(Collection<Long> orderIds, String type);

    Optional<OrderImage> findByIdAndOrder_IdAndTypeIgnoreCase(Long id, Long orderId, String type);
}
