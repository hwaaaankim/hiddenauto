package com.dev.HiddenBATHAuto.controller.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.nonStandardList.NonStandardOrderDeleteRequest;
import com.dev.HiddenBATHAuto.dto.nonStandardList.NonStandardOrderDeleteResponse;
import com.dev.HiddenBATHAuto.service.nonstandard.NonStandardTaskBulkDeleteService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/management/api/non-standard-task")
@RequiredArgsConstructor
@Validated
public class NonStandardTaskBulkDeleteApiController {

    private final NonStandardTaskBulkDeleteService nonStandardTaskBulkDeleteService;

    @PostMapping("/delete-tasks")
    public ResponseEntity<NonStandardOrderDeleteResponse> deleteTasks(
            @RequestBody NonStandardOrderDeleteRequest request
    ) {
        try {
            NonStandardOrderDeleteResponse response =
                    nonStandardTaskBulkDeleteService.deleteTasksByOrderIds(request.getOrderIds());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(NonStandardOrderDeleteResponse.fail(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(NonStandardOrderDeleteResponse.fail("태스크 삭제 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/delete-orders")
    public ResponseEntity<NonStandardOrderDeleteResponse> deleteOrders(
            @RequestBody NonStandardOrderDeleteRequest request
    ) {
        try {
            NonStandardOrderDeleteResponse response =
                    nonStandardTaskBulkDeleteService.deleteOrdersByOrderIds(request.getOrderIds());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(NonStandardOrderDeleteResponse.fail(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(NonStandardOrderDeleteResponse.fail("주문 삭제 중 오류가 발생했습니다."));
        }
    }
}