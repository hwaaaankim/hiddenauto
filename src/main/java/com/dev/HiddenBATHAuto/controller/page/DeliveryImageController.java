package com.dev.HiddenBATHAuto.controller.page;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.service.order.OrderService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/team/delivery-tools")
@PreAuthorize("hasAnyRole('MANAGEMENT', 'INTERNAL_EMPLOYEE') and principal.teamName == '배송팀'")
@RequiredArgsConstructor
public class DeliveryImageController {
    private final OrderService orderService;
    private final CompanyRepository companyRepository;

    @GetMapping("/companies")
    public List<String> companies(@RequestParam(defaultValue = "") String keyword) {
        if (keyword.isBlank()) return List.of();
        return companyRepository.findTop50ByCompanyNameContainingOrderByCompanyNameAsc(keyword.trim())
                .stream().map(c -> c.getCompanyName()).distinct().toList();
    }

    @GetMapping("/{orderId}/images")
    public List<OrderService.DeliveryImageView> images(@AuthenticationPrincipal PrincipalDetails principal,
            @PathVariable Long orderId) {
        return orderService.getCompletedDeliveryImages(principal.getMember(), orderId);
    }

    @PostMapping("/{orderId}/images")
    public List<OrderService.DeliveryImageView> save(@AuthenticationPrincipal PrincipalDetails principal,
            @PathVariable Long orderId,
            @RequestParam(required = false) List<Long> expectedIds,
            @RequestParam(required = false) List<Long> keepIds,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) throws IOException {
        return orderService.replaceCompletedDeliveryImages(principal.getMember(), orderId, expectedIds, keepIds, files);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, AccessDeniedException.class})
    public ResponseEntity<?> invalid(RuntimeException ex) {
        return ResponseEntity.status(ex instanceof AccessDeniedException ? 403 : 400)
                .body(Map.of("message", ex.getMessage()));
    }
}
