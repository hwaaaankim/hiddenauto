package com.dev.HiddenBATHAuto.orderExcelUpload.support;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * 발주 등록 파일의 열 위치를 시트 단위로 확정합니다.
 * 기존 19열 양식과 Company 거래처 오른쪽에 참고용 거래처명을 추가한 20열 양식을 지원합니다.
 */
public enum OrderExcelColumnLayout {
    LEGACY_19(0),
    REGIONAL_20(1);

    public static final String REGIONAL_CUSTOMER_NAME_HEADER = "거래처명(지역 포함)";

    private final int offsetAfterCompany;

    OrderExcelColumnLayout(int offsetAfterCompany) {
        this.offsetAfterCompany = offsetAfterCompany;
    }

    /** 기존 19열 기준 인덱스를 현재 시트의 실제 열 인덱스로 바꿉니다. */
    public int mapIndex(int legacyIndex) {
        return legacyIndex < 3 ? legacyIndex : legacyIndex + offsetAfterCompany;
    }

    /**
     * 열 개수만으로 추정하지 않습니다. 20열 양식은 제목으로 확인하고 D열의 참고용
     * 거래처명은 업체 매칭에 사용하지 않습니다. 기존 양식의 추가 메모 열은 계속 무시합니다.
     */
    public static OrderExcelColumnLayout detect(Sheet sheet, OrderExcelCellReader reader) {
        if (sheet == null) {
            return LEGACY_19;
        }

        OrderExcelColumnLayout detected = null;
        for (Row row : sheet) {
            if (!isDateHeader(reader.text(row, 0)) || !isCompanyHeader(reader.text(row, 2))) {
                continue;
            }

            String fourthHeader = normalizeHeader(reader.text(row, 3));
            String fifthHeader = normalizeHeader(reader.text(row, 4));
            OrderExcelColumnLayout rowLayout;
            if (fourthHeader.equals(normalizeHeader(REGIONAL_CUSTOMER_NAME_HEADER))) {
                requireRegionalHeaders(row, reader);
                rowLayout = REGIONAL_20;
            } else if (isItemHeader(fourthHeader)) {
                // 기존 19열 파일의 뒤에 별도 메모가 추가되어도 기존 열 위치를 유지합니다.
                rowLayout = LEGACY_19;
            } else if (isItemHeader(fifthHeader)) {
                throw invalidFormat(row, "D열 제목은 '" + REGIONAL_CUSTOMER_NAME_HEADER
                        + "', E열 제목은 '품목'이어야 합니다.");
            } else {
                continue;
            }

            if (detected != null && detected != rowLayout) {
                throw invalidFormat(row, "한 시트에 기존 19열 양식과 신규 20열 양식을 섞어서 사용할 수 없습니다.");
            }
            detected = rowLayout;
        }
        // 제목 행 없이 사용해 온 기존 파일의 동작을 그대로 유지합니다.
        return detected == null ? LEGACY_19 : detected;
    }

    private static void requireRegionalHeaders(Row row, OrderExcelCellReader reader) {
        if (!isItemHeader(normalizeHeader(reader.text(row, 4)))) {
            throw invalidFormat(row, "E열 제목은 '품목'이어야 합니다. 신규 양식을 다시 다운로드해 주세요.");
        }
        requireHeader(row, reader, 5, "규격");
        requireHeader(row, reader, 6, "수량");
        requireHeader(row, reader, 7, "비고");
        requireHeader(row, reader, 8, "단위");
        requireHeader(row, reader, 9, "담당자");
        requireHeader(row, reader, 10, "담당자");
        requireHeader(row, reader, 12, "단가");
        requireHeader(row, reader, 13, "공급가액");
        requireHeader(row, reader, 14, "부가세");
        requireHeader(row, reader, 15, "합계");
        requireHeader(row, reader, 19, "사업자번호");
    }

    private static void requireHeader(Row row, OrderExcelCellReader reader, int column, String expected) {
        if (!normalizeHeader(reader.text(row, column)).equals(normalizeHeader(expected))) {
            throw invalidFormat(row, String.valueOf((char) ('A' + column)) + "열 제목은 '"
                    + expected + "'이어야 합니다. 신규 양식을 다시 다운로드해 주세요.");
        }
    }

    private static boolean isDateHeader(String value) {
        String header = normalizeHeader(value);
        return header.equals("날짜") || header.equals("출고일");
    }

    private static boolean isCompanyHeader(String value) {
        String header = normalizeHeader(value);
        return header.equals("거래처") || header.equals("거래처명");
    }

    private static boolean isItemHeader(String header) {
        return header.equals("품목") || header.equals("품목명");
    }

    private static String normalizeHeader(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replace('\u3000', ' ')
                .replaceAll("\\s+", "").trim();
    }

    private static IllegalArgumentException invalidFormat(Row row, String message) {
        return new IllegalArgumentException("엑셀 " + (row.getRowNum() + 1) + "행의 열 구성을 확인해 주세요. " + message);
    }
}
