package com.dev.HiddenBATHAuto.dto.client.bulk;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ClientBulkImportSaveRequest {

    private List<ClientBulkImportPreviewRow> rows = new ArrayList<>();
}
