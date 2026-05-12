package com.dev.HiddenBATHAuto.controller.api;

import static com.dev.HiddenBATHAuto.dto.process.ProcessTestDtos.SessionResponse;
import static com.dev.HiddenBATHAuto.dto.process.ProcessTestDtos.StartSessionRequest;
import static com.dev.HiddenBATHAuto.dto.process.ProcessTestDtos.SubmitAnswerRequest;
import static com.dev.HiddenBATHAuto.dto.process.ProcessTestDtos.TestProcessSummaryResponse;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.service.process.ProcessTestService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/process-test/api")
@RequiredArgsConstructor
public class ProcessTestApiController {

    private final ProcessTestService processTestService;

    @GetMapping("/processes")
    public ResponseEntity<List<TestProcessSummaryResponse>> getProcesses() {
        return ResponseEntity.ok(processTestService.getProcessListForTest());
    }

    @PostMapping(
            value = "/sessions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<SessionResponse> startSession(@RequestPart("payload") StartSessionRequest request) {
        return ResponseEntity.ok(processTestService.startSession(request));
    }

    @GetMapping("/sessions/{sessionKey}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable String sessionKey) {
        return ResponseEntity.ok(processTestService.getSession(sessionKey));
    }

    @PostMapping(
            value = "/sessions/{sessionKey}/answers",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<SessionResponse> submitAnswer(
            @PathVariable String sessionKey,
            @RequestPart("payload") SubmitAnswerRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ResponseEntity.ok(processTestService.submitAnswer(sessionKey, request, files));
    }

    /**
     * 특정 답변부터 다시 진행.
     *
     * 동작:
     * - unitKey에 해당하는 답변 포함 이후 답변 삭제
     * - 현재 UNIT을 unitKey로 되돌림
     * - 완료 상태였어도 다시 IN_PROGRESS로 변경
     * - 가격 계산 결과 재계산
     */
    @PostMapping("/sessions/{sessionKey}/answers/{unitKey}/reset")
    public ResponseEntity<SessionResponse> resetFromAnswer(
            @PathVariable String sessionKey,
            @PathVariable String unitKey
    ) {
        return ResponseEntity.ok(processTestService.resetFromAnswer(sessionKey, unitKey));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "result", "fail",
                "message", e.getMessage()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "result", "fail",
                "message", e.getMessage()
        ));
    }
}