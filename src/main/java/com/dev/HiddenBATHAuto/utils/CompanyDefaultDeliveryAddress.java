package com.dev.HiddenBATHAuto.utils;

import com.dev.HiddenBATHAuto.model.auth.Company;

/** Shared by Excel registration and admin direct-delivery preview. */
public record CompanyDefaultDeliveryAddress(
        String zipCode, String doName, String siName, String guName,
        String roadAddress, String detailAddress, boolean fallbackUsed) {
    public static CompanyDefaultDeliveryAddress from(Company company) {
        if (company == null) return new CompanyDefaultDeliveryAddress("", "", "", "", "", "", false);
        String road = meaningful(company.getRoadAddress());
        String detail = meaningful(company.getDetailAddress());
        boolean fallback = road.isBlank() && detail.isBlank();
        if (fallback) {
            road = meaningful(company.getOriginAddress());
            if (road.isBlank()) road = meaningful(company.getJibunAddress());
        }
        return new CompanyDefaultDeliveryAddress(meaningful(company.getZipCode()),
                KoreanAdministrativeRegionNormalizer.canonicalProvinceName(company.getDoName() == null ? "" : company.getDoName().trim()),
                meaningful(company.getSiName()), meaningful(company.getGuName()), road, detail,
                fallback && !road.isBlank());
    }
    private static String meaningful(String value) {
        String text = value == null ? "" : value.trim();
        return text.equals("-") || text.equals("–") || text.equals("—") ? "" : text;
    }
}
