package com.dev.HiddenBATHAuto.repository.storefront;

import com.dev.HiddenBATHAuto.model.storefront.StoreDeliveryMethod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreDeliveryMethodRepository extends JpaRepository<StoreDeliveryMethod, Long> {
    List<StoreDeliveryMethod> findAllByOrderByIdAsc();
}
