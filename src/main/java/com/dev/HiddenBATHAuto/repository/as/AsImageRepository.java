package com.dev.HiddenBATHAuto.repository.as;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.task.AsImage;

@Repository
public interface AsImageRepository extends JpaRepository<AsImage, Long> {
	
	List<AsImage> findByAsTask_Id(Long asTaskId);
}
