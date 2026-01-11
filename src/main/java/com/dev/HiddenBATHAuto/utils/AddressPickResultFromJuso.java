package com.dev.HiddenBATHAuto.utils;


import java.util.List;

import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.address.AddressPickResult;
import com.dev.HiddenBATHAuto.dto.address.JusoAddrLinkResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class AddressPickResultFromJuso {

    private AddressPickResultFromJuso() {}

    public static AddressPickResult convert(JusoAddrLinkResponse jr, String detail) {
        if (jr == null || jr.getResults() == null) return null;

        List<JusoAddrLinkResponse.JusoItem> list = jr.getResults().getJuso();
        if (list == null || list.isEmpty()) return null;

        // 1순위만 사용(필요하면 여기서 scoring 가능)
        JusoAddrLinkResponse.JusoItem it = list.get(0);
        if (it == null) return null;

        String zip = safe(it.getZipNo());

        // 도/시/구 매핑:
        // - siNm: 경기도/서울특별시
        // - sggNm: 남양주시/강남구 등
        // - emdNm: 진접읍/역삼동 등
        // 엑셀 컬럼이 do/si/gu로만 되어 있으니:
        // do=siNm, si=sggNm, gu=emdNm(읍면동까지 내려오는 경우를 gu 칼럼에 넣음)
        String doName = safe(it.getSiNm());
        String siName = safe(it.getSggNm());
        String guName = safe(it.getEmdNm());

        // 도로명/지번
        String road = buildRoad(it);
        String jibun = safe(it.getJibunAddr());

        // 최소 하나라도 있으면 success
        boolean ok = StringUtils.hasText(road) || StringUtils.hasText(jibun) || StringUtils.hasText(doName);
        log.info("[JUSO-CONVERT] zipNo='{}' roadPart1='{}' jibun='{}'",
                it.getZipNo(), it.getRoadAddrPart1(), it.getJibunAddr());

        AddressPickResult r = AddressPickResult.empty(safe(detail));
        r.setZip(zip);
        r.setDoName(doName);
        r.setSiName(siName);
        r.setGuName(guName);
        r.setRoadAddress(road);
        r.setJibunAddress(jibun);
        r.setDetailAddress(safe(detail));
        r.setSuccess(ok);

        return r;
    }

    private static String buildRoad(JusoAddrLinkResponse.JusoItem it) {
        String p1 = safe(it.getRoadAddrPart1());
        String p2 = safe(it.getRoadAddrPart2());
        if (!p1.isBlank() && !p2.isBlank()) return (p1 + " " + p2).trim();
        if (!p1.isBlank()) return p1.trim();

        // fallback
        String roadAddr = safe(it.getRoadAddr());
        return roadAddr;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
