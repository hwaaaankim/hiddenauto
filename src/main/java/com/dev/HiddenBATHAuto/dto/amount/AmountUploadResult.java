package com.dev.HiddenBATHAuto.dto.amount;

public record AmountUploadResult(
        boolean success,
        String message,
        int savedCount
) {
    public static AmountUploadResult ok(String message, int savedCount) {
        return new AmountUploadResult(true, message, savedCount);
    }
}
