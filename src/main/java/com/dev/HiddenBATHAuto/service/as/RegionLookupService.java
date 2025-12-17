package com.dev.HiddenBATHAuto.service.as;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.repository.auth.CityRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionLookupService {

    private final ProvinceRepository provinceRepository;
    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;

    public String getProvinceName(Long provinceId) {
        if (provinceId == null) return null;
        return provinceRepository.findById(provinceId)
                .map(p -> p.getName())
                .orElse(null);
    }

    public String getCityName(Long cityId) {
        if (cityId == null) return null;
        return cityRepository.findById(cityId)
                .map(c -> c.getName())
                .orElse(null);
    }

    public String getDistrictName(Long districtId) {
        if (districtId == null) return null;
        return districtRepository.findById(districtId)
                .map(d -> d.getName())
                .orElse(null);
    }

    /**
     * ✅ 도/광역시 명칭 별칭 허용
     * - 경기도 <-> 경기
     * - 서울특별시 <-> 서울
     * - 부산광역시 <-> 부산
     * - ... (필요 시 확장)
     */
    public List<String> getProvinceAliases(String provinceName) {
        if (provinceName == null || provinceName.isBlank()) {
            return null; // 필터 미적용
        }

        String n = provinceName.trim();

        // 기본은 자기 자신 포함
        List<String> list = new ArrayList<>();
        list.add(n);

        // 흔한 패턴: 접미어 제거 버전도 허용
        // (경기도 -> 경기, 충청남도 -> 충청남, 서울특별시 -> 서울, 전라북도 -> 전라북 등)
        String shortened = n;
        if (shortened.endsWith("특별시")) shortened = shortened.substring(0, shortened.length() - 3);
        else if (shortened.endsWith("광역시")) shortened = shortened.substring(0, shortened.length() - 3);
        else if (shortened.endsWith("특별자치시")) shortened = shortened.substring(0, shortened.length() - 5);
        else if (shortened.endsWith("특별자치도")) shortened = shortened.substring(0, shortened.length() - 5);
        else if (shortened.endsWith("자치도")) shortened = shortened.substring(0, shortened.length() - 3);
        else if (shortened.endsWith("도")) shortened = shortened.substring(0, shortened.length() - 1);

        if (!shortened.equals(n) && !shortened.isBlank()) {
            list.add(shortened);
        }

        // 중복 제거
        return list.stream().distinct().toList();
    }
}
