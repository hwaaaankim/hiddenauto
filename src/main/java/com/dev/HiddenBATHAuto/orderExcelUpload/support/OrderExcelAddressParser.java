package com.dev.HiddenBATHAuto.orderExcelUpload.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class OrderExcelAddressParser {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(01[016789][-\\s]?\\d{3,4}[-\\s]?\\d{4})");
    private static final Pattern ZIP_PATTERN = Pattern.compile("\\b\\d{5}\\b");

    public ParsedSiteAddress parse(String rawValue) {
        ParsedSiteAddress result = new ParsedSiteAddress();
        String raw = rawValue == null ? "" : rawValue.trim();
        result.setRaw(raw);

        if (raw.isBlank()) {
            result.getWarnings().add("현장 주소 원문이 비어 있습니다.");
            return result;
        }

        String addressPart = raw;
        String contactPart = "";

        int slashIndex = raw.lastIndexOf('/');
        if (slashIndex >= 0) {
            addressPart = raw.substring(0, slashIndex).trim();
            contactPart = raw.substring(slashIndex + 1).trim();
        }

        Matcher zipMatcher = ZIP_PATTERN.matcher(addressPart);
        if (zipMatcher.find()) {
            result.setZipCode(zipMatcher.group());
            addressPart = addressPart.replace(zipMatcher.group(), "").trim();
        } else {
            result.getWarnings().add("현장 주소에 우편번호가 없어 siteZipCode는 비워둡니다.");
        }

        Matcher phoneMatcher = PHONE_PATTERN.matcher(contactPart.isBlank() ? raw : contactPart);
        if (phoneMatcher.find()) {
            String phone = phoneMatcher.group().replaceAll("\\s+", "-");
            result.setRecipientPhone(phone);

            String nameSource = contactPart.isBlank() ? raw.substring(Math.max(0, phoneMatcher.start() - 20), phoneMatcher.start()) : contactPart.substring(0, phoneMatcher.start());
            String name = nameSource.replace("/", "").trim();
            if (!name.isBlank()) {
                result.setRecipientName(name);
            }
        }

        String[] tokens = addressPart.split("\\s+");
        if (tokens.length > 0) {
            int cursor = 0;

            if (isProvinceLike(tokens[cursor])) {
                result.setDoName(tokens[cursor]);
                cursor++;
            } else if (isCityLike(tokens[cursor])) {
                result.setDoName(tokens[cursor]);
                result.getWarnings().add("도/광역시 정보가 없어 첫 행정구역을 siteDoName에 임시 저장했습니다.");
                cursor++;
            }

            if (cursor < tokens.length && isCityLike(tokens[cursor])) {
                result.setSiName(tokens[cursor]);
                cursor++;
            }

            if (cursor < tokens.length && isDistrictLike(tokens[cursor])) {
                result.setGuName(tokens[cursor]);
            }
        }

        result.setRoadAddress(addressPart);
        result.setDetailAddress(null);

        if (result.getRoadAddress() == null || result.getRoadAddress().isBlank()) {
            result.setRoadAddress(raw);
            result.getWarnings().add("주소 본문을 분리하지 못해 원문 전체를 현장 도로명주소로 저장합니다.");
        }

        return result;
    }

    public boolean looksLikeAddress(String value) {
        if (value == null || value.trim().isBlank()) {
            return false;
        }

        String text = value.trim();
        boolean hasPhone = PHONE_PATTERN.matcher(text).find();
        boolean hasAddressKeyword = text.contains(" 로 ")
                || text.contains("로 ")
                || text.contains("길 ")
                || text.contains("번길")
                || text.contains("대로")
                || text.contains("아파트")
                || text.contains("타워")
                || text.contains("빌딩")
                || text.contains("동 ")
                || text.contains("호");

        return hasPhone || (text.contains("/") && hasAddressKeyword);
    }

    private boolean isProvinceLike(String token) {
        return token.endsWith("도")
                || token.endsWith("특별시")
                || token.endsWith("광역시")
                || token.endsWith("특별자치시")
                || token.endsWith("특별자치도");
    }

    private boolean isCityLike(String token) {
        return token.endsWith("시") || token.endsWith("군");
    }

    private boolean isDistrictLike(String token) {
        return token.endsWith("구") || token.endsWith("군");
    }
}
