package com.dev.HiddenBATHAuto.repository.productmaster;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.productmaster.ProductStockMovementAddon;

public interface ProductStockMovementAddonRepository extends JpaRepository<ProductStockMovementAddon, Long> {

    boolean existsByOptionValueId(Long optionValueId);

    boolean existsByOptionValue_Group_Id(Long groupId);

    boolean existsByMovement_Product_IdAndOptionValue_Id(Long productId, Long optionValueId);
}
