package com.dev.HiddenBATHAuto.service.production;

import java.util.List;

/**
 * 시리즈별 재단 정의입니다.
 * 같은 계산식을 쓰는 신규 시리즈는 이 profile만 추가하면 됩니다.
 * 계산식 자체가 다른 신규 구조는 formulaCode를 새로 만들고 MaterialCuttingRule 구현체를 추가합니다.
 */
public record MaterialCuttingSeriesProfile(
        String seriesCode,
        String seriesLabel,
        List<String> aliases,
        String formulaCode,
        String formulaLabel,
        FrontBandType frontBandType,
        int ceramicDoorHandleGapMm,
        int marbleDoorHandleGapMm,
        int doorHeightAddMm,
        int defaultDoorCountFor600
) {

    public enum FrontBandType {
        SINGLE_L,
        SINGLE_L_WITH_EXTRA_70,
        SOFT_SPLIT_25_95
    }
}
