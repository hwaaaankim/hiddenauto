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

import com.dev.HiddenBATHAuto.dto.asnotification.AsImportantNotificationBatchDto;
import com.dev.HiddenBATHAuto.dto.asnotification.AsNotificationIdsRequest;
import com.dev.HiddenBATHAuto.dto.asnotification.AsNotificationItemDto;
import com.dev.HiddenBATHAuto.dto.asnotification.AsNotificationPageDto;
import com.dev.HiddenBATHAuto.dto.asnotification.AsNotificationSummaryDto;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.asnotification.AsNotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/internal/as-notifications")
@RequiredArgsConstructor
public class AsNotificationController {

    private final AsNotificationService service;

    @GetMapping("/summary")
    public AsNotificationSummaryDto summary(@AuthenticationPrincipal PrincipalDetails principal) {
        return service.getSummary(principal != null ? principal.getMember() : null);
    }

    @GetMapping
    public AsNotificationPageDto list(@AuthenticationPrincipal PrincipalDetails principal,
                                      @RequestParam(defaultValue = "false") boolean importantOnly,
                                      @RequestParam(required = false) Long cursor,
                                      @RequestParam(defaultValue = "50") int size) {
        return service.getNotifications(principal != null ? principal.getMember() : null, importantOnly, cursor, size);
    }

    @GetMapping("/important/pending")
    public AsImportantNotificationBatchDto pending(@AuthenticationPrincipal PrincipalDetails principal,
                                                    @RequestParam(defaultValue = "100") int size) {
        return service.getPendingImportantNotifications(principal != null ? principal.getMember() : null, size);
    }

    @PostMapping("/{notificationId}/read")
    public AsNotificationItemDto read(@AuthenticationPrincipal PrincipalDetails principal,
                                      @PathVariable Long notificationId) {
        return service.markRead(principal != null ? principal.getMember() : null, notificationId);
    }

    @PostMapping("/read-loaded")
    public ResponseEntity<Map<String, Object>> readLoaded(@AuthenticationPrincipal PrincipalDetails principal,
                                                          @RequestBody(required = false) AsNotificationIdsRequest request) {
        int count = service.markLoadedRead(principal != null ? principal.getMember() : null,
                request != null ? request.getNotificationIds() : null);
        return ResponseEntity.ok(Map.of("success", true, "updatedCount", count));
    }

    @PostMapping("/important/confirm-loaded")
    public ResponseEntity<Map<String, Object>> confirm(@AuthenticationPrincipal PrincipalDetails principal,
                                                       @RequestBody(required = false) AsNotificationIdsRequest request) {
        int count = service.confirmImportant(principal != null ? principal.getMember() : null,
                request != null ? request.getNotificationIds() : null);
        return ResponseEntity.ok(Map.of("success", true, "updatedCount", count));
    }
}
