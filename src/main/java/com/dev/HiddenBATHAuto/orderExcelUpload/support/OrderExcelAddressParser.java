package com.dev.HiddenBATHAuto.orderExcelUpload.support;

import java.util.Map;
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
    private static final Pattern ROAD_CORE_PATTERN = Pattern.compile("^(.+?(?:대로|번길|로|길)\\s*\\d+(?:-\\d+)?)(.*)$");
    private static final Pattern ROAD_NUMBER_PATTERN = Pattern.compile(".*(?:대로|번길|로|길)\\s*\\d+(?:-\\d+)?.*");
    private static final Pattern REGION_PATTERN = Pattern.compile(".*(?:특별시|광역시|특별자치시|특별자치도|[가-힣]+도|[가-힣]+시|[가-힣]+군|[가-힣]+구)\\s+.*");
    private static final Pattern JIBUN_ADDRESS_PATTERN = Pattern.compile(".*[가-힣0-9]+(?:동|읍|면|리)\\s+산?\\d+(?:-\\d+)?.*");
    private static final Pattern DETAIL_HINT_PATTERN = Pattern.compile(".*(?:\\d+\\s*동|\\d+\\s*호|호실|층|지하|상가|오피스텔|아파트|빌라|건물|타워).*");


    private static final Map<String, String> PROVINCE_ALIASES = Map.ofEntries(
            Map.entry("서울", "서울특별시"),
            Map.entry("서울시", "서울특별시"),
            Map.entry("서울특별시", "서울특별시"),
            Map.entry("부산", "부산광역시"),
            Map.entry("부산시", "부산광역시"),
            Map.entry("부산광역시", "부산광역시"),
            Map.entry("대구", "대구광역시"),
            Map.entry("대구시", "대구광역시"),
            Map.entry("대구광역시", "대구광역시"),
            Map.entry("인천", "인천광역시"),
            Map.entry("인천시", "인천광역시"),
            Map.entry("인천광역시", "인천광역시"),
            Map.entry("광주", "광주광역시"),
            Map.entry("광주시", "광주광역시"),
            Map.entry("광주광역시", "광주광역시"),
            Map.entry("대전", "대전광역시"),
            Map.entry("대전시", "대전광역시"),
            Map.entry("대전광역시", "대전광역시"),
            Map.entry("울산", "울산광역시"),
            Map.entry("울산시", "울산광역시"),
            Map.entry("울산광역시", "울산광역시"),
            Map.entry("세종", "세종특별자치시"),
            Map.entry("세종시", "세종특별자치시"),
            Map.entry("세종특별자치시", "세종특별자치시"),
            Map.entry("경기", "경기도"),
            Map.entry("경기도", "경기도"),
            Map.entry("강원", "강원특별자치도"),
            Map.entry("강원도", "강원특별자치도"),
            Map.entry("강원특별자치도", "강원특별자치도"),
            Map.entry("충북", "충청북도"),
            Map.entry("충청북도", "충청북도"),
            Map.entry("충남", "충청남도"),
            Map.entry("충청남도", "충청남도"),
            Map.entry("전북", "전북특별자치도"),
            Map.entry("전라북도", "전북특별자치도"),
            Map.entry("전북특별자치도", "전북특별자치도"),
            Map.entry("전남", "전라남도"),
            Map.entry("전라남도", "전라남도"),
            Map.entry("경북", "경상북도"),
            Map.entry("경상북도", "경상북도"),
            Map.entry("경남", "경상남도"),
            Map.entry("경상남도", "경상남도"),
            Map.entry("제주", "제주특별자치도"),
            Map.entry("제주도", "제주특별자치도"),
            Map.entry("제주특별자치도", "제주특별자치도")
    );

    private final OrderExcelExternalAddressSearchService externalAddressSearchService;

    /**
     * 외부 API를 호출하지 않고 원문에 포함된 도/시/구를 우선 해석합니다.
     * 담당자 자동 배정은 이 결과로 먼저 시도한 뒤 실패한 경우에만 외부 API를 사용합니다.
     */
    public ParsedSiteAddress parseLocal(String rawValue) {
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
            result.setAddressPart(addressPart);
        }

        RoadAddressParts parts = splitRoadCoreAndDetail(addressPart);
        String detailFromContactPart = extractDetailFromContactPart(contactPart);

        parseRegionByTokens(result, parts.core());
        result.setRoadAddress(parts.core());
        result.setOriginalRoadAddress(parts.core());
        result.setDetailAddress(joinDetails(parts.detail(), detailFromContactPart));
        return result;
    }

    /**
     * 직접 해석한 주소로 담당자를 찾지 못한 경우에만 한 번 호출합니다.
     * 외부 주소 검색 결과가 있으면 도/시/구와 표준 도로명주소를 덮어씁니다.
     */
    public ParsedSiteAddress resolveWithExternal(ParsedSiteAddress local) {
        ParsedSiteAddress result = local == null ? new ParsedSiteAddress() : local;
        String addressPart = firstNonBlank(result.getAddressPart(), result.getRaw());

        if (addressPart.isBlank()) {
            return result;
        }

        RoadAddressParts parts = splitRoadCoreAndDetail(addressPart);
        String originalFullAddress = firstNonBlank(result.getAddressPart(), result.getRaw(), addressPart);
        String originalRoadAddress = firstNonBlank(result.getOriginalRoadAddress(), result.getRoadAddress(), parts.core(), addressPart);
        String existingDetailAddress = firstNonBlank(result.getDetailAddress(), parts.detail(), extractDetail(addressPart, originalRoadAddress));
        Optional<ResolvedExternalAddress> resolved = externalAddressSearchService.resolve(addressPart);

        if (resolved.isPresent()) {
            ResolvedExternalAddress external = resolved.get();
            String roadAddress = firstNonBlank(external.getRoadAddress(), parts.core(), addressPart);
            String detailAddress = firstNonBlank(existingDetailAddress, extractDetail(addressPart, roadAddress), parts.detail());

            // 지번/약식 주소를 외부 API로 도로명 주소로 보정하는 경우,
            // API 결과는 도로명까지만 반환되고 동/호수·건물명 같은 상세주소가 사라질 수 있습니다.
            // 상세주소를 정확히 분리하지 못했더라도 원문에 상세주소로 보이는 정보가 있으면
            // 원문 주소 전체를 상세주소 fallback으로 보존합니다.
            // 예) 성동구 하왕십리동 992, 무학현대 103동 803호
            //     -> roadAddress=서울 성동구 무학봉길 49
            //     -> detailAddress=성동구 하왕십리동 992, 무학현대 103동 803호
            if (detailAddress.isBlank()
                    && !sameAddressText(roadAddress, originalFullAddress)
                    && shouldKeepOriginalAddressAsDetail(originalFullAddress)) {
                detailAddress = originalFullAddress;
            }

            result.setExternalResolved(true);
            result.setExternalSource(external.getSource());
            result.setZipCode(firstNonBlank(external.getZipCode(), result.getZipCode()));
            result.setDoName(firstNonBlank(external.getDoName(), result.getDoName()));
            result.setSiName(firstNonBlank(external.getSiName(), result.getSiName()));
            result.setGuName(firstNonBlank(external.getGuName(), result.getGuName()));
            result.setOriginalRoadAddress(originalRoadAddress);
            result.setRoadAddress(roadAddress);
            result.setJibunAddress(external.getJibunAddress());
            result.setDetailAddress(detailAddress);

            if (!sameAddressText(roadAddress, originalRoadAddress)) {
                String correctionText = buildAddressCorrectionText(roadAddress, originalRoadAddress, detailAddress);
                result.setAddressCorrectionText(correctionText);
                result.getWarnings().add("주소 API로 도/시/구와 도로명 주소를 보정했습니다. " + correctionText);
            }
            return result;
        }

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

    /**
     * 기존 호출부 호환용입니다. 주소 정규화 자체가 필요한 경우에는 로컬 해석 후 외부 검색까지 수행합니다.
     */
    public ParsedSiteAddress parse(String rawValue) {
        return resolveWithExternal(parseLocal(rawValue));
    }

    public boolean looksLikeAddress(String value) {
        if (value == null || value.trim().isBlank()) {
            return false;
        }

        String text = normalize(value);
        boolean hasPhone = PHONE_PATTERN.matcher(text).find();
        boolean hasRoadNumber = ROAD_NUMBER_PATTERN.matcher(text).matches();
        boolean hasRegion = REGION_PATTERN.matcher(text).matches();
        boolean hasJibunAddress = JIBUN_ADDRESS_PATTERN.matcher(text).matches();
        boolean hasAddressKeyword = text.contains("대로")
                || text.contains("번길")
                || ROAD_NUMBER_PATTERN.matcher(text).matches()
                || text.contains("아파트")
                || text.contains("타워")
                || text.contains("빌딩")
                || text.contains("동 ")
                || text.contains("호");

        // 일반적인 전체 도로명 주소: "인천광역시 연수구 계림로 95"
        if (hasRoadNumber && hasRegion) {
            return true;
        }

        // 지번 주소 또는 우편번호가 포함된 주소
        if (hasJibunAddress && hasRegion) {
            return true;
        }
        if (ZIP_PATTERN.matcher(text).find() && (hasRoadNumber || hasJibunAddress || hasRegion)) {
            return true;
        }

        // 기존 엑셀 형식: "주소 / 수령자 전화번호"
        if (text.contains("/") && hasAddressKeyword) {
            return true;
        }

        return hasPhone && hasAddressKeyword;
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
        String canonicalProvince = canonicalProvince(tokens[cursor]);
        if (!canonicalProvince.isBlank()) {
            result.setDoName(canonicalProvince);
            cursor++;
        } else if (isProvinceLike(tokens[cursor])) {
            result.setDoName(tokens[cursor]);
            cursor++;
        } else if (isDistrictLike(tokens[cursor])) {
            // "강남구 학동로 64"처럼 시·도가 생략된 주소에서 강남구를 도로 잘못 저장하지 않습니다.
            // 외부 주소 API가 실패한 경우 province는 비워 두고 district만 보존합니다.
            result.setGuName(tokens[cursor]);
            return;
        } else if (isCityLike(tokens[cursor])) {
            // 시·도가 없는 "용인시 수지구 ..." 형식은 city부터 보존합니다.
            result.setSiName(tokens[cursor]);
            cursor++;
        }

        if (cursor < tokens.length && isCityLike(tokens[cursor]) && result.getSiName() == null) {
            result.setSiName(tokens[cursor]);
            cursor++;
        }

        if (cursor < tokens.length && isDistrictLike(tokens[cursor])) {
            result.setGuName(tokens[cursor]);
            cursor++;
        }

        // 비광역도 주소에서 "경기도 용인시 수지구"처럼 시 다음 구가 붙는 경우를 보정합니다.
        if (cursor < tokens.length && result.getGuName() == null && isDistrictLike(tokens[cursor])) {
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

    private String extractDetailFromContactPart(String contactPart) {
        String source = normalize(contactPart).replace("/", " ");
        if (source.isBlank()) {
            return "";
        }

        Matcher phoneMatcher = PHONE_PATTERN.matcher(source);
        if (phoneMatcher.find()) {
            source = source.substring(0, phoneMatcher.start()).trim();
        }

        source = source.replaceAll("^[,\\s]+", "").replaceAll("[,\\s]+$", "").trim();
        if (source.isBlank() || !looksLikeDetailText(source)) {
            return "";
        }

        String[] tokens = source.split("\\s+");
        if (tokens.length >= 2) {
            String last = tokens[tokens.length - 1];
            if (last.matches("[가-힣]{2,4}") && !looksLikeDetailText(last)) {
                StringBuilder withoutName = new StringBuilder();
                for (int i = 0; i < tokens.length - 1; i++) {
                    if (withoutName.length() > 0) {
                        withoutName.append(' ');
                    }
                    withoutName.append(tokens[i]);
                }
                String cleaned = normalize(withoutName.toString());
                if (looksLikeDetailText(cleaned)) {
                    return cleaned;
                }
            }
        }

        return source;
    }

    private boolean looksLikeDetailText(String value) {
        String text = normalize(value);
        return !text.isBlank() && DETAIL_HINT_PATTERN.matcher(text).matches();
    }


    private boolean shouldKeepOriginalAddressAsDetail(String value) {
        String text = normalize(value);
        if (text.isBlank()) {
            return false;
        }

        if (looksLikeDetailText(text)) {
            return true;
        }

        // 엑셀 원문에서 콤마, 세미콜론, 파이프, 괄호 등으로 주소와 상세정보를 구분한 경우도 보존합니다.
        if (text.matches(".*[,;|｜、，].*")) {
            return true;
        }

        // 건물명 + 숫자 조합처럼 명확한 동/호 표기가 없어도 상세주소일 가능성이 높은 표현을 보존합니다.
        return text.matches(".*[가-힣]{2,}\\s*\\d{1,4}[-호]?\\d{0,4}.*");
    }

    private String joinDetails(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isBlank()) {
            return b;
        }
        if (b.isBlank() || compact(a).contains(compact(b))) {
            return a;
        }
        if (compact(b).contains(compact(a))) {
            return b;
        }
        return a + " " + b;
    }

    private String buildAddressCorrectionText(String changedRoadAddress, String originalRoadAddress, String detailAddress) {
        StringBuilder builder = new StringBuilder();
        builder.append("변경된 주소: ").append(firstNonBlank(changedRoadAddress, "-"));
        builder.append(" / 기존주소: ").append(firstNonBlank(originalRoadAddress, "-"));
        if (!firstNonBlank(detailAddress).isBlank()) {
            builder.append(" / 상세주소: ").append(detailAddress.trim());
        }
        return builder.toString();
    }

    private boolean sameAddressText(String left, String right) {
        String a = compact(left);
        String b = compact(right);
        return !a.isBlank() && a.equals(b);
    }

    private String compact(String value) {
        return normalize(value).replaceAll("\\s+", "");
    }

    private String canonicalProvince(String value) {
        String normalized = normalize(value).replaceAll("\\s+", "");
        return PROVINCE_ALIASES.getOrDefault(normalized, "");
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
