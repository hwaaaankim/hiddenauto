package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 도메인별 보조 답변 서비스입니다.
 *
 * 중요:
 * 이전 버전에는 테스트용 A~E 시리즈/슬라이드 문 규칙을 자동 확장하는 코드가 들어 있었습니다.
 * 그 코드는 실제 하부장 주문 규칙과 무관한 지식을 생성할 수 있어 제거했습니다.
 * 이 클래스는 이제 저장된 processJson/constraintsJson에 근거가 있을 때만 짧은 직답을 보조합니다.
 */
@Service
public class RagDomainRuleExpansionService {

    /**
     * 더 이상 하드코딩된 테스트 지식을 자동 생성하지 않습니다.
     */
    public boolean hasExpandableKnowledge(String text) {
        return false;
    }

    /**
     * 더 이상 저장 지식을 임의로 확장하지 않습니다.
     */
    public String buildExpansionText(String text) {
        return "";
    }

    /**
     * 더 이상 processJson에 임의 규칙을 주입하지 않습니다.
     */
    public Map<String, Object> mergeIntoProcessJson(Map<String, Object> processJson, String text) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (processJson != null) merged.putAll(processJson);
        return merged;
    }

    /**
     * 저장된 JSON 안에 해당 키워드가 존재하는 경우에만, 반복적인 GPT 호출 없이 안전한 직답을 생성합니다.
     */
    public String tryAnswerFromExpandedRules(String message, Map<String, Object> processJson) {
        if (!StringUtils.hasText(message) || processJson == null || processJson.isEmpty()) return null;

        String normalizedMessage = normalize(message);
        String knowledgeText = String.valueOf(processJson);
        String normalizedKnowledge = normalize(knowledgeText);

        if (containsAny(normalizedMessage, "걸레받이", "걸레받이가")) {
            if (!normalizedKnowledge.contains("걸레받이")) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("걸레받이는 형태가 다리형인 경우에만 확인해야 하는 조건부 항목입니다.\n");
            sb.append("- 다리형: 걸레받이 여부를 고객에게 질문합니다.\n");
            sb.append("- 벽걸이형: 걸레받이 질문을 하지 않습니다.\n");
            if (containsAny(normalizedKnowledge, "가격규칙이부족", "가격규칙부족", "필요하면", "물어봐라")) {
                sb.append("- 가격 규칙은 아직 확정되지 않았으므로 계산 전에 추가 확인이 필요합니다.");
            }
            return sb.toString().trim();
        }

        if (containsAny(normalizedMessage, "상판")) {
            if (!normalizedKnowledge.contains("상판")) return null;
            return "상판 질문은 마블시리즈에서만 노출합니다. 마블시리즈가 아닌 경우에는 상판 질문을 하지 않습니다. 상판 가격은 선택 상판의 단가군과 W-D 기준 가격표로 계산해야 하며, 가격표 파일이 필요합니다.";
        }

        if (containsAny(normalizedMessage, "세면대")) {
            if (!normalizedKnowledge.contains("세면대")) return null;
            Integer width = extractWidth(message);
            StringBuilder sb = new StringBuilder();
            sb.append("세면대 질문은 마블시리즈가 아닐 때만 노출합니다. 마블시리즈에서는 세면대 질문을 하지 않습니다.");
            if (width != null) {
                sb.append("\nW ").append(width).append(" 기준 최대 세면대 수량은 ").append(maxBasinCount(width)).append("개입니다.");
            } else {
                sb.append("\n수량 기준은 W 600 미만 1개, W 600 이상 1000 미만 2개, W 1000 이상 3개까지입니다.");
            }
            sb.append("\n세면대 종류별 추가금은 아직 별도 자료가 필요합니다.");
            return sb.toString();
        }

        if (containsAny(normalizedMessage, "문", "여닫이", "서랍", "혼합")) {
            if (!normalizedKnowledge.contains("문")) return null;
            Integer width = extractWidth(message);
            if (width == null) return null;
            DoorRule door = doorRule(width);
            return "W " + width + " 기준 가능한 문 구성은 아래와 같습니다.\n"
                    + "- 구간: " + door.rangeLabel + "\n"
                    + "- 여닫이 기준 최대: " + door.maxSwingDoorSections + "칸\n"
                    + "- 서랍 최대: " + door.maxDrawers + "개\n"
                    + "- 혼합식: 여닫이 1칸 자리에 서랍 1개 또는 2개를 넣는 방식으로 구성 가능합니다.\n"
                    + "- 슬라이드는 현재 확정 규칙에 포함되어 있지 않으므로 가능 문 형태로 답하지 않습니다.";
        }

        if (containsAny(normalizedMessage, "손잡이")) {
            if (!normalizedKnowledge.contains("손잡이")) return null;
            return "손잡이는 설치 여부를 먼저 확인하고, 설치한다면 어떤 문/서랍에 어떤 손잡이를 몇 개 설치할지 반복 입력으로 받아야 합니다. 가격 후보는 10,000원·15,000원·20,000원·25,000원·30,000원이지만, 손잡이 이름과 가격 매칭은 아직 확인이 필요합니다.";
        }

        if (containsAny(normalizedMessage, "마구리")) {
            if (!normalizedKnowledge.contains("마구리")) return null;
            return "마구리는 설치 여부를 확인한 뒤 높이와 설치 면을 입력받습니다. 높이 150mm 미만은 무료이고, 150mm 이상은 설치 면 기준 길이 × 100원으로 계산합니다. 좌/우는 D, 전면/후방은 W, 전좌·전우는 W+D, 전좌우는 W+D+D, 전좌우후는 W+D+D+W 기준입니다.";
        }

        if (containsAny(normalizedMessage, "타공")) {
            if (!normalizedKnowledge.contains("타공")) return null;
            return "타공은 설치 여부, 개수, 위치를 입력받습니다. 위치는 도면 파일이 가능하면 파일로 받고, 어렵다면 텍스트로 받습니다. 1개까지는 무료이고 2개부터 20,000원이 추가된다는 규칙은 저장되어 있으나, ‘2개 이상 총 20,000원’인지 ‘2개째부터 개당 20,000원’인지는 계산 전 확인이 필요합니다.";
        }

        if (containsAny(normalizedMessage, "기타옵션", "드라이걸이", "휴지걸이", "LED", "엘이디")) {
            if (!containsAny(normalizedKnowledge, "드라이걸이", "휴지걸이", "LED")) return null;
            return "기타 옵션은 드라이걸이, 휴지걸이, LED입니다. 설치한다고 하면 상/하/좌/우 중 위치를 받아야 합니다. 드라이걸이 5,000원, 휴지걸이 5,000원, LED 20,000원으로 계산합니다.";
        }

        if (containsAny(normalizedMessage, "사이즈", "크기", "WDH", "W/D/H", "AS", "에이에스")) {
            if (!containsAny(normalizedKnowledge, "사이즈", "WDH", "AS", "무상AS")) return null;
            return "사이즈는 W/D/H 숫자 입력으로 받습니다. 예시 기준은 W 600~1800, D 500~1000, H 600~1200이며 정확한 품목별 범위는 사이즈 제한표 엑셀 업로드 후 확정해야 합니다. H가 1200을 초과하는 제작은 가능할 수 있지만 무상 AS 불가 안내가 필요합니다.";
        }

        return compactKeywordAnswer(message, knowledgeText);
    }

    private String compactKeywordAnswer(String message, String knowledgeText) {
        String keyword = firstKeyword(message);
        if (!StringUtils.hasText(keyword)) return null;
        String normalizedKnowledge = normalize(knowledgeText);
        if (!normalizedKnowledge.contains(normalize(keyword))) return null;

        List<String> hits = new ArrayList<>();
        String[] parts = knowledgeText.split("[\\n,;]");
        for (String part : parts) {
            String p = part == null ? "" : part.trim();
            if (p.length() < 4) continue;
            if (normalize(p).contains(normalize(keyword)) && !hits.contains(p)) hits.add(p);
            if (hits.size() >= 5) break;
        }
        if (hits.isEmpty()) return null;
        return "저장된 지식에서 확인되는 " + keyword + " 관련 내용입니다.\n- " + String.join("\n- ", hits);
    }

    private String firstKeyword(String message) {
        List<String> keywords = List.of("걸레받이", "상판", "세면대", "문", "손잡이", "마구리", "타공", "드라이걸이", "휴지걸이", "LED", "사이즈", "색상", "형태", "품목", "시리즈");
        for (String keyword : keywords) {
            if (message != null && message.contains(keyword)) return keyword;
        }
        return "";
    }

    private int maxBasinCount(int width) {
        if (width < 600) return 1;
        if (width < 1000) return 2;
        return 3;
    }

    private DoorRule doorRule(int width) {
        if (width < 600) return new DoorRule("600 미만", 2, 4);
        if (width < 1000) return new DoorRule("600 이상 1000 미만", 3, 6);
        return new DoorRule("1000 이상", 4, 8);
    }

    private Integer extractWidth(String message) {
        if (!StringUtils.hasText(message)) return null;
        Matcher preferred = Pattern.compile("(?:W|w|넓이|폭)[^0-9]{0,12}(\\d{2,5})").matcher(message);
        if (preferred.find()) return parseInt(preferred.group(1));
        if (!containsAny(message, "넓이", "폭", "W", "w", "문", "세면대")) return null;
        Matcher any = Pattern.compile("(\\d{2,5})").matcher(message);
        if (any.find()) return parseInt(any.group(1));
        return null;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        String t = text;
        String compact = normalize(text);
        for (String needle : needles) {
            if (needle == null) continue;
            if (t.contains(needle) || compact.contains(normalize(needle))) return true;
        }
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").trim().toUpperCase(Locale.ROOT);
    }

    private record DoorRule(String rangeLabel, int maxSwingDoorSections, int maxDrawers) {}
}
