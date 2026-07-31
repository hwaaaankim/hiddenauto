package com.dev.HiddenBATHAuto.service.auth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Province;
import com.dev.HiddenBATHAuto.repository.auth.CityRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;


/**
 * 주소 API에서 전달된 시/도, 시/군/구, 법정동 값을
 * tb_province / tb_city / tb_district 기준으로 정규화합니다.
 *
 * <p>저장 원칙:</p>
 * <ul>
 *     <li>서울특별시 관악구 → 도=서울특별시, 시=빈값, 구=관악구</li>
 *     <li>경기도 용인시 수지구 → 도=경기도, 시=용인시, 구=수지구</li>
 *     <li>경기도 이천시 백사면 → 도=경기도, 시=이천시, 구=DB에 등록된 경우에만 저장</li>
 * </ul>
 *
 * <p>data.bname(동/읍/면)을 무조건 guName으로 저장하지 않고,
 * 실제 District 테이블에 존재하는 이름만 guName으로 확정합니다.</p>
 */
@Service
public class AddressRegionResolver {

    private static final Map<String, String> PROVINCE_ALIASES = Map.ofEntries(
            Map.entry("서울", "서울특별시"),
            Map.entry("서울시", "서울특별시"),
            Map.entry("서울특별시", "서울특별시"),
            Map.entry("부산", "부산광역시"),
            Map.entry("부산시", "부산광역시"),
            Map.entry("부산광역시", "부산광역시"),
            Map.entry("대구", "대구광역시"),
            Map.entry("대구시", "대구광역시"),
            Map.entry("대구광역시", "대구광역시"),
            Map.entry("인천", "인천광역시"),
            Map.entry("인천시", "인천광역시"),
            Map.entry("인천광역시", "인천광역시"),
            Map.entry("광주", "광주광역시"),
            Map.entry("광주시", "광주광역시"),
            Map.entry("광주광역시", "광주광역시"),
            Map.entry("대전", "대전광역시"),
            Map.entry("대전시", "대전광역시"),
            Map.entry("대전광역시", "대전광역시"),
            Map.entry("울산", "울산광역시"),
            Map.entry("울산시", "울산광역시"),
            Map.entry("울산광역시", "울산광역시"),
            Map.entry("세종", "세종특별자치시"),
            Map.entry("세종시", "세종특별자치시"),
            Map.entry("세종특별자치시", "세종특별자치시"),
            Map.entry("경기", "경기도"),
            Map.entry("경기도", "경기도"),
            Map.entry("강원", "강원특별자치도"),
            Map.entry("강원도", "강원특별자치도"),
            Map.entry("강원특별자치도", "강원특별자치도"),
            Map.entry("충북", "충청북도"),
            Map.entry("충청북도", "충청북도"),
            Map.entry("충남", "충청남도"),
            Map.entry("충청남도", "충청남도"),
            Map.entry("전북", "전북특별자치도"),
            Map.entry("전라북도", "전북특별자치도"),
            Map.entry("전북특별자치도", "전북특별자치도"),
            Map.entry("전남", "전라남도"),
            Map.entry("전라남도", "전라남도"),
            Map.entry("경북", "경상북도"),
            Map.entry("경상북도", "경상북도"),
            Map.entry("경남", "경상남도"),
            Map.entry("경상남도", "경상남도"),
            Map.entry("제주", "제주특별자치도"),
            Map.entry("제주도", "제주특별자치도"),
            Map.entry("제주특별자치도", "제주특별자치도")
    );

    private final ProvinceRepository provinceRepository;
    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;

    public AddressRegionResolver(
            ProvinceRepository provinceRepository,
            CityRepository cityRepository,
            DistrictRepository districtRepository
    ) {
        this.provinceRepository = provinceRepository;
        this.cityRepository = cityRepository;
        this.districtRepository = districtRepository;
    }

    /**
     * 전달받은 주소 행정구역을 DB 기준으로 정규화합니다.
     * Province가 DB에 없으면 잘못된 값을 저장하지 않도록 가입/수정을 중단합니다.
     */
    @Transactional(readOnly = true)
    public ResolvedRegion resolve(
            String doName,
            String siName,
            String guName,
            String roadAddress
    ) {
        String cleanedDoName = clean(doName);
        String cleanedSiName = clean(siName);
        String cleanedGuName = clean(guName);
        String cleanedRoadAddress = clean(roadAddress);

        List<Province> provinces = provinceRepository.findAll();
        Province province = findProvince(provinces, cleanedDoName, cleanedRoadAddress);

        if (province == null) {
            throw new IllegalArgumentException(
                    "주소의 시·도 정보를 행정구역 DB에서 찾을 수 없습니다. 주소검색을 다시 진행해 주세요."
            );
        }

        List<City> provinceCities = cityRepository.findAll().stream()
                .filter(city -> belongsToProvince(city, province))
                .toList();

        List<District> provinceDistricts = districtRepository.findAll().stream()
                .filter(district -> belongsToProvince(district, province))
                .toList();

        List<String> candidates = buildCandidates(
                cleanedSiName,
                cleanedGuName,
                cleanedRoadAddress,
                province.getName()
        );

        City city = findCityByName(provinceCities, candidates);
        District district;

        if (city != null) {
            Long selectedCityId = city.getId();
            district = findDistrictByName(
                    provinceDistricts.stream()
                            .filter(item -> item.getCity() != null)
                            .filter(item -> Objects.equals(item.getCity().getId(), selectedCityId))
                            .toList(),
                    candidates
            );
        } else {
            // 서울/광역시처럼 City 없이 Province 바로 아래 District가 연결된 구조를 우선합니다.
            district = findDistrictByName(
                    provinceDistricts.stream()
                            .filter(item -> item.getCity() == null)
                            .toList(),
                    candidates
            );

            // 데이터가 City 하위로 구성되어 있는데 프런트 값이 일부 누락된 경우,
            // District를 먼저 찾고 그 District의 City를 역으로 복구합니다.
            if (district == null) {
                district = findDistrictByName(provinceDistricts, candidates);
                if (district != null && district.getCity() != null) {
                    city = district.getCity();
                }
            }
        }

        return new ResolvedRegion(
                clean(province.getName()),
                city == null ? "" : clean(city.getName()),
                district == null ? "" : clean(district.getName())
        );
    }

    private Province findProvince(List<Province> provinces, String doName, String roadAddress) {
        String requestedCanonical = provinceCanonical(doName);

        if (!requestedCanonical.isBlank()) {
            Province exact = provinces.stream()
                    .filter(item -> provinceCanonical(item.getName()).equals(requestedCanonical))
                    .findFirst()
                    .orElse(null);
            if (exact != null) {
                return exact;
            }
        }

        List<String> roadTokens = splitTokens(roadAddress);
        if (!roadTokens.isEmpty()) {
            String tokenCanonical = provinceCanonical(roadTokens.get(0));
            if (!tokenCanonical.isBlank()) {
                return provinces.stream()
                        .filter(item -> provinceCanonical(item.getName()).equals(tokenCanonical))
                        .findFirst()
                        .orElse(null);
            }
        }

        return null;
    }

    private List<String> buildCandidates(
            String siName,
            String guName,
            String roadAddress,
            String provinceName
    ) {
        Set<String> ordered = new LinkedHashSet<>();

        addCandidate(ordered, siName, provinceName);
        splitTokens(siName).forEach(value -> addCandidate(ordered, value, provinceName));

        addCandidate(ordered, guName, provinceName);
        splitTokens(guName).forEach(value -> addCandidate(ordered, value, provinceName));

        splitTokens(roadAddress).forEach(value -> addCandidate(ordered, value, provinceName));

        return new ArrayList<>(ordered);
    }

    private void addCandidate(Set<String> target, String value, String provinceName) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            return;
        }

        if (provinceCanonical(cleaned).equals(provinceCanonical(provinceName))) {
            return;
        }

        target.add(cleaned);
    }

    private City findCityByName(List<City> items, List<String> candidates) {
        for (String candidate : candidates) {
            for (City item : items) {
                if (sameName(item.getName(), candidate)) {
                    return item;
                }
            }
        }
        return null;
    }

    private District findDistrictByName(List<District> items, List<String> candidates) {
        for (String candidate : candidates) {
            for (District item : items) {
                if (sameName(item.getName(), candidate)) {
                    return item;
                }
            }
        }
        return null;
    }

    private boolean belongsToProvince(City city, Province province) {
        return city != null
                && city.getProvince() != null
                && Objects.equals(city.getProvince().getId(), province.getId());
    }

    private boolean belongsToProvince(District district, Province province) {
        if (district == null || province == null) {
            return false;
        }

        if (district.getProvince() != null
                && Objects.equals(district.getProvince().getId(), province.getId())) {
            return true;
        }

        return district.getCity() != null
                && district.getCity().getProvince() != null
                && Objects.equals(district.getCity().getProvince().getId(), province.getId());
    }

    private boolean sameName(String left, String right) {
        return clean(left).equals(clean(right));
    }

    private List<String> splitTokens(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            return List.of();
        }
        return List.of(cleaned.split("\\s+"));
    }

    private String provinceCanonical(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            return "";
        }
        return PROVINCE_ALIASES.getOrDefault(cleaned, cleaned);
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }

    public record ResolvedRegion(
            String doName,
            String siName,
            String guName
    ) {
    }
}
