package com.dev.HiddenBATHAuto.repository.productmaster;

import com.dev.HiddenBATHAuto.model.productmaster.ProductStudioAsset;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStudioAssetRepository extends JpaRepository<ProductStudioAsset, String> {
  List<ProductStudioAsset> findByOwnerTypeAndOwnerIdOrderByCreatedAtAsc(String type, Long id);

  List<ProductStudioAsset> findTop200ByOwnerTypeAndCreatedAtBefore(String type, LocalDateTime time);
}
