package com.dev.HiddenBATHAuto.repository.process;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.process.ProcessDefinition;

public interface ProcessDefinitionRepository extends JpaRepository<ProcessDefinition, Long> {

    List<ProcessDefinition> findAllByOrderByCreatedAtDesc();

    boolean existsByProcessKey(String processKey);

    Optional<ProcessDefinition> findWithAllById(Long id);
}