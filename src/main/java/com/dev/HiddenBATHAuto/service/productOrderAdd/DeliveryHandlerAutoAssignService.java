package com.dev.HiddenBATHAuto.service.productOrderAdd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
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

    /**
     * 도/시/구 기준으로 배송 담당자를 찾습니다.
     *
     * 매칭 우선순위:
     * 1. 도 + 시 + 구/군 담당자
     * 2. 도 + 시 전체 담당자(district=null)
     * 3. 도 전체 담당자(city=null, district=null)
     *
     * 서울/광역시처럼 city가 비어 있는 주소는 도 + 구/군 담당자를 1순위로 봅니다.
     */
    public Optional<Member> findRandomDeliveryHandler(String doName, String siName, String guName) {
        if (isBlank(doName)) {
            return Optional.empty();
        }

        RegionKey key = resolveRegionKey(doName, siName, guName);
        if (key.provinceId() == null) {
            return Optional.empty();
        }

        List<MemberRegion> matches = findCandidateRegions(key);
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        Map<Member, Integer> bestScopePerMember = new HashMap<>();

        for (MemberRegion memberRegion : matches) {
            if (memberRegion == null || memberRegion.getMember() == null) {
                continue;
            }

            Member member = memberRegion.getMember();
            if (!member.isEnabled()) {
                continue;
            }

            int score = scopeScore(memberRegion, key);
            if (score <= 0) {
                continue;
            }

            bestScopePerMember.merge(member, score, Math::max);
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

    private List<MemberRegion> findCandidateRegions(RegionKey key) {
        Map<Long, MemberRegion> unique = new LinkedHashMap<>();

        addCandidateRegions(unique, memberRegionRepository.findDeliveryRegionMatches(
                DELIVERY_TEAM_NAME,
                key.provinceId(),
                key.cityId(),
                key.districtId()
        ));

        // Repository 쿼리가 exact-match 형태로 남아 있어도 시 전체 담당자를 찾을 수 있도록 보정합니다.
        if (key.districtId() != null) {
            addCandidateRegions(unique, memberRegionRepository.findDeliveryRegionMatches(
                    DELIVERY_TEAM_NAME,
                    key.provinceId(),
                    key.cityId(),
                    null
            ));
        }

        // 도 전체 담당자까지 후보로 포함합니다.
        if (key.cityId() != null || key.districtId() != null) {
            addCandidateRegions(unique, memberRegionRepository.findDeliveryRegionMatches(
                    DELIVERY_TEAM_NAME,
                    key.provinceId(),
                    null,
                    null
            ));
        }

        return new ArrayList<>(unique.values());
    }

    private void addCandidateRegions(Map<Long, MemberRegion> unique, List<MemberRegion> regions) {
        if (regions == null || regions.isEmpty()) {
            return;
        }
        for (MemberRegion region : regions) {
            if (region == null || region.getId() == null) {
                continue;
            }
            unique.putIfAbsent(region.getId(), region);
        }
    }

    private int scopeScore(MemberRegion memberRegion, RegionKey key) {
        if (memberRegion == null || memberRegion.getProvince() == null || key.provinceId() == null) {
            return 0;
        }
        if (!key.provinceId().equals(memberRegion.getProvince().getId())) {
            return 0;
        }

        Long regionCityId = memberRegion.getCity() == null ? null : memberRegion.getCity().getId();
        Long regionDistrictId = memberRegion.getDistrict() == null ? null : memberRegion.getDistrict().getId();

        // 도 전체 담당자: city=null, district=null
        if (regionCityId == null && regionDistrictId == null) {
            return 1;
        }

        // 서울/광역시: city는 비어 있고 district만 비교합니다.
        if (key.cityId() == null) {
            if (key.districtId() != null && regionCityId == null && key.districtId().equals(regionDistrictId)) {
                return 3;
            }
            return 0;
        }

        if (!key.cityId().equals(regionCityId)) {
            return 0;
        }

        // 시 전체 담당자: district=null
        if (regionDistrictId == null) {
            return 2;
        }

        // 구/군까지 정확히 들어온 경우만 구/군 담당자를 선택합니다.
        if (key.districtId() != null && key.districtId().equals(regionDistrictId)) {
            return 3;
        }

        return 0;
    }

    private RegionKey resolveRegionKey(String doName, String siName, String guName) {
        String provinceBase = normalizeBase(doName);
        String cityBase = normalizeBase(siName);
        String districtBase = normalizeBase(guName);

        Province province = pickBest(provinceRepository.findAll(), Province::getName, provinceBase);
        if (province == null) {
            return new RegionKey(null, null, null);
        }

        Long provinceId = province.getId();
        Long cityId = null;
        Long districtId = null;

        if (!isBlank(cityBase)) {
            City city = pickBest(cityRepository.findByProvinceId(provinceId), City::getName, cityBase);
            cityId = city == null ? null : city.getId();
        }

        if (!isBlank(districtBase)) {
            List<District> districts = cityId == null
                    ? districtRepository.findByProvinceId(provinceId)
                    : districtRepository.findByCityId(cityId);
            District district = pickBest(districts, District::getName, districtBase);
            districtId = district == null ? null : district.getId();
        }

        return new RegionKey(provinceId, cityId, districtId);
    }

    private <T> T pickBest(List<T> list, Function<T, String> nameFn, String base) {
        if (list == null || list.isEmpty() || isBlank(base)) {
            return null;
        }

        String normalizedBase = normalizeBase(base);
        if (isBlank(normalizedBase)) {
            return null;
        }

        for (T item : list) {
            String normalizedName = normalizeBase(nameFn.apply(item));
            if (normalizedBase.equals(normalizedName)) {
                return item;
            }
        }

        for (T item : list) {
            String normalizedName = normalizeBase(nameFn.apply(item));
            if (!isBlank(normalizedName)
                    && (normalizedName.startsWith(normalizedBase) || normalizedBase.startsWith(normalizedName))) {
                return item;
            }
        }

        for (T item : list) {
            String normalizedName = normalizeBase(nameFn.apply(item));
            if (!isBlank(normalizedName)
                    && (normalizedName.contains(normalizedBase) || normalizedBase.contains(normalizedName))) {
                return item;
            }
        }

        return null;
    }

    private String normalizeBase(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.replace('\u00A0', ' ').trim().replaceAll("\\s+", "");
        if (trimmed.isBlank()) {
            return "";
        }

        trimmed = switch (trimmed) {
            case "서울", "서울시", "서울특별시" -> "서울";
            case "부산", "부산시", "부산광역시" -> "부산";
            case "대구", "대구시", "대구광역시" -> "대구";
            case "인천", "인천시", "인천광역시" -> "인천";
            case "광주", "광주시", "광주광역시" -> "광주";
            case "대전", "대전시", "대전광역시" -> "대전";
            case "울산", "울산시", "울산광역시" -> "울산";
            case "세종", "세종시", "세종특별자치시" -> "세종";
            case "경기", "경기도" -> "경기";
            case "강원", "강원도", "강원특별자치도" -> "강원";
            case "충북", "충청북도" -> "충북";
            case "충남", "충청남도" -> "충남";
            case "전북", "전라북도", "전북특별자치도" -> "전북";
            case "전남", "전라남도" -> "전남";
            case "경북", "경상북도" -> "경북";
            case "경남", "경상남도" -> "경남";
            case "제주", "제주도", "제주특별자치도" -> "제주";
            default -> trimmed;
        };

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

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private record RegionKey(
            Long provinceId,
            Long cityId,
            Long districtId
    ) {
    }
}
