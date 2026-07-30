package com.dev.HiddenBATHAuto.dto.orderchange;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderChangeNoticeDto {
    private Long eventId;
    private String sourceArea;
    private String sourceAreaLabel;
    private String actorUsername;
    private String actorDisplayName;
    private String operationLabel;
    private String requestPath;
    private String summary;
    private String changedAtText;
    private List<FieldChange> fields;

    public record FieldChange(
            String fieldKey,
            String fieldLabel,
            String beforeValue,
            String afterValue
    ) {
    }
}
