package com.dev.HiddenBATHAuto.repository.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.nonstandard.BigSort;

@Repository
public interface ProductBigSortRepository extends JpaRepository<BigSort, Long>{

	List<BigSort> findAllByOrderByBigSortIndexAsc();
	
	Page<BigSort> findAllByOrderByBigSortIndexAsc(Pageable pageable);
	
	BigSort findByName(String name);
	
	@Query("SELECT MAX(bigSortIndex) FROM BigSort")
	Optional<Integer> findFirstIndex();
	
	
}
