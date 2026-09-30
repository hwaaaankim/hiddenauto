package com.dev.HiddenBATHAuto.repository.productmaster;

import com.dev.HiddenBATHAuto.model.productmaster.ProductStudioAsset;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductStudioAssetRepository extends JpaRepository<ProductStudioAsset, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from ProductStudioAsset a where a.id = :id")
  Optional<ProductStudioAsset> findForUpdate(@Param("id") String id);

  List<ProductStudioAsset> findByOwnerTypeAndOwnerIdOrderByCreatedAtAsc(String type, Long id);

  List<ProductStudioAsset> findTop200ByOwnerTypeAndCreatedAtBefore(String type, LocalDateTime time);
}
