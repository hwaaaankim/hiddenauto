package com.dev.HiddenBATHAuto.repository.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.product.BigSort;
import com.dev.HiddenBATHAuto.model.product.MiddleSort;

@Repository
public interface ProductMiddleSortRepository extends JpaRepository<MiddleSort, Long>{
	
	List<MiddleSort> findAllByBigSort(BigSort bigsort);
	
	List<MiddleSort> findAllByOrderByMiddleSortIndexAsc();
	
	List<MiddleSort> findAllByBigSortOrderByMiddleSortIndexAsc(BigSort bigsort);
	
	Page<MiddleSort> findAllByOrderByMiddleSortIndexAsc(Pageable pageable);
	
	Page<MiddleSort> findAllByBigSortOrderByMiddleSortIndexAsc(Pageable pageable, BigSort bigsort);
	
	@Query("SELECT MAX(middleSortIndex) FROM MiddleSort")
	Optional<Integer> findFirstIndex();
	
	@Query("SELECT ms FROM MiddleSort ms " +
		       "JOIN FETCH ms.bigSort bs " +
		       "LEFT JOIN FETCH ms.products p " +
		       "WHERE bs.id = :bigSortId " +
		       "AND (p.order = true OR p.order IS NULL)")
	List<MiddleSort> findMiddleSortsByBigSortIdWithOrderedProducts(@Param("bigSortId") Long bigSortId);

}
