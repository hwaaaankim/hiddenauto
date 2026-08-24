package com.dev.HiddenBATHAuto.orderExcelUpload.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.utils.KoreanAdministrativeRegionNormalizer;

/**
 * 등록주소지 선택, 담당자 자동배정, 최종 저장에서 동일하게 사용하는 주소 검증기입니다.
 *
 * 검증 기준
 * - 우편번호: 숫자 5자리
 * - 도/광역시: 대한민국 17개 시·도 또는 통상 약칭
 * - 일반 도: 시/군 필수
 * - 특별시/광역시: 구/군 필수(세종특별자치시는 예외)
 * - 도로명/지번/원본 주소 중 하나 이상 필수
 *
 * 상세주소는 단독 건물·매장 주소도 존재하므로 필수로 강제하지 않습니다.
 */
@Component
public class OrderExcelAddressValidator {

    private static final Pattern ZIP_CODE_PATTERN = Pattern.compile("^\\d{5}$");


    public OrderExcelAddressValidationResult validate(
            String addressLabel,
            String zipCode,
            String doName,
            String siName,
            String guName,
            String roadAddress,
            String jibunAddress,
            String originAddress
    ) {
        String label = text(addressLabel).isBlank() ? "배송지" : text(addressLabel);
        String zip = digits(zipCode);
        String province = text(doName);
        String city = text(siName);
        String district = text(guName);
        String road = meaningful(roadAddress);
        String jibun = meaningful(jibunAddress);
        String origin = meaningful(originAddress);

        List<String> messages = new ArrayList<>();

        if (zip.isBlank()) {
            messages.add(label + " 우편번호가 비어 있습니다. 주소검색 또는 등록주소지검색으로 주소를 다시 선택해 주세요.");
        } else if (!ZIP_CODE_PATTERN.matcher(zip).matches()) {
            messages.add(label + " 우편번호는 숫자 5자리여야 합니다: " + text(zipCode));
        }

        if (province.isBlank()) {
            messages.add(label + " 도/시 값이 비어 있습니다. 주소검색으로 행정구역을 확인해 주세요.");
        } else if (!KoreanAdministrativeRegionNormalizer.isKnownProvince(province)) {
            messages.add(label + " 도/시 값이 대한민국 행정구역 형식과 맞지 않습니다: " + province);
        } else if (KoreanAdministrativeRegionNormalizer.isSejong(province)) {
            // 세종특별자치시는 city/district가 없는 현재 DB 구조를 정상으로 허용합니다.
        } else if (KoreanAdministrativeRegionNormalizer.isMetropolitanProvince(province)) {
            if (district.isBlank()) {
                messages.add(label + " 구/군 값이 비어 있습니다. " + province + " 주소는 구/군을 확인해 주세요.");
            }
        } else if (city.isBlank()) {
            messages.add(label + " 시/군 값이 비어 있습니다. " + province + " 주소는 시/군을 확인해 주세요.");
        }

        if (road.isBlank() && jibun.isBlank() && origin.isBlank()) {
            messages.add(label + " 도로명·지번·원본 주소가 모두 비어 있습니다. 주소검색 또는 등록주소지검색으로 주소를 입력해 주세요.");
        }

        return new OrderExcelAddressValidationResult(messages);
    }

    private String digits(String value) {
        return text(value).replaceAll("[^0-9]", "");
    }

    private String meaningful(String value) {
        String normalized = text(value);
        if (normalized.equals("-") || normalized.equals("–") || normalized.equals("—")) {
            return "";
        }
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
