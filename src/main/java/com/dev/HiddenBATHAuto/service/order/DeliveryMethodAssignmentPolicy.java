package com.dev.HiddenBATHAuto.service.order;

import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;

/**
 * 배송수단명에 포함된 핵심 단어를 기준으로 담당자/배송순번 관리 정책을 분류합니다.
 *
 * <p>DB의 배송수단명이 "직배송(무료)", "현장배송 - 서울", "화물(착불)"처럼
 * 부가 문구를 포함할 수 있으므로 완전 일치가 아닌 포함 여부로 판정합니다.</p>
 */
public final class DeliveryMethodAssignmentPolicy {

    private static final String DIRECT_KEYWORD = "직배송";
    private static final String SITE_KEYWORD = "현장배송";
    private static final String FREIGHT_KEYWORD = "화물";

    private DeliveryMethodAssignmentPolicy() {
    }

    public enum MethodGroup {
        DIRECT_OR_SITE,
        FREIGHT,
        NO_HANDLER
    }

    public static MethodGroup classify(DeliveryMethod deliveryMethod) {
        return classify(deliveryMethod != null ? deliveryMethod.getMethodName() : null);
    }

    public static MethodGroup classify(String methodName) {
        String normalized = normalize(methodName);

        /* 화물은 전용 99,999 구간을 사용하므로 다른 키워드보다 우선합니다. */
        if (normalized.contains(FREIGHT_KEYWORD)) {
            return MethodGroup.FREIGHT;
        }

        if (normalized.contains(DIRECT_KEYWORD) || normalized.contains(SITE_KEYWORD)) {
            return MethodGroup.DIRECT_OR_SITE;
        }

        return MethodGroup.NO_HANDLER;
    }

    public static boolean requiresHandler(DeliveryMethod deliveryMethod) {
        return classify(deliveryMethod) != MethodGroup.NO_HANDLER;
    }

    public static boolean isDirectOrSite(DeliveryMethod deliveryMethod) {
        return classify(deliveryMethod) == MethodGroup.DIRECT_OR_SITE;
    }

    public static boolean isFreight(DeliveryMethod deliveryMethod) {
        return classify(deliveryMethod) == MethodGroup.FREIGHT;
    }

    public static boolean containsKeyword(String methodName, String keyword) {
        String normalizedKeyword = normalize(keyword);
        return !normalizedKeyword.isBlank() && normalize(methodName).contains(normalizedKeyword);
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replaceAll("\\(금액:.*?\\)", "")
                .replaceAll("\\s+", "")
                .trim();
    }
}
