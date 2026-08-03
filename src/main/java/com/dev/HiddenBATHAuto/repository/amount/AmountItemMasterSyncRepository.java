package com.dev.HiddenBATHAuto.repository.amount;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.amount.AmountItemMaster;

@Repository
public interface AmountItemMasterSyncRepository
        extends JpaRepository<AmountItemMaster, Long> {

    /**
     * item_code는 UNIQUE 컬럼이 아니므로 동일 제품코드가 여러 건일 수 있습니다.
     * 동일 코드가 존재하면 해당되는 모든 엔티티를 가져옵니다.
     */
    List<AmountItemMaster> findAllByItemCodeIn(
            Collection<String> itemCodes
    );
}