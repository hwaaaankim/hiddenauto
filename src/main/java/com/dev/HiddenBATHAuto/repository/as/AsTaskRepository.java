package com.dev.HiddenBATHAuto.repository.as;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.task.AsTask;

@Repository
public interface AsTaskRepository extends JpaRepository<AsTask, Long> {

	@Query("SELECT a FROM AsTask a WHERE a.requestedBy.company.id = :companyId")
	Page<AsTask> findByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

}
