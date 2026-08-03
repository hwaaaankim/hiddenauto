package com.dev.HiddenBATHAuto.dto.production;

import java.util.Locale;

/**
 * 생산팀 목록의 다중 정렬 조건 한 건입니다.
 *
 * key는 화면과 서버가 합의한 허용 키만 사용하며,
 * ascending=true는 오름차순, false는 내림차순입니다.
 */
public record ProductionSortOrder(String key, boolean ascending) {

    public ProductionSortOrder {
        key = key == null ? "" : key.trim();
    }

    public String directionName() {
        return ascending ? "ASC" : "DESC";
    }

    public static ProductionSortOrder of(String key, String direction) {
        String normalizedDirection = direction == null
                ? "ASC"
                : direction.trim().toUpperCase(Locale.ROOT);

        return new ProductionSortOrder(key, !"DESC".equals(normalizedDirection));
    }
}
