package com.dev.HiddenBATHAuto.controller.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.ApiResponse;
import com.dev.HiddenBATHAuto.dto.profile.InternalProfileResponse;
import com.dev.HiddenBATHAuto.dto.profile.InternalProfileUpdateRequest;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.service.auth.InternalProfileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/my-profile")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGEMENT', 'ROLE_INTERNAL_EMPLOYEE')")
public class InternalProfileApiController {

	private final InternalProfileService internalProfileService;

	@GetMapping
	public ResponseEntity<ApiResponse<InternalProfileResponse>> getMyProfile(
			@AuthenticationPrincipal PrincipalDetails principal
	) {
		InternalProfileResponse profile = internalProfileService.getProfile(authenticatedMemberId(principal));
		return ResponseEntity.ok(ApiResponse.ok(profile));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<InternalProfileResponse>> updateMyProfile(
			@AuthenticationPrincipal PrincipalDetails principal,
			@RequestBody(required = false) InternalProfileUpdateRequest request
	) {
		InternalProfileResponse profile = internalProfileService.updateProfile(
				authenticatedMemberId(principal),
				request
		);
		return ResponseEntity.ok(ApiResponse.ok(profile));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException exception) {
		return error(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(HttpMessageNotReadableException exception) {
		return error(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
		return error(HttpStatus.FORBIDDEN, exception.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
		log.error("내정보 처리 중 예기치 않은 오류가 발생했습니다.", exception);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "내정보 처리 중 오류가 발생했습니다.");
	}

	private Long authenticatedMemberId(PrincipalDetails principal) {
		if (principal == null || principal.getId() == null) {
			throw new AccessDeniedException("로그인 사용자 정보를 확인할 수 없습니다.");
		}
		return principal.getId();
	}

	private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
		String safeMessage = message == null || message.isBlank()
				? status.getReasonPhrase()
				: message.trim();
		return ResponseEntity.status(status).body(ApiResponse.fail(safeMessage, null));
	}
}
