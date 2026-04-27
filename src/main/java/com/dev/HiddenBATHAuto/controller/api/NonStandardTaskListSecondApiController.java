package com.dev.HiddenBATHAuto.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.task.OrderCheckCompleteRequest;
import com.dev.HiddenBATHAuto.dto.task.OrderCheckCompleteResponse;
import com.dev.HiddenBATHAuto.service.order.OrderCheckStatusService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/management/api/non-standard-task-list-second")
@RequiredArgsConstructor
public class NonStandardTaskListSecondApiController {

    private final OrderCheckStatusService orderCheckStatusService;

    @PostMapping("/check-complete")
    public ResponseEntity<OrderCheckCompleteResponse> checkComplete(
            @RequestBody OrderCheckCompleteRequest request,
            Authentication authentication
    ) {
        String checkedByUsername = authentication != null ? authentication.getName() : "UNKNOWN";

        int checkedCount = orderCheckStatusService.markChecked(
                request.getOrderIds(),
                checkedByUsername
        );

        return ResponseEntity.ok(new OrderCheckCompleteResponse(
                true,
                checkedCount + "건이 체크완료 처리되었습니다.",
                checkedCount
        ));
    }
}