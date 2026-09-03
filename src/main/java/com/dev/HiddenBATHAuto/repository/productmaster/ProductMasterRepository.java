package com.dev.HiddenBATHAuto.repository.productmaster;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.productmaster.ProductMaster;

import jakarta.persistence.LockModeType;

public interface ProductMasterRepository
        extends JpaRepository<ProductMaster, Long>, JpaSpecificationExecutor<ProductMaster> {

    boolean existsByProductNameIgnoreCase(String productName);

    boolean existsByProductNameIgnoreCaseAndIdNot(String productName, Long id);

    boolean existsByProductCode(String productCode);

    boolean existsByProductCodeAndIdNot(String productCode, Long id);

    boolean existsByCatalogCode(String catalogCode);

    boolean existsByConfigurationHash(String configurationHash);

    boolean existsByConfigurationHashAndIdNot(String configurationHash, Long id);

    Optional<ProductMaster> findByCatalogCodeIgnoreCase(String catalogCode);

    Optional<ProductMaster> findByProductCode(String productCode);

    Optional<ProductMaster> findByQrPublicToken(String qrPublicToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductMaster p where p.id = :id")
    Optional<ProductMaster> findForUpdate(@Param("id") Long id);
}
