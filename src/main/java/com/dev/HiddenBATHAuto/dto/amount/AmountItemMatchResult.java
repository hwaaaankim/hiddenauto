package com.dev.HiddenBATHAuto.dto.amount;

import com.dev.HiddenBATHAuto.model.amount.AmountItemMaster;

public record AmountItemMatchResult(
        AmountItemMaster item,
        int score,
        String level,
        String reason,
        boolean aiUsed
) {
    public static AmountItemMatchResult empty(String reason) {
        return new AmountItemMatchResult(null, 0, "REVIEW", reason, false);
    }

    public boolean exact() {
        return score >= 97;
    }

    public boolean partial() {
        return score >= 51 && score < 97;
    }
}
