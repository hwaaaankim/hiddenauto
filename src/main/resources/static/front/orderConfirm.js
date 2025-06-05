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
	let cart;
	if (orderSource === 'cart') {
		cart = JSON.parse(localStorage.getItem('pendingCart')) || [];
	} else if (orderSource === 'direct') {
		cart = JSON.parse(localStorage.getItem('direct')) || [];
	} else {
		cart = [];
	}

	let appliedPoint = 0;
	let unloadConfirm = true;

	const mainAddr = document.getElementById("main-address");
	const detailAddr = document.getElementById("main-detail");
	const doInput = document.getElementById("main-do");
	const siInput = document.getElementById("main-si");
	const guInput = document.getElementById("main-gu");
	const zipInput = document.getElementById("main-zipcode");
	const deliverySelect = document.getElementById("delivery-method");
	const deliveryDate = document.getElementById("delivery-date");
	const pointLimit = typeof userPoint !== 'undefined' ? userPoint : 10000;
	document.getElementById("user-point-view").innerText = `${pointLimit.toLocaleString()} 원`;

	if (orderSource === 'direct') {
		// direct 주문은 페이지 나가기 경고
		window.addEventListener('beforeunload', (e) => {
			if (unloadConfirm) {
				e.preventDefault();
				e.returnValue = '';
			}
		});
		if (cart.length > 0) {
			window.directOrderData = JSON.parse(JSON.stringify(cart));
			localStorage.removeItem('direct');
		}
	}
	
	if (orderSource === 'cart') {
		if (cart.length > 0) {
			window.pendingOrderData = JSON.parse(JSON.stringify(cart));
			window.addEventListener('beforeunload', () => {
				localStorage.removeItem('pendingCart'); // cart는 confirm 없음
			});
		}
	}

	function getCategoryLabel(optionJson) {
		return optionJson["카테고리"] || optionJson?.category?.label || '';
	}


	function getAdjustedLeadDate() {
		const now = new Date();
		const currentHour = now.getHours();
		const leadTime = currentHour < 14 ? 2 : 3;
	
		let addedDays = 0;
		let deliveryDate = new Date();
	
		while (addedDays < leadTime) {
			deliveryDate.setDate(deliveryDate.getDate() + 1);
			const day = deliveryDate.getDay();
			if (day !== 0 && day !== 6) {
				// 평일만 카운트
				addedDays++;
			}
		}
	
		// 마지막 도착일이 주말이면 다시 평일로 밀기
		while (deliveryDate.getDay() === 0 || deliveryDate.getDay() === 6) {
			deliveryDate.setDate(deliveryDate.getDate() + 1);
		}
	
		const y = deliveryDate.getFullYear();
		const m = String(deliveryDate.getMonth() + 1).padStart(2, '0');
		const d = String(deliveryDate.getDate()).padStart(2, '0');
		return `${y}-${m}-${d}`;
	}


	function calculateTotalAmount() {
		return cart.reduce((sum, item) => sum + (item.quantity || 1) * (item.price || 10000), 0);
	}

	function updateAmounts(pointUsage = appliedPoint) {
		const totalAmount = calculateTotalAmount();
		const shippingAmount = calculateTotalShipping();
		const finalAmount = totalAmount - pointUsage + shippingAmount;
	
		totalAmountElem.innerText = `${totalAmount.toLocaleString()} 원`;
		pointUsageElem.innerText = `${pointUsage.toLocaleString()} 원`;
		shippingAmountElem.innerText = `${shippingAmount.toLocaleString()} 원`;
		finalAmountElem.innerText = `${Math.max(finalAmount, 0).toLocaleString()} 원`;
	}

	function calculateTotalShipping() {
		let totalShipping = 0;
		const allToggles = [...document.querySelectorAll('.address-toggle')];
		const useCommon = allToggles.some(input => !input.checked);
	
		allToggles.forEach((toggle, index) => {
			if (toggle.checked) {
				// 개별 배송 수단
				const methodId = document.getElementById(`delivery-method-${index}`)?.value;
				const method = deliveryMethods.find(m => m.id.toString() === methodId);
				if (method) totalShipping += method.methodPrice || 0;
			}
		});
	
		if (useCommon) {
			const commonMethodId = document.getElementById("delivery-method")?.value;
			const method = deliveryMethods.find(m => m.id.toString() === commonMethodId);
			if (method) totalShipping += method.methodPrice || 0;
		}
	
		return totalShipping;
	}

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
			alert(`최대 ${pointLimit.toLocaleString()} 포인트까지 사용할 수 있습니다.`);
			pointUsage = pointLimit;
			pointInput.value = pointLimit; // ✅ 강제 조정
		}

		const totalAmount = calculateTotalAmount();
		if (pointUsage > totalAmount) {
			pointUsage = totalAmount;
			pointInput.value = totalAmount; // ✅ 금액 초과 시 자동 조정
		}

		appliedPoint = pointUsage;
		updateAmounts(appliedPoint);
	}


	function resetPointUsage() {
		pointInput.value = "";
		appliedPoint = 0;
		updateAmounts(0);
	}

	function renderDeliveryMethods() {
		deliverySelect.innerHTML = `<option value="">=== 배송수단 선택 ===</option>`;
		deliveryMethods.forEach(method => {
			const opt = document.createElement("option");
			opt.value = method.id;
			opt.text = `${method.methodName} (금액: ${method.methodPrice})`;
			deliverySelect.appendChild(opt);
		});
	}

	function setGlobalDeliveryMinDate() {
		deliveryDate.min = getAdjustedLeadDate(); // ✅ 현재 시간 기준으로 리드타임 반영
	}

	function renderOrderItems() {
		if (!cart.length) {
			alert('발주 정보가 없습니다. 주문을 시작 해 주세요.');
			location.href = '/index';
			return;
		}

		let orderHTML = "";
		cart.forEach((item, index) => {
			const { optionJson, quantity, price } = item;
			const itemPrice = price || 10000;
			const totalPrice = itemPrice * (quantity || 1);
			const categoryLabel = getCategoryLabel(optionJson);
			const minDate = getAdjustedLeadDate(); // ✅ 현재 시간 기준

			orderHTML += `
				<div class="mb-4">
					<div class="row vertical-center" style="gap:20px;">
						<div class="col-auto">
							<img src="/front/images/pictures/10s.jpg" class="rounded-m shadow-xl" width="80">
						</div>
						<div class="col-auto">
							<span class="font-11">제품분류</span>
							<p class="mt-n2 mb-1"><strong class="color-theme">${categoryLabel}</strong></p>
						</div>
						<div class="col-auto">
							<span class="font-11">수량</span>
							<p class="mt-n2 mb-1"><strong class="color-theme">${quantity || 1} 개</strong></p>
						</div>
						<div class="col-auto">
							<span class="font-11">제품금액</span>
							<p class="mt-n2 mb-1"><strong class="color-theme">${itemPrice.toLocaleString()} 원</strong></p>
						</div>
						<div class="col-auto">
							<span class="font-11">총 금액</span>
							<p class="mt-n2 mb-1"><strong class="color-theme total-amount-with-shipping" id="total-with-shipping-${index}">${totalPrice.toLocaleString()} 원</strong></p>
						</div>
						<div class="col-auto address-container">
							<label class="switch-label">배송지 별도 입력
								<label class="switch">
									<input type="checkbox" class="address-toggle" data-index="${index}">
									<span class="slider"></span>
								</label>
							</label>
						</div>
					</div>
					<div class="hidden-section" id="hidden-section-${index}" style="display:none; overflow:hidden; height:0;">
						<div class="input-group">
							<input type="text" placeholder="주소를 검색해 주세요." class="address-input" id="addr-main-${index}" readonly />
							<button type="button" class="addr-search-btn" data-index="${index}">주소검색</button>
						</div>
						<div class="input-group mt-1">
							<input type="text" placeholder="상세주소를 입력 해 주세요." class="detail-address-input" id="addr-detail-${index}" />
						</div>
						<input type="hidden" class="do-input" id="addr-do-${index}" />
						<input type="hidden" class="si-input" id="addr-si-${index}" />
						<input type="hidden" class="gu-input" id="addr-gu-${index}" />
						<input type="hidden" class="zipcode-input" id="addr-zipcode-${index}" />
						<div class="input-group mt-2">
							<div class="form-item">
								<label>배송수단 선택</label>
								<select class="delivery-method-select" id="delivery-method-${index}">
									<option value="">=== 배송수단 선택 ===</option>
									${deliveryMethods.map(method => `<option value="${method.id}">${method.methodName} (금액: ${method.methodPrice})</option>`).join('')}
								</select>
							</div>
							<div class="form-item">
								<label>배송 희망일 선택</label>
								<input type="date" id="delivery-date-${index}" min="${minDate}" />
							</div>
						</div>
					</div>
					<div class="divider"></div>
				</div>`;
		});
		orderContainer.innerHTML = orderHTML;

		// 바인딩
		bindAddressToggles();
		bindAddressSearches();
		bindDeliverySelects();
	}

	function updatePaymentInfoSectionVisibility() {
		const allToggles = [...document.querySelectorAll('.address-toggle')];
		const allChecked = allToggles.every(input => input.checked);
		const someChecked = allToggles.some(input => input.checked);
		const paymentSection = document.querySelector('.payment-check-container');

		if (allChecked) {
			paymentSection.style.display = 'none';
		} else {
			paymentSection.style.display = 'block';
		}
	}

	function bindAddressToggles() {
		document.querySelectorAll(".address-toggle").forEach(toggle => {
			toggle.addEventListener("change", (e) => {
				const index = e.target.dataset.index;
				const section = document.getElementById(`hidden-section-${index}`);
				if (e.target.checked) {
					section.style.display = "block";
					section.style.height = section.scrollHeight + "px";
				} else {
					section.style.height = section.scrollHeight + "px";
					setTimeout(() => section.style.height = "0", 10);
				}
				updatePaymentInfoSectionVisibility();
			});
		});
		document.querySelectorAll(".hidden-section").forEach(section => {
			section.addEventListener("transitionend", () => {
				if (section.style.height === "0px") section.style.display = "none";
				else section.style.height = "auto";
			});
		});
	}

	function bindAddressSearches() {
		document.querySelectorAll(".addr-search-btn").forEach(btn => {
			btn.addEventListener("click", (e) => {
				const index = e.target.dataset.index;
				new daum.Postcode({
					oncomplete: function(data) {
						document.getElementById(`addr-main-${index}`).value = data.roadAddress || data.jibunAddress || "";
						document.getElementById(`addr-detail-${index}`).value = "";
						document.getElementById(`addr-do-${index}`).value = data.sido || "";
						document.getElementById(`addr-si-${index}`).value = data.sigungu || "";
						document.getElementById(`addr-gu-${index}`).value = data.bname || "";
						document.getElementById(`addr-zipcode-${index}`).value = data.zonecode || "";
					}
				}).open();
			});
		});
	}

	// 로더 표시 함수
	function showPreloader() {
		const preloader = document.getElementById("preloader");
		if (preloader) preloader.classList.remove("preloader-hide");
	}

	// 로더 숨김 함수
	function hidePreloader() {
		const preloader = document.getElementById("preloader");
		if (preloader) preloader.classList.add("preloader-hide");
	}

	function bindDeliverySelects() {
		document.querySelectorAll(".delivery-method-select").forEach(select => {
			select.addEventListener("change", (e) => {
				const index = e.target.id.split("-").pop();
				const method = deliveryMethods.find(m => m.id.toString() === e.target.value);
				const item = cart[index];
				const base = (item.quantity || 1) * (item.price || 10000);
				document.getElementById(`total-with-shipping-${index}`).innerText = `${(base + (method?.methodPrice || 0)).toLocaleString()} 원`;
				updateAmounts(); // ✅ 배송비 변경 시 반영
			});
		});
	
		// 공통 배송수단
		const commonSelect = document.getElementById("delivery-method");
		if (commonSelect) {
			commonSelect.addEventListener("change", () => {
				updateAmounts(); // ✅ 반영
			});
		}
	}


	// 초기 실행
	pointInput.addEventListener("input", handlePointInput);
	applyButton.addEventListener("click", applyPointUsage);
	cancelButton.addEventListener("click", resetPointUsage);

	document.getElementById("same-address").addEventListener("change", function() {
		if (this.checked) {
			mainAddr.value = companyAddress.main;
			detailAddr.value = companyAddress.detail;
			doInput.value = companyAddress.doName;
			siInput.value = companyAddress.siName;
			guInput.value = companyAddress.guName;
			zipInput.value = companyAddress.zipCode;
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
			oncomplete: function(data) {
				mainAddr.value = data.roadAddress || data.jibunAddress || "";
				detailAddr.value = "";
				doInput.value = data.sido || "";
				siInput.value = data.sigungu || "";
				guInput.value = data.bname || "";
				zipInput.value = data.zonecode || "";
			}
		}).open();
	});
	document.getElementById("orderConfirmButton").addEventListener("click", () => {
		if (!validateDeliveryInputs()) return; // ✅ 배송지 검증 추가
		const getDateVal = id => document.getElementById(id)?.value || "";
		const allToggles = [...document.querySelectorAll('.address-toggle')];
		const useCommon = allToggles.some(input => !input.checked); // 일부만 배송지 별도 입력
		const orderData = [];

		cart.forEach((item, index) => {
			const isSeparate = document.querySelector(`#addr-main-${index}`)?.closest('.hidden-section')?.style.display === 'block';
			const getVal = id => document.getElementById(id)?.value || "";
			const getSelectedMethodId = id => document.getElementById(id)?.value || null;

			const methodSelectId = isSeparate ? `delivery-method-${index}` : `delivery-method`;

			const addressInfo = {
				preferredDeliveryDate: getDateVal(isSeparate ? `delivery-date-${index}` : `delivery-date`),
				mainAddress: getVal(isSeparate ? `addr-main-${index}` : `main-address`),
				detailAddress: getVal(isSeparate ? `addr-detail-${index}` : `main-detail`),
				zipCode: getVal(isSeparate ? `addr-zipcode-${index}` : `main-zipcode`),
				doName: getVal(isSeparate ? `addr-do-${index}` : `main-do`),
				siName: getVal(isSeparate ? `addr-si-${index}` : `main-si`),
				guName: getVal(isSeparate ? `addr-gu-${index}` : `main-gu`),
				deliveryMethodId: getSelectedMethodId(methodSelectId),
				deliveryPrice: getDeliveryPrice(methodSelectId)
			};
			orderData.push({
				quantity: item.quantity || 1,
				price: item.price || 10000,
				optionJson: item.optionJson || {},
				pointUsed: appliedPoint,
				...addressInfo
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
			
				if (orderSource === 'direct') {
					localStorage.removeItem('direct');
				} else if (orderSource === 'cart') {
					// 주문 완료 시만 cart에서 제거
					const fullCart = JSON.parse(localStorage.getItem('cart')) || [];
					const pending = window.pendingOrderData || [];
			
					const isSameOption = (a, b) =>
						JSON.stringify(a.optionJson) === JSON.stringify(b.optionJson);
			
					const updatedCart = fullCart.filter(cartItem =>
						!pending.some(pendingItem => isSameOption(cartItem, pendingItem))
					);
			
					localStorage.setItem('cart', JSON.stringify(updatedCart));
					localStorage.removeItem('pendingCart');
				}
			
				location.href = "/index";
			})
			.catch(err => {
				hidePreloader();
				alert("발주 처리 중 오류 발생: " + err.message);
			});
	});

	function getDeliveryPrice(selectId) {
		const selectedId = document.getElementById(selectId)?.value;
		const method = deliveryMethods.find(m => m.id.toString() === selectedId);
		return method?.methodPrice || 0;
	}

	function validateDeliveryInputs() {
		const allToggles = [...document.querySelectorAll('.address-toggle')];
		const hasCommonInput = allToggles.some(input => !input.checked);
		const totalOrders = allToggles.length;

		// ✅ 1. 오더별 별도 입력 검증
		for (let index = 0; index < totalOrders; index++) {
			const toggle = allToggles[index];
			if (!toggle.checked) continue; // 별도 입력 아님 → 건너뜀

			const requiredFields = [
				{ id: `addr-main-${index}`, label: '주소' },
				{ id: `addr-detail-${index}`, label: '상세주소' },
				{ id: `addr-zipcode-${index}`, label: '우편번호' },
				{ id: `delivery-method-${index}`, label: '배송수단' },
				{ id: `delivery-date-${index}`, label: '배송희망일' }
			];

			for (const field of requiredFields) {
				const el = document.getElementById(field.id);
				if (!el || !el.value?.trim()) {
					alert(`오더 ${index + 1}의 ${field.label}를 입력해주세요.`);
					el?.focus();
					return false;
				}
			}
		}

		// ✅ 2. 공통입력 검증 (배송지 직접입력 아닌 오더가 1개 이상 있을 때)
		if (hasCommonInput) {
			const commonFields = [
				{ id: 'main-address', label: '공통 주소' },
				{ id: 'main-detail', label: '공통 상세주소' },
				{ id: 'main-zipcode', label: '공통 우편번호' },
				{ id: 'delivery-method', label: '공통 배송수단' },
				{ id: 'delivery-date', label: '공통 배송희망일' }
			];

			for (const field of commonFields) {
				const el = document.getElementById(field.id);
				if (!el || !el.value?.trim()) {
					alert(`${field.label}를 입력해주세요.`);
					el?.focus();
					return false;
				}
			}
		}

		return true;
	}

	function getDeliveryMethodId(selectId) {
		const selectedId = document.getElementById(selectId)?.value;
		return selectedId ? parseInt(selectedId, 10) : null;
	}
	renderOrderItems();
	renderDeliveryMethods();
	setGlobalDeliveryMinDate();
	updatePaymentInfoSectionVisibility();
	updateAmounts();
	hidePreloader();
});
