package com.dev.HiddenBATHAuto.repository.productmaster;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeImage;

public interface ProductAttributeImageRepository extends JpaRepository<ProductAttributeImage, Long> {

    Optional<ProductAttributeImage> findByPublicToken(String publicToken);

    List<ProductAttributeImage> findAllByGroup_IdOrderBySortOrderAscIdAsc(Long groupId);

    List<ProductAttributeImage> findAllByOptionValue_IdOrderBySortOrderAscIdAsc(Long valueId);

    List<ProductAttributeImage> findAllByGroup_IdInOrderBySortOrderAscIdAsc(Collection<Long> groupIds);

    List<ProductAttributeImage> findAllByOptionValue_IdInOrderBySortOrderAscIdAsc(Collection<Long> valueIds);

    List<ProductAttributeImage> findAllByOptionValue_Group_IdOrderBySortOrderAscIdAsc(Long groupId);
}
