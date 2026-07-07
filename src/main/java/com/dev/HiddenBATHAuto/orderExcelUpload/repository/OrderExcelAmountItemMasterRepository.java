package com.dev.HiddenBATHAuto.orderExcelUpload.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.amount.AmountItemMaster;

public interface OrderExcelAmountItemMasterRepository extends JpaRepository<AmountItemMaster, Long> {
    Optional<AmountItemMaster> findFirstByItemNameOrderByIdDesc(String itemName);
    List<AmountItemMaster> findByItemName(String itemName);

    @Query(value = "select * from tb_amount_item_master m where replace(coalesce(m.item_name, ''), ' ', '') = :normalizedItemName order by m.id desc limit 1", nativeQuery = true)
    Optional<AmountItemMaster> findFirstByItemNameWithoutSpaces(@Param("normalizedItemName") String normalizedItemName);

    @Query(value = """
            select distinct coalesce(nullif(trim(m.middle_category_name), ''), '분류없음')
            from tb_amount_item_master m
            where coalesce(trim(m.middle_category_name), '') <> ''
            order by 1
            """, nativeQuery = true)
    List<String> findDistinctMiddleCategoryNames();

    @Query(value = """
            select distinct coalesce(nullif(trim(m.middle_category_name), ''), '분류없음')
            from tb_amount_item_master m
            where replace(coalesce(m.category_name, ''), ' ', '') = :normalizedCategoryName
              and coalesce(trim(m.middle_category_name), '') <> ''
            order by 1
            """, nativeQuery = true)
    List<String> findDistinctMiddleCategoryNamesByCategoryName(@Param("normalizedCategoryName") String normalizedCategoryName);
}
