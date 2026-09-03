package com.dev.HiddenBATHAuto.repository.productmaster;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeValue;

public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, Long> {

    @EntityGraph(attributePaths = "group")
    @Query("select v from ProductAttributeValue v where v.id in :ids")
    List<ProductAttributeValue> findAllWithGroupByIdIn(@Param("ids") Collection<Long> ids);

    @EntityGraph(attributePaths = "group")
    @Query("select v from ProductAttributeValue v where v.id = :id")
    Optional<ProductAttributeValue> findWithGroupById(@Param("id") Long id);

    boolean existsByValueCode(String valueCode);

    boolean existsByGroupIdAndCustomerLabelIgnoreCase(Long groupId, String customerLabel);

    boolean existsByGroupIdAndManagementLabelIgnoreCase(Long groupId, String managementLabel);

    boolean existsByGroupIdAndProductionLabelIgnoreCase(Long groupId, String productionLabel);

    boolean existsByGroupIdAndCustomerLabelIgnoreCaseAndIdNot(Long groupId, String customerLabel, Long id);

    boolean existsByGroupIdAndManagementLabelIgnoreCaseAndIdNot(Long groupId, String managementLabel, Long id);

    boolean existsByGroupIdAndProductionLabelIgnoreCaseAndIdNot(Long groupId, String productionLabel, Long id);

    @Query("select coalesce(max(v.sortOrder), 0) from ProductAttributeValue v where v.group.id = :groupId")
    int findMaxSortOrderByGroupId(@Param("groupId") Long groupId);
}
