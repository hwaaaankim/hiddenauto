package com.dev.HiddenBATHAuto.repository.productmaster;

import com.dev.HiddenBATHAuto.model.productmaster.ProductActualMovement;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ProductActualMovementRepository
    extends JpaRepository<ProductActualMovement, Long> {
  List<ProductActualMovement> findByActualIdOrderByIdDesc(Long actualId);
}
