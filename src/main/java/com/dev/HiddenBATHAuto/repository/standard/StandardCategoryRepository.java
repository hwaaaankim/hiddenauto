package com.dev.HiddenBATHAuto.repository.standard;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.standard.StandardCategory;

@Repository
public interface StandardCategoryRepository extends JpaRepository<StandardCategory, Long> {
	List<StandardCategory> findAllByOrderByNameAsc();

}