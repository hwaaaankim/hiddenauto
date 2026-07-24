package com.dev.HiddenBATHAuto.dto.client.bulk;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientBulkImportPreviewRow {

    private int excelRowNumber;
    private boolean saveTarget;

    private String companyName;
    private String businessNumber;
    private String representativeName;

    private String telephone;
    private String phone;
    private String email;

    private String originAddress;
    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String jibunAddress;
    private String roadAddress;
    private String detailAddress;

    private boolean addressResolved;
    private String addressSource;

    private boolean existingCompanyDuplicate;
    private boolean existingMemberDuplicate;

    @Builder.Default
    private List<ClientBulkImportIssue> issues = new ArrayList<>();

    public void addIssue(ClientBulkImportIssue issue) {
        if (issue == null) {
            return;
        }
        if (issues == null) {
            issues = new ArrayList<>();
        }
        issues.add(issue);
    }

    public boolean hasError() {
        return issues != null && issues.stream().anyMatch(issue -> "ERROR".equalsIgnoreCase(issue.level()));
    }
}
