package com.dev.HiddenBATHAuto.repository.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.task.Task;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>{

	Page<Task> findAllByOrderByIdDesc(Pageable pageable);
	
	@Query("SELECT t FROM Task t WHERE t.requestedBy.company.id = :companyId")
	Page<Task> findByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

}
