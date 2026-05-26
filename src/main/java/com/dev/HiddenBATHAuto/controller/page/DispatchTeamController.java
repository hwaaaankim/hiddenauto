package com.dev.HiddenBATHAuto.controller.page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.BulkDispatchCompleteRequest;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.BulkDispatchCompleteResponse;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.DeliveryMethodDto;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.DispatchOrderSearchRequest;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.DispatchOrderSearchResponse;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.ProvinceChildrenResponse;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.RegionOptionDto;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.UpdateDeliveryMethodRequest;
import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.model.auth.Province;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.repository.auth.CityRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.service.dispatch.DispatchTeamService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/team")
@PreAuthorize("hasRole('INTERNAL_EMPLOYEE')")
@RequiredArgsConstructor
public class DispatchTeamController {

    private final DispatchTeamService dispatchTeamService;
    private final TeamCategoryRepository teamCategoryRepository;
    private final DeliveryMethodRepository deliveryMethodRepository;
    private final ProvinceRepository provinceRepository;
    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;

    @GetMapping("/dispatchList")
    public String dispatchList(
            @AuthenticationPrincipal PrincipalDetails principal,
            Model model
    ) {
        Member loginMember = principal.getMember();
        dispatchTeamService.validateDispatchTeamMember(loginMember);

        List<TeamCategory> productCategories = teamCategoryRepository.findByTeamName("생산팀");
        List<DeliveryMethod> deliveryMethods = deliveryMethodRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        List<Province> provinces = provinceRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));

        model.addAttribute("productCategories", productCategories);
        model.addAttribute("deliveryMethods", deliveryMethods);
        model.addAttribute("provinces", provinces);
        model.addAttribute("today", LocalDate.now());

        return "administration/team/dispatch/dispatchList";
    }

    @PostMapping("/dispatchList/api/orders/search")
    @ResponseBody
    public ResponseEntity<DispatchOrderSearchResponse> searchOrders(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody(required = false) DispatchOrderSearchRequest request
    ) {
        DispatchOrderSearchResponse response =
                dispatchTeamService.searchDispatchOrders(request, principal.getMember());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/dispatchList/api/orders/complete")
    @ResponseBody
    public ResponseEntity<BulkDispatchCompleteResponse> completeDispatchOrders(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody BulkDispatchCompleteRequest request
    ) {
        BulkDispatchCompleteResponse response =
                dispatchTeamService.completeDispatchOrders(
                        request != null ? request.getOrderIds() : List.of(),
                        principal.getMember()
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/dispatchList/api/orders/{orderId}/delivery-method")
    @ResponseBody
    public ResponseEntity<DeliveryMethodDto> updateDeliveryMethod(
            @AuthenticationPrincipal PrincipalDetails principal,
            @PathVariable Long orderId,
            @RequestBody UpdateDeliveryMethodRequest request
    ) {
        DeliveryMethodDto response =
                dispatchTeamService.updateDeliveryMethod(
                        orderId,
                        request != null ? request.getDeliveryMethodId() : null,
                        principal.getMember()
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/dispatchList/api/regions/provinces")
    @ResponseBody
    public ResponseEntity<List<RegionOptionDto>> getProvinces() {
        List<RegionOptionDto> result = provinceRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toRegionOptionDto)
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/dispatchList/api/regions/provinces/{provinceId}/children")
    @ResponseBody
    public ResponseEntity<ProvinceChildrenResponse> getProvinceChildren(
            @PathVariable Long provinceId
    ) {
        List<RegionOptionDto> cities = cityRepository.findByProvinceIdOrderByIdAsc(provinceId)
                .stream()
                .map(this::toRegionOptionDto)
                .toList();

        List<RegionOptionDto> directDistricts = districtRepository.findByProvinceIdAndCityIsNullOrderByIdAsc(provinceId)
                .stream()
                .map(this::toRegionOptionDto)
                .toList();

        ProvinceChildrenResponse response = ProvinceChildrenResponse.builder()
                .cities(cities)
                .districts(directDistricts)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/dispatchList/api/regions/cities/{cityId}/districts")
    @ResponseBody
    public ResponseEntity<List<RegionOptionDto>> getCityDistricts(
            @PathVariable Long cityId
    ) {
        List<RegionOptionDto> districts = districtRepository.findByCityIdOrderByIdAsc(cityId)
                .stream()
                .map(this::toRegionOptionDto)
                .toList();

        return ResponseEntity.ok(districts);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "success", false,
                        "message", e.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", false,
                        "message", e.getMessage() != null ? e.getMessage() : "요청 처리 중 오류가 발생했습니다."
                ));
    }

    private RegionOptionDto toRegionOptionDto(Province province) {
        return RegionOptionDto.builder()
                .id(province.getId())
                .name(province.getName())
                .build();
    }

    private RegionOptionDto toRegionOptionDto(City city) {
        return RegionOptionDto.builder()
                .id(city.getId())
                .name(city.getName())
                .build();
    }

    private RegionOptionDto toRegionOptionDto(District district) {
        return RegionOptionDto.builder()
                .id(district.getId())
                .name(district.getName())
                .build();
    }
}