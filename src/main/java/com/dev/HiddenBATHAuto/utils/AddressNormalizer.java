package com.dev.HiddenBATHAuto.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.excel.KakaoDocument;
import com.dev.HiddenBATHAuto.dto.excel.KakaoJibunAddress;
import com.dev.HiddenBATHAuto.dto.excel.KakaoKeywordDoc;
import com.dev.HiddenBATHAuto.dto.excel.KakaoResponse;
import com.dev.HiddenBATHAuto.dto.excel.KakaoRoadAddress;

import lombok.Builder;
import lombok.Value;

/**
 * 카카오 주소 응답 정규화/선택 유틸 - 전체 클래스 - 도/시/구 분리 - 도로명/지번 전체주소 추출 - 우편번호/스코어링/키워드 변환 등
 *
 * 의존 타입: KakaoDocument, KakaoRoadAddress, KakaoJibunAddress, KakaoResponse,
 * KakaoKeywordDoc (동일 패키지 또는 import 경로에 존재해야 합니다)
 */
public class AddressNormalizer {

	// ===================== 핵심 로직: 도/시/구 분리 =====================

	@Value
	@Builder
	public static class AdminParts {
		String doName; // 서울/경기/강원/제주 등(접미사 제거)
		String siName; // 도 단위일 때만 채움(예: 고양시). 서울/부산/세종 등은 빈값.
		String guName; // 서울/부산 등은 r2(중구/영등포구). 도 단위는 r3에서 구/군/시로 끝나면 채움. (r2가 "고양시 일산동구"면 분리)
	}

	/**
	 * 카카오 Document에서 규칙에 맞게 도/시/구를 분리
	 */
	public static AdminParts splitAdmin(KakaoDocument doc) {
		KakaoRoadAddress ra = doc.getRoadAddress();
		KakaoJibunAddress ja = doc.getAddress();

		String r1 = firstNonBlank(ra != null ? ra.getRegion1depthName() : null,
				ja != null ? ja.getRegion1depthName() : null);
		String r2 = firstNonBlank(ra != null ? ra.getRegion2depthName() : null,
				ja != null ? ja.getRegion2depthName() : null);
		String r3 = firstNonBlank(ra != null ? ra.getRegion3depthName() : null,
				ja != null ? ja.getRegion3depthName() : null);

		// 도명은 항상 접미사 제거한 축약형으로 저장
		String doNameNorm = normalizeDo(r1);

		// 메트로 판별을 "정규화된 도명"으로 재확인
		boolean isMetro = isMetroByDo(doNameNorm);
		String siName = "";
		String guName = "";

		if (isMetro) {
			// 서울/부산/대구/인천/광주/대전/울산/세종
			// - 시는 공란, 구는 r2에서 '...구/군'만 추출
			siName = "";
			guName = extractGuLikeToken(r2);
		} else {
			// 경기도/강원/충북/충남/전북/전남/경북/경남/제주
			// 1) 기본: 시=r2, 구=r3(구/군/시로 끝날 때만)
			String siCandidate = safe(r2);
			String guCandidate = pickGuLikeFromR3(r3);

			// 2) 예외: r2가 "고양시 일산동구"처럼 시/구 동시 포함일 때 r2에서 분리
			SplitSiGu split = splitSiGuFromR2(r2);
			if (split != null) {
				siName = split.si;
				guName = split.gu;
			} else {
				siName = siCandidate;
				guName = guCandidate; // 없으면 빈문자
			}
		}

		return AdminParts.builder().doName(safe(doNameNorm)).siName(safe(siName)).guName(safe(guName)).build();
	}

	// ===================== 판별/정규화 유틸 =====================

	/**
	 * '특별시/광역시/특별자치시/특별자치도/도' 접미사를 제거하여 축약 표기 반환 예) '서울특별시'→'서울', '경기도'→'경기',
	 * '제주특별자치도'→'제주'
	 */
	public static String normalizeDo(String r1) {
		if (!StringUtils.hasText(r1))
			return "";
		r1 = r1.trim();
		if (r1.endsWith("특별자치시"))
			return r1.replace("특별자치시", "");
		if (r1.endsWith("특별자치도"))
			return r1.replace("특별자치도", "");
		if (r1.endsWith("특별시"))
			return r1.replace("특별시", "");
		if (r1.endsWith("광역시"))
			return r1.replace("광역시", "");
		if (r1.endsWith("도"))
			return r1.substring(0, r1.length() - 1);
		return r1;
	}

	/** 메트로(서울/부산/대구/인천/광주/대전/울산/세종) 여부 - 정규화된 도명 기준 */
	public static boolean isMetroByDo(String doNorm) {
		if (!StringUtils.hasText(doNorm))
			return false;
		switch (doNorm) {
		case "서울":
		case "부산":
		case "대구":
		case "인천":
		case "광주":
		case "대전":
		case "울산":
		case "세종":
			return true;
		default:
			return false;
		}
	}

	/** r3에서 '...구/군/시'로 끝나는 토큰만 구로 인정 */
	private static String pickGuLikeFromR3(String token) {
		if (!StringUtils.hasText(token))
			return "";
		String t = token.trim();
		if (t.endsWith("구") || t.endsWith("군") || t.endsWith("시"))
			return t;
		return "";
	}

	/** r2가 "고양시 일산동구" 패턴이면 시/구 분리, 아니면 null */
	private static SplitSiGu splitSiGuFromR2(String r2) {
		if (!StringUtils.hasText(r2))
			return null;
		String t = r2.trim();

		// 1) 공백 있는 일반 케이스: "<...시> <...구|군>"
		Pattern p1 = Pattern.compile("^(.+?시)\\s+(.+?(구|군))$");
		Matcher m1 = p1.matcher(t);
		if (m1.find()) {
			return new SplitSiGu(m1.group(1), m1.group(2));
		}

		// 2) 공백 없는 케이스: "고양시일산동구"
		Pattern p2 = Pattern.compile("^(.+?시)(.+?(구|군))$");
		Matcher m2 = p2.matcher(t);
		if (m2.find()) {
			return new SplitSiGu(m2.group(1), m2.group(2));
		}

		// 3) r2가 이미 "성동구" 같은 구만 들어있을 수도 있음(메트로가 아닌데도)
		if (t.endsWith("구") || t.endsWith("군")) {
			// 기본 로직에 맡김
			return null;
		}
		return null;
	}

	/** r2에서 '...구/군'만 뽑아냄 (메트로용 보강) */
	private static String extractGuLikeToken(String r2) {
		if (!StringUtils.hasText(r2))
			return "";
		String t = r2.trim();

		String[] toks = t.split("\\s+");
		for (String tok : toks) {
			if (tok.endsWith("구") || tok.endsWith("군"))
				return tok;
		}
		// 그래도 못 찾으면, 끝이 구/군이면 그대로 반환
		if (t.endsWith("구") || t.endsWith("군"))
			return t;
		return "";
	}

	private static String safe(String s) {
		return s == null ? "" : s.trim();
	}

	private static String firstNonBlank(String... arr) {
		if (arr == null)
			return "";
		for (String s : arr) {
			if (StringUtils.hasText(s))
				return s.trim();
		}
		return "";
	}

	// ===================== 주소 필드 추출 유틸 =====================

	/** 우편번호(도로명 주소에 있을 때만 제공) */
	public static String getZip(KakaoDocument doc) {
		KakaoRoadAddress r = doc.getRoadAddress();
		return r != null && notEmpty(r.getZoneNo()) ? r.getZoneNo() : "";
	}

	/** 도로명 전체주소(없으면 빈문자) */
	public static String getRoadFull(KakaoDocument doc) {
		KakaoRoadAddress r = doc.getRoadAddress();
		if (r != null && notEmpty(r.getAddressName()))
			return r.getAddressName();
		return "";
	}

	/** 지번 전체주소(우선: address.address_name, 보조: document.address_name) */
	public static String getJibunFull(KakaoDocument doc) {
		KakaoJibunAddress a = doc.getAddress();
		if (a != null && notEmpty(a.getAddressName()))
			return a.getAddressName();
		if (notEmpty(doc.getAddressName()))
			return doc.getAddressName();
		return "";
	}

	/** (기존) 도로명 있으면 도로명, 없으면 document.address_name */
	public static String getRoadOrAddressName(KakaoDocument doc) {
		String road = getRoadFull(doc);
		if (notEmpty(road))
			return road;
		if (notEmpty(doc.getAddressName()))
			return doc.getAddressName();
		return "";
	}

	/** 상세 파트 병합 */
	public static String mergeDetails(String... parts) {
		StringBuilder sb = new StringBuilder();
		for (String p : parts) {
			if (notEmpty(p)) {
				if (sb.length() > 0)
					sb.append(' ');
				sb.append(p.trim());
			}
		}
		return sb.toString();
	}

	private static boolean notEmpty(String s) {
		return s != null && !s.isBlank();
	}

	// ===================== 스코어링/키워드 변환 =====================

	/**
	 * Kakao 주소 검색 응답에서 최적 후보 선택 - 도로명 존재 + zone_no 가점 - query 포함/유사 여부 소가점
	 */
	public static KakaoDocument pickBest(KakaoResponse resp, String query) {
		if (resp == null || resp.getDocuments() == null || resp.getDocuments().isEmpty())
			return null;
		KakaoDocument best = null;
		int bestScore = -1;
		for (KakaoDocument d : resp.getDocuments()) {
			int s = 0;
			if (d.getRoadAddress() != null)
				s += 3;
			if (d.getRoadAddress() != null && notEmpty(d.getRoadAddress().getZoneNo()))
				s += 2;
			if (notEmpty(d.getAddressName()) && notEmpty(query)) {
				String a = d.getAddressName().replaceAll("[\\s-]", "");
				String q = query.replaceAll("[\\s-]", "");
				if (a.contains(q) || q.contains(a))
					s += 1;
			}
			if (s > bestScore) {
				bestScore = s;
				best = d;
			}
		}
		return best;
	}

	/**
	 * 키워드 검색 → 주소 필드 변환(도로명/지번 모두 세팅 시도, 행정 파트는 추정치) - zone_no는 제공되지 않음
	 */
	public static NormalizedAddress fromKeyword(KakaoKeywordDoc kd) {
		if (kd == null)
			return null;
		String road = kd.getRoadAddressName(); // 도로명
		String jibun = kd.getAddressName(); // 지번
		String addr = notEmpty(road) ? road : jibun;
		if (!notEmpty(addr))
			return null;

		NormalizedAddress na = new NormalizedAddress();
		na.setRoadAddress(road);
		na.setJibunAddress(jibun);

		// 간단 토큰 추정(정확도 낮음, 보조용)
		String[] toks = addr.split("\\s+");
		if (toks.length > 0)
			na.setDoName(normalizeDo(toks[0]));
		if (toks.length > 1)
			na.setSiName(toks[1]);
		if (toks.length > 2) {
			String g = toks[2];
			na.setGuName(g.endsWith("구") || g.endsWith("군") || g.endsWith("시") ? g : "");
		}
		return na;
	}

	// ===================== 내부 전달용 POJO =====================

	public static class NormalizedAddress {
		private String doName, siName, guName, roadAddress, jibunAddress, zipCode;

		public String getDoName() {
			return doName;
		}

		public void setDoName(String v) {
			doName = v;
		}

		public String getSiName() {
			return siName;
		}

		public void setSiName(String v) {
			siName = v;
		}

		public String getGuName() {
			return guName;
		}

		public void setGuName(String v) {
			guName = v;
		}

		public String getRoadAddress() {
			return roadAddress;
		}

		public void setRoadAddress(String v) {
			roadAddress = v;
		}

		public String getJibunAddress() {
			return jibunAddress;
		}

		public void setJibunAddress(String v) {
			jibunAddress = v;
		}

		public String getZipCode() {
			return zipCode;
		}

		public void setZipCode(String v) {
			zipCode = v;
		}
	}

	// ===================== 내부 도우미 =====================

	private static class SplitSiGu {
		final String si;
		final String gu;

		private SplitSiGu(String si, String gu) {
			this.si = si;
			this.gu = gu;
		}
	}
}