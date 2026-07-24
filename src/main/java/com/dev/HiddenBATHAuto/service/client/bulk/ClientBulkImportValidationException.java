package com.dev.HiddenBATHAuto.service.client.bulk;

import java.util.List;

import com.dev.HiddenBATHAuto.dto.client.bulk.ClientBulkImportIssue;

public class ClientBulkImportValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<ClientBulkImportIssue> issues;

    public ClientBulkImportValidationException(String message, List<ClientBulkImportIssue> issues) {
        super(message);
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public List<ClientBulkImportIssue> getIssues() {
        return issues;
    }
}
