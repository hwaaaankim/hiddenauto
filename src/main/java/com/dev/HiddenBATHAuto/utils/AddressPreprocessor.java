package com.dev.HiddenBATHAuto.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddressPreprocessor {

	/** 도로명 뒤 'N가' 제거: '을지로 3가 50' -> '을지로 50' */
	public static String removeGaAfterRoad(String s) {
	    if (s == null) return "";
	    // '…로 N가 ' 패턴 제거
	    String r = s.replaceAll("([가-힣A-Za-z]+로)\\s*\\d+가\\b", "$1");
	    // '충무로4가 10' -> '충무로 10'
	    r = r.replaceAll("([가-힣A-Za-z]+로)\\s*\\d+가\\s*", "$1 ");
	    r = r.replaceAll("\\s+", " ").trim();
	    return r;
	}

	/** 지번형(동/리 + 숫자[-숫자])만 추출: '서울 중구 을지로 125-1 (을지로3가)' -> '서울 중구 을지로동 125-1' X 
	 *  입력에 '동/리 + 지번'이 있을 때만 구성
	 */
	public static String buildJibunQuery(String s) {
	    if (s == null) return "";
	    String t = stripParen(s);
	    t = t.replaceAll("\\s+", " ").trim();
	    // 광역 보강
	    t = ensureProvince(t);
	    // 패턴: "... 동 123-4" 또는 "... 리 123-4"
	    java.util.regex.Matcher m = java.util.regex.Pattern
	            .compile("^(?<head>.*?)(?<dongri>\\S+(동|리))\\s+(?<no>\\d+(?:-\\d+)?)")
	            .matcher(t);
	    if (m.find()) {
	        String head = m.group("head").trim();
	        String dr   = m.group("dongri").trim();
	        String no   = m.group("no").trim();
	        return (head + " " + dr + " " + no).replaceAll("\\s+", " ").trim();
	    }
	    return "";
	}
	
	public static String buildCompactQuery(String s, int maxLen) {
		if (s == null)
			return "";
		String q = s.trim();

		// 1) 이미 있는 쉼표 뒤/괄호 상세 제거(본문만 남김)
		q = stripAfterComma(stripParen(q));

		// 2) 도로명 붙임 띄우기 + 잡음 제거 + 도 보강
		q = normalizeRoadSpacing(q);
		q = stripNoise(q);
		q = ensureProvince(q);

		// 3) 토큰 단위로 앞에서부터 누적하며 maxLen 이하로 유지
		String[] toks = q.split("\\s+");
		StringBuilder sb = new StringBuilder();
		for (String t : toks) {
			if (sb.length() == 0) {
				if (t.length() <= maxLen)
					sb.append(t);
				else
					sb.append(cutByCodePoint(t, maxLen));
			} else {
				if (sb.length() + 1 + t.length() <= maxLen) {
					sb.append(' ').append(t);
				} else {
					break;
				}
			}
		}
		return sb.toString();
	}

	/** 유니코드 코드포인트 기준 안전 자르기(문자 100자 제한 대응) */
	public static String cutByCodePoint(String s, int maxLen) {
		if (s == null)
			return "";
		int count = s.codePointCount(0, s.length());
		if (count <= maxLen)
			return s;
		int endIndex = s.offsetByCodePoints(0, maxLen);
		return s.substring(0, endIndex);
	}

	// 광역 축약 → 정식명
	private static final Map<String, String> REGION_ALIAS = Map.ofEntries(Map.entry("서울시", "서울특별시"),
			Map.entry("인천시", "인천광역시"), Map.entry("부산시", "부산광역시"), Map.entry("대구시", "대구광역시"), Map.entry("광주시", "광주광역시"),
			Map.entry("대전시", "대전광역시"), Map.entry("울산시", "울산광역시"));

	// 시 → 도 보강(최소 샘플; 필요시 추가)
	private static final Map<String, String> CITY_TO_DO = Map.ofEntries(Map.entry("군포시", "경기도"),
			Map.entry("성남시", "경기도"), Map.entry("수원시", "경기도"), Map.entry("고양시", "경기도"), Map.entry("하남시", "경기도"),
			Map.entry("안양시", "경기도"), Map.entry("부천시", "경기도"), Map.entry("남양주시", "경기도"));

	public static String clean(String raw) {
		if (raw == null)
			return "";
		String s = raw.trim();
		s = s.replaceAll("^(창고주소\\s*[:：])\\s*", ""); // 창고주소: 제거
		s = s.replaceAll("[,，]+", ", ");
		s = s.replaceAll("\\s+", " ");
		// 광역 축약 교정
		for (var e : REGION_ALIAS.entrySet()) {
			if (s.startsWith(e.getKey())) {
				s = s.replaceFirst("^" + Pattern.quote(e.getKey()), e.getValue());
				break;
			}
		}
		return s;
	}

	/** 괄호 내용 → detail 후보 */
	public static String extractParenDetail(String cleaned) {
		Matcher m = Pattern.compile("\\(([^)]+)\\)").matcher(cleaned);
		List<String> d = new ArrayList<>();
		while (m.find())
			d.add(m.group(1));
		return d.isEmpty() ? "" : String.join(" ", d);
	}

	/** 괄호 제거 */
	public static String stripParen(String cleaned) {
		return cleaned.replaceAll("\\([^)]*\\)", "").trim();
	}

	/** "…, 102" 같은 쉼표 뒤 상세 */
	public static String extractCommaTailDetail(String stripped) {
		int idx = stripped.indexOf(',');
		if (idx > -1 && idx < stripped.length() - 1) {
			return stripped.substring(idx + 1).trim();
		}
		return "";
	}

	/** 쉼표 이전까지 본문 */
	public static String stripAfterComma(String stripped) {
		int idx = stripped.indexOf(',');
		if (idx > -1)
			return stripped.substring(0, idx).trim();
		return stripped;
	}

	/** 도로명 붙임 정규화: "내정로107번길" → "내정로 107번길", "학동로24길" → "학동로 24길" */
	public static String normalizeRoadSpacing(String q) {
		if (q == null)
			return "";
		String s = q;
		// "...로숫자번길" 패턴
		s = s.replaceAll("([가-힣A-Za-z]+로)(\\d+번길)", "$1 $2");
		// "...로숫자" (예: 학동로24)
		s = s.replaceAll("([가-힣A-Za-z]+로)(\\d+)([^\\d])", "$1 $2$3");
		// "...길숫자" (예: 봉은사로44길36 → 봉은사로44길 36) — 먼저 로/길 사이 보정 후, 길 뒤 숫자 분리
		s = s.replaceAll("([가-힣A-Za-z0-9]+길)(\\d+)", "$1 $2");
		// 공백 2회 이상 정리
		s = s.replaceAll("\\s+", " ").trim();
		return s;
	}

	/** 잡음 토큰 제거 → detail 이동용 */
	public static String extractNoiseToDetail(String s) {
		if (s == null)
			return "";
		// 번지/외n필지/층/호/상가/빌딩호실 등
		Matcher m = Pattern.compile("(\\d+\\s*층|\\d+\\s*호|\\d+-?\\d*\\s*호|외\\d+필지|\\S+빌딩\\d*층|\\S+상가\\d*호)").matcher(s);
		List<String> d = new ArrayList<>();
		while (m.find())
			d.add(m.group());
		return d.isEmpty() ? "" : String.join(" ", d);
	}

	/** 본문에서 잡음 제거 */
	public static String stripNoise(String s) {
		if (s == null)
			return "";
		String r = s;
		r = r.replaceAll("번지", ""); // 지번 토큰
		r = r.replaceAll("외\\d+필지", ""); // 필지
		r = r.replaceAll("\\d+\\s*층", ""); // 층
		r = r.replaceAll("\\d+\\s*호", ""); // 호
		r = r.replaceAll("\\S+빌딩\\d*층", "");
		r = r.replaceAll("\\S+상가\\d*호", "");
		r = r.replaceAll("\\s+", " ").trim();
		return r;
	}

	/** 도(광역) 보강: 시작이 '군포시' 같은 경우 → '경기도 군포시 ...'로 */
	public static String ensureProvince(String s) {
		if (s == null || s.isBlank())
			return s;
		// 첫 토큰이 'OO시/OO군/OO구'로 시작하면 도 보강
		Matcher m = Pattern.compile("^(\\S+시|\\S+군|\\S+구)\\b").matcher(s);
		if (m.find()) {
			String si = m.group(1);
			String province = CITY_TO_DO.get(si);
			if (province != null) {
				return province + " " + s;
			}
		}
		return s;
	}
}