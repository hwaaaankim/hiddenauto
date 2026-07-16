package com.dev.HiddenBATHAuto.controller.page;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkDeliveryMethodChangeRequest;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkDeliveryMethodChangeResponse;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkDeliveryMethodPreviewRequest;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkDeliveryMethodPreviewResponse;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkHandlerChangePreviewRequest;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkHandlerChangePreviewResponse;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkHandlerChangeRequest;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkHandlerChangeResponse;
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
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
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
    private final MemberRepository memberRepository;

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
        List<Member> deliveryHandlers =
                memberRepository.findByTeam_NameAndEnabledTrueOrderByNameAscUsernameAsc("배송팀");

        model.addAttribute("productCategories", productCategories);
        model.addAttribute("deliveryMethods", deliveryMethods);
        model.addAttribute("deliveryHandlers", deliveryHandlers);
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

    @PostMapping("/dispatchList/api/orders/excel")
    @ResponseBody
    public ResponseEntity<byte[]> downloadExcel(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody(required = false) DispatchOrderSearchRequest request
    ) {
        byte[] excelBytes = dispatchTeamService.createDispatchOrdersExcel(request, principal.getMember());

        String filename = "출고팀_업무현황_" + LocalDate.now() + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(excelBytes.length);

        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
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

    @PostMapping("/dispatchList/api/orders/bulk-handler/preview")
    @ResponseBody
    public ResponseEntity<BulkHandlerChangePreviewResponse> previewBulkHandlerChange(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody(required = false) BulkHandlerChangePreviewRequest request
    ) {
        BulkHandlerChangePreviewResponse response =
                dispatchTeamService.previewBulkHandlerChange(
                        request != null ? request.getOrderIds() : List.of(),
                        principal.getMember()
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/dispatchList/api/orders/bulk-handler")
    @ResponseBody
    public ResponseEntity<BulkHandlerChangeResponse> bulkChangeDeliveryHandler(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody BulkHandlerChangeRequest request
    ) {
        BulkHandlerChangeResponse response =
                dispatchTeamService.bulkChangeDeliveryHandler(
                        request,
                        principal.getMember()
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/dispatchList/api/orders/bulk-delivery-method/preview")
    @ResponseBody
    public ResponseEntity<BulkDeliveryMethodPreviewResponse> previewBulkDeliveryMethodChange(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody(required = false) BulkDeliveryMethodPreviewRequest request
    ) {
        BulkDeliveryMethodPreviewResponse response =
                dispatchTeamService.previewBulkDeliveryMethodChange(
                        request != null ? request.getOrderIds() : List.of(),
                        request != null ? request.getDeliveryMethodId() : null,
                        principal.getMember()
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/dispatchList/api/orders/bulk-delivery-method")
    @ResponseBody
    public ResponseEntity<BulkDeliveryMethodChangeResponse> bulkChangeDeliveryMethod(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody BulkDeliveryMethodChangeRequest request
    ) {
        BulkDeliveryMethodChangeResponse response =
                dispatchTeamService.bulkChangeDeliveryMethod(
                        request,
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
                        request != null ? request.getDeliveryHandlerId() : null,
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