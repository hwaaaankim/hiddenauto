package com.dev.HiddenBATHAuto.dto.client.bulk;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
public class ClientBulkImportSaveRequest {

    private List<ClientBulkImportPreviewRow> rows = new ArrayList<>();
}
