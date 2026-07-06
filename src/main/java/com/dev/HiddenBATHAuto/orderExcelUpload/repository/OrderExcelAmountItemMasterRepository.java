package com.dev.HiddenBATHAuto.orderExcelUpload.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.amount.AmountItemMaster;

public interface OrderExcelAmountItemMasterRepository extends JpaRepository<AmountItemMaster, Long> {
    Optional<AmountItemMaster> findFirstByItemNameOrderByIdDesc(String itemName);
    List<AmountItemMaster> findByItemName(String itemName);
}
