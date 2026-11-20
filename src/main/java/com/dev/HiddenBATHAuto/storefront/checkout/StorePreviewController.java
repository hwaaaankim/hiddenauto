package com.dev.HiddenBATHAuto.storefront.checkout;

import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Answer;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.EvaluateRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.StatusRequest;
import com.dev.HiddenBATHAuto.service.productmaster.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequiredArgsConstructor
@RequestMapping("/customer/store-preview")
@PreAuthorize("hasAnyRole('CUSTOMER_REPRESENTATIVE','CUSTOMER_EMPLOYEE')")
public class StorePreviewController {
    private final StorePreviewAccess access;
    private final StorePreviewService preview;
    private final ProductStorefrontService storefront;
    private final ProductStudioService studio;
    private final ProductPricingService prices;
    private final ProductStudioAssetService assets;
    private final ProductStudioExtensionService extension;
    private final com.dev.HiddenBATHAuto.repository.auth.CompanyDeliveryAddressRepository addresses;

    @GetMapping({"", "/series/{id}", "/products/{id}"})
    public String page(@AuthenticationPrincipal PrincipalDetails principal, HttpSession session, Model model,
            @RequestParam(defaultValue = "name") String subject, @RequestParam(defaultValue = "") String keyword) {
        long company = access.company(principal);
        model.addAttribute("storeToken", access.token(session));
        model.addAttribute("storeUser", company + ":" + principal.getId());
        model.addAttribute("subject", "series".equals(subject) ? "series" : "name");
        model.addAttribute("keyword", keyword.substring(0, Math.min(100, keyword.length())));
        return "customer/storePreview";
    }

    @GetMapping("/api/customer-collections") @ResponseBody
    public Object catalog(@AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam(defaultValue = "false") boolean nonStandard,
            @RequestParam(defaultValue = "name") String subject, @RequestParam(defaultValue = "") String keyword) {
        access.company(principal);
        if (keyword.length() > 100) throw new IllegalArgumentException("검색어는 100자 이내로 입력해 주세요.");
        return ApiResponse.ok(storefront.collections(nonStandard, subject, keyword, true));
    }

    @GetMapping("/api/customer-series/{id}") @ResponseBody
    public Object series(@AuthenticationPrincipal PrincipalDetails principal, @PathVariable long id) {
        access.company(principal);
        return ApiResponse.ok(storefront.series(id, true));
    }

    @PostMapping("/api/customer-series/{id}/configure") @ResponseBody
    public Object configure(@AuthenticationPrincipal PrincipalDetails principal, @PathVariable long id,
            @RequestBody Map<Long, String> options) {
        access.company(principal);
        return ApiResponse.ok(storefront.configure(id, options, true));
    }

    @GetMapping("/api/products/{id}") @ResponseBody
    public Object detail(@AuthenticationPrincipal PrincipalDetails principal, @PathVariable long id) {
        access.company(principal);
        return ApiResponse.ok(preview.orderable(id));
    }

    @PostMapping("/api/products/{id}/customer-evaluate") @ResponseBody
    public Object evaluate(@AuthenticationPrincipal PrincipalDetails principal, @PathVariable long id,
            @RequestBody Map<String, Answer> answers) {
        long company = access.company(principal);
        preview.orderable(id);
        return ApiResponse.ok(studio.evaluateCustomerPreview(id, answers, preview.actor(company)));
    }

    @PostMapping("/api/products/{id}/pricing/evaluate") @ResponseBody
    public Object price(@AuthenticationPrincipal PrincipalDetails principal, @PathVariable long id,
            @RequestBody EvaluateRequest request) {
        long company = access.company(principal);
        preview.orderable(id);
        var verified = studio.evaluate(id,
                new com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.EvaluateRequest(null, request.answers()),
                preview.actor(company));
        if (!verified.complete()) {
            throw new IllegalArgumentException("현재 분기의 필수 질문을 먼저 완료해 주세요.");
        }
        return ApiResponse.ok(ProductPricingWire.response(prices.evaluate(id, new EvaluateRequest(null, verified.answers()))));
    }

    @PostMapping("/api/pricing/status") @ResponseBody
    public Object status(@AuthenticationPrincipal PrincipalDetails principal, @RequestBody StatusRequest request) {
        access.company(principal);
        if (request.productIds() == null || request.productIds().size() > 10) throw new IllegalArgumentException("제품을 확인해 주세요.");
        request.productIds().forEach(preview::orderable);
        return ApiResponse.ok(ProductPricingWire.response(prices.status(request.productIds())));
    }

    @GetMapping("/api/faq/{topicId}") @ResponseBody
    public Object faq(@AuthenticationPrincipal PrincipalDetails principal, @PathVariable long topicId) {
        access.company(principal);
        return ApiResponse.ok(extension.topics().stream().filter(topic -> topic.id().equals(topicId)).toList());
    }

    @PostMapping(value = "/api/assets/stage", consumes = "multipart/form-data") @ResponseBody
    public Object stage(@AuthenticationPrincipal PrincipalDetails principal, HttpSession session,
            @RequestHeader("X-Store-Token") String token, @RequestPart("files") List<MultipartFile> files) {
        long company = access.company(principal);
        access.check(session, token);
        return ApiResponse.ok(assets.stage(files, preview.actor(company)));
    }

    @GetMapping("/api/assets/{id}") @ResponseBody
    public Object file(@AuthenticationPrincipal PrincipalDetails principal, @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean download) {
        long company = access.company(principal);
        var file = assets.require(id);
        if ("STAGED".equals(file.getOwnerType())) {
            if (!preview.actor(company).equals(file.getCreatedBy())) throw new NoSuchElementException();
        } else if (!Set.of("PRODUCT", "PROCESS", "GROUP", "VALUE", "FAQ", "INPUT").contains(file.getOwnerType())) {
            throw new NoSuchElementException();
        }
        return assets.content(file, download);
    }

    public record ItemRequest(StorePreviewService.Selection selection, boolean bookmark) {}
    public record CheckRequest(StorePreviewService.Item item, boolean bookmark) {}
    public record SaveRequest(String kind, String note, StorePreviewService.Item item) {}

    @PostMapping("/api/item") @ResponseBody
    public Object item(@AuthenticationPrincipal PrincipalDetails principal, @RequestBody ItemRequest body) {
        return ApiResponse.ok(preview.item(body.selection(), access.company(principal), body.bookmark()));
    }

    @PostMapping("/api/item/check") @ResponseBody
    public Object check(@AuthenticationPrincipal PrincipalDetails principal, @RequestBody CheckRequest body) {
        return ApiResponse.ok(preview.check(body.item(), access.company(principal), body.bookmark()));
    }

    public record MetadataRequest(List<Long> productIds) {}

    @PostMapping("/api/items/metadata") @ResponseBody
    public Object metadata(@AuthenticationPrincipal PrincipalDetails principal, @RequestBody MetadataRequest body) {
        access.company(principal);
        return ApiResponse.ok(preview.cardMetadata(body.productIds()));
    }

    @GetMapping("/api/counts") @ResponseBody
    public Object counts(@AuthenticationPrincipal PrincipalDetails principal) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(ApiResponse.ok(preview.savedCounts(access.company(principal))));
    }

    @GetMapping("/api/saved") @ResponseBody
    public Object saved(@AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam(required = false) String kind) {
        return ApiResponse.ok(preview.saved(access.company(principal), kind));
    }

    @GetMapping("/api/saved/{id}") @ResponseBody
    public Object savedItem(@AuthenticationPrincipal PrincipalDetails principal, @PathVariable long id) {
        return ApiResponse.ok(preview.saved(access.company(principal), id));
    }

    @PostMapping("/api/saved") @ResponseBody
    public Object save(@AuthenticationPrincipal PrincipalDetails principal, HttpSession session,
            @RequestHeader("X-Store-Token") String token, @RequestBody SaveRequest body) {
        access.check(session, token);
        preview.save(access.company(principal), body.kind(), body.note(), body.item());
        return ApiResponse.ok(true);
    }

    @DeleteMapping("/api/saved/{id}") @ResponseBody
    public Object delete(@AuthenticationPrincipal PrincipalDetails principal, HttpSession session,
            @RequestHeader("X-Store-Token") String token, @PathVariable long id) {
        access.check(session, token);
        preview.remove(access.company(principal), id);
        return ApiResponse.ok(true);
    }

    public record CleanupRequest(String stamp, String kind) {}

    @DeleteMapping("/api/saved/{id}/invalid") @ResponseBody
    public Object removeInvalid(@AuthenticationPrincipal PrincipalDetails principal, HttpSession session,
            @RequestHeader("X-Store-Token") String token, @PathVariable long id, @RequestBody CleanupRequest body) {
        access.check(session, token);
        return ApiResponse.ok(preview.removeInvalid(access.company(principal), id, body.stamp(), body.kind()));
    }

    @GetMapping("/api/checkout") @ResponseBody
    public Object checkoutOptions(@AuthenticationPrincipal PrincipalDetails principal) {
        long company = access.company(principal);
        return ApiResponse.ok(Map.of("methods", StoreShipping.METHODS, "companyAddress", preview.companyAddress(company),
                "addresses", addresses.findByCompany_IdOrderByIdAsc(company).stream().map(a -> new StorePreviewService.Address(
                    a.getZipCode(), a.getDoName(), a.getSiName(), a.getGuName(), a.getRoadAddress(), null, a.getDetailAddress())).toList(), "points", preview.balance(company),
                "today", preview.today().toString(), "calendarThrough", preview.calendarThrough().toString(), "holidays", preview.holidays().stream().map(Object::toString).toList()));
    }

    @PostMapping("/api/checkout") @ResponseBody
    public Object checkout(@AuthenticationPrincipal PrincipalDetails principal, @RequestBody StorePreviewService.Checkout body) {
        return ApiResponse.ok(preview.checkout(access.company(principal), body));
    }

    @ExceptionHandler(StorePreviewService.Changed.class)
    @ResponseBody
    public ResponseEntity<?> changed(StorePreviewService.Changed e) {
        return ResponseEntity.status(409).body(e.lineKey() == null ? Map.of("message", e.getMessage(), "invalid", e.invalid())
                : Map.of("message", e.getMessage(), "lineKey", e.lineKey(), "invalid", e.invalid()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, NoSuchElementException.class})
    @ResponseBody
    public ResponseEntity<?> invalid(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("message", Objects.toString(e.getMessage(), "제품 정보를 다시 확인해 주세요.")));
    }
}
