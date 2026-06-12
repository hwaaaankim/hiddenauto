package com.dev.HiddenBATHAuto.rag.dto;

import java.util.UUID;

public record RagInquiryRequest(
        UUID sessionId,
        String companyName,
        String customerName,
        String phone,
        String email,
        String memo
) {}
