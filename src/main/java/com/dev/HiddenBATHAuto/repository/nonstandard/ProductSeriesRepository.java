package com.dev.HiddenBATHAuto.repository.nonstandard;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.nonstandard.ProductSort;
import com.dev.HiddenBATHAuto.model.nonstandard.Series;

@Repository
public interface ProductSeriesRepository extends JpaRepository<Series, Long>{
	
	List<Series> findAllByProductSort(ProductSort productsort);
	
	List<Series> findAllByOrderBySeriesIndexAsc();
	
	List<Series> findAllByProductSortOrderBySeriesIndexAsc(ProductSort productsort);
	
	Page<Series> findAllByOrderBySeriesIndexAsc(Pageable pageable);
	
	Page<Series> findAllByProductSortOrderBySeriesIndexAsc(Pageable pageable, ProductSort productsort);
	
	@Query("SELECT MAX(seriesIndex) FROM Series")
	Optional<Integer> findFirstIndex();
	
	@Query("SELECT ms FROM Series ms " +
		       "JOIN FETCH ms.productSort bs " +
		       "LEFT JOIN FETCH ms.products p " +
		       "WHERE bs.id = :productSortId")
	List<Series> findMiddleSortsByBigSortIdWithOrderedProducts(@Param("productSortId") Long productSortId);
	
	List<Series> findByName(String name);

    List<Series> findTop200BySeriesRepImageRoadIsNullOrSeriesRepImageRoadEquals(String blank);

}
