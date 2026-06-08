document.addEventListener("DOMContentLoaded", () => {
	const orderContainer = document.getElementById("orderContainer");
	const totalAmountElem = document.getElementById("total-amount");
	const pointUsageElem = document.getElementById("point-usage");
	const finalAmountElem = document.getElementById("final-amount");
	const pointInput = document.getElementById("point-input");
	const applyButton = document.getElementById("apply-button");
	const cancelButton = document.getElementById("cancel-button");
	const shippingAmountElem = document.getElementById("shipping-amount");

	const orderSource = window.orderSource || "cart";
	const cart = window.cart || [];

	let appliedPoint = 0;
	let unloadConfirm = true;
	let addressModalTarget = "main";

	// =========================
	// 공통 주소/배송 입력
	// =========================
	const mainAddr = document.getElementById("main-address");
	const detailAddr = document.getElementById("main-detail");
	const doInput = document.getElementById("main-do");
	const siInput = document.getElementById("main-si");
	const guInput = document.getElementById("main-gu");
	const zipInput = document.getElementById("main-zipcode");
	const deliverySelect = document.getElementById("delivery-method");
	const deliveryDate = document.getElementById("delivery-date");

	// =========================
	// 현장주소 입력
	// =========================
	const siteAddressSection = document.getElementById("site-address-section");
	const siteSameAsMemberCheck = document.getElementById("site-same-as-member");
	const siteAddr = document.getElementById("site-address");
	const siteDetailAddr = document.getElementById("site-detail");
	const siteDoInput = document.getElementById("site-do");
	const siteSiInput = document.getElementById("site-si");
	const siteGuInput = document.getElementById("site-gu");
	const siteZipInput = document.getElementById("site-zipcode");
	const siteAddrSearchBtn = document.getElementById("site-addr-search");
	const siteAddressOpenModalBtn = document.getElementById("site-address-open-modal");

	// =========================
	// 주문자 정보 입력
	// =========================
	const ordererNameInput = document.getElementById("orderer-name");
	const ordererPhoneInput = document.getElementById("orderer-phone");
	const ordererSameAsMemberCheck = document.getElementById("orderer-same-as-member");

	const $openOrdererModalBtn = document.getElementById("order-confirm-orderer-open-modal");
	const $ordererModalOverlay = document.getElementById("order-confirm-orderer-modal-overlay");
	const $ordererModalClose = document.getElementById("order-confirm-orderer-modal-close");
	const $ordererList = document.getElementById("order-confirm-orderer-list");

	const memberInfo = (typeof loginMemberInfo !== "undefined" && loginMemberInfo)
		? loginMemberInfo
		: { name: "", phone: "" };

	const ordererInfos = (typeof companyOrdererInfos !== "undefined" && Array.isArray(companyOrdererInfos))
		? companyOrdererInfos
		: [];

	const pointLimit = typeof userPoint !== "undefined" ? userPoint : 0;
	const dmList = (typeof deliveryMethods !== "undefined" && Array.isArray(deliveryMethods)) ? deliveryMethods : [];

	// =========================
	// 배송지 선택 모달 DOM
	// =========================
	const $openAddressModalBtn = document.getElementById("order-confirm-added-open-address-modal");
	const $modalOverlay = document.getElementById("order-confirm-added-modal-overlay");
	const $modalClose = document.getElementById("order-confirm-added-modal-close");
	const $addressList = document.getElementById("order-confirm-added-address-list");

	const deliveryAddresses = (typeof companyDeliveryAddresses !== "undefined" && Array.isArray(companyDeliveryAddresses))
		? companyDeliveryAddresses
		: [];

	document.getElementById("user-point-view").innerText = `${Number(pointLimit || 0).toLocaleString()} 원`;

	function getCategoryLabel(optionJson) {
		return optionJson["카테고리"] || optionJson?.category?.label || "";
	}

	function escapeHtml(s) {
		return String(s ?? "")
			.replace(/&/g, "&amp;")
			.replace(/</g, "&lt;")
			.replace(/>/g, "&gt;")
			.replace(/"/g, "&quot;")
			.replace(/'/g, "&#039;");
	}

	function normalizeText(value) {
		return String(value || "").trim();
	}

	function getCompanyBaseAddress() {
		return {
			id: "member",
			label: "회원 주소",
			roadAddress: (companyAddress && companyAddress.main) ? companyAddress.main : "",
			detailAddress: (companyAddress && companyAddress.detail) ? companyAddress.detail : "",
			zipCode: (companyAddress && companyAddress.zipCode) ? companyAddress.zipCode : "",
			doName: (companyAddress && companyAddress.doName) ? companyAddress.doName : "",
			siName: (companyAddress && companyAddress.siName) ? companyAddress.siName : "",
			guName: (companyAddress && companyAddress.guName) ? companyAddress.guName : ""
		};
	}

	function getSelectedDeliveryMethod() {
		const selectedId = deliverySelect?.value;
		if (!selectedId) return null;
		return dmList.find(m => String(m.id) === String(selectedId)) || null;
	}

	function isSiteDeliverySelected() {
		const method = getSelectedDeliveryMethod();
		return normalizeText(method?.methodName) === "현장배송";
	}

	// =========================
	// 이탈 경고/직접구매 beacon 삭제 로직
	// =========================
	if (orderSource === "direct" || orderSource === "cart") {
		window.addEventListener("beforeunload", (e) => {
			if (unloadConfirm) {
				e.preventDefault();
				e.returnValue = "";
			}
		});

		if (orderSource === "direct") {
			window.addEventListener("unload", () => {
				if (!unloadConfirm) return;

				const cartIds = (window.cart || []).map(item => item.id);
				if (cartIds.length > 0) {
					const blob = new Blob([JSON.stringify(cartIds)], { type: "application/json" });
					navigator.sendBeacon("/api/v2/cartDeleteAll", blob);
				}
			});
		}
	}

	// =========================
	// 리드타임
	// =========================
	function getAdjustedLeadDate() {
		const now = new Date();
		const currentHour = now.getHours();
		const leadTime = currentHour < 14 ? 2 : 3;

		let addedDays = 0;
		let d = new Date();

		while (addedDays < leadTime) {
			d.setDate(d.getDate() + 1);
			const day = d.getDay();
			if (day !== 0 && day !== 6) {
				addedDays++;
			}
		}

		while (d.getDay() === 0 || d.getDay() === 6) {
			d.setDate(d.getDate() + 1);
		}

		const y = d.getFullYear();
		const m = String(d.getMonth() + 1).padStart(2, "0");
		const dd = String(d.getDate()).padStart(2, "0");
		return `${y}-${m}-${dd}`;
	}

	// =========================
	// 금액 계산
	// =========================
	function calculateTotalAmount() {
		return cart.reduce((sum, item) => sum + (item.quantity || 1) * (item.price || 10000), 0);
	}

	function calculateTotalShipping() {
		const method = getSelectedDeliveryMethod();
		return method ? (method.methodPrice || 0) : 0;
	}

	function updateAmounts(pointUsage = appliedPoint) {
		const totalAmount = calculateTotalAmount();
		const shippingAmount = calculateTotalShipping();
		const finalAmount = totalAmount - pointUsage + shippingAmount;

		totalAmountElem.innerText = `${totalAmount.toLocaleString()} 원`;
		pointUsageElem.innerText = `${Number(pointUsage || 0).toLocaleString()} 원`;
		shippingAmountElem.innerText = `${shippingAmount.toLocaleString()} 원`;
		finalAmountElem.innerText = `${Math.max(finalAmount, 0).toLocaleString()} 원`;
	}

	// =========================
	// 포인트 입력
	// =========================
	function handlePointInput() {
		const value = pointInput.value;
		if (!/^\d*$/.test(value)) {
			alert("숫자만 입력 가능합니다.");
			pointInput.value = value.replace(/\D/g, "");
		}
	}

	function applyPointUsage() {
		let pointUsage = parseInt(pointInput.value, 10) || 0;

		if (pointUsage > pointLimit) {
			alert(`최대 ${Number(pointLimit).toLocaleString()} 포인트까지 사용할 수 있습니다.`);
			pointUsage = pointLimit;
			pointInput.value = String(pointLimit);
		}

		const totalAmount = calculateTotalAmount();
		if (pointUsage > totalAmount) {
			pointUsage = totalAmount;
			pointInput.value = String(totalAmount);
		}

		appliedPoint = pointUsage;
		updateAmounts(appliedPoint);
	}

	function resetPointUsage() {
		pointInput.value = "";
		appliedPoint = 0;
		updateAmounts(0);
	}

	// =========================
	// 배송수단 렌더
	// =========================
	function renderDeliveryMethods() {
		if (!deliverySelect) return;

		deliverySelect.innerHTML = `<option value="">=== 배송수단 선택 ===</option>`;
		dmList.forEach(method => {
			const opt = document.createElement("option");
			opt.value = method.id;
			opt.text = `${method.methodName} (금액: ${Number(method.methodPrice || 0).toLocaleString()}원)`;
			deliverySelect.appendChild(opt);
		});
	}

	function setGlobalDeliveryMinDate() {
		if (!deliveryDate) return;
		deliveryDate.min = getAdjustedLeadDate();
	}

	function toggleSiteAddressSection() {
		if (!siteAddressSection) return;

		if (isSiteDeliverySelected()) {
			siteAddressSection.style.display = "";
		} else {
			siteAddressSection.style.display = "none";
			clearSiteAddressInputs();
		}
	}

	// =========================
	// 주문 아이템 렌더
	// =========================
	function renderOrderItems() {
		if (!cart.length) {
			alert("발주 정보가 없습니다. 주문을 시작 해 주세요.");
			location.href = "/index";
			return;
		}

		let orderHTML = "";
		cart.forEach((item) => {
			const { localizedOptionJson, quantity, price } = item;
			const itemPrice = price || 10000;
			const qty = quantity || 1;
			const totalPrice = itemPrice * qty;

			let parsedOptionJson = {};
			try {
				parsedOptionJson = localizedOptionJson ? JSON.parse(localizedOptionJson) : {};
			} catch (e) {
				parsedOptionJson = {};
			}

			const categoryLabel = getCategoryLabel(parsedOptionJson);

			orderHTML += `
				<div class="mb-4">
					<div class="row vertical-center" style="gap:20px;">
						<div class="col-auto">
							<span class="font-11">제품분류</span>
							<p class="mt-n2 mb-1"><strong class="color-theme">${escapeHtml(categoryLabel)}</strong></p>
						</div>
						<div class="col-auto">
							<span class="font-11">수량</span>
							<p class="mt-n2 mb-1"><strong class="color-theme">${qty} 개</strong></p>
						</div>
						<div class="col-auto">
							<span class="font-11">제품금액</span>
							<p class="mt-n2 mb-1"><strong class="color-theme">${itemPrice.toLocaleString()} 원</strong></p>
						</div>
						<div class="col-auto">
							<span class="font-11">총 금액</span>
							<p class="mt-n2 mb-1"><strong class="color-theme">${totalPrice.toLocaleString()} 원</strong></p>
						</div>
					</div>
					<div class="divider"></div>
				</div>`;
		});

		orderContainer.innerHTML = orderHTML;
	}

	// =========================
	// 로더
	// =========================
	function showPreloader() {
		const preloader = document.getElementById("preloader");
		if (preloader) preloader.classList.remove("preloader-hide");
	}

	function hidePreloader() {
		const preloader = document.getElementById("preloader");
		if (preloader) preloader.classList.add("preloader-hide");
	}

	// =========================
	// 주소 세팅
	// =========================
	function applyAddressToInputs(addr, target = "main") {
		if (target === "site") {
			siteAddr.value = addr.roadAddress || "";
			siteDetailAddr.value = addr.detailAddress || "";
			siteZipInput.value = addr.zipCode || "";
			siteDoInput.value = addr.doName || "";
			siteSiInput.value = addr.siName || "";
			siteGuInput.value = addr.guName || "";
			return;
		}

		mainAddr.value = addr.roadAddress || "";
		detailAddr.value = addr.detailAddress || "";
		zipInput.value = addr.zipCode || "";
		doInput.value = addr.doName || "";
		siInput.value = addr.siName || "";
		guInput.value = addr.guName || "";
	}

	function clearSiteAddressInputs() {
		if (siteSameAsMemberCheck) siteSameAsMemberCheck.checked = false;
		if (siteAddr) siteAddr.value = "";
		if (siteDetailAddr) siteDetailAddr.value = "";
		if (siteZipInput) siteZipInput.value = "";
		if (siteDoInput) siteDoInput.value = "";
		if (siteSiInput) siteSiInput.value = "";
		if (siteGuInput) siteGuInput.value = "";
	}

	function renderAddressModalList() {
		if (!$addressList) return;

		const base = getCompanyBaseAddress();

		const list = [base].concat(
			deliveryAddresses.map(a => ({
				id: a.id,
				label: "등록 배송지",
				roadAddress: a.roadAddress || "",
				detailAddress: a.detailAddress || "",
				zipCode: a.zipCode || "",
				doName: a.doName || "",
				siName: a.siName || "",
				guName: a.guName || ""
			}))
		);

		$addressList.innerHTML = "";

		list.forEach(item => {
			const line1 = item.roadAddress || "";
			const line2Parts = [];

			if (item.zipCode) line2Parts.push(`(${item.zipCode})`);
			if (item.doName || item.siName || item.guName) {
				line2Parts.push(`${item.doName || ""} ${item.siName || ""} ${item.guName || ""}`.trim());
			}
			if (item.detailAddress) line2Parts.push(item.detailAddress);

			const line2 = line2Parts.join(" · ");

			const wrap = document.createElement("div");
			wrap.className = "order-confirm-added-address-item";

			const text = document.createElement("div");
			text.className = "order-confirm-added-address-text";

			const t1 = document.createElement("div");
			t1.className = "order-confirm-added-address-line1 order-confirm-added-address-badge";
			if (item.id === "member") {
				t1.innerHTML = `${escapeHtml(line1)} <sup>회원 주소</sup>`;
			} else {
				t1.textContent = line1;
			}

			const t2 = document.createElement("div");
			t2.className = "order-confirm-added-address-line2";
			t2.textContent = line2;

			text.appendChild(t1);
			text.appendChild(t2);

			const btn = document.createElement("button");
			btn.type = "button";
			btn.className = "order-confirm-added-address-select-btn";
			btn.textContent = "선택";
			btn.addEventListener("click", () => {
				applyAddressToInputs(item, addressModalTarget);

				if (addressModalTarget === "site" && siteSameAsMemberCheck) {
					siteSameAsMemberCheck.checked = item.id === "member";
				}

				closeAddressModal();
			});

			wrap.appendChild(text);
			wrap.appendChild(btn);

			$addressList.appendChild(wrap);
		});
	}

	function openAddressModal(target = "main") {
		if (!$modalOverlay) return;

		addressModalTarget = target;
		renderAddressModalList();
		$modalOverlay.classList.add("order-confirm-added-open");
		$modalOverlay.setAttribute("aria-hidden", "false");
	}

	function closeAddressModal() {
		if (!$modalOverlay) return;
		$modalOverlay.classList.remove("order-confirm-added-open");
		$modalOverlay.setAttribute("aria-hidden", "true");
	}

	function openDaumPostcode(target = "main") {
		new daum.Postcode({
			oncomplete: function(data) {
				const road = data.roadAddress || data.jibunAddress || "";

				applyAddressToInputs({
					roadAddress: road,
					detailAddress: "",
					doName: data.sido || "",
					siName: data.sigungu || "",
					guName: data.bname || "",
					zipCode: data.zonecode || ""
				}, target);

				if (target === "site") {
					if (siteSameAsMemberCheck) siteSameAsMemberCheck.checked = false;
					siteDetailAddr.focus();
				} else {
					detailAddr.focus();
				}
			}
		}).open();
	}

	// =========================
	// 주문자 정보 세팅
	// =========================
	function applyOrdererToInputs(orderer) {
		ordererNameInput.value = orderer.ordererName || "";
		ordererPhoneInput.value = orderer.phone || orderer.ordererPhone || "";
	}

	function clearOrdererInputs() {
		ordererNameInput.value = "";
		ordererPhoneInput.value = "";
	}

	function renderOrdererModalList() {
		if (!$ordererList) return;

		$ordererList.innerHTML = "";

		if (!ordererInfos.length) {
			const empty = document.createElement("div");
			empty.className = "order-confirm-added-address-item";
			empty.textContent = "등록된 주문자 정보가 없습니다.";
			$ordererList.appendChild(empty);
			return;
		}

		ordererInfos.forEach(item => {
			const wrap = document.createElement("div");
			wrap.className = "order-confirm-added-address-item";

			const text = document.createElement("div");
			text.className = "order-confirm-added-address-text";

			const t1 = document.createElement("div");
			t1.className = "order-confirm-added-address-line1";
			t1.textContent = item.ordererName || "-";

			const t2 = document.createElement("div");
			t2.className = "order-confirm-added-address-line2";
			t2.textContent = item.phone || "-";

			text.appendChild(t1);
			text.appendChild(t2);

			const btn = document.createElement("button");
			btn.type = "button";
			btn.className = "order-confirm-added-address-select-btn";
			btn.textContent = "선택";
			btn.addEventListener("click", () => {
				if (ordererSameAsMemberCheck) {
					ordererSameAsMemberCheck.checked = false;
				}
				applyOrdererToInputs(item);
				closeOrdererModal();
			});

			wrap.appendChild(text);
			wrap.appendChild(btn);
			$ordererList.appendChild(wrap);
		});
	}

	function openOrdererModal() {
		if (!$ordererModalOverlay) return;
		renderOrdererModalList();
		$ordererModalOverlay.classList.add("order-confirm-added-open");
		$ordererModalOverlay.setAttribute("aria-hidden", "false");
	}

	function closeOrdererModal() {
		if (!$ordererModalOverlay) return;
		$ordererModalOverlay.classList.remove("order-confirm-added-open");
		$ordererModalOverlay.setAttribute("aria-hidden", "true");
	}

	// =========================
	// 이벤트 바인딩
	// =========================
	if ($openAddressModalBtn) {
		$openAddressModalBtn.addEventListener("click", () => openAddressModal("main"));
	}

	if (siteAddressOpenModalBtn) {
		siteAddressOpenModalBtn.addEventListener("click", () => openAddressModal("site"));
	}

	if ($modalClose) {
		$modalClose.addEventListener("click", closeAddressModal);
	}

	if ($modalOverlay) {
		$modalOverlay.addEventListener("click", (e) => {
			if (e.target === $modalOverlay) closeAddressModal();
		});
	}

	if ($openOrdererModalBtn) {
		$openOrdererModalBtn.addEventListener("click", openOrdererModal);
	}

	if ($ordererModalClose) {
		$ordererModalClose.addEventListener("click", closeOrdererModal);
	}

	if ($ordererModalOverlay) {
		$ordererModalOverlay.addEventListener("click", (e) => {
			if (e.target === $ordererModalOverlay) closeOrdererModal();
		});
	}

	if (ordererSameAsMemberCheck) {
		ordererSameAsMemberCheck.addEventListener("change", () => {
			if (ordererSameAsMemberCheck.checked) {
				applyOrdererToInputs({
					ordererName: memberInfo.name || "",
					phone: memberInfo.phone || ""
				});
			} else {
				clearOrdererInputs();
			}
		});
	}

	if (siteSameAsMemberCheck) {
		siteSameAsMemberCheck.addEventListener("change", () => {
			if (siteSameAsMemberCheck.checked) {
				applyAddressToInputs(getCompanyBaseAddress(), "site");
			} else {
				clearSiteAddressInputs();
			}
		});
	}

	[ordererNameInput, ordererPhoneInput].forEach(el => {
		if (!el) return;
		el.addEventListener("input", () => {
			if (!ordererSameAsMemberCheck || !ordererSameAsMemberCheck.checked) return;

			const isStillSame =
				normalizeText(ordererNameInput.value) === normalizeText(memberInfo.name) &&
				normalizeText(ordererPhoneInput.value) === normalizeText(memberInfo.phone);

			if (!isStillSame) {
				ordererSameAsMemberCheck.checked = false;
			}
		});
	});

	if (siteDetailAddr) {
		siteDetailAddr.addEventListener("input", () => {
			if (siteSameAsMemberCheck) {
				siteSameAsMemberCheck.checked = false;
			}
		});
	}

	document.getElementById("main-addr-search").addEventListener("click", () => {
		openDaumPostcode("main");
	});

	if (siteAddrSearchBtn) {
		siteAddrSearchBtn.addEventListener("click", () => {
			openDaumPostcode("site");
		});
	}

	if (deliverySelect) {
		deliverySelect.addEventListener("change", () => {
			updateAmounts();
			toggleSiteAddressSection();
		});
	}

	// =========================
	// 입력 검증
	// =========================
	function validateDeliveryInputs() {
		const ordererFields = [
			{ el: ordererNameInput, label: "주문자 이름" },
			{ el: ordererPhoneInput, label: "주문자 연락처" }
		];

		for (const f of ordererFields) {
			if (!f.el || !normalizeText(f.el.value)) {
				alert(`${f.label}을 입력해주세요.`);
				f.el?.focus();
				return false;
			}
		}

		const commonFields = [
			{ el: mainAddr, label: "공통 주소" },
			{ el: detailAddr, label: "공통 상세주소" },
			{ el: zipInput, label: "공통 우편번호" },
			{ el: deliverySelect, label: "공통 배송수단" },
			{ el: deliveryDate, label: "공통 배송희망일" }
		];

		for (const f of commonFields) {
			if (!f.el || !normalizeText(f.el.value)) {
				alert(`${f.label}를 입력해주세요.`);
				f.el?.focus();
				return false;
			}
		}

		if (isSiteDeliverySelected()) {
			const siteFields = [
				{ el: siteAddr, label: "현장주소" },
				{ el: siteDetailAddr, label: "현장 상세주소" },
				{ el: siteZipInput, label: "현장 우편번호" }
			];

			for (const f of siteFields) {
				if (!f.el || !normalizeText(f.el.value)) {
					alert(`${f.label}를 입력해주세요.`);
					f.el?.focus();
					return false;
				}
			}
		}

		return true;
	}

	function getDeliveryPriceFromCommon() {
		const method = getSelectedDeliveryMethod();
		return method?.methodPrice || 0;
	}

	// =========================
	// 발주하기
	// =========================
	document.getElementById("orderConfirmButton").addEventListener("click", () => {
		if (!validateDeliveryInputs()) return;

		const siteDelivery = isSiteDeliverySelected();

		const commonOrderInfo = {
			ordererName: normalizeText(ordererNameInput.value),
			ordererPhone: normalizeText(ordererPhoneInput.value),
			preferredDeliveryDate: deliveryDate.value || "",

			mainAddress: mainAddr.value || "",
			detailAddress: detailAddr.value || "",
			zipCode: zipInput.value || "",
			doName: doInput.value || "",
			siName: siInput.value || "",
			guName: guInput.value || "",

			siteAddress: siteDelivery ? (siteAddr.value || "") : "",
			siteDetailAddress: siteDelivery ? (siteDetailAddr.value || "") : "",
			siteZipCode: siteDelivery ? (siteZipInput.value || "") : "",
			siteDoName: siteDelivery ? (siteDoInput.value || "") : "",
			siteSiName: siteDelivery ? (siteSiInput.value || "") : "",
			siteGuName: siteDelivery ? (siteGuInput.value || "") : "",

			deliveryMethodId: deliverySelect.value ? Number(deliverySelect.value) : null,
			deliveryPrice: getDeliveryPriceFromCommon()
		};

		const orderData = [];
		cart.forEach((item) => {
			orderData.push({
				cartId: item.id,
				...commonOrderInfo
			});
		});

		showPreloader();
		unloadConfirm = false;

		const payload = {
			items: orderData,
			pointUsed: appliedPoint
		};

		fetch("/api/order/submit", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(payload)
		})
			.then(res => {
				if (!res.ok) {
					return res.text().then(text => {
						throw new Error(text || "서버 오류 발생");
					});
				}
				return res.text();
			})
			.then(msg => {
				hidePreloader();
				alert(msg);
				location.href = "/index";
			})
			.catch(err => {
				hidePreloader();
				unloadConfirm = true;
				alert("발주 처리 중 오류 발생: " + err.message);
			});
	});

	// =========================
	// 세션 중복 방지 로직
	// =========================
	const cartIds = (window.cart || []).map(item => item.id);
	const stored = sessionStorage.getItem("lastCartIds");

	if (stored) {
		const lastCartIds = JSON.parse(stored);

		const isSame = cartIds.length === lastCartIds.length &&
			lastCartIds.every(id => cartIds.includes(id));

		if (isSame) {
			alert("해당 장바구니 정보는 만료되었습니다.\n다시 발주를 진행해주세요.");
			sessionStorage.removeItem("lastCartIds");
			location.href = "/cart";
			return;
		}
	}

	if (cartIds.length > 0) {
		sessionStorage.setItem("lastCartIds", JSON.stringify(cartIds));
	}

	// =========================
	// 초기 실행
	// =========================
	pointInput.addEventListener("input", handlePointInput);
	applyButton.addEventListener("click", applyPointUsage);
	cancelButton.addEventListener("click", resetPointUsage);

	renderOrderItems();
	renderDeliveryMethods();
	setGlobalDeliveryMinDate();
	toggleSiteAddressSection();
	updateAmounts();
	hidePreloader();
});