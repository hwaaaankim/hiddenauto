package com.dev.HiddenBATHAuto.service.production;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingParsedOptionsDto;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MaterialCuttingOptionParser {

    private static final int MATERIAL_THICKNESS_MM = 15;
    private static final int LEG_HEIGHT_MM = 180;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d{2,4})");
    private static final Pattern EXPLICIT_WDH_PATTERN = Pattern.compile(
            "(?i)(?:w|width|넓이|폭)\\s*[:=]?\\s*(\\d{2,4}).*?(?:d|depth|깊이)\\s*[:=]?\\s*(\\d{2,4}).*?(?:h|height|높이)\\s*[:=]?\\s*(\\d{2,4})"
    );
    private static final Pattern EXPLICIT_WHD_PATTERN = Pattern.compile(
            "(?i)(?:w|width|넓이|폭)\\s*[:=]?\\s*(\\d{2,4}).*?(?:h|height|높이)\\s*[:=]?\\s*(\\d{2,4}).*?(?:d|depth|깊이)\\s*[:=]?\\s*(\\d{2,4})"
    );

    private final ObjectMapper objectMapper;
    private final MaterialCuttingSeriesProfileRegistry seriesProfileRegistry;

    public MaterialCuttingParsedOptionsDto parse(Order order) {
        Map<String, String> sourceOptions = parseSourceOptions(order);

        String productSeries = pickFirstValue(sourceOptions, List.of(
                "제품시리즈", "시리즈", "series", "productSeries", "ProductSeries"
        ));
        String productName = pickFirstValue(sourceOptions, List.of(
                "제품", "제품명", "product", "productName", "ProductName"
        ));

        if (isBlank(productName) && order != null && order.getOrderItem() != null) {
            productName = safeText(order.getOrderItem().getProductName());
        }

        String searchableText = joinForSearch(productSeries, productName, sourceOptions);

        int[] size = parseSize(sourceOptions, searchableText);
        Integer width = size[0] > 0 ? size[0] : null;
        Integer depth = size[1] > 0 ? size[1] : null;
        Integer height = size[2] > 0 ? size[2] : null;

        MaterialCuttingSeriesProfile seriesProfile = seriesProfileRegistry
                .resolveByText(searchableText)
                .orElse(null);
        InstallType installType = detectInstallType(sourceOptions, searchableText);
        TopType topType = detectTopType(sourceOptions, searchableText, height);
        DoorMode doorMode = detectDoorMode(sourceOptions, searchableText);
        boolean indoorDoor = !containsAny(normalize(searchableText), List.of("아웃도어", "아웃 도어", "outdoor", "out-door"));

        MarbleEdge marbleEdge = detectMarbleEdge(sourceOptions, searchableText, topType);
        int doorCount = detectDoorCount(sourceOptions, searchableText, width);
        boolean sixHundredWidthTarget = isSixHundredWidthTarget(width, searchableText);

        String bodyType = installType == InstallType.LEG ? "LEG" : installType == InstallType.WALL ? "WALL" : "UNKNOWN";
        String bodyTypeLabel = installType.label;

        String doorModeCode = doorMode == DoorMode.HINGED ? "HINGED" : doorMode == DoorMode.NON_HINGED ? "NON_HINGED" : "UNKNOWN";
        String doorModeLabel = doorMode.label;

        boolean edgeFront = marbleEdge.edgeFront;
        boolean edgeBack = marbleEdge.edgeBack;
        boolean edgeLeft = marbleEdge.edgeLeft;
        boolean edgeRight = marbleEdge.edgeRight;
        String edgeLabel = marbleEdge.label;

        ComputedBody body = computeBody(width, depth, height, installType, topType, marbleEdge);

        String formulaSummary = buildFormulaSummary(seriesProfile, installType, topType, marbleEdge, body);

        return new MaterialCuttingParsedOptionsDto(
                width,
                depth,
                height,
                MATERIAL_THICKNESS_MM,
                LEG_HEIGHT_MM,
                bodyType,
                bodyTypeLabel,
                doorModeCode,
                doorModeLabel,
                edgeFront,
                edgeBack,
                edgeLeft,
                edgeRight,
                edgeLabel,
                sourceOptions,
                seriesProfile != null ? seriesProfile.seriesCode() : "UNKNOWN",
                seriesProfile != null ? seriesProfile.seriesLabel() : "미분류",
                seriesProfile != null ? seriesProfile.formulaCode() : "UNKNOWN",
                seriesProfile != null ? seriesProfile.formulaLabel() : "공식 미확인",
                installType.code,
                installType.label,
                topType.code,
                topType.label,
                marbleEdge.code,
                marbleEdge.label,
                doorCount,
                indoorDoor,
                sixHundredWidthTarget,
                body.bodyWidthMm,
                body.bodyDepthMm,
                body.bodyHeightMm,
                formulaSummary
        );
    }

    private Map<String, String> parseSourceOptions(Order order) {
        Map<String, String> result = new LinkedHashMap<>();

        if (order == null) {
            return result;
        }

        OrderItem item = order.getOrderItem();

        if (item == null) {
            return result;
        }

        /*
         * 중요:
         * 생산 리스트에 보이는 값은 optionJson 원문만이 아니라
         * OrderItem.productionProductName / productionSize / productionColor / productionCategory
         * 같은 생산용 정규화 필드에서 오는 경우가 많습니다.
         * 따라서 optionJson 파싱 전에 이 필드들을 먼저 검색 대상에 넣습니다.
         */
        putIfNotBlank(result, "주문상품명", item.getProductName());
        putIfNotBlank(result, "생산제품명", item.getProductionProductName());
        putIfNotBlank(result, "생산사이즈", item.getProductionSize());
        putIfNotBlank(result, "생산색상", item.getProductionColor());
        putIfNotBlank(result, "생산카테고리", item.getProductionCategory());

        putIfNotBlank(result, "관리자비고", order.getAdminMemo());
        putIfNotBlank(result, "주문비고", order.getOrderComment());

        if (isBlank(item.getOptionJson())) {
            return result;
        }

        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    item.getOptionJson(),
                    new TypeReference<Map<String, Object>>() {
                    }
            );

            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                String key = safeText(entry.getKey());
                String value = safeText(entry.getValue());

                if (!key.isBlank() && !value.isBlank()) {
                    result.put(key, value);
                }
            }
        } catch (Exception e) {
            // optionJson 파싱 실패 시에도 화면 전체가 죽지 않도록 원문만 보존합니다.
            result.put("optionJson", item.getOptionJson());
        }

        return result;
    }

    private int[] parseSize(Map<String, String> options, String searchableText) {
        String sizeText = pickFirstValue(options, List.of(
                "사이즈", "제품사이즈", "size", "Size", "규격", "규격사이즈"
        ));

        String merged = joinForSearch(sizeText, searchableText, options);
        String normalized = normalize(merged);

        Matcher wdh = EXPLICIT_WDH_PATTERN.matcher(normalized);
        if (wdh.find()) {
            return new int[] {toInt(wdh.group(1)), toInt(wdh.group(2)), toInt(wdh.group(3))};
        }

        Matcher whd = EXPLICIT_WHD_PATTERN.matcher(normalized);
        if (whd.find()) {
            return new int[] {toInt(whd.group(1)), toInt(whd.group(3)), toInt(whd.group(2))};
        }

        Integer explicitW = pickIntByKeys(options, List.of("W", "w", "넓이", "폭", "width", "Width"));
        Integer explicitD = pickIntByKeys(options, List.of("D", "d", "깊이", "depth", "Depth"));
        Integer explicitH = pickIntByKeys(options, List.of("H", "h", "높이", "height", "Height"));

        if (explicitW != null || explicitD != null || explicitH != null) {
            return new int[] {
                    explicitW != null ? explicitW : 0,
                    explicitD != null ? explicitD : 0,
                    explicitH != null ? explicitH : 0
            };
        }

        Matcher matcher = NUMBER_PATTERN.matcher(sizeText.isBlank() ? normalized : sizeText);
        int[] numbers = new int[3];
        int index = 0;
        while (matcher.find() && index < 3) {
            numbers[index++] = toInt(matcher.group(1));
        }

        if (index < 3) {
            return new int[] {0, 0, 0};
        }

        int a = numbers[0];
        int b = numbers[1];
        int c = numbers[2];

        /*
         * 무라벨 3개 숫자 해석
         * - 500*800*180: W/H/D 계열로 보고 W=500, D=180, H=800
         * - 630*460*800: W/D/H 계열로 보고 W=630, D=460, H=800
         */
        if (b >= 650 && c <= 600) {
            return new int[] {a, c, b};
        }

        return new int[] {a, b, c};
    }

    private ComputedBody computeBody(
            Integer width,
            Integer depth,
            Integer height,
            InstallType installType,
            TopType topType,
            MarbleEdge marbleEdge
    ) {
        if (width == null || depth == null || height == null
                || installType == InstallType.UNKNOWN
                || topType == TopType.UNKNOWN) {
            return new ComputedBody(null, null, null);
        }

        int bodyW;
        int bodyD;
        int bodyH;

        if (topType == TopType.CERAMIC) {
            bodyW = width - (MATERIAL_THICKNESS_MM * 2);
            bodyD = depth - 5;
            bodyH = installType == InstallType.LEG
                    ? height - 150 - 20
                    : height - 15;
        } else {
            bodyW = width
                    - (marbleEdge.edgeLeft ? MATERIAL_THICKNESS_MM : 0)
                    - (marbleEdge.edgeRight ? MATERIAL_THICKNESS_MM : 0);
            bodyD = depth
                    - (marbleEdge.edgeFront ? MATERIAL_THICKNESS_MM : 0)
                    - (marbleEdge.edgeBack ? MATERIAL_THICKNESS_MM : 0);
            bodyH = installType == InstallType.LEG
                    ? height - 150 - 20
                    : height - 15;
        }

        return new ComputedBody(
                Math.max(bodyW, 0),
                Math.max(bodyD, 0),
                Math.max(bodyH, 0)
        );
    }

    private String buildFormulaSummary(
            MaterialCuttingSeriesProfile seriesProfile,
            InstallType installType,
            TopType topType,
            MarbleEdge marbleEdge,
            ComputedBody body
    ) {
        if (body.bodyWidthMm == null || body.bodyDepthMm == null || body.bodyHeightMm == null) {
            return "필수 옵션 부족으로 장 기준 사이즈를 계산하지 못했습니다.";
        }

        String seriesLabel = seriesProfile != null ? seriesProfile.seriesLabel() : "미분류";

        return seriesLabel + " / "
                + installType.label + " / "
                + topType.label + (topType == TopType.MARBLE ? " / " + marbleEdge.label : "")
                + " => 장 기준 "
                + body.bodyWidthMm + "W × "
                + body.bodyDepthMm + "D × "
                + body.bodyHeightMm + "H";
    }

    private CuttingSeries detectSeries(String text) {
        String n = normalize(text);

        if (n.contains("클린")) {
            return CuttingSeries.CLEAN;
        }
        if (n.contains("심플")) {
            return CuttingSeries.SIMPLE;
        }
        if (n.contains("소프트")) {
            return CuttingSeries.SOFT;
        }
        if (n.contains("코지")) {
            return CuttingSeries.COZY;
        }
        if (n.contains("라운드")) {
            return CuttingSeries.ROUND;
        }

        return CuttingSeries.UNKNOWN;
    }

    private InstallType detectInstallType(Map<String, String> options, String text) {
        String explicit = pickFirstValue(options, List.of(
                "설치타입", "설치 타입", "설치형태", "설치 형태", "형태", "바디형태", "다리", "다리 여부", "타입"
        ));
        String n = normalize(joinForSearch(explicit, text, options));

        if (containsAny(n, List.of("벽걸이", "벽걸", "무다리", "다리없", "다리 없음", "wall"))) {
            return InstallType.WALL;
        }

        if (containsAny(n, List.of("다리형", "다리 있음", "다리있", "다리", "leg"))) {
            return InstallType.LEG;
        }

        /*
         * 기존 데이터에는 다리형/벽걸이형이 옵션 원문에 빠지고
         * 제품명/생산카테고리에는 "하부장"만 들어있는 경우가 있습니다.
         * 별도 벽걸이 표기가 없으면 하부장 기본값은 다리형으로 봅니다.
         */
        if (containsAny(n, List.of("하부장", "하부 장"))) {
            return InstallType.LEG;
        }

        return InstallType.UNKNOWN;
    }

    private TopType detectTopType(Map<String, String> options, String text, Integer heightMm) {
        String explicit = pickFirstValue(options, List.of(
                "상판타입", "상판 타입", "세면대타입", "세면대 타입", "도기/대리석", "세면대", "상판", "도기", "대리석"
        ));
        String n = normalize(joinForSearch(explicit, text, options));

        if (containsAny(n, List.of("도기", "매립", "ceramic"))) {
            return TopType.CERAMIC;
        }

        if (containsAny(n, List.of("대리석", "상판", "marble"))) {
            return TopType.MARBLE;
        }

        /*
         * 기존 생산 리스트 데이터에는 "클린 하부장"처럼 상판 타입이 빠져도
         * H=700 대리석형, H=800 도기형 패턴으로 저장된 건이 있습니다.
         * 명시 텍스트가 없을 때만 높이로 보정합니다.
         */
        if (heightMm != null) {
            if (heightMm <= 720) {
                return TopType.MARBLE;
            }
            if (heightMm >= 780) {
                return TopType.CERAMIC;
            }
        }

        return TopType.UNKNOWN;
    }

    private DoorMode detectDoorMode(Map<String, String> options, String text) {
        String explicit = pickFirstValue(options, List.of(
                "문형태", "문 형태", "문타입", "문 타입", "도어타입", "도어 타입", "형태", "열림방식", "문 방식"
        ));
        String n = normalize(joinForSearch(explicit, text, options));

        if (containsAny(n, List.of("서랍", "슬라이드", "레일", "drawer", "slide"))) {
            return DoorMode.NON_HINGED;
        }

        if (containsAny(n, List.of("여닫이", "힌지", "hinge", "hinged", "도어", "문"))) {
            return DoorMode.HINGED;
        }

        // 대상 시리즈의 6xx 장은 별도 서랍/슬라이드 표기가 없으면 여닫이로 취급합니다.
        return DoorMode.HINGED;
    }

    private MarbleEdge detectMarbleEdge(Map<String, String> options, String text, TopType topType) {
        if (topType != TopType.MARBLE) {
            return MarbleEdge.none();
        }

        String raw = pickFirstValue(options, List.of(
                "마구리", "마구리면", "마구리 면", "마구리면수", "마구리 면수", "대리석마구리", "대리석 마구리"
        ));
        String n = normalize(raw);

        if (n.isBlank()) {
            return new MarbleEdge("EDGE_3_DEFAULT", "3면(좌/우/전, 기본값)", true, false, true, true);
        }

        if (n.contains("3")) {
            return new MarbleEdge("EDGE_3", "3면(좌/우/전)", true, false, true, true);
        }

        if (n.contains("2")) {
            boolean front = containsAny(n, List.of("전", "앞", "front"));
            boolean back = containsAny(n, List.of("후", "뒤", "back"));
            boolean left = containsAny(n, List.of("좌", "왼", "left"));
            boolean right = containsAny(n, List.of("우", "오른", "right"));

            if (!front && !back && !left && !right) {
                left = true;
                right = true;
            }

            return new MarbleEdge("EDGE_2", buildEdgeLabel(front, back, left, right), front, back, left, right);
        }

        boolean front = containsAny(n, List.of("전", "앞", "front"));
        boolean back = containsAny(n, List.of("후", "뒤", "back"));
        boolean left = containsAny(n, List.of("좌", "왼", "left"));
        boolean right = containsAny(n, List.of("우", "오른", "right"));

        if (front || back || left || right) {
            int count = 0;
            count += front ? 1 : 0;
            count += back ? 1 : 0;
            count += left ? 1 : 0;
            count += right ? 1 : 0;
            return new MarbleEdge("EDGE_" + count, buildEdgeLabel(front, back, left, right), front, back, left, right);
        }

        return MarbleEdge.unknown();
    }

    private String buildEdgeLabel(boolean front, boolean back, boolean left, boolean right) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        if (left) {
            sb.append("좌");
            count++;
        }
        if (right) {
            if (sb.length() > 0) {
                sb.append("/");
            }
            sb.append("우");
            count++;
        }
        if (front) {
            if (sb.length() > 0) {
                sb.append("/");
            }
            sb.append("전");
            count++;
        }
        if (back) {
            if (sb.length() > 0) {
                sb.append("/");
            }
            sb.append("후");
            count++;
        }
        return count + "면(" + sb + ")";
    }

    private int detectDoorCount(Map<String, String> options, String text, Integer width) {
        Integer explicit = pickIntByKeys(options, List.of(
                "문수량", "문 수량", "도어수량", "도어 수량", "문짝수", "문짝 수", "도어", "문"
        ));

        if (explicit != null && explicit > 0) {
            return explicit;
        }

        Matcher matcher = Pattern.compile("(\\d+)\\s*(?:도어|door|문)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (matcher.find()) {
            int count = toInt(matcher.group(1));
            if (count > 0) {
                return count;
            }
        }

        return width != null && width < 550 ? 1 : 2;
    }

    private boolean isSixHundredWidthTarget(Integer width, String text) {
        if (width != null && width >= 600 && width < 700) {
            return true;
        }

        Matcher matcher = Pattern.compile("6\\d{2}\\s*장").matcher(text);
        return matcher.find();
    }

    private Integer pickIntByKeys(Map<String, String> options, List<String> keys) {
        String value = pickFirstValue(options, keys);
        if (value.isBlank()) {
            return null;
        }

        Matcher matcher = NUMBER_PATTERN.matcher(value);
        if (matcher.find()) {
            return toInt(matcher.group(1));
        }

        return null;
    }

    private void putIfNotBlank(Map<String, String> map, String key, Object value) {
        if (map == null || isBlank(key)) {
            return;
        }

        String text = safeText(value);
        if (!text.isBlank()) {
            map.put(key, text);
        }
    }

    private String pickFirstValue(Map<String, String> map, List<String> keys) {
        if (map == null || map.isEmpty() || keys == null) {
            return "";
        }

        for (String key : keys) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (normalizeKey(entry.getKey()).equals(normalizeKey(key))) {
                    String value = safeText(entry.getValue());
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
        }

        return "";
    }

    private String joinForSearch(String a, String b, Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        if (!isBlank(a)) {
            sb.append(a).append(' ');
        }
        if (!isBlank(b)) {
            sb.append(b).append(' ');
        }
        if (map != null) {
            map.forEach((k, v) -> sb.append(k).append(' ').append(v).append(' '));
        }
        return sb.toString();
    }

    private String joinForSearch(String... values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (!isBlank(value)) {
                sb.append(value).append(' ');
            }
        }
        return sb.toString();
    }

    private boolean containsAny(String text, List<String> needles) {
        if (text == null || needles == null) {
            return false;
        }

        for (String needle : needles) {
            if (text.contains(normalize(needle))) {
                return true;
            }
        }

        return false;
    }

    private int toInt(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private String normalizeKey(String value) {
        return normalize(value).replace(" ", "");
    }

    private String normalize(String value) {
        return safeText(value).toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private String safeText(Object value) {
        if (value == null) {
            return "";
        }

        return String.valueOf(value)
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private enum CuttingSeries {
        CLEAN("CLEAN", "클린시리즈"),
        SIMPLE("SIMPLE", "심플시리즈"),
        SOFT("SOFT", "소프트시리즈"),
        COZY("COZY", "코지시리즈"),
        ROUND("ROUND", "라운드시리즈"),
        UNKNOWN("UNKNOWN", "미분류");

        private final String code;
        private final String label;

        CuttingSeries(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }

    private enum InstallType {
        LEG("LEG", "다리형"),
        WALL("WALL", "벽걸이형"),
        UNKNOWN("UNKNOWN", "설치형태 미확인");

        private final String code;
        private final String label;

        InstallType(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }

    private enum TopType {
        CERAMIC("CERAMIC", "도기타입"),
        MARBLE("MARBLE", "대리석타입"),
        UNKNOWN("UNKNOWN", "상판타입 미확인");

        private final String code;
        private final String label;

        TopType(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }

    private enum DoorMode {
        HINGED("여닫이"),
        NON_HINGED("여닫이 아님"),
        UNKNOWN("문 형태 미확인");

        private final String label;

        DoorMode(String label) {
            this.label = label;
        }
    }

    private record ComputedBody(Integer bodyWidthMm, Integer bodyDepthMm, Integer bodyHeightMm) {
    }

    private record MarbleEdge(
            String code,
            String label,
            boolean edgeFront,
            boolean edgeBack,
            boolean edgeLeft,
            boolean edgeRight
    ) {
        private static MarbleEdge none() {
            return new MarbleEdge("NONE", "해당없음", false, false, false, false);
        }

        private static MarbleEdge unknown() {
            return new MarbleEdge("UNKNOWN", "마구리 미확인", false, false, false, false);
        }
    }
}
