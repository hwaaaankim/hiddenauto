package com.dev.HiddenBATHAuto.orderExcelUpload.support;

/** Address boundaries shared by local splitting and external search candidates. */
public final class OrderExcelAddressSyntax {
    private OrderExcelAddressSyntax() {}

    public static final String ROAD_SUFFIX = "(?:대로|로|길)(?:\\s*\\d+(?:번길|길))?";
    // Atomic numbers prevent a failed suffix check from accepting only part of a number.
    // 종로1가/을지로3가 are legal localities, not road names followed by building numbers.
    public static final String BUILDING_NUMBER =
            "(?>\\d+(?:-\\d+)?)(?!\\d|-(?=\\d)|가(?=\\s|\\d|$)|(?:번길|길)(?=\\s|\\d|$)|\\s*(?:동|호|층)(?=\\s|\\d|$))";
    // Non-numeric 건물명 such as 현대상가 must not become a legal locality.
    public static final String LOCALITY = "[가-힣·]+(?:동(?:\\d+가)?|읍|면|리|\\d+가)";
    public static final String JIBUN_CORE = "(?<![가-힣0-9·])" + LOCALITY
            + "\\s+산?(?>\\d+(?:-\\d+)?)(?!\\d|-(?=\\d)|\\s*(?:동|호|층)(?=\\s|\\d|$))";
}
