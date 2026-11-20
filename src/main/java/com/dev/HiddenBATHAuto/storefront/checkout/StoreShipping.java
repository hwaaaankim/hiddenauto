package com.dev.HiddenBATHAuto.storefront.checkout;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class StoreShipping {
    public record Method(long id, String name, String description) {}
    static final List<Method> METHODS = List.of(
        new Method(1, "직배송", "업체 주소로 무료 배송"),
        new Method(2, "현장배송", "같은 날짜와 같은 현장당 40,000원"),
        new Method(3, "선불택배", "대분류·가로 규격·묶음 수량으로 계산"),
        new Method(4, "착불택배", "포장비만 청구하며 운임은 수령 시 결제"),
        new Method(5, "화물", "운임과 포장비는 관리자가 추후 안내"));
    record Product(String category, BigDecimal width, int quantity) {}
    record Quote(Integer delivery, Integer packing, String reason) {}
    static Quote quote(long method, boolean jeju, List<Product> products) {
        if (method == 1) return new Quote(0, 0, "직배송 무료");
        if (method == 2) return new Quote(40000, 0, "동일 날짜·현장 1회");
        if (method == 5) return new Quote(null, null, "화물 · 관리자 견적 대기");
        if (method != 3 && method != 4) throw new IllegalArgumentException("배송수단을 확인해 주세요.");
        Set<String> kinds = products.stream().map(p -> switch (p.category()) {
            case "플랩장", "상부장" -> "상부장";
            case "슬라이드장", "슬라이드" -> "슬라이드";
            case "거울", "하부장" -> p.category();
            default -> "";
        }).collect(Collectors.toSet());
        if (kinds.contains("")) return new Quote(null, null, "운임 확인 필요 · 요금표에 없는 대분류");
        boolean wideCabinet = false, largeMirror = false;
        int count = 0;
        for (Product p : products) {
            count = Math.addExact(count, p.quantity());
            if (!Set.of("하부장", "거울").contains(p.category())) continue;
            if (p.width() == null || p.width().signum() <= 0)
                return new Quote(null, null, "운임 확인 필요 · " + p.category() + " 가로 규격 미확정");
            wideCabinet |= p.category().equals("하부장") && p.width().compareTo(BigDecimal.valueOf(600)) > 0;
            largeMirror |= p.category().equals("거울") && p.width().compareTo(BigDecimal.valueOf(1200)) >= 0;
        }
        int packing = 30000, freight = 60000;
        String rule = "혼합·초과 수량 파레트";
        if (wideCabinet) {
            freight = 80000; rule = "600mm 초과 하부장 포함 파레트";
        } else if (kinds.size() == 1) {
            String kind = kinds.iterator().next();
            if (kind.equals("거울") && count <= 4 && !largeMirror) {
                packing = 20000; freight = count <= 2 ? 6000 : 10000; rule = "거울 스티로폼";
            } else if (kind.equals("거울") && largeMirror && count <= 4) {
                freight = 30000; rule = "1200mm 이상 거울 파레트";
            } else if (kind.equals("상부장") && count <= 4) {
                packing = count == 1 ? 20000 : 30000;
                freight = count == 1 ? 10000 : count == 2 ? 30000 : 40000;
                rule = count == 1 ? "상부장 스티로폼" : "상부장 파레트";
            } else if (kind.equals("슬라이드") && count <= 2) {
                freight = count == 1 ? 40000 : 60000; rule = "슬라이드장 파레트";
            } else if (kind.equals("하부장") && count == 1) {
                freight = 40000; rule = "600mm 이하 하부장 파레트";
            }
        }
        if (method == 4) freight = 0;
        else if (jeju) freight += 30000;
        return new Quote(freight, packing, rule + " · " + count + "개" +
            (method == 4 ? " · 착불 운임 제외" : jeju ? " · 제주 운임 30,000원 추가" : ""));
    }
}
