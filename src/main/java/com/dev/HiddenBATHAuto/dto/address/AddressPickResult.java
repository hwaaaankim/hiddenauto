package com.dev.HiddenBATHAuto.dto.address;

import com.dev.HiddenBATHAuto.utils.AddressNormalizer;

import lombok.Data;

@Data
public class AddressPickResult {

    private boolean success;

    private String zip;
    private String doName;
    private String siName;
    private String guName;

    private String roadAddress;
    private String jibunAddress;

    private String detailAddress;

    public static AddressPickResult from(KakaoDocument doc, String detail) {
        AddressPickResult r = new AddressPickResult();
        r.success = true;

        r.zip = AddressNormalizer.getZip(doc);

        AddressNormalizer.AdminParts parts = AddressNormalizer.splitAdmin(doc);
        r.doName = parts.getDoName();
        r.siName = parts.getSiName();
        r.guName = parts.getGuName();

        r.roadAddress = AddressNormalizer.getRoadFull(doc);
        r.jibunAddress = AddressNormalizer.getJibunFull(doc);

        r.detailAddress = detail == null ? "" : detail.trim();
        return r;
    }

    public static AddressPickResult from(AddressNormalizer.NormalizedAddress na, String detail) {
        AddressPickResult r = new AddressPickResult();
        r.success = true;

        r.zip = safe(na.getZipCode());
        r.doName = safe(na.getDoName());
        r.siName = safe(na.getSiName());
        r.guName = safe(na.getGuName());

        r.roadAddress = safe(na.getRoadAddress());
        r.jibunAddress = safe(na.getJibunAddress());

        r.detailAddress = detail == null ? "" : detail.trim();
        return r;
    }

    public static AddressPickResult empty(String detail) {
        AddressPickResult r = new AddressPickResult();
        r.success = false;
        r.zip = "";
        r.doName = "";
        r.siName = "";
        r.guName = "";
        r.roadAddress = "";
        r.jibunAddress = "";
        r.detailAddress = detail == null ? "" : detail.trim();
        return r;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}