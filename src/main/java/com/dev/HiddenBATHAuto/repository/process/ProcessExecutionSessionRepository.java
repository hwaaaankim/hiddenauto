package com.dev.HiddenBATHAuto.repository.process;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.process.ProcessExecutionSession;

public interface ProcessExecutionSessionRepository extends JpaRepository<ProcessExecutionSession, Long> {

    Optional<ProcessExecutionSession> findWithAllBySessionKey(String sessionKey);

    boolean existsBySessionKey(String sessionKey);
}