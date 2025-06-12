document.addEventListener('DOMContentLoaded', () => {

	// 공통 유틸 함수 정의
	const getSelectedValue = (name) => {
		const el = document.querySelector(`input[name="${name}"]:checked`);
		return el ? el.value : null;
	};

	const getSelectedLabel = (name) => {
		const el = document.querySelector(`input[name="${name}"]:checked`);
		return el ? el.nextElementSibling.textContent.trim() : null;
	};

	// 가격계산 버튼 클릭 이벤트 등록
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

		// 필수 옵션 체크
		if (document.querySelectorAll('input[name="sizeId"]').length > 0 && !sizeId) {
			alert("사이즈를 선택해주세요."); return;
		}
		if (document.querySelectorAll('input[name="colorId"]').length > 0 && !colorId) {
			alert("색상을 선택해주세요."); return;
		}

		// 수량 유효성 검사
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
					quantity // 필요 시 서버에서 사용하도록 전달
				})
			});

			if (!response.ok) throw new Error('서버 오류');

			const data = await response.json();
			document.querySelector('.standard-detail-price-result').style.display = 'block';
			document.querySelector('.standard-detail-price-result').innerHTML = `
				<hr>
				<p><strong>기본 가격:</strong> ${data.basePrice.toLocaleString()} 원</p>
				<p><strong>추가 옵션:</strong> ${data.additionalPrice.toLocaleString()} 원</p>
				<p><strong>수량:</strong> ${data.quantity} 개</p>
				<p><strong>최종 합계:</strong> <span id="calculated-price">${data.totalPrice.toLocaleString()}</span> 원</p>
			`;

			// 장바구니/발주 버튼 활성화
			document.querySelector('.standard-detail-btn-cart').disabled = false;
			document.querySelector('.standard-detail-btn-order').disabled = false;

		} catch (e) {
			alert("가격 계산에 실패했습니다. 다시 시도해주세요.");
			console.error(e);
		}
	});
});
