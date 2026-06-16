package com.dev.HiddenBATHAuto.dto.amount;

import com.dev.HiddenBATHAuto.model.amount.AmountCustomerMaster;

public record AmountCustomerMatchResult(
        AmountCustomerMaster customer,
        int score,
        String level,
        String reason
) {
    public static AmountCustomerMatchResult empty(String reason) {
        return new AmountCustomerMatchResult(null, 0, "REVIEW", reason);
    }
}
