// 관리자 공통 헤더 - 내정보 팝업 및 공통 비밀번호 표시/숨김
(function () {
	"use strict";

	const PROFILE_ENDPOINT = "/api/internal/my-profile";

	function byId(id) {
		return document.getElementById(id);
	}

	function utf8ByteLength(value) {
		if (typeof TextEncoder !== "undefined") {
			return new TextEncoder().encode(value).length;
		}
		return new Blob([value]).size;
	}

	function formatMobilePhoneInput(value) {
		const raw = String(value ?? "");

		// 숫자와 하이픈 외의 문자가 있으면 사용자가 입력한 값을 유지합니다.
		if (!/^[0-9-]*$/.test(raw)) {
			return raw;
		}

		const digits = raw.replaceAll("-", "");
		if (!/^01\d/.test(digits) || digits.length > 11) {
			return raw;
		}

		if (digits.length <= 3) return digits;
		if (digits.length <= 7) return digits.slice(0, 3) + "-" + digits.slice(3);
		if (digits.length <= 10) {
			return digits.slice(0, 3) + "-" + digits.slice(3, -4) + "-" + digits.slice(-4);
		}
		return digits.slice(0, 3) + "-" + digits.slice(3, 7) + "-" + digits.slice(7);
	}

	function resetPasswordVisibility(scope) {
		scope.querySelectorAll("[data-admin-password-toggle]").forEach(function (button) {
			const input = byId(button.dataset.passwordTarget);
			if (!input) return;

			input.type = "password";
			button.setAttribute("aria-pressed", "false");
			button.setAttribute(
				"aria-label",
				(button.getAttribute("aria-label") || "비밀번호 표시").replace(/숨기기$/, "표시")
			);
			const icon = button.querySelector("i");
			if (icon) {
				icon.classList.remove("ri-eye-off-line");
				icon.classList.add("ri-eye-line");
			}
		});
	}

	function bindPasswordToggles() {
		document.addEventListener("click", function (event) {
			const button = event.target.closest("[data-admin-password-toggle]");
			if (!button) return;

			const input = byId(button.dataset.passwordTarget);
			if (!input) return;

			const willShow = input.type === "password";
			input.type = willShow ? "text" : "password";
			button.setAttribute("aria-pressed", String(willShow));
			button.setAttribute(
				"aria-label",
				(button.getAttribute("aria-label") || "비밀번호 표시")
					.replace(/표시$/, willShow ? "숨기기" : "표시")
					.replace(/숨기기$/, willShow ? "숨기기" : "표시")
			);

			const icon = button.querySelector("i");
			if (icon) {
				icon.classList.toggle("ri-eye-line", !willShow);
				icon.classList.toggle("ri-eye-off-line", willShow);
			}
			input.focus({ preventScroll: true });
		});
	}

	async function requestProfile(options) {
		const requestOptions = Object.assign({
			method: "GET",
			credentials: "same-origin",
			headers: { "Accept": "application/json" }
		}, options || {});

		if (requestOptions.body) {
			requestOptions.headers["Content-Type"] = "application/json";
		}

		const response = await fetch(PROFILE_ENDPOINT, requestOptions);
		const responseText = await response.text();
		let payload = null;

		if (responseText) {
			try {
				payload = JSON.parse(responseText);
			} catch (error) {
				throw new Error(response.redirected
					? "로그인 상태가 만료되었습니다. 다시 로그인해 주세요."
					: "서버 응답 형식을 확인할 수 없습니다.");
			}
		}

		if (!response.ok || !payload || payload.success === false) {
			throw new Error(payload?.message || "내정보를 처리하지 못했습니다.");
		}

		return payload.data || {};
	}

	function initProfileModal() {
		const modal = byId("admin-my-profile-modal");
		if (!modal) return;

		const form = byId("admin-my-profile-form");
		const fields = byId("admin-my-profile-fields");
		const status = byId("admin-my-profile-status");
		const saveButton = byId("admin-my-profile-save-btn");
		const username = byId("admin-my-profile-username");
		const name = byId("admin-my-profile-name");
		const phone = byId("admin-my-profile-phone");
		const telephone = byId("admin-my-profile-telephone");
		const email = byId("admin-my-profile-email");
		const password = byId("admin-my-profile-password");
		const passwordConfirm = byId("admin-my-profile-password-confirm");

		if (!form || !fields || !status || !saveButton || !username || !name
				|| !phone || !telephone || !email || !password || !passwordConfirm) {
			return;
		}

		let loaded = false;
		let requestSequence = 0;
		const saveButtonHtml = saveButton.innerHTML;

		function showStatus(message, type) {
			status.className = "alert internal-profile-status alert-" + type;
			status.textContent = message;
			status.classList.remove("d-none");
		}

		function hideStatus() {
			status.classList.add("d-none");
			status.textContent = "";
		}

		function setLoadedState(value) {
			loaded = value;
			fields.disabled = !value;
			saveButton.disabled = !value;
		}

		function fillProfile(profile) {
			username.value = profile.username || "";
			name.value = profile.name || "";
			phone.value = profile.phone || "";
			telephone.value = profile.telephone || "";
			email.value = profile.email || "";
			password.value = "";
			passwordConfirm.value = "";
			resetPasswordVisibility(modal);
		}

		function setSaving(saving) {
			saveButton.disabled = saving || !loaded;
			saveButton.innerHTML = saving
				? '<span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>저장 중...'
				: saveButtonHtml;
		}

		phone.addEventListener("input", function () {
			const formatted = formatMobilePhoneInput(phone.value);
			if (formatted !== phone.value) phone.value = formatted;
		});

		modal.addEventListener("show.bs.modal", async function () {
			const sequence = ++requestSequence;
			form.reset();
			form.classList.remove("was-validated");
			resetPasswordVisibility(modal);
			setLoadedState(false);
			showStatus("정보를 불러오는 중입니다.", "info");

			try {
				const profile = await requestProfile();
				if (sequence !== requestSequence) return;
				fillProfile(profile);
				setLoadedState(true);
				hideStatus();
			} catch (error) {
				if (sequence !== requestSequence) return;
				setLoadedState(false);
				showStatus(error.message || "내정보를 불러오지 못했습니다.", "danger");
			}
		});

		modal.addEventListener("hidden.bs.modal", function () {
			requestSequence += 1;
			setLoadedState(false);
			setSaving(false);
			form.reset();
			form.classList.remove("was-validated");
			resetPasswordVisibility(modal);
			showStatus("정보를 불러오는 중입니다.", "info");
		});

		form.addEventListener("submit", async function (event) {
			event.preventDefault();
			if (!loaded) return;

			form.classList.add("was-validated");
			if (!form.checkValidity()) {
				form.reportValidity();
				return;
			}

			const newPassword = password.value;
			const confirmedPassword = passwordConfirm.value;
			const passwordRequested = newPassword.length > 0 || confirmedPassword.length > 0;

			if (passwordRequested && (!newPassword || !confirmedPassword)) {
				showStatus("새 비밀번호와 비밀번호 확인을 모두 입력해 주세요.", "danger");
				(!newPassword ? password : passwordConfirm).focus();
				return;
			}

			if (passwordRequested && newPassword.trim().length === 0) {
				showStatus("비밀번호는 공백으로만 설정할 수 없습니다.", "danger");
				password.focus();
				return;
			}

			if (passwordRequested && newPassword !== confirmedPassword) {
				showStatus("새 비밀번호와 비밀번호 확인이 일치하지 않습니다.", "danger");
				passwordConfirm.focus();
				return;
			}

			if (passwordRequested && utf8ByteLength(newPassword) > 72) {
				showStatus("비밀번호는 UTF-8 기준 72바이트 이하로 입력해 주세요.", "danger");
				password.focus();
				return;
			}

			setSaving(true);
			showStatus("변경사항을 저장하는 중입니다.", "info");
			const sequence = ++requestSequence;

			try {
				const profile = await requestProfile({
					method: "POST",
					body: JSON.stringify({
						name: name.value.trim(),
						phone: phone.value.trim() || null,
						telephone: telephone.value.trim() || null,
						email: email.value.trim() || null,
						password: passwordRequested ? newPassword : null,
						passwordConfirm: passwordRequested ? confirmedPassword : null
					})
				});

				if (sequence !== requestSequence) return;
				fillProfile(profile);
				form.classList.remove("was-validated");
				showStatus("개인정보가 저장되었습니다.", "success");
			} catch (error) {
				if (sequence !== requestSequence) return;
				showStatus(error.message || "개인정보 저장에 실패했습니다.", "danger");
			} finally {
				if (sequence === requestSequence) setSaving(false);
			}
		});
	}

	function init() {
		bindPasswordToggles();
		initProfileModal();
	}

	if (document.readyState === "loading") {
		document.addEventListener("DOMContentLoaded", init, { once: true });
	} else {
		init();
	}
})();
