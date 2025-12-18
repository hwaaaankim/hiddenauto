package com.dev.HiddenBATHAuto.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.address.KakaoCoord2AddressAddress;
import com.dev.HiddenBATHAuto.dto.address.KakaoCoord2AddressDoc;
import com.dev.HiddenBATHAuto.dto.address.KakaoCoord2AddressResponse;
import com.dev.HiddenBATHAuto.dto.address.KakaoCoord2AddressRoadAddress;
import com.dev.HiddenBATHAuto.dto.address.KakaoDocument;
import com.dev.HiddenBATHAuto.dto.address.KakaoJibunAddress;
import com.dev.HiddenBATHAuto.dto.address.KakaoKeywordDoc;
import com.dev.HiddenBATHAuto.dto.address.KakaoResponse;
import com.dev.HiddenBATHAuto.dto.address.KakaoRoadAddress;

import lombok.Builder;
import lombok.Value;

public class AddressNormalizer {

    @Value
    @Builder
    public static class AdminParts {
        String doName;
        String siName;
        String guName;
    }

    public static AdminParts splitAdmin(KakaoDocument doc) {
        KakaoRoadAddress ra = doc.getRoadAddress();
        KakaoJibunAddress ja = doc.getAddress();

        String r1 = firstNonBlank(
                ra != null ? ra.getRegion1depthName() : null,
                ja != null ? ja.getRegion1depthName() : null
        );
        String r2 = firstNonBlank(
                ra != null ? ra.getRegion2depthName() : null,
                ja != null ? ja.getRegion2depthName() : null
        );
        String r3 = firstNonBlank(
                ra != null ? ra.getRegion3depthName() : null,
                ja != null ? ja.getRegion3depthName() : null
        );

        String doNameNorm = normalizeDo(r1);
        boolean isMetro = isMetroByDo(doNameNorm);

        String siName = "";
        String guName = "";

        if (isMetro) {
            siName = "";
            guName = extractGuLikeToken(r2);
        } else {
            SplitSiGu split = splitSiGuFromR2(r2);
            if (split != null) {
                siName = split.si;
                guName = split.gu;
            } else {
                siName = safe(r2);
                guName = pickGuLikeFromR3(r3);
            }
        }

        return AdminParts.builder()
                .doName(safe(doNameNorm))
                .siName(safe(siName))
                .guName(safe(guName))
                .build();
    }

    /** ✅ coord2address → NormalizedAddress */
    public static NormalizedAddress fromCoord2Address(KakaoCoord2AddressResponse resp) {
        if (resp == null || resp.getDocuments() == null || resp.getDocuments().isEmpty()) return null;

        KakaoCoord2AddressDoc d0 = resp.getDocuments().get(0);
        if (d0 == null) return null;

        KakaoCoord2AddressRoadAddress road = d0.getRoadAddress();
        KakaoCoord2AddressAddress addr = d0.getAddress();

        String roadFull = road != null ? safe(road.getAddressName()) : "";
        String zip = road != null ? safe(road.getZoneNo()) : "";

        String jibunFull = addr != null ? safe(addr.getAddressName()) : "";
        String r1 = addr != null ? safe(addr.getRegion1depthName()) : "";
        String r2 = addr != null ? safe(addr.getRegion2depthName()) : "";
        String r3 = addr != null ? safe(addr.getRegion3depthName()) : "";

        String doNorm = normalizeDo(r1);
        boolean isMetro = isMetroByDo(doNorm);

        String siName = "";
        String guName = "";

        if (isMetro) {
            siName = "";
            guName = extractGuLikeToken(r2);
        } else {
            SplitSiGu split = splitSiGuFromR2(r2);
            if (split != null) {
                siName = split.si;
                guName = split.gu;
            } else {
                siName = safe(r2);
                guName = pickGuLikeFromR3(r3);
            }
        }

        NormalizedAddress na = new NormalizedAddress();
        na.setZipCode(zip);
        na.setDoName(doNorm);
        na.setSiName(siName);
        na.setGuName(guName);
        na.setRoadAddress(roadFull);
        na.setJibunAddress(jibunFull);

        if (!notEmpty(na.getRoadAddress()) && !notEmpty(na.getJibunAddress())) return null;
        return na;
    }

    public static String normalizeDo(String r1) {
        if (!StringUtils.hasText(r1)) return "";
        r1 = r1.trim();
        if (r1.endsWith("특별자치시")) return r1.replace("특별자치시", "");
        if (r1.endsWith("특별자치도")) return r1.replace("특별자치도", "");
        if (r1.endsWith("특별시"))     return r1.replace("특별시", "");
        if (r1.endsWith("광역시"))     return r1.replace("광역시", "");
        if (r1.endsWith("도"))         return r1.substring(0, r1.length() - 1);
        return r1;
    }

    public static boolean isMetroByDo(String doNorm) {
        if (!StringUtils.hasText(doNorm)) return false;
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

    private static String pickGuLikeFromR3(String token) {
        if (!StringUtils.hasText(token)) return "";
        String t = token.trim();
        if (t.endsWith("구") || t.endsWith("군") || t.endsWith("시")) return t;
        return "";
    }

    private static SplitSiGu splitSiGuFromR2(String r2) {
        if (!StringUtils.hasText(r2)) return null;
        String t = r2.trim();

        Pattern p1 = Pattern.compile("^(.+?시)\\s+(.+?(구|군))$");
        Matcher m1 = p1.matcher(t);
        if (m1.find()) return new SplitSiGu(m1.group(1), m1.group(2));

        Pattern p2 = Pattern.compile("^(.+?시)(.+?(구|군))$");
        Matcher m2 = p2.matcher(t);
        if (m2.find()) return new SplitSiGu(m2.group(1), m2.group(2));

        return null;
    }

    private static String extractGuLikeToken(String r2) {
        if (!StringUtils.hasText(r2)) return "";
        String t = r2.trim();

        String[] toks = t.split("\\s+");
        for (String tok : toks) {
            if (tok.endsWith("구") || tok.endsWith("군")) return tok;
        }
        if (t.endsWith("구") || t.endsWith("군")) return t;
        return "";
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String firstNonBlank(String... arr) {
        if (arr == null) return "";
        for (String s : arr) {
            if (StringUtils.hasText(s)) return s.trim();
        }
        return "";
    }

    public static String getZip(KakaoDocument doc) {
        KakaoRoadAddress r = doc.getRoadAddress();
        return r != null && notEmpty(r.getZoneNo()) ? r.getZoneNo() : "";
    }

    public static String getRoadFull(KakaoDocument doc) {
        KakaoRoadAddress r = doc.getRoadAddress();
        if (r != null && notEmpty(r.getAddressName())) return r.getAddressName();
        return "";
    }

    public static String getJibunFull(KakaoDocument doc) {
        KakaoJibunAddress a = doc.getAddress();
        if (a != null && notEmpty(a.getAddressName())) return a.getAddressName();
        if (notEmpty(doc.getAddressName())) return doc.getAddressName();
        return "";
    }

    public static String mergeDetails(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (notEmpty(p)) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(p.trim());
            }
        }
        return sb.toString();
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isBlank();
    }

    public static KakaoDocument pickBest(KakaoResponse resp, String query) {
        if (resp == null || resp.getDocuments() == null || resp.getDocuments().isEmpty()) return null;
        KakaoDocument best = null;
        int bestScore = -1;

        for (KakaoDocument d : resp.getDocuments()) {
            int s = 0;
            if (d.getRoadAddress() != null) s += 3;
            if (d.getRoadAddress() != null && notEmpty(d.getRoadAddress().getZoneNo())) s += 2;

            if (notEmpty(d.getAddressName()) && notEmpty(query)) {
                String a = d.getAddressName().replaceAll("[\\s-]", "");
                String q = query.replaceAll("[\\s-]", "");
                if (a.contains(q) || q.contains(a)) s += 1;
            }

            if (s > bestScore) {
                bestScore = s;
                best = d;
            }
        }
        return best;
    }

    public static NormalizedAddress fromKeyword(KakaoKeywordDoc kd) {
        if (kd == null) return null;

        String road  = kd.getRoadAddressName();
        String jibun = kd.getAddressName();
        String addr  = notEmpty(road) ? road : jibun;
        if (!notEmpty(addr)) return null;

        NormalizedAddress na = new NormalizedAddress();
        na.setRoadAddress(safe(road));
        na.setJibunAddress(safe(jibun));

        String[] toks = addr.split("\\s+");
        if (toks.length > 0) na.setDoName(normalizeDo(toks[0]));
        if (toks.length > 1) na.setSiName(toks[1]);
        if (toks.length > 2) {
            String g = toks[2];
            na.setGuName(g.endsWith("구") || g.endsWith("군") || g.endsWith("시") ? g : "");
        }
        return na;
    }

    public static class NormalizedAddress {
        private String doName, siName, guName, roadAddress, jibunAddress, zipCode;

        public String getDoName() { return doName; }
        public void setDoName(String v) { doName = v; }

        public String getSiName() { return siName; }
        public void setSiName(String v) { siName = v; }

        public String getGuName() { return guName; }
        public void setGuName(String v) { guName = v; }

        public String getRoadAddress() { return roadAddress; }
        public void setRoadAddress(String v) { roadAddress = v; }

        public String getJibunAddress() { return jibunAddress; }
        public void setJibunAddress(String v) { jibunAddress = v; }

        public String getZipCode() { return zipCode; }
        public void setZipCode(String v) { zipCode = v; }
    }

    private static class SplitSiGu {
        final String si;
        final String gu;
        private SplitSiGu(String si, String gu) {
            this.si = si;
            this.gu = gu;
        }
    }
    
    public static AdminParts splitAdminRobust(KakaoDocument doc) {
        if (doc == null) return AdminParts.builder().doName("").siName("").guName("").build();

        // 1) 기존 로직(road/jibun region 기반)
        AdminParts p = splitAdmin(doc);
        boolean ok = StringUtils.hasText(p.getDoName()) || StringUtils.hasText(p.getGuName());
        if (ok) return p;

        // 2) region이 비면, address_name에서 토큰 파싱
        String full = firstNonBlank(
                doc.getAddressName(),
                doc.getRoadAddress() != null ? doc.getRoadAddress().getAddressName() : null,
                doc.getAddress() != null ? doc.getAddress().getAddressName() : null
        );

        if (!StringUtils.hasText(full)) {
            return AdminParts.builder().doName("").siName("").guName("").build();
        }

        String[] toks = full.trim().replaceAll("\\s+", " ").split("\\s+");
        String r1 = toks.length > 0 ? toks[0] : "";
        String r2 = toks.length > 1 ? toks[1] : "";
        String r3 = toks.length > 2 ? toks[2] : "";

        String doNameNorm = normalizeDo(r1);
        boolean isMetro = isMetroByDo(doNameNorm);

        String siName = "";
        String guName = "";

        if (isMetro) {
            siName = "";
            // 서울특별시 중구 ... => r2가 gu
            guName = pickGuLikeFromR3(r2);
        } else {
            // 경기도 구리시 ... => r2가 시, r3가 구/군일 수도
            siName = safe(r2);
            String g = pickGuLikeFromR3(r3);
            guName = safe(g);
        }

        return AdminParts.builder()
                .doName(safe(doNameNorm))
                .siName(safe(siName))
                .guName(safe(guName))
                .build();
    }
}