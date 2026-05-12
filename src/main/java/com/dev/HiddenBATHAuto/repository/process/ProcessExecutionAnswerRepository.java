package com.dev.HiddenBATHAuto.repository.process;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.process.ProcessExecutionAnswer;
import com.dev.HiddenBATHAuto.model.process.ProcessExecutionSession;

public interface ProcessExecutionAnswerRepository extends JpaRepository<ProcessExecutionAnswer, Long> {

    Optional<ProcessExecutionAnswer> findBySessionAndUnitKey(ProcessExecutionSession session, String unitKey);

    List<ProcessExecutionAnswer> findBySessionOrderByCreatedAtAsc(ProcessExecutionSession session);
}