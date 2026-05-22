package com.dev.HiddenBATHAuto.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.CreateProcessRequest;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.DeleteInfoImageRequest;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.InfoImageDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.ProcessDetailRequest;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.ProcessDetailResponse;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.ProcessSummaryResponse;
import com.dev.HiddenBATHAuto.service.process.ProcessMakerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/process-maker/api/processes")
@RequiredArgsConstructor
public class ProcessMakerApiController {

    private final ProcessMakerService processMakerService;

    @GetMapping
    public ResponseEntity<List<ProcessSummaryResponse>> getProcessList() {
        return ResponseEntity.ok(processMakerService.getProcessList());
    }

    @PostMapping
    public ResponseEntity<ProcessDetailResponse> createProcess(@RequestBody CreateProcessRequest request) {
        return ResponseEntity.ok(processMakerService.createProcess(request));
    }

    @GetMapping("/{processId}")
    public ResponseEntity<ProcessDetailResponse> getProcess(@PathVariable Long processId) {
        return ResponseEntity.ok(processMakerService.getProcess(processId));
    }

    @PutMapping("/{processId}")
    public ResponseEntity<ProcessDetailResponse> updateProcess(
            @PathVariable Long processId,
            @RequestBody ProcessDetailRequest request
    ) {
        return ResponseEntity.ok(processMakerService.updateProcess(processId, request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "result", "fail",
                "message", e.getMessage()
        ));
    }
    
    @PostMapping(
            value = "/{processId}/info-images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<List<InfoImageDto>> uploadInfoImages(
            @PathVariable Long processId,
            @RequestPart("files") List<MultipartFile> files
    ) {
        return ResponseEntity.ok(processMakerService.uploadInfoImages(processId, files));
    }

    @DeleteMapping("/{processId}/info-images")
    public ResponseEntity<Map<String, Object>> deleteInfoImage(
            @PathVariable Long processId,
            @RequestBody DeleteInfoImageRequest request
    ) {
        processMakerService.deleteUploadedInfoImage(processId, request);
        return ResponseEntity.ok(Map.of(
                "result", "success"
        ));
    }
}