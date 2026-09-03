package com.dev.HiddenBATHAuto.controller.api.productmaster;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.AttributeImageResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.AttributeImagePolicyResponse;
import com.dev.HiddenBATHAuto.service.productmaster.ProductAttributeImageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/api/product-master")
@PreAuthorize("hasRole('ADMIN')")
public class ProductAttributeImageApiController {

    private final ProductAttributeImageService imageService;

    @GetMapping("/image-policy")
    public ApiResponse<AttributeImagePolicyResponse> imagePolicy() {
        return ApiResponse.ok(imageService.getPolicy());
    }

    @PostMapping(value = "/groups/{groupId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<AttributeImageResponse>>> uploadGroupImages(
            @PathVariable Long groupId,
            @RequestPart("images") List<MultipartFile> images,
            Principal principal
    ) {
        List<AttributeImageResponse> saved = imageService.uploadToGroup(groupId, images, actor(principal));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("그룹 이미지를 등록했습니다.", saved));
    }

    @PostMapping(value = "/values/{valueId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<AttributeImageResponse>>> uploadValueImages(
            @PathVariable Long valueId,
            @RequestPart("images") List<MultipartFile> images,
            Principal principal
    ) {
        List<AttributeImageResponse> saved = imageService.uploadToValue(valueId, images, actor(principal));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("옵션값 이미지를 등록했습니다.", saved));
    }

    @DeleteMapping("/images/{imageId}")
    public ApiResponse<Void> deleteImage(@PathVariable Long imageId) {
        imageService.deleteImage(imageId);
        return ApiResponse.ok("이미지 등록을 해제했습니다.", null);
    }

    private String actor(Principal principal) {
        return principal == null ? "SYSTEM" : principal.getName();
    }
}
