package com.dev.HiddenBATHAuto.service.productOrderAdd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRegion;
import com.dev.HiddenBATHAuto.model.auth.Province;
import com.dev.HiddenBATHAuto.repository.auth.CityRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRegionRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryHandlerAutoAssignService {

    private static final String DELIVERY_TEAM_NAME = "배송팀";

    private final ProvinceRepository provinceRepository;
    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;
    private final MemberRegionRepository memberRegionRepository;

    public Optional<Member> findRandomDeliveryHandler(String doName, String siName, String guName) {
        if (doName == null || doName.isBlank()) {
            return Optional.empty();
        }

        RegionKey key = resolveRegionKey(doName, siName, guName);

        if (key.provinceId() == null) {
            return Optional.empty();
        }

        List<MemberRegion> matches = memberRegionRepository.findDeliveryRegionMatches(
                DELIVERY_TEAM_NAME,
                key.provinceId(),
                key.cityId(),
                key.districtId()
        );

        if (matches == null || matches.isEmpty()) {
            return Optional.empty();
        }

        Map<Member, Integer> bestScopePerMember = new HashMap<>();

        for (MemberRegion memberRegion : matches) {
            if (memberRegion == null || memberRegion.getMember() == null) {
                continue;
            }

            bestScopePerMember.merge(
                    memberRegion.getMember(),
                    scopeScore(memberRegion),
                    Math::max
            );
        }

        if (bestScopePerMember.isEmpty()) {
            return Optional.empty();
        }

        int topScope = bestScopePerMember.values()
                .stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(1);

        List<Member> topCandidates = bestScopePerMember.entrySet()
                .stream()
                .filter(entry -> entry.getValue() == topScope)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (topCandidates.isEmpty()) {
            return Optional.empty();
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(topCandidates.size());
        return Optional.of(topCandidates.get(randomIndex));
    }

    private int scopeScore(MemberRegion memberRegion) {
        if (memberRegion.getDistrict() != null) {
            return 3;
        }

        if (memberRegion.getCity() != null) {
            return 2;
        }

        return 1;
    }

    private RegionKey resolveRegionKey(String doName, String siName, String guName) {
        String provinceBase = normalizeBase(doName);
        String cityBase = siName == null ? null : normalizeBase(siName);
        String districtBase = guName == null ? null : normalizeBase(guName);

        List<Province> provinces = provinceRepository.findAll();
        Province province = pickByBase(provinces, Province::getName, provinceBase);

        if (province == null) {
            province = pickByRelaxed(provinces, Province::getName, provinceBase);
        }

        if (province == null) {
            return new RegionKey(null, null, null);
        }

        Long provinceId = province.getId();

        City city = null;
        Long cityId = null;

        if (cityBase != null && !cityBase.isBlank()) {
            List<City> cities = cityRepository.findByProvinceId(provinceId);
            city = pickByBase(cities, City::getName, cityBase);

            if (city == null) {
                city = pickByRelaxed(cities, City::getName, cityBase);
            }

            cityId = city == null ? null : city.getId();
        }

        District district = null;
        Long districtId = null;

        if (districtBase != null && !districtBase.isBlank()) {
            List<District> districts = cityId == null
                    ? districtRepository.findByProvinceId(provinceId)
                    : districtRepository.findByCityId(cityId);

            district = pickByBase(districts, District::getName, districtBase);

            if (district == null) {
                district = pickByRelaxed(districts, District::getName, districtBase);
            }

            districtId = district == null ? null : district.getId();
        }

        return new RegionKey(provinceId, cityId, districtId);
    }

    private String normalizeBase(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.isBlank()) {
            return "";
        }

        String[] suffixes = {
                "특별자치도",
                "특별자치시",
                "광역시",
                "특별시",
                "자치시",
                "자치구",
                "자치군",
                "도",
                "시",
                "군",
                "구"
        };

        for (String suffix : suffixes) {
            if (trimmed.endsWith(suffix)) {
                return trimmed.substring(0, trimmed.length() - suffix.length());
            }
        }

        return trimmed;
    }

    private <T> T pickByBase(List<T> list, java.util.function.Function<T, String> nameFn, String base) {
        if (base == null || base.isBlank()) {
            return null;
        }

        String normalizedBase = normalizeBase(base);

        for (T item : list) {
            String name = nameFn.apply(item);
            String normalizedName = normalizeBase(name);

            if (normalizedName != null
                    && (normalizedName.contains(normalizedBase) || normalizedBase.contains(normalizedName))) {
                return item;
            }
        }

        return null;
    }

    private <T> T pickByRelaxed(List<T> list, java.util.function.Function<T, String> nameFn, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String normalizedKeyword = keyword.replaceAll("\\s+", "");

        for (T item : list) {
            String name = nameFn.apply(item);

            if (name == null) {
                continue;
            }

            String normalizedName = name.replaceAll("\\s+", "");

            if (normalizedName.contains(normalizedKeyword) || normalizedKeyword.contains(normalizedName)) {
                return item;
            }
        }

        return null;
    }

    private record RegionKey(
            Long provinceId,
            Long cityId,
            Long districtId
    ) {
    }
}
