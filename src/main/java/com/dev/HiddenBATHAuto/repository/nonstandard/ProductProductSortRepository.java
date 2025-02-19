package com.dev.HiddenBATHAuto.repository.nonstandard;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.nonstandard.ProductSort;

@Repository
public interface ProductProductSortRepository extends JpaRepository<ProductSort, Long>{

	List<ProductSort> findAllByOrderByProductSortIndexAsc();
	
	Page<ProductSort> findAllByOrderByProductSortIndexAsc(Pageable pageable);
	
	ProductSort findByName(String name);
	
	@Query("SELECT MAX(productSortIndex) FROM ProductSort")
	Optional<Integer> findFirstIndex();
}
