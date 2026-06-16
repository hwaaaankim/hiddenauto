package com.dev.HiddenBATHAuto.repository.amount;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.dev.HiddenBATHAuto.model.amount.AmountCustomerMaster;

public interface AmountCustomerMasterRepository extends JpaRepository<AmountCustomerMaster, Long>, JpaSpecificationExecutor<AmountCustomerMaster> {

    Optional<AmountCustomerMaster> findFirstByCustomerCode(String customerCode);

    List<AmountCustomerMaster> findByCustomerCodeIn(Collection<String> customerCodes);
}
