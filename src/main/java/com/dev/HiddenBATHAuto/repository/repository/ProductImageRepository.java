package com.dev.HiddenBATHAuto.repository.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.nonstandard.ProductImage;

import jakarta.transaction.Transactional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long>{

	@Transactional
	int deleteAllByProductId(Long id);
	
	List<ProductImage> findAllByProductId(Long id);
}
