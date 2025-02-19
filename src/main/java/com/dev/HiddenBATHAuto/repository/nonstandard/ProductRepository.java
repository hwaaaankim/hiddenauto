package com.dev.HiddenBATHAuto.repository.nonstandard;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.nonstandard.Product;
import com.dev.HiddenBATHAuto.model.nonstandard.ProductSort;
import com.dev.HiddenBATHAuto.model.nonstandard.Series;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>{

	Product findByName(String name);
	
	List<Product> findAllByProductSort(ProductSort productSort);
	
	List<Product> findAllBySeries(Series series);
	
	Page<Product> findAllByOrderByIdDesc(Pageable pageble);
	
	Page<Product> findAllByProductSort(Pageable pageable, ProductSort productSort);
	
	Page<Product> findAllBySeries(Pageable pageable, Series bigSort);
	
	Page<Product> findAllByNameContains(Pageable pageble, String name);
	
	Page<Product> findAllByNameContainsOrderByIdDesc(Pageable pageble, String name);
	
	Page<Product> findAllByProductSortAndNameContains(Pageable pageable, ProductSort productSort, String name);
	
	Page<Product> findAllBySeriesAndNameContains(Pageable pageable, Series series, String name);
	
	Page<Product> findAllByProductSortAndNameContainsOrderByIdDesc(Pageable pageable, ProductSort productSort, String name);
	
	Page<Product> findAllBySeriesAndNameContainsOrderByIdDesc(Pageable pageable, Series series, String name);

	Page<Product> findAllByOrderByProductIndexAsc(Pageable pageble);
	
	Page<Product> findAllByProductSortOrderByProductIndexAsc(Pageable pageable, ProductSort productSort);
	
	Page<Product> findAllBySeriesOrderByProductIndexAsc(Pageable pageable, Series series);
	
	Page<Product> findAllByProductSortAndNameContainsOrderByProductIndexAsc(Pageable pageable, ProductSort productSort, String name);
	
	Page<Product> findAllBySeriesAndNameContainsOrderByProductIndexAsc(Pageable pageable, Series series, String name);

	List<Product> findAllByOrderByProductIndexAsc();
	
	@Query("SELECT MAX(productIndex) FROM Product")
	Optional<Integer> findFirstIndex();
	
}



























