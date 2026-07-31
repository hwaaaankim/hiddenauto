package com.dev.HiddenBATHAuto.orderExcelUpload.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 엑셀 발주 미리보기에서 사용하는 주소 검증 결과입니다.
 */
public class OrderExcelAddressValidationResult {

    private final List<String> messages;

    public OrderExcelAddressValidationResult(List<String> messages) {
        this.messages = messages == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public boolean isValid() {
        return messages.isEmpty();
    }

    public List<String> getMessages() {
        return messages;
    }

    public String getMessage() {
        return String.join(" ", messages);
    }
}
