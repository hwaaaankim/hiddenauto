package com.dev.HiddenBATHAuto.repository.amount;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.dev.HiddenBATHAuto.model.amount.AmountItemMaster;

public interface AmountItemMasterRepository extends JpaRepository<AmountItemMaster, Long>, JpaSpecificationExecutor<AmountItemMaster> {

    Optional<AmountItemMaster> findFirstByItemCode(String itemCode);

    List<AmountItemMaster> findByItemCodeIn(Collection<String> itemCodes);
}
