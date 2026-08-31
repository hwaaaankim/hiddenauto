package com.dev.HiddenBATHAuto.repository.as;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.task.as.audit.AsChangeEvent;

public interface AsChangeEventRepository extends JpaRepository<AsChangeEvent, Long> {

    @EntityGraph(attributePaths = {"fields"})
    @Query("""
            select distinct e
              from AsChangeEvent e
             where e.asTaskIdSnapshot = :asTaskId
             order by e.id desc
            """)
    List<AsChangeEvent> findHistory(@Param("asTaskId") Long asTaskId, Pageable pageable);
}
