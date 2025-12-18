/* /administration/assets/team/production/productionList.js */
/* global document, window, fetch */

(function() {
	'use strict';

	const canBulkComplete = (document.getElementById('team-production-can-bulk-complete')?.value === 'true');
	const $btn = document.getElementById('team-production-bulk-done-btn');
	const $checkAll = document.getElementById('team-production-check-all');
	const $items = Array.from(document.querySelectorAll('.team-production-check-item'));

	function getCsrf() {
		// 보통 Spring Security + Thymeleaf 환경에서 meta로 주입합니다.
		// fragments에 없다면 추가 필요합니다.
		const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
		const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
		return { token, header };
	}

	function getCheckedIds() {
		return $items
			.filter(cb => !cb.disabled && cb.checked)
			.map(cb => Number(cb.getAttribute('data-order-id')))
			.filter(n => !Number.isNaN(n));
	}

	function syncButtonState() {
		if (!canBulkComplete) {
			$btn.disabled = true;
			if ($checkAll) $checkAll.disabled = true;
			return;
		}
		const ids = getCheckedIds();
		$btn.disabled = (ids.length === 0);
	}

	function syncCheckAllState() {
		if (!$checkAll) return;
		if (!canBulkComplete) {
			$checkAll.checked = false;
			$checkAll.disabled = true;
			return;
		}
		const enabledItems = $items.filter(cb => !cb.disabled);
		if (enabledItems.length === 0) {
			$checkAll.checked = false;
			return;
		}
		const allChecked = enabledItems.every(cb => cb.checked);
		const noneChecked = enabledItems.every(cb => !cb.checked);

		$checkAll.indeterminate = (!allChecked && !noneChecked);
		$checkAll.checked = allChecked;
	}

	// 초기 잠금 처리
	if (!canBulkComplete) {
		$items.forEach(cb => { cb.checked = false; cb.disabled = true; });
		if ($checkAll) { $checkAll.checked = false; $checkAll.disabled = true; }
		$btn.disabled = true;
		return;
	}

	// 헤더 전체선택(현재 페이지)
	if ($checkAll) {
		$checkAll.addEventListener('change', function() {
			const checked = $checkAll.checked;
			$items.forEach(cb => {
				if (!cb.disabled) cb.checked = checked;
			});
			syncButtonState();
			syncCheckAllState();
		});
	}

	// 개별 체크
	$items.forEach(cb => {
		cb.addEventListener('change', function() {
			syncButtonState();
			syncCheckAllState();
		});
	});

	// 생산완료처리
	$btn.addEventListener('click', async function() {
		const ids = getCheckedIds();
		if (ids.length === 0) return;

		if (!window.confirm(`선택된 ${ids.length}건을 생산완료 처리하시겠습니까?`)) return;

		const csrf = getCsrf();
		const headers = { 'Content-Type': 'application/json' };
		if (csrf.token && csrf.header) headers[csrf.header] = csrf.token;

		try {
			const res = await fetch('/api/team/production/orders/complete', {
				method: 'POST',
				headers,
				body: JSON.stringify({ orderIds: ids })
			});

			if (!res.ok) {
				const text = await res.text();
				alert(text || '처리에 실패했습니다.');
				return;
			}

			alert('생산완료 처리되었습니다.');
			window.location.reload();
		} catch (e) {
			alert(e?.message || '네트워크 오류가 발생했습니다.');
		}
	});

	// 초기 상태
	syncButtonState();
	syncCheckAllState();

})();
