document.addEventListener("DOMContentLoaded", () => {
    const orderContainer = document.getElementById("orderContainer");
    const totalAmountElem = document.getElementById("total-amount");
    const pointUsageElem = document.getElementById("point-usage");
    const finalAmountElem = document.getElementById("final-amount");
    const pointInput = document.getElementById("point-input");
    const applyButton = document.getElementById("apply-button");
    const cancelButton = document.getElementById("cancel-button");

    const pointLimit = 10000;
    const productPrice = 10000;
    const orderSource = window.orderSource || 'cart';
    let cart = JSON.parse(localStorage.getItem(orderSource)) || [];
    let appliedPoint = 0;

    // 새로고침 경고: direct만 해당
    if (orderSource === 'direct') {
        window.addEventListener('beforeunload', function (e) {
            e.preventDefault();
            e.returnValue = '';
        });
    }

    // direct 데이터는 화면 렌더링 이후 삭제 (단, 임시 변수에 저장해둠)
    if (orderSource === 'direct' && cart.length > 0) {
        window.directOrderData = JSON.parse(JSON.stringify(cart));
        localStorage.removeItem('direct');
    }

    function calculateTotalAmount() {
        return cart.reduce((sum, item) => sum + (item.quantity || 1) * productPrice, 0);
    }

    function updateAmounts(pointUsage = appliedPoint) {
        const totalAmount = calculateTotalAmount();
        const finalAmount = totalAmount - pointUsage;

        totalAmountElem.innerText = `${totalAmount.toLocaleString()} 원`;
        pointUsageElem.innerText = `${pointUsage.toLocaleString()} 원`;
        finalAmountElem.innerText = `${Math.max(finalAmount, 0).toLocaleString()} 원`;
    }

    function renderOrderItems() {
        if (!cart.length) {
            alert('발주 정보가 없습니다. 주문을 시작 해 주세요.');
            location.href = '/index';
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
                        <p class="mt-n2 mb-1"><strong class="color-theme">0000-00-00</strong></p>
                    </div>
                    <div class="col-auto">
                        <span class="font-11">금액</span>
                        <p class="mt-n2 mb-1"><strong class="color-theme">${(productPrice * (item.quantity || 1)).toLocaleString()} 원</strong></p>
                    </div>
                    <div class="col-auto">
                        <span class="font-11">수량</span>
                        <p class="mt-n2 mb-1"><strong class="color-theme">${item.quantity || 1} 개</strong></p>
                    </div>
                    <div class="col-auto">
                        <span class="font-11">제품코드</span>
                        <p class="mt-n2 mb-1"><strong class="color-theme">${item.code || "CODE"}</strong></p>
                    </div>
                    <div class="col-auto">
                        <span class="font-11">제품명</span>
                        <p class="mt-n2 mb-1"><strong class="color-theme">${item.product || "NAME"}</strong></p>
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

        document.querySelectorAll(".address-toggle").forEach((toggle) => {
            toggle.addEventListener("change", (e) => {
                const index = e.target.getAttribute("data-index");
                const hiddenSection = document.getElementById(`hidden-section-${index}`);
                if (e.target.checked) {
                    hiddenSection.style.display = "block";
                    const height = hiddenSection.scrollHeight;
                    hiddenSection.style.height = `${height}px`;
                } else {
                    hiddenSection.style.height = `${hiddenSection.scrollHeight}px`;
                    setTimeout(() => {
                        hiddenSection.style.height = "0";
                    }, 10);
                }
            });
        });

        document.querySelectorAll(".hidden-section").forEach((section) => {
            section.addEventListener("transitionend", () => {
                if (section.style.height === "0px") {
                    section.style.display = "none";
                } else {
                    section.style.height = "auto";
                }
            });
        });

        updateAmounts();
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
            alert(`최대 ${pointLimit} 포인트까지 사용할 수 있습니다.`);
            pointUsage = pointLimit;
        }
        const totalAmount = calculateTotalAmount();
        if (pointUsage > totalAmount) pointUsage = totalAmount;
        appliedPoint = pointUsage;
        updateAmounts(appliedPoint);
    }

    function resetPointUsage() {
        pointInput.value = "";
        appliedPoint = 0;
        updateAmounts(0);
    }

    // 발주하기 버튼 처리
    const orderButton = document.getElementById("orderConfirmButton");
	if (orderButton) {
	    orderButton.addEventListener("click", () => {
	        const orderData = (orderSource === 'direct') ? window.directOrderData : cart;
	
	        // POST 요청 전송
	        fetch("/api/order/submit", {
	            method: "POST",
	            headers: {
	                "Content-Type": "application/json"
	            },
	            body: JSON.stringify(
	                orderData.map(item => ({
	                    selections: { ...item }, // 제품 선택 내용
	                    quantity: item.quantity || 1
	                }))
	            )
	        })
	        .then(res => {
	            if (!res.ok) throw new Error("서버 오류 발생");
	            return res.text();
	        })
	        .then(msg => {
	            alert(msg);
	            if (orderSource === 'cart') {
	                localStorage.removeItem('cart');
	            }
	            location.href = "/index";
	        })
	        .catch(err => {
	            alert("발주 처리 중 오류 발생: " + err.message);
	        });
	    });
	}

    pointInput.addEventListener("input", handlePointInput);
    applyButton.addEventListener("click", applyPointUsage);
    cancelButton.addEventListener("click", resetPointUsage);

    renderOrderItems();
});
