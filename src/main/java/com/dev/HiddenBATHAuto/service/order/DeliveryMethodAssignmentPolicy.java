package com.dev.HiddenBATHAuto.service.order;

import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;

/**
 * 배송수단명에 포함된 핵심 단어를 기준으로 배송담당자/배송순번 정책을 판정합니다.
 *
 * <p>배송수단명에는 "(금액: ...)" 같은 부가 문구나 공백이 포함될 수 있으므로
 * 완전 일치가 아니라 정규화 후 핵심 단어 포함 여부로 판정합니다.</p>
 *
 * <p>중요: {@link MethodGroup}은 기존 출고팀 호출부 호환을 위한 분류값입니다.
 * 실제로 담당자를 보유할 수 있는지는 {@link #allowsHandler(DeliveryMethod)},
 * 반드시 지정해야 하는지는 {@link #requiresHandler(DeliveryMethod)}를 사용합니다.</p>
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

    /**
     * 기존 출고팀 로직에서 사용하는 호환 분류입니다.
     *
     * <ul>
     *     <li>DIRECT_OR_SITE: 직배송/현장배송</li>
     *     <li>FREIGHT: 화물</li>
     *     <li>NO_HANDLER: 그 외 기존 분류</li>
     * </ul>
     *
     * <p>NO_HANDLER라는 이름만으로 담당자 허용 여부를 판단하지 마십시오.
     * 기타 배송수단은 관리자 수정 정책상 선택적 담당자 배정이 가능할 수 있으므로
     * 실제 허용 여부는 {@link #allowsHandler(DeliveryMethod)}를 사용해야 합니다.</p>
     */
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

        // 과거 99,999 화물 배송순번 데이터/화면 분류 호환을 위해 별도 그룹을 유지합니다.
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
     * 직배송/현장배송만 필수입니다.
     */
    public static boolean requiresHandler(DeliveryMethod deliveryMethod) {
        return classify(deliveryMethod) == MethodGroup.DIRECT_OR_SITE;
    }

    /**
     * 배송팀 담당자를 가질 수 없는 배송수단인지 판정합니다.
     *
     * <p>미지정, 화물, 방문, 택배, 미배송은 담당자 배정 금지입니다.</p>
     */
    public static boolean prohibitsHandler(DeliveryMethod deliveryMethod) {
        return prohibitsHandler(deliveryMethod != null ? deliveryMethod.getMethodName() : null);
    }

    public static boolean prohibitsHandler(String methodName) {
        String normalized = normalize(methodName);

        if (normalized.isBlank()) {
            return true;
        }

        return normalized.contains(FREIGHT_KEYWORD)
                || normalized.contains(VISIT_KEYWORD)
                || normalized.contains(PARCEL_KEYWORD)
                || normalized.contains(UNDELIVERED_KEYWORD);
    }

    /**
     * 배송팀 담당자를 보유할 수 있는 배송수단입니다.
     *
     * <p>직배송/현장배송은 필수, 그 밖의 허용 배송수단은 선택사항입니다.
     * 화물/방문/택배/미배송/미지정은 false입니다.</p>
     */
    public static boolean allowsHandler(DeliveryMethod deliveryMethod) {
        return !prohibitsHandler(deliveryMethod);
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

    /**
     * 배송수단 비교용 정규화입니다.
     * "(금액: ...)" 표시 문구와 모든 공백을 제거합니다.
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replaceAll("\\(\\s*금액\\s*:.*?\\)", "")
                .replaceAll("\\s+", "")
                .trim();
    }
}
