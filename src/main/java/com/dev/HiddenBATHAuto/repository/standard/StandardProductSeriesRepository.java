package com.dev.HiddenBATHAuto.repository.standard;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.standard.StandardProductSeries;

@Repository
public interface StandardProductSeriesRepository extends JpaRepository<StandardProductSeries, Long> {

	List<StandardProductSeries> findByCategoryId(Long categoryId);

	List<StandardProductSeries> findByCategory_IdOrderByNameAsc(Long categoryId);

	Optional<StandardProductSeries> findByIdAndCategory_Id(Long id, Long categoryId);
}
