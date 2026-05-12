package com.dev.HiddenBATHAuto.service.process;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

@Getter
public class ProcessDefinitionValidationResult {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public void addError(String message) {
        if (message != null && !message.isBlank()) {
            errors.add(message);
        }
    }

    public void addWarning(String message) {
        if (message != null && !message.isBlank()) {
            warnings.add(message);
        }
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}