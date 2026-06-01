package com.dev.HiddenBATHAuto.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.service.order.OrderCheckStatusService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/management/api/non-standard-task-list-second")
@RequiredArgsConstructor
public class NonStandardTaskListSecondApiController {

	private final OrderCheckStatusService orderCheckStatusService;

    @PostMapping("/check-complete")
    public ResponseEntity<Map<String, Object>> checkComplete(
            @RequestBody CheckCompleteRequest request,
            Authentication authentication
    ) {
        int changedCount = orderCheckStatusService.markChecked(
                request != null ? request.orderIds() : List.of(),
                resolveUsername(authentication)
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", changedCount + "건이 체크완료 처리되었습니다.",
                "changedCount", changedCount
        ));
    }

    private String resolveUsername(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "UNKNOWN";
        }

        return authentication.getName().trim();
    }

    public record CheckCompleteRequest(List<Long> orderIds) {
    }
}