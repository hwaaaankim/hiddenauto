package com.dev.HiddenBATHAuto.controller.api;

import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.*;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.DispatchOrderRowDto;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.service.dispatch.DispatchTeamService;
import com.dev.HiddenBATHAuto.service.order.DeliveryMethodAssignmentPolicy;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/order-management")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminOrderManagementController {
    private final DispatchTeamService service;
    private final DeliveryMethodRepository methods;
    private final MemberRepository members;

    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of("methods", methods.findAll(Sort.by("id")).stream().map(m -> Map.of(
                "id", m.getId(), "name", m.getMethodName(),
                "handlerRequired", DeliveryMethodAssignmentPolicy.requiresHandler(m),
                "site", DeliveryMethodAssignmentPolicy.containsKeyword(m.getMethodName(), "현장배송"))).toList(),
                "handlers", members.findByTeam_NameAndEnabledTrueOrderByNameAscUsernameAsc("배송팀").stream()
                        .map(m -> Map.of("id", m.getId(), "name", m.getName() + " (" + m.getUsername() + ")")).toList());
    }

    @PostMapping("/preview")
    public List<DispatchOrderRowDto> preview(@RequestBody OrderManagementPreviewRequest request,
            @AuthenticationPrincipal PrincipalDetails principal) {
        return service.getAdminOrderManagementPreview(request.getOrderIds(), principal.getMember());
    }

    @PostMapping
    public OrderManagementSaveResponse save(@RequestBody OrderManagementSaveRequest request,
            @AuthenticationPrincipal PrincipalDetails principal) {
        return service.saveAdminOrderManagement(request, principal.getMember());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalid(RuntimeException error) { return Map.of("message", error.getMessage()); }
}
