package com.dev.HiddenBATHAuto.controller.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.as.RegionChildrenResponse;
import com.dev.HiddenBATHAuto.repository.auth.CityRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/regions")
public class RegionApiController {

    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;

    @GetMapping("/provinces/{provinceId}/children")
    public RegionChildrenResponse getProvinceChildren(@PathVariable Long provinceId) {

        var cities = cityRepository.findByProvinceIdOrderByNameAsc(provinceId);
        if (cities != null && !cities.isEmpty()) {
            List<RegionChildrenResponse.RegionOptionDto> items = cities.stream()
                    .map(c -> new RegionChildrenResponse.RegionOptionDto(c.getId(), c.getName()))
                    .toList();
            return new RegionChildrenResponse("CITY", items);
        }

        var districts = districtRepository.findByProvinceIdAndCityIsNullOrderByNameAsc(provinceId);
        List<RegionChildrenResponse.RegionOptionDto> items = districts.stream()
                .map(d -> new RegionChildrenResponse.RegionOptionDto(d.getId(), d.getName()))
                .toList();
        return new RegionChildrenResponse("DISTRICT", items);
    }

    @GetMapping("/cities/{cityId}/districts")
    public List<RegionChildrenResponse.RegionOptionDto> getDistrictsByCity(@PathVariable Long cityId) {
        return districtRepository.findByCityIdOrderByNameAsc(cityId).stream()
                .map(d -> new RegionChildrenResponse.RegionOptionDto(d.getId(), d.getName()))
                .toList();
    }
}
