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
    private static final String VISIT_KEYWORD = "방문";
    private static final String PARCEL_KEYWORD = "택배";
    private static final String UNDELIVERED_KEYWORD = "미배송";

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

        /* 화물 그룹은 과거 99,999 배송순번 데이터/화면 분류 호환을 위해 별도로 유지합니다. */
        if (normalized.contains(FREIGHT_KEYWORD)) {
            return MethodGroup.FREIGHT;
        }

        if (normalized.contains(DIRECT_KEYWORD) || normalized.contains(SITE_KEYWORD)) {
            return MethodGroup.DIRECT_OR_SITE;
        }

        return MethodGroup.NO_HANDLER;
    }

    /**
     * 배송팀 담당자를 반드시 지정해야 하는 배송수단입니다.
     *
     * <p>화물은 방문/택배와 동일하게 배송팀 담당자를 지정하지 않습니다.
     * 따라서 직배송/현장배송만 담당자 배정 대상입니다.</p>
     */
    public static boolean requiresHandler(DeliveryMethod deliveryMethod) {
        return classify(deliveryMethod) == MethodGroup.DIRECT_OR_SITE;
    }

    /**
     * 배송팀 담당자를 보유할 수 있는 배송수단입니다.
     *
     * <p>화물/방문/택배/미배송은 담당자를 보유하지 않습니다. 그 밖의 사용자 정의
     * 배송수단은 기존 관리자 수정 정책과의 호환을 위해 선택적 담당자 배정을 허용합니다.</p>
     */
    public static boolean allowsHandler(DeliveryMethod deliveryMethod) {
        String normalized = normalize(deliveryMethod != null ? deliveryMethod.getMethodName() : null);

        if (normalized.isBlank()) {
            return false;
        }

        return !normalized.contains(FREIGHT_KEYWORD)
                && !normalized.contains(VISIT_KEYWORD)
                && !normalized.contains(PARCEL_KEYWORD)
                && !normalized.contains(UNDELIVERED_KEYWORD);
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
