package com.dev.HiddenBATHAuto.controller.api;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.ordernotification.OrderImportantNotificationBatchDto;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationConfirmImportantRequest;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationItemDto;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationPageDto;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationReadLoadedRequest;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationSummaryDto;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationCategory;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.ordernotification.OrderNotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/internal/order-notifications")
@RequiredArgsConstructor
public class OrderNotificationController {

    private final OrderNotificationService notificationService;

    @GetMapping("/summary")
    public OrderNotificationSummaryDto summary(@AuthenticationPrincipal PrincipalDetails principal) {
        return notificationService.getSummary(principal != null ? principal.getMember() : null);
    }

    @GetMapping
    public OrderNotificationPageDto list(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam(required = false) OrderNotificationCategory category,
            @RequestParam(defaultValue = "false") boolean importantOnly,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "50") int size
    ) {
        return notificationService.getNotifications(
                principal != null ? principal.getMember() : null,
                category,
                importantOnly,
                cursor,
                size
        );
    }

    @GetMapping("/important/pending")
    public OrderImportantNotificationBatchDto pendingImportant(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam(defaultValue = "100") int size
    ) {
        return notificationService.getPendingImportantNotifications(
                principal != null ? principal.getMember() : null,
                size
        );
    }

    @PostMapping("/important/confirm-loaded")
    public ResponseEntity<Map<String, Object>> confirmImportantLoaded(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody(required = false) OrderNotificationConfirmImportantRequest request
    ) {
        int updatedCount = notificationService.confirmImportant(
                principal != null ? principal.getMember() : null,
                request != null ? request.getNotificationIds() : null
        );
        return ResponseEntity.ok(Map.of(
                "success", true,
                "updatedCount", updatedCount
        ));
    }

    @PostMapping("/{notificationId}/read")
    public OrderNotificationItemDto read(
            @AuthenticationPrincipal PrincipalDetails principal,
            @PathVariable Long notificationId
    ) {
        return notificationService.markRead(
                principal != null ? principal.getMember() : null,
                notificationId
        );
    }

    @PostMapping("/read-loaded")
    public ResponseEntity<Map<String, Object>> readLoaded(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody(required = false) OrderNotificationReadLoadedRequest request
    ) {
        int updatedCount = notificationService.markLoadedRead(
                principal != null ? principal.getMember() : null,
                request != null ? request.getNotificationIds() : null
        );
        return ResponseEntity.ok(Map.of(
                "success", true,
                "updatedCount", updatedCount
        ));
    }
}
