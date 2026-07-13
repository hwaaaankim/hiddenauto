package com.dev.HiddenBATHAuto.controller.page;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.delivery.route.DeliveryRouteDtos.Page;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.order.DeliveryCompletionService;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryRouteService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/team/deliveryRoute")
@PreAuthorize("hasRole('INTERNAL_EMPLOYEE')")
@RequiredArgsConstructor
public class DeliveryRouteController {

    private final DeliveryRouteService deliveryRouteService;
    private final DeliveryCompletionService deliveryCompletionService;

    @GetMapping
    public String getDeliveryRoutePage(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            Model model
    ) {
        Member loginMember = requireLoginMember(principal);
        LocalDate selectedDate = deliveryDate == null ? LocalDate.now() : deliveryDate;
        Page routePage = deliveryRouteService.getRoutePage(loginMember, selectedDate);

        model.addAttribute("routePage", routePage);
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("previousDate", selectedDate.minusDays(1));
        model.addAttribute("nextDate", selectedDate.plusDays(1));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isToday", selectedDate.equals(LocalDate.now()));

        return "administration/team/delivery/deliveryRoute";
    }

    /**
     * 업체별 오늘 배송 화면에서 같은 묶음의 선택 주문을 한 번에 배송완료 처리합니다.
     * 업로드한 모든 이미지는 선택된 모든 주문에 각각 독립 파일/OrderImage로 저장됩니다.
     */
    @PostMapping("/complete")
    @ResponseBody
    public ResponseEntity<?> completeSelectedRouteOrders(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam("deliveryDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam("orderIds") List<Long> orderIds,
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ) {
        try {
            Member loginMember = requireLoginMember(principal);

            List<Long> completedOrderIds = deliveryCompletionService.completeRouteSelection(
                    loginMember,
                    deliveryDate,
                    orderIds,
                    files
            );

            int uploadedImageCount = countValidImageFiles(files);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("completedOrderIds", completedOrderIds);
            body.put("completedCount", completedOrderIds.size());
            body.put("uploadedImageCount", uploadedImageCount);
            body.put("message", completedOrderIds.size() + "건을 배송완료 처리했습니다.");

            return ResponseEntity.ok(body);

        } catch (AccessDeniedException e) {
            return errorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage() != null ? e.getMessage() : "배송완료 처리 중 오류가 발생했습니다."
            );
        }
    }

    private int countValidImageFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return 0;
        }

        return (int) files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .filter(file -> file.getContentType() != null)
                .filter(file -> file.getContentType().toLowerCase(Locale.ROOT).startsWith("image/"))
                .count();
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message != null ? message : "요청 처리 중 오류가 발생했습니다.");
        return ResponseEntity.status(status).body(body);
    }

    private Member requireLoginMember(PrincipalDetails principal) {
        if (principal == null || principal.getMember() == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        Member member = principal.getMember();

        if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
            throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
        }

        return member;
    }
}
