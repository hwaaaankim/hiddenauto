package com.dev.HiddenBATHAuto.dto.excel;

import com.dev.HiddenBATHAuto.utils.AddressNormalizer;

import io.micrometer.common.lang.NonNull;

/**
 * 최종 선택 결과 DTO (엑셀/JSON 공용)
 * 열 순서 요구사항에 맞춰 지번주소 필드(jibunAddress) 추가
 */
public record AddressPickResult(
        String zip,            // 우편번호
        String doName,         // 도 (예: 경기)
        String siName,         // 시 (예: 파주시)
        String guName,         // 구 (예: 일산동구) - 없으면 ""
        String roadAddress,    // 도로명 전체주소 (예: 경기 파주시 금월로 117)
        String jibunAddress,   // 지번 전체주소   (예: 경기 파주시 야동동 1028)
        String detailAddress   // 상세주소       (괄호/쉼표에서 뽑아 합친 상세)
) {

    public static AddressPickResult from(@NonNull KakaoDocument d, String... details) {
        if (d == null) {
            return empty(details);
        }
        String zip   = AddressNormalizer.getZip(d);
        String road  = AddressNormalizer.getRoadFull(d);
        String jibun = AddressNormalizer.getJibunFull(d);

        AddressNormalizer.AdminParts parts = AddressNormalizer.splitAdmin(d);
        String det = AddressNormalizer.mergeDetails(details);

        return new AddressPickResult(
                orBlank(zip),
                orBlank(parts.getDoName()),
                orBlank(parts.getSiName()),
                orBlank(parts.getGuName()),
                orBlank(road),
                orBlank(jibun),
                det
        );
    }

    public static AddressPickResult from(AddressNormalizer.NormalizedAddress na, String... details) {
        if (na == null) {
            return empty(details);
        }
        String det = AddressNormalizer.mergeDetails(details);
        return new AddressPickResult(
                orBlank(na.getZipCode()),
                orBlank(na.getDoName()),
                orBlank(na.getSiName()),
                orBlank(na.getGuName()),
                orBlank(na.getRoadAddress()),
                orBlank(na.getJibunAddress()),
                det
        );
    }

    public static AddressPickResult empty(String... details) {
        String det = AddressNormalizer.mergeDetails(details);
        return new AddressPickResult("", "", "", "", "", "", det);
    }

    private static String orBlank(String s) {
        return (s == null) ? "" : s;
    }
}