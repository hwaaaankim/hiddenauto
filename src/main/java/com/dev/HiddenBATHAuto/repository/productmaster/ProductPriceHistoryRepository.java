package com.dev.HiddenBATHAuto.repository.productmaster;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.productmaster.ProductPriceHistory;

public interface ProductPriceHistoryRepository extends JpaRepository<ProductPriceHistory, Long> {

    List<ProductPriceHistory> findByProductIdOrderByCreatedAtDescIdDesc(Long productId);
}
