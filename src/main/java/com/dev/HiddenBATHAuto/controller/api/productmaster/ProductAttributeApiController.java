package com.dev.HiddenBATHAuto.controller.api.productmaster;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.GroupResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.GroupSaveRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ReorderRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ValueResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ValueSaveRequest;
import com.dev.HiddenBATHAuto.service.productmaster.ProductAttributeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/api/product-master")
@PreAuthorize("hasRole('ADMIN')")
public class ProductAttributeApiController {

    private final ProductAttributeService attributeService;

    @GetMapping("/groups")
    public ApiResponse<List<GroupResponse>> groups(
            @RequestParam(name = "includeInactive", defaultValue = "true") boolean includeInactive
    ) {
        return ApiResponse.ok(attributeService.getGroups(includeInactive));
    }

    @GetMapping("/groups/{groupId}")
    public ApiResponse<GroupResponse> group(@PathVariable Long groupId) {
        return ApiResponse.ok(attributeService.getGroup(groupId));
    }

    @PostMapping(value = "/groups", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            @Valid @RequestBody GroupSaveRequest request,
            Principal principal
    ) {
        GroupResponse saved = attributeService.createGroup(request, actor(principal));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("옵션 그룹을 등록했습니다.", saved));
    }

    @PostMapping(value = "/groups", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<GroupResponse>> createGroupWithImages(
            @Valid @RequestPart("request") GroupSaveRequest request,
            @RequestPart(name = "images", required = false) List<MultipartFile> images,
            Principal principal
    ) {
        GroupResponse saved = attributeService.createGroup(request, images, actor(principal));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("옵션 그룹과 이미지를 등록했습니다.", saved));
    }

    @PutMapping(value = "/groups/{groupId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<GroupResponse> updateGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody GroupSaveRequest request,
            Principal principal
    ) {
        return ApiResponse.ok(
                "옵션 그룹을 수정했습니다.",
                attributeService.updateGroup(groupId, request, actor(principal))
        );
    }

    @PutMapping(value = "/groups/{groupId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<GroupResponse> updateGroupWithImages(
            @PathVariable Long groupId,
            @Valid @RequestPart("request") GroupSaveRequest request,
            @RequestPart(name = "images", required = false) List<MultipartFile> images,
            Principal principal
    ) {
        return ApiResponse.ok(
                "옵션 그룹과 이미지를 수정했습니다.",
                attributeService.updateGroup(groupId, request, images, actor(principal))
        );
    }

    @DeleteMapping("/groups/{groupId}")
    public ApiResponse<Void> deleteGroup(@PathVariable Long groupId) {
        attributeService.deleteGroup(groupId);
        return ApiResponse.ok("옵션 그룹을 삭제했습니다.", null);
    }

    @PostMapping("/groups/reorder")
    public ApiResponse<List<GroupResponse>> reorderGroups(
            @Valid @RequestBody ReorderRequest request,
            Principal principal
    ) {
        return ApiResponse.ok(
                "옵션 그룹 순서를 저장했습니다.",
                attributeService.reorderGroups(request, actor(principal))
        );
    }

    @PostMapping(value = "/groups/{groupId}/values", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ValueResponse>> createValue(
            @PathVariable Long groupId,
            @Valid @RequestBody ValueSaveRequest request,
            Principal principal
    ) {
        ValueResponse saved = attributeService.createValue(groupId, request, actor(principal));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("옵션값을 등록했습니다.", saved));
    }

    @PostMapping(value = "/groups/{groupId}/values", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ValueResponse>> createValueWithImages(
            @PathVariable Long groupId,
            @Valid @RequestPart("request") ValueSaveRequest request,
            @RequestPart(name = "images", required = false) List<MultipartFile> images,
            Principal principal
    ) {
        ValueResponse saved = attributeService.createValue(groupId, request, images, actor(principal));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("옵션값과 이미지를 등록했습니다.", saved));
    }

    @PutMapping(value = "/values/{valueId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ValueResponse> updateValue(
            @PathVariable Long valueId,
            @Valid @RequestBody ValueSaveRequest request,
            Principal principal
    ) {
        return ApiResponse.ok(
                "옵션값을 수정했습니다.",
                attributeService.updateValue(valueId, request, actor(principal))
        );
    }

    @PutMapping(value = "/values/{valueId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ValueResponse> updateValueWithImages(
            @PathVariable Long valueId,
            @Valid @RequestPart("request") ValueSaveRequest request,
            @RequestPart(name = "images", required = false) List<MultipartFile> images,
            Principal principal
    ) {
        return ApiResponse.ok(
                "옵션값과 이미지를 수정했습니다.",
                attributeService.updateValue(valueId, request, images, actor(principal))
        );
    }

    @DeleteMapping("/values/{valueId}")
    public ApiResponse<Void> deleteValue(@PathVariable Long valueId) {
        attributeService.deleteValue(valueId);
        return ApiResponse.ok("옵션값을 삭제했습니다.", null);
    }

    @PostMapping("/groups/{groupId}/values/reorder")
    public ApiResponse<GroupResponse> reorderValues(
            @PathVariable Long groupId,
            @Valid @RequestBody ReorderRequest request,
            Principal principal
    ) {
        return ApiResponse.ok(
                "옵션값 순서를 저장했습니다.",
                attributeService.reorderValues(groupId, request, actor(principal))
        );
    }

    private String actor(Principal principal) {
        return principal == null ? "SYSTEM" : principal.getName();
    }
}
