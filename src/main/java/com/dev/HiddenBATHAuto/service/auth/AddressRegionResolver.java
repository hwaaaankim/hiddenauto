package com.dev.HiddenBATHAuto.service.auth;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Province;
import com.dev.HiddenBATHAuto.repository.auth.CityRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;
import com.dev.HiddenBATHAuto.utils.AdministrativeRegionStructureNormalizer;
import com.dev.HiddenBATHAuto.utils.AdministrativeRegionStructureNormalizer.NormalizedRegion;
import com.dev.HiddenBATHAuto.utils.KoreanAdministrativeRegionNormalizer;

/**
 * 주소 API에서 전달된 시/도, 시/군/구, 법정동 값을
 * tb_province / tb_city / tb_district 기준으로 정규화합니다.
 *
 * <p>저장 원칙:</p>
 * <ul>
 *     <li>서울특별시 관악구 -> 도=서울특별시, 시=빈값, 구=관악구</li>
 *     <li>경기도 용인시 수지구 -> 도=경기도, 시=용인시, 구=수지구</li>
 *     <li>경기도 이천시 백사면 -> 도=경기도, 시=이천시, 구=빈값</li>
 * </ul>
 *
 * <p>도 지역의 시/군 이름을 District로 선택하지 않습니다. 과거 행정구역 데이터에
 * 이천시 같은 시/군이 Province 직속 District로 들어 있어도 저장 결과에서는
 * City 단계로 승격하여 기존 데이터 구조 차이 때문에 주소 칸이 뒤바뀌지 않게 합니다.</p>
 */
@Service
public class AddressRegionResolver {

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
     * 전달받은 주소를 구조 기준으로 먼저 분류한 뒤 행정구역 DB와 대조합니다.
     * Province가 DB에 없거나 도 지역의 시/군을 어느 단계에서도 확인할 수 없으면
     * 잘못된 값을 저장하지 않고 가입/수정을 중단합니다.
     */
    @Transactional(readOnly = true)
    public ResolvedRegion resolve(
            String doName,
            String siName,
            String guName,
            String roadAddress
    ) {
        NormalizedRegion structured = AdministrativeRegionStructureNormalizer.normalize(
                doName,
                siName,
                guName,
                roadAddress
        );

        List<Province> provinces = provinceRepository.findAll();
        Province province = findProvince(provinces, structured.doName(), roadAddress);

        if (province == null) {
            throw new IllegalArgumentException(
                    "주소의 시·도 정보를 행정구역 DB에서 찾을 수 없습니다. 주소검색을 다시 진행해 주세요."
            );
        }

        boolean metropolitan = KoreanAdministrativeRegionNormalizer.isMetropolitanProvince(province.getName());

        List<City> provinceCities = cityRepository.findAll().stream()
                .filter(city -> belongsToProvince(city, province))
                .toList();

        List<District> provinceDistricts = districtRepository.findAll().stream()
                .filter(district -> belongsToProvince(district, province))
                .toList();

        String requestedCityName = metropolitan ? "" : clean(structured.siName());
        String requestedDistrictName = clean(structured.guName());

        City city = requestedCityName.isBlank()
                ? null
                : findCityByName(provinceCities, requestedCityName);

        District district = findDistrict(
                provinceDistricts,
                city,
                requestedCityName,
                requestedDistrictName,
                metropolitan
        );

        if (!metropolitan && city == null && district != null && district.getCity() != null) {
            if (requestedCityName.isBlank() || sameName(district.getCity().getName(), requestedCityName)) {
                city = district.getCity();
            } else {
                district = null;
            }
        }

        String resolvedCityName = "";
        if (!metropolitan) {
            if (city != null) {
                resolvedCityName = clean(city.getName());
            } else if (!requestedCityName.isBlank()) {
                // 과거 데이터에서 이천시/양평군 등이 Province 직속 District로 저장된 경우에도
                // 주소 문자열은 요구되는 Province-City-District 구조로 저장합니다.
                District legacyCityLevelDistrict = findDistrictByName(
                        provinceDistricts,
                        requestedCityName
                );

                if (legacyCityLevelDistrict != null) {
                    resolvedCityName = clean(legacyCityLevelDistrict.getName());
                } else {
                    throw new IllegalArgumentException(
                            "주소의 시·군 정보(" + requestedCityName
                                    + ")를 행정구역 DB에서 찾을 수 없습니다. 주소검색을 다시 진행해 주세요."
                    );
                }
            }
        }

        String resolvedDistrictName = district == null
                ? ""
                : clean(district.getName());

        // 도 지역의 시/군은 어떤 DB 배치에서도 guName으로 내려가지 않게 마지막으로 방어합니다.
        if (!metropolitan && isCityLevelName(resolvedDistrictName)) {
            if (resolvedCityName.isBlank()) {
                resolvedCityName = resolvedDistrictName;
            }
            resolvedDistrictName = "";
        }

        return new ResolvedRegion(
                provinceCanonical(province.getName()),
                resolvedCityName,
                resolvedDistrictName
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
        int limit = Math.min(roadTokens.size(), 2);
        for (int i = 0; i < limit; i++) {
            String roadToken = roadTokens.get(i);
            String tokenCanonical = provinceCanonical(roadToken);
            if (tokenCanonical.isBlank()) {
                continue;
            }

            Province matched = provinces.stream()
                    .filter(item -> provinceCanonical(item.getName()).equals(tokenCanonical))
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                return matched;
            }
        }

        return null;
    }

    private District findDistrict(
            List<District> provinceDistricts,
            City city,
            String requestedCityName,
            String requestedDistrictName,
            boolean metropolitan
    ) {
        if (requestedDistrictName.isBlank()) {
            return null;
        }

        if (metropolitan) {
            District directDistrict = findDistrictByName(
                    provinceDistricts.stream()
                            .filter(item -> item.getCity() == null)
                            .toList(),
                    requestedDistrictName
            );

            return directDistrict != null
                    ? directDistrict
                    : findDistrictByName(provinceDistricts, requestedDistrictName);
        }

        if (city != null) {
            Long selectedCityId = city.getId();
            return findDistrictByName(
                    provinceDistricts.stream()
                            .filter(item -> item.getCity() != null)
                            .filter(item -> Objects.equals(item.getCity().getId(), selectedCityId))
                            .toList(),
                    requestedDistrictName
            );
        }

        District matched = findDistrictByName(provinceDistricts, requestedDistrictName);
        if (matched == null || requestedCityName.isBlank() || matched.getCity() == null) {
            return matched;
        }

        return sameName(matched.getCity().getName(), requestedCityName) ? matched : null;
    }

    private City findCityByName(List<City> items, String candidate) {
        if (candidate.isBlank()) {
            return null;
        }

        return items.stream()
                .filter(item -> sameName(item.getName(), candidate))
                .findFirst()
                .orElse(null);
    }

    private District findDistrictByName(List<District> items, String candidate) {
        if (candidate.isBlank()) {
            return null;
        }

        return items.stream()
                .filter(item -> sameName(item.getName(), candidate))
                .findFirst()
                .orElse(null);
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

    private boolean isCityLevelName(String value) {
        String cleaned = clean(value);
        return cleaned.endsWith("시") || cleaned.endsWith("군");
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
        return KoreanAdministrativeRegionNormalizer.canonicalProvinceName(value);
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
