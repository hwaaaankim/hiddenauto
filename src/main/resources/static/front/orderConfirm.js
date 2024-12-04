document.addEventListener("DOMContentLoaded", () => {
    const orderContainer = document.getElementById("orderContainer");
    const totalAmountElem = document.getElementById("total-amount");
    const pointUsageElem = document.getElementById("point-usage");
    const finalAmountElem = document.getElementById("final-amount");
    const pointInput = document.getElementById("point-input");
    const applyButton = document.getElementById("apply-button");
    const cancelButton = document.getElementById("cancel-button");

    const pointLimit = 10000; // 최대 포인트 사용 가능 금액
    const productPrice = 10000; // 개당 제품 가격
    const cart = JSON.parse(localStorage.getItem("cart")) || [];
    let appliedPoint = 0; // 적용된 포인트 금액

    // 총 금액 계산
    function calculateTotalAmount() {
        return cart.reduce((sum, item) => sum + (item.quantity || 1) * productPrice, 0);
    }

    // 금액 표시 업데이트
    function updateAmounts(pointUsage = appliedPoint) {
        const totalAmount = calculateTotalAmount();
        const finalAmount = totalAmount - pointUsage;

        totalAmountElem.innerText = `${totalAmount.toLocaleString()} 원`;
        pointUsageElem.innerText = `${pointUsage.toLocaleString()} 원`;
        finalAmountElem.innerText = `${Math.max(finalAmount, 0).toLocaleString()} 원`;
    }

    // 초기 렌더링
   function renderOrderItems() {
	    if (!cart.length) {
	        orderContainer.innerHTML = `
	            <div class="card card-style">
	                <div class="content">
	                    <h3>발주 정보가 없습니다.</h3>
	                    <p>장바구니에 제품을 추가한 후 다시 시도하세요.</p>
	                </div>
	            </div>`;
	        updateAmounts();
	        return;
	    }
	
	    let orderHTML = "";
	
	    cart.forEach((item, index) => {
	        orderHTML += `
	        <div class="mb-4">
	            <div class="row vertical-center" style="gap:20px;">
	                <div class="col-auto">
	                    <img src="/front/images/pictures/10s.jpg" class="rounded-m shadow-xl" width="80">
	                </div>
	                <div class="col-auto">
	                    <span class="font-11">배송 예정일</span>
	                    <p class="mt-n2 mb-1">
	                        <strong class="color-theme">0000-00-00</strong>
	                    </p>
	                </div>
	                <div class="col-auto">
	                    <span class="font-11">금액</span>
	                    <p class="mt-n2 mb-1">
	                        <strong class="color-theme">${(productPrice * (item.quantity || 1)).toLocaleString()} 원</strong>
	                    </p>
	                </div>
	                <div class="col-auto">
	                    <span class="font-11">수량</span>
	                    <p class="mt-n2 mb-1">
	                        <strong class="color-theme">${item.quantity || 1} 개</strong>
	                    </p>
	                </div>
	                <div class="col-auto">
	                    <span class="font-11">제품코드</span>
	                    <p class="mt-n2 mb-1">
	                        <strong class="color-theme">${item.code || "CODE"}</strong>
	                    </p>
	                </div>
	                <div class="col-auto">
	                    <span class="font-11">제품명</span>
	                    <p class="mt-n2 mb-1">
	                        <strong class="color-theme">${item.product || "NAME"}</strong>
	                    </p>
	                </div>
	                <div class="col-auto address-container">
	                    <label class="switch-label">
	                        배송지 별도 입력
	                        <label class="switch">
	                            <input type="checkbox" class="address-toggle" data-index="${index}">
	                            <span class="slider"></span>
	                        </label>
	                    </label>
	                </div>
	            </div>
	            <div class="hidden-section" id="hidden-section-${index}" style="display:none; overflow:hidden; height:0;">
	                <div class="input-style">
	                    <input type="text" placeholder="주소 입력">
	                    <button class="btn btn-sm rounded-m text-uppercase font-800">주소검색</button>
	                </div>
	                <div class="input-style">
	                    <input type="text" placeholder="상세 주소 입력">
	                </div>
	            </div>
	            <div class="divider"></div>
	        </div>`;
	    });
	
	    orderContainer.innerHTML = orderHTML;
	
	    // 스위치 이벤트 추가
	    document.querySelectorAll(".address-toggle").forEach((toggle) => {
	        toggle.addEventListener("change", (e) => {
	            const index = e.target.getAttribute("data-index");
	            const hiddenSection = document.getElementById(`hidden-section-${index}`);
	
	            if (e.target.checked) {
	                hiddenSection.style.display = "block"; // 우선 표시
	                const height = hiddenSection.scrollHeight; // 실제 높이 계산
	                hiddenSection.style.height = `${height}px`; // 높이 설정
	                hiddenSection.style.overflow = "hidden";
	            } else {
	                hiddenSection.style.height = `${hiddenSection.scrollHeight}px`; // 현재 높이로 고정
	                setTimeout(() => {
	                    hiddenSection.style.height = "0"; // 슬라이드 축소
	                }, 10); // 비동기 처리
	            }
	        });
	    });
	
	    // transitionend 이벤트로 상태 업데이트
	    document.querySelectorAll(".hidden-section").forEach((section) => {
	        section.addEventListener("transitionend", () => {
	            if (section.style.height === "0px") {
	                section.style.display = "none";
	            } else {
	                section.style.height = "auto"; // 높이를 자동으로 조정
	            }
	        });
	    });
	
	    updateAmounts();
	}



    // 포인트 입력 처리
    function handlePointInput() {
        const value = pointInput.value;
        if (!/^\d*$/.test(value)) {
            alert("숫자만 입력 가능합니다.");
            pointInput.value = value.replace(/\D/g, ""); // 숫자 외 제거
            return;
        }
    }

    // 포인트 적용
    function applyPointUsage() {
        let pointUsage = parseInt(pointInput.value, 10) || 0;

        if (pointUsage > pointLimit) {
            alert(`최대 ${pointLimit} 포인트까지 사용할 수 있습니다.`);
            pointUsage = pointLimit;
        }

        const totalAmount = calculateTotalAmount();
        if (pointUsage > totalAmount) {
            pointUsage = totalAmount;
        }

        appliedPoint = pointUsage;
        updateAmounts(appliedPoint);
    }

    // 초기화
    function resetPointUsage() {
        pointInput.value = "";
        appliedPoint = 0;
        updateAmounts(0);
    }

    // 이벤트 핸들러 등록
    pointInput.addEventListener("input", handlePointInput);
    applyButton.addEventListener("click", applyPointUsage);
    cancelButton.addEventListener("click", resetPointUsage);

    // 페이지 로드 시 렌더링
    renderOrderItems();
});
