package com.dev.HiddenBATHAuto.service.production;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.service.production.MaterialCuttingSeriesProfile.FrontBandType;

@Component
public class MaterialCuttingSeriesProfileRegistry {

    private final List<MaterialCuttingSeriesProfile> profiles;
    private final Map<String, MaterialCuttingSeriesProfile> profileMap;

    public MaterialCuttingSeriesProfileRegistry() {
        List<MaterialCuttingSeriesProfile> list = new ArrayList<>();

        list.add(new MaterialCuttingSeriesProfile(
                "CLEAN",
                "클린시리즈",
                List.of("클린", "클린장", "clean"),
                MaterialCuttingFormulaCodes.HINGED_INDOOR_600_BASE,
                "6xx 인도어 여닫이 기본 공식",
                FrontBandType.SINGLE_L,
                20,
                35,
                0,
                2
        ));

        list.add(new MaterialCuttingSeriesProfile(
                "SIMPLE",
                "심플시리즈",
                List.of("심플", "심플장", "simple"),
                MaterialCuttingFormulaCodes.HINGED_INDOOR_600_BASE,
                "6xx 인도어 여닫이 기본 공식",
                FrontBandType.SINGLE_L_WITH_EXTRA_70,
                30,
                100,
                0,
                2
        ));

        list.add(new MaterialCuttingSeriesProfile(
                "SOFT",
                "소프트시리즈",
                List.of("소프트", "소프트장", "soft"),
                MaterialCuttingFormulaCodes.HINGED_INDOOR_600_BASE,
                "6xx 인도어 여닫이 기본 공식",
                FrontBandType.SOFT_SPLIT_25_95,
                65,
                80,
                0,
                2
        ));

        list.add(new MaterialCuttingSeriesProfile(
                "COZY",
                "코지시리즈",
                List.of("코지", "코지장", "cozy"),
                MaterialCuttingFormulaCodes.HINGED_INDOOR_600_BASE,
                "6xx 인도어 여닫이 기본 공식",
                FrontBandType.SINGLE_L,
                20,
                35,
                0,
                2
        ));

        list.add(new MaterialCuttingSeriesProfile(
                "ROUND",
                "라운드시리즈",
                List.of("라운드", "라운드장", "round"),
                MaterialCuttingFormulaCodes.HINGED_INDOOR_600_BASE,
                "6xx 인도어 여닫이 기본 공식",
                FrontBandType.SINGLE_L,
                20,
                35,
                50,
                2
        ));

        this.profiles = Collections.unmodifiableList(list);

        Map<String, MaterialCuttingSeriesProfile> map = new LinkedHashMap<>();
        for (MaterialCuttingSeriesProfile profile : profiles) {
            map.put(profile.seriesCode(), profile);
        }
        this.profileMap = Collections.unmodifiableMap(map);
    }

    public List<MaterialCuttingSeriesProfile> profiles() {
        return profiles;
    }

    public Optional<MaterialCuttingSeriesProfile> findByCode(String seriesCode) {
        if (seriesCode == null || seriesCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profileMap.get(seriesCode.trim().toUpperCase(Locale.ROOT)));
    }

    public Optional<MaterialCuttingSeriesProfile> resolveByText(String text) {
        String normalized = normalize(text);

        if (normalized.isBlank()) {
            return Optional.empty();
        }

        return profiles.stream()
                .filter(profile -> profile.aliases().stream()
                        .map(this::normalize)
                        .anyMatch(normalized::contains))
                .findFirst();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll("\\s{2,}", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
