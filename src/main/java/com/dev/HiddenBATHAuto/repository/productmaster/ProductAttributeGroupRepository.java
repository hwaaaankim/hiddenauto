package com.dev.HiddenBATHAuto.repository.productmaster;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeRole;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeGroup;

public interface ProductAttributeGroupRepository extends JpaRepository<ProductAttributeGroup, Long> {

    @EntityGraph(attributePaths = "values")
    List<ProductAttributeGroup> findAllByOrderBySortOrderAscIdAsc();

    @EntityGraph(attributePaths = "values")
    @Query("select g from ProductAttributeGroup g where g.id = :id")
    Optional<ProductAttributeGroup> findWithValuesById(@Param("id") Long id);

    boolean existsByGroupCode(String groupCode);

    boolean existsByCustomerLabelIgnoreCase(String customerLabel);

    boolean existsByManagementLabelIgnoreCase(String managementLabel);

    boolean existsByProductionLabelIgnoreCase(String productionLabel);

    boolean existsByCustomerLabelIgnoreCaseAndIdNot(String customerLabel, Long id);

    boolean existsByManagementLabelIgnoreCaseAndIdNot(String managementLabel, Long id);

    boolean existsByProductionLabelIgnoreCaseAndIdNot(String productionLabel, Long id);

    boolean existsBySystemRole(ProductAttributeRole systemRole);

    boolean existsBySystemRoleAndIdNot(ProductAttributeRole systemRole, Long id);

    @Query("select coalesce(max(g.sortOrder), 0) from ProductAttributeGroup g")
    int findMaxSortOrder();
}
