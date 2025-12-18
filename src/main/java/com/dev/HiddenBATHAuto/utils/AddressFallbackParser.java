package com.dev.HiddenBATHAuto.utils;

import java.util.regex.Pattern;

public class AddressFallbackParser {

	public static class FallbackResult {
		public String doName = "";
		public String siName = "";
		public String guName = "";
		public String roadGuess = "";
		public String jibunGuess = "";
	}

	/**
	 * ✅ 카카오가 documents=[]일 때도 "도/시/구"를 규칙대로 파싱
	 *
	 * 규칙: 1) 도가 "*특별시" "*광역시" "*특별자치시" 이면 → 시는 무조건 "" - 그 다음 토큰에서 구/군을 찾으면 구로 채움 2)
	 * 도가 "*도" "*특별자치도" 이면 - 다음 토큰이 ~시/~군 이면 시로 - 그 다음 토큰이 ~구/~군/~시 이면 구로(3단계가 항상 구는
	 * 아니라 방어)
	 */
	public static FallbackResult parse(String raw) {
		FallbackResult r = new FallbackResult();
		if (raw == null)
			return r;

		String s = raw.trim().replaceAll("\\s+", " ");
		if (s.isEmpty())
			return r;

		// 괄호 제거 + 콤마 뒤 제거(상세는 이미 별도로 보관하므로)
		String base = s.replaceAll("\\([^)]*\\)", " ").trim();
		int comma = base.indexOf(',');
		if (comma > -1)
			base = base.substring(0, comma).trim();
		base = base.replaceAll("\\s+", " ").trim();

		String[] toks = base.split(" ");
		if (toks.length == 0)
			return r;

		// 1) 도(광역단위) = 첫 토큰 그대로 사용
		String doTok = toks[0].trim();
		r.doName = doTok;

		boolean noSiGroup = isNoSiGroup(doTok);

		if (noSiGroup) {
			// ✅ 특별시/광역시/특별자치시: 시는 무조건 빈 값
			r.siName = "";

			// ✅ 구/군 찾기: 일반적으로 toks[1]이 "종로구" 같은 형태
			if (toks.length >= 2) {
				String t1 = toks[1].trim();
				if (endsWithAny(t1, "구", "군")) {
					r.guName = t1;
				} else {
					// 예외: toks[1]이 구/군이 아니면 뒤에서 구/군을 탐색
					r.guName = findFirstEndsWith(toks, 1, "구", "군");
				}
			}

		} else {
			// ✅ 도/특별자치도: 2번째 토큰이 시/군이면 시로
			if (toks.length >= 2) {
				String t1 = toks[1].trim();
				if (endsWithAny(t1, "시", "군")) {
					r.siName = t1;

					// 3번째 토큰이 구/군/시이면 구로(구 없는 군 단위면 빈 값 유지)
					if (toks.length >= 3) {
						String t2 = toks[2].trim();
						if (endsWithAny(t2, "구", "군", "시")) {
							r.guName = t2;
						} else {
							r.guName = ""; // 읍/면/동/리 등은 구 없음
						}
					}
				} else {
					// ✅ 예외: 도 다음이 바로 구/군으로 오는 희귀 케이스 방어
					// ex) "경기도 수지구 ..." 같은 데이터 오류/축약
					r.siName = "";
					if (endsWithAny(t1, "구", "군")) {
						r.guName = t1;
					} else {
						r.guName = "";
					}
				}
			}
		}

		// 2) 도로명/지번 guess (엑셀 칸 채우기용, 정규화 목적 아님)
		if (looksLikeRoad(base))
			r.roadGuess = base;
		if (looksLikeJibun(base))
			r.jibunGuess = base;

		return r;
	}

	private static boolean isNoSiGroup(String doTok) {
		if (doTok == null)
			return false;
		String t = doTok.trim();
		return t.endsWith("특별시") || t.endsWith("광역시") || t.endsWith("특별자치시");
	}

	private static boolean endsWithAny(String s, String... suffixes) {
		if (s == null)
			return false;
		String t = s.trim();
		for (String suf : suffixes) {
			if (t.endsWith(suf))
				return true;
		}
		return false;
	}

	private static String findFirstEndsWith(String[] toks, int start, String... suffixes) {
		if (toks == null)
			return "";
		for (int i = start; i < toks.length; i++) {
			String t = toks[i] == null ? "" : toks[i].trim();
			if (endsWithAny(t, suffixes))
				return t;
		}
		return "";
	}

	private static boolean looksLikeRoad(String s) {
		if (s == null)
			return false;
		// "충무로9길 42" / "반송로 259" / "을지로13길 5"
		return Pattern.compile("(로|길)\\s*\\d{1,5}").matcher(s).find();
	}

	private static boolean looksLikeJibun(String s) {
		if (s == null)
			return false;
		// "... (동|리|가) 123-4"
		return Pattern.compile("(동|리|가)\\s*\\d+(?:-\\d+)?").matcher(s).find();
	}
}