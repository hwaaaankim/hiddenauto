let calculatedMainPrice = 0; // 💡 전역 선언

document.addEventListener('DOMContentLoaded', () => {

	const getSelectedValue = (name) => {
		const el = document.querySelector(`input[name="${name}"]:checked`);
		return el ? el.value : null;
	};

	const getSelectedLabel = (name) => {
		const el = document.querySelector(`input[name="${name}"]:checked`);
		return el ? el.nextElementSibling.textContent.trim() : null;
	};

	// 가격 계산 버튼
	const calculateBtn = document.querySelector('.standard-detail-btn-calculate');
	calculateBtn.addEventListener('click', async () => {
		const productId = document.querySelector('input[name="productId"]').value;
		const sizeId = getSelectedValue('sizeId');
		const colorId = getSelectedValue('colorId');
		const tissue = getSelectedLabel('tissuePositionId') || '추가안함';
		const dry = getSelectedLabel('dryPositionId') || '추가안함';
		const outlet = getSelectedLabel('outletPositionId') || '추가안함';
		const led = getSelectedLabel('ledPositionId') || '추가안함';
		const quantityInput = document.getElementById('quantity');

		if (document.querySelectorAll('input[name="sizeId"]').length > 0 && !sizeId) {
			alert("사이즈를 선택해주세요."); return;
		}
		if (document.querySelectorAll('input[name="colorId"]').length > 0 && !colorId) {
			alert("색상을 선택해주세요."); return;
		}

		const quantity = Number(quantityInput.value);
		if (isNaN(quantity) || quantity <= 0) {
			alert("수량은 1 이상의 숫자만 입력 가능합니다.");
			quantityInput.focus();
			return;
		}

		try {
			const response = await fetch('/api/standard/calculate', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					productId,
					sizeId,
					colorId,
					tissuePositionName: tissue,
					dryPositionName: dry,
					outletPositionName: outlet,
					ledPositionName: led,
					quantity
				})
			});

			if (!response.ok) throw new Error('서버 오류');

			const data = await response.json();
			calculatedMainPrice = data.totalPrice;

			const priceResultEl = document.querySelector('.standard-detail-price-result');
			priceResultEl.style.display = 'block';
			priceResultEl.innerHTML = `
				<hr>
				<p><strong>기본 가격:</strong> ${data.basePrice.toLocaleString()} 원</p>
				<p><strong>추가 옵션:</strong> ${data.additionalPrice.toLocaleString()} 원</p>
				<p><strong>수량:</strong> ${data.quantity} 개</p>
				<p><strong>최종 합계:</strong> <span id="calculated-price">${data.totalPrice.toLocaleString()}</span> 원</p>
			`;

			// ✅ 가격 표시 영역으로 스크롤
			priceResultEl.scrollIntoView({ behavior: 'smooth' });

			document.querySelector('.standard-detail-btn-cart').disabled = false;
			document.querySelector('.standard-detail-btn-order').disabled = false;

		} catch (e) {
			alert("가격 계산에 실패했습니다. 다시 시도해주세요.");
			console.error(e);
		}
	});

	function buildStandardLocalizedOptionJson() {
		const getCheckedText = (name) => {
			const el = document.querySelector(`input[name="${name}"]:checked`);
			return el ? el.nextElementSibling.textContent.trim() : "없음";
		};
	
		const productName = document.querySelector('h3')?.textContent.trim() || "";
		const productCode = document.querySelector('[name="productId"]')?.getAttribute('data-code') || "";
		const categoryName = document.querySelector('[name="categoryName"]')?.value || "";
	
		return {
			카테고리: categoryName,
			제품명: productName,
			제품코드: productCode,
			사이즈: getCheckedText('sizeId'),
			색상: getCheckedText('colorId'),
			티슈위치: getCheckedText('tissuePositionId'),
			드라이걸이: getCheckedText('dryPositionId'),
			콘센트: getCheckedText('outletPositionId'),
			LED: getCheckedText('ledPositionId')
		};
	}

	// 장바구니
	async function addToCartStandard() {
		if (!confirm('해당 상품을 장바구니에 담으시겠습니까?')) return;
	
		const quantity = parseInt(document.getElementById('quantity')?.value) || 1;
		const unitPrice = Math.round((calculatedMainPrice || 10000) / quantity); // ✅ 단가 전송
		const localizedOptionJson = buildStandardLocalizedOptionJson();
		const optionJson = { ...localizedOptionJson };
		const additionalInfo = document.getElementById('final-additional-info')?.value || null;
	
		const formData = new FormData();
		formData.append('quantity', quantity);
		formData.append('price', unitPrice);
		formData.append('optionJson', JSON.stringify(optionJson));
		formData.append('localizedOptionJson', JSON.stringify(localizedOptionJson));
		formData.append('standard', true);
		if (additionalInfo) formData.append('additionalInfo', additionalInfo);
	
		try {
			const response = await fetch('/api/v2/insertCart', {
				method: 'POST',
				body: formData
			});
			if (!response.ok) throw new Error('서버 오류 발생');
			alert('장바구니에 담겼습니다.');
			window.updateBagIcon?.();
			window.location.href = '/standardOrderProduct';
		} catch (err) {
			console.error('🛑 규격 장바구니 저장 실패:', err);
			alert('장바구니 저장에 실패했습니다.');
		}
	}


	// 발주하기
	async function addToOrderStandard() {
		if (!confirm('해당 상품을 발주하시겠습니까?')) return;
	
		const quantity = parseInt(document.getElementById('quantity')?.value) || 1;
		const unitPrice = Math.round((calculatedMainPrice || 10000) / quantity); // ✅ 단가 전송
		const localizedOptionJson = buildStandardLocalizedOptionJson();
		const optionJson = { ...localizedOptionJson };
		const additionalInfo = document.getElementById('final-additional-info')?.value || null;
	
		const formData = new FormData();
		formData.append('from', 'direct');
		formData.append('quantity', quantity);
		formData.append('price', unitPrice);
		formData.append('optionJson', JSON.stringify(optionJson));
		formData.append('localizedOptionJson', JSON.stringify(localizedOptionJson));
		formData.append('standard', true);
		if (additionalInfo) formData.append('additionalInfo', additionalInfo);
	
		try {
			const response = await fetch('/orderConfirm', {
				method: 'POST',
				body: formData
			});
			if (!response.ok) throw new Error('서버 오류 발생');
			const html = await response.text();
			document.open();
			document.write(html);
			document.close();
		} catch (err) {
			console.error('🛑 규격 발주 실패:', err);
			alert('발주 처리 중 오류가 발생했습니다.');
		}
	}

	// 버튼 등록
	document.querySelector('.standard-detail-btn-cart')?.addEventListener('click', (e) => {
		e.target.disabled = true;
		addToCartStandard().finally(() => e.target.disabled = false);
	});
	document.querySelector('.standard-detail-btn-order')?.addEventListener('click', (e) => {
		e.target.disabled = true;
		addToOrderStandard().finally(() => e.target.disabled = false);
	});
});
