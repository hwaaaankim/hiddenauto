package com.dev.HiddenBATHAuto.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberExcelUploadResult {

    private int totalRows;        // 헤더 제외 실제 처리 대상 수
    private int successRows;      // 성공(생성/갱신 포함)
    private int createdMembers;   // 신규 Member 생성
    private int updatedMembers;   // 기존 Member 갱신
    private int createdCompanies; // 신규 Company 생성
    private int updatedCompanies; // 기존 Company 갱신
    private int failedRows;       // 실패 행 수

    private List<RowError> errors = new ArrayList<>();

    @Getter
    @Setter
    public static class RowError {
        private int rowIndex;   // 엑셀 행 번호(0-based)
        private String message;

        public RowError(int rowIndex, String message) {
            this.rowIndex = rowIndex;
            this.message = message;
        }
    }
}