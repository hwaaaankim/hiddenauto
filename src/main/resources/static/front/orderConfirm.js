document.addEventListener("DOMContentLoaded", () => {
	const orderContainer = document.getElementById("orderContainer");
	const totalAmountElem = document.getElementById("total-amount");
	const pointUsageElem = document.getElementById("point-usage");
	const finalAmountElem = document.getElementById("final-amount");
	const pointInput = document.getElementById("point-input");
	const applyButton = document.getElementById("apply-button");
	const cancelButton = document.getElementById("cancel-button");
	const shippingAmountElem = document.getElementById("shipping-amount");

	const orderSource = window.orderSource || 'cart';
	const cart = window.cart || [];

	let appliedPoint = 0;
	let unloadConfirm = true;

	// ✅ 공통 주소/배송 입력만 사용
	const mainAddr = document.getElementById("main-address");
	const detailAddr = document.getElementById("main-detail");
	const doInput = document.getElementById("main-do");
	const siInput = document.getElementById("main-si");
	const guInput = document.getElementById("main-gu");
	const zipInput = document.getElementById("main-zipcode");
	const deliverySelect = document.getElementById("delivery-method");
	const deliveryDate = document.getElementById("delivery-date");

	const pointLimit = typeof userPoint !== 'undefined' ? userPoint : 0;
	const dmList = (typeof deliveryMethods !== 'undefined' && Array.isArray(deliveryMethods)) ? deliveryMethods : [];

	document.getElementById("user-point-view").innerText = `${Number(pointLimit || 0).toLocaleString()} 원`;

	function getCategoryLabel(optionJson) {
		return optionJson["카테고리"] || optionJson?.category?.label || '';
	}

	// =========================
	// 이탈 경고/직접구매 beacon 삭제 로직 유지
	// =========================
	if (orderSource === 'direct' || orderSource === 'cart') {
		window.addEventListener('beforeunload', (e) => {
			if (unloadConfirm) {
				e.preventDefault();
				e.returnValue = '';
			}
		});

		if (orderSource === 'direct') {
			window.addEventListener('unload', () => {
				if (!unloadConfirm) return;

				const cartIds = (window.cart || []).map(item => item.id);
				if (cartIds.length > 0) {
					const blob = new Blob([JSON.stringify(cartIds)], { type: 'application/json' });
					navigator.sendBeacon('/api/v2/cartDeleteAll', blob);
				}
			});
		}
	}

	// =========================
	// 리드타임(기존 로직 유지)
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
		const m = String(d.getMonth() + 1).padStart(2, '0');
		const dd = String(d.getDate()).padStart(2, '0');
		return `${y}-${m}-${dd}`;
	}

	// =========================
	// 금액 계산
	// =========================
	function calculateTotalAmount() {
		return cart.reduce((sum, item) => sum + (item.quantity || 1) * (item.price || 10000), 0);
	}

	// ✅ 배송비는 “공통 배송수단 1개” 기준으로만 계산
	function calculateTotalShipping() {
		const methodId = deliverySelect?.value;
		if (!methodId) return 0;

		const method = dmList.find(m => String(m.id) === String(methodId));
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
			opt.text = `${method.methodName} (금액: ${method.methodPrice})`;
			deliverySelect.appendChild(opt);
		});
	}

	// =========================
	// 공통 배송희망일 min 설정
	// =========================
	function setGlobalDeliveryMinDate() {
		if (!deliveryDate) return;
		deliveryDate.min = getAdjustedLeadDate();
	}

	// =========================
	// 주문 아이템 렌더 (✅ 주소 입력 UI 제거)
	// =========================
	function renderOrderItems() {
		if (!cart.length) {
			alert('발주 정보가 없습니다. 주문을 시작 해 주세요.');
			location.href = '/index';
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
							<p class="mt-n2 mb-1"><strong class="color-theme">${categoryLabel}</strong></p>
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
	// 공통 주소 입력 바인딩
	// =========================
	document.getElementById("same-address").addEventListener("change", function () {
		if (this.checked) {
			mainAddr.value = (companyAddress && companyAddress.main) ? companyAddress.main : "";
			detailAddr.value = (companyAddress && companyAddress.detail) ? companyAddress.detail : "";
			doInput.value = (companyAddress && companyAddress.doName) ? companyAddress.doName : "";
			siInput.value = (companyAddress && companyAddress.siName) ? companyAddress.siName : "";
			guInput.value = (companyAddress && companyAddress.guName) ? companyAddress.guName : "";
			zipInput.value = (companyAddress && companyAddress.zipCode) ? companyAddress.zipCode : "";
		} else {
			mainAddr.value = "";
			detailAddr.value = "";
			doInput.value = "";
			siInput.value = "";
			guInput.value = "";
			zipInput.value = "";
		}
	});

	document.getElementById("main-addr-search").addEventListener("click", () => {
		new daum.Postcode({
			oncomplete: function (data) {
				mainAddr.value = data.roadAddress || data.jibunAddress || "";
				detailAddr.value = "";
				doInput.value = data.sido || "";
				siInput.value = data.sigungu || "";
				guInput.value = data.bname || "";
				zipInput.value = data.zonecode || "";
			}
		}).open();
	});

	// ✅ 공통 배송수단 변경 시 배송비/최종금액 반영
	if (deliverySelect) {
		deliverySelect.addEventListener("change", () => {
			updateAmounts();
		});
	}

	// =========================
	// 공통 입력 검증(✅ 제품별 검증 제거)
	// =========================
	function validateDeliveryInputs() {
		const commonFields = [
			{ el: mainAddr, label: '공통 주소' },
			{ el: detailAddr, label: '공통 상세주소' },
			{ el: zipInput, label: '공통 우편번호' },
			{ el: deliverySelect, label: '공통 배송수단' },
			{ el: deliveryDate, label: '공통 배송희망일' }
		];

		for (const f of commonFields) {
			if (!f.el || !String(f.el.value || '').trim()) {
				alert(`${f.label}를 입력해주세요.`);
				f.el?.focus();
				return false;
			}
		}

		return true;
	}

	function getDeliveryPriceFromCommon() {
		const selectedId = deliverySelect?.value;
		const method = dmList.find(m => String(m.id) === String(selectedId));
		return method?.methodPrice || 0;
	}

	// =========================
	// 발주하기 클릭(✅ 모든 item에 동일 주소/배송정보를 채워서 전송)
	// =========================
	document.getElementById("orderConfirmButton").addEventListener("click", () => {
		if (!validateDeliveryInputs()) return;

		const commonAddressInfo = {
			preferredDeliveryDate: deliveryDate.value || "",
			mainAddress: mainAddr.value || "",
			detailAddress: detailAddr.value || "",
			zipCode: zipInput.value || "",
			doName: doInput.value || "",
			siName: siInput.value || "",
			guName: guInput.value || "",
			deliveryMethodId: deliverySelect.value ? Number(deliverySelect.value) : null,
			deliveryPrice: getDeliveryPriceFromCommon()
		};

		const orderData = [];
		cart.forEach((item) => {
			orderData.push({
				cartId: item.id, // ✅ 오직 cartId만 사용
				...commonAddressInfo
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
				if (!res.ok) throw new Error("서버 오류 발생");
				return res.text();
			})
			.then(msg => {
				hidePreloader();
				alert(msg);
				location.href = "/index";
			})
			.catch(err => {
				hidePreloader();
				alert("발주 처리 중 오류 발생: " + err.message);
			});
	});

	// =========================
	// 세션 중복 방지 로직(기존 유지)
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
	updateAmounts();
	hidePreloader();
});
