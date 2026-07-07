package com.dev.HiddenBATHAuto.orderExcelUpload.support;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.orderExcelUpload.service.OrderExcelExternalAddressSearchService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderExcelAddressParser {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(01[016789][-\\s]?\\d{3,4}[-\\s]?\\d{4})");
    private static final Pattern ZIP_PATTERN = Pattern.compile("\\b\\d{5}\\b");

    /**
     * 도로명 주소 검색에 필요한 핵심 주소입니다.
     * 예: "인천광역시 연수구 계림로 95 (청학동, 현대아파트) 103동 1303호"
     *     -> core="인천광역시 연수구 계림로 95", detail="(청학동, 현대아파트) 103동 1303호"
     */
    private static final Pattern ROAD_CORE_PATTERN = Pattern.compile("^(.+?(?:대로|로|길|번길)\\s*\\d+(?:-\\d+)?)(.*)$");

    private final OrderExcelExternalAddressSearchService externalAddressSearchService;

    public ParsedSiteAddress parse(String rawValue) {
        ParsedSiteAddress result = new ParsedSiteAddress();
        String raw = normalize(rawValue);
        result.setRaw(raw);

        if (raw.isBlank()) {
            result.getWarnings().add("현장 주소 원문이 비어 있습니다.");
            return result;
        }

        String addressPart = raw;
        String contactPart = "";

        int slashIndex = raw.indexOf('/');
        if (slashIndex >= 0) {
            addressPart = raw.substring(0, slashIndex).trim();
            contactPart = raw.substring(slashIndex + 1).trim();
        }

        result.setAddressPart(addressPart);
        result.setContactPart(contactPart);
        parseContact(result, raw, contactPart);

        Matcher zipMatcher = ZIP_PATTERN.matcher(addressPart);
        if (zipMatcher.find()) {
            result.setZipCode(zipMatcher.group());
            addressPart = addressPart.replace(zipMatcher.group(), "").trim();
        }

        RoadAddressParts parts = splitRoadCoreAndDetail(addressPart);
        Optional<ResolvedExternalAddress> resolved = externalAddressSearchService.resolve(addressPart);
        if (resolved.isEmpty() && !parts.core().equals(addressPart)) {
            resolved = externalAddressSearchService.resolve(parts.core());
        }

        if (resolved.isPresent()) {
            ResolvedExternalAddress external = resolved.get();
            String roadAddress = firstNonBlank(external.getRoadAddress(), parts.core(), addressPart);

            result.setExternalResolved(true);
            result.setZipCode(firstNonBlank(result.getZipCode(), external.getZipCode()));
            result.setDoName(external.getDoName());
            result.setSiName(external.getSiName());
            result.setGuName(external.getGuName());
            result.setRoadAddress(roadAddress);
            result.setJibunAddress(external.getJibunAddress());
            result.setDetailAddress(firstNonBlank(extractDetail(addressPart, roadAddress), parts.detail()));
            return result;
        }

        parseRegionByTokens(result, parts.core());
        result.setRoadAddress(parts.core());
        result.setDetailAddress(parts.detail());

        if (!parts.core().equals(addressPart)) {
            result.getWarnings().add("주소 API 검색 결과가 없어서 도로명 핵심주소와 상세주소를 자동 분리했습니다. 우편번호는 Daum 주소검색으로 확인해 주세요.");
        } else {
            result.getWarnings().add("주소 API 검색 결과가 없어 원문 주소를 그대로 저장합니다. 도/시/구와 상세주소를 확인해 주세요.");
        }

        if (result.getZipCode() == null || result.getZipCode().isBlank()) {
            result.getWarnings().add("현장 주소에 우편번호가 없어 우편번호는 비워둡니다.");
        }

        return result;
    }

    public boolean looksLikeAddress(String value) {
        if (value == null || value.trim().isBlank()) {
            return false;
        }

        String text = normalize(value);
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
                || text.contains("호")
                || text.matches(".*[가-힣]+구\\s+[가-힣0-9]+.*");

        return hasPhone || (text.contains("/") && hasAddressKeyword);
    }

    private void parseContact(ParsedSiteAddress result, String raw, String contactPart) {
        String source = contactPart == null || contactPart.isBlank() ? raw : contactPart;
        Matcher phoneMatcher = PHONE_PATTERN.matcher(source);
        if (!phoneMatcher.find()) {
            return;
        }

        String phone = phoneMatcher.group().replaceAll("[-\\s]+", "-");
        result.setRecipientPhone(phone);

        String beforePhone = source.substring(0, phoneMatcher.start()).replace("/", " ").trim();
        String[] nameTokens = beforePhone.split("\\s+");
        if (nameTokens.length > 0) {
            String candidate = nameTokens[nameTokens.length - 1].trim();
            if (!candidate.isBlank() && candidate.length() <= 12 && !candidate.contains(":")) {
                result.setRecipientName(candidate);
            }
        }
    }

    private void parseRegionByTokens(ParsedSiteAddress result, String addressPart) {
        String[] tokens = normalize(addressPart).split("\\s+");
        if (tokens.length == 0) {
            return;
        }

        int cursor = 0;
        if (isProvinceLike(tokens[cursor])) {
            result.setDoName(tokens[cursor]);
            cursor++;
        } else if (isCityLike(tokens[cursor]) || isDistrictLike(tokens[cursor])) {
            result.setDoName(tokens[cursor]);
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

    private RoadAddressParts splitRoadCoreAndDetail(String addressPart) {
        String source = normalize(addressPart);
        if (source.isBlank()) {
            return new RoadAddressParts("", "");
        }

        Matcher matcher = ROAD_CORE_PATTERN.matcher(source);
        if (matcher.find()) {
            String core = normalize(matcher.group(1));
            String detail = normalize(matcher.group(2));
            detail = detail.replaceAll("^[,\\s]+", "").trim();
            return new RoadAddressParts(core, detail);
        }

        return new RoadAddressParts(source, "");
    }

    private String extractDetail(String addressPart, String roadAddress) {
        String source = normalize(addressPart);
        String road = normalize(roadAddress);
        if (source.isBlank() || road.isBlank()) {
            return "";
        }
        if (source.startsWith(road)) {
            return source.substring(road.length()).trim();
        }

        Matcher roadNumberMatcher = Pattern.compile("(?:대로|로|길|번길)\\s*\\d+(?:-\\d+)?").matcher(source);
        if (roadNumberMatcher.find()) {
            String detail = source.substring(roadNumberMatcher.end()).trim();
            return detail.replaceAll("^[,\\s]+", "").trim();
        }

        String compactSource = source.replaceAll("\\s+", "");
        String compactRoad = road.replaceAll("\\s+", "");
        if (compactSource.startsWith(compactRoad)) {
            return splitRoadCoreAndDetail(source).detail();
        }

        return "";
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private record RoadAddressParts(String core, String detail) {
    }
}
