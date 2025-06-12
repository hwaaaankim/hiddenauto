package com.dev.HiddenBATHAuto.repository.standard;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.standard.StandardProductPrice;

@Repository
public interface StandardProductPriceRepository extends JpaRepository<StandardProductPrice, Long>{

	@Query("SELECT s.price FROM StandardProductPrice s " +
	           "WHERE s.product.id = :productId " +
	           "AND (:sizeId IS NULL OR s.size.id = :sizeId) " +
	           "AND (:colorId IS NULL OR s.color.id = :colorId)")
    Optional<Integer> findPrice(@Param("productId") Long productId,
                                @Param("sizeId") Long sizeId,
                                @Param("colorId") Long colorId);
}
