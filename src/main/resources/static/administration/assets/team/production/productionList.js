/* /administration/assets/team/production/productionList.js */
/* global document, window, fetch */

(function () {
	'use strict';

	// ===== DOM =====
	const $form = document.getElementById('team-production-filter-form');

	const $sortKey = document.getElementById('team-production-sortKey');
	const $sortDir = document.getElementById('team-production-sortDir');
	const $sortBtns = Array.from(document.querySelectorAll('.team-production-sort-btn'));

	const canBulkComplete = (document.getElementById('team-production-can-bulk-complete')?.value === 'true');
	const $btnBulkDone = document.getElementById('team-production-bulk-done-btn');

	const $checkAll = document.getElementById('team-production-check-all');
	const $items = Array.from(document.querySelectorAll('.team-production-check-item'));

	// ===== util =====
	function getCsrf() {
		const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
		const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
		return { token, header };
	}

	function getCheckedItems() {
		return $items
			.filter(cb => cb && !cb.disabled && cb.checked)
			.map(cb => ({
				id: Number(cb.getAttribute('data-order-id')),
				status: String(cb.getAttribute('data-status') || '').trim().toUpperCase()
			}))
			.filter(x => !Number.isNaN(x.id));
	}

	function getCheckedIds() {
		return getCheckedItems().map(x => x.id);
	}

	// ✅ (5) CONFIRMED만 생산완료 가능 검증
	function getInvalidForComplete(items) {
		// 하나라도 CONFIRMED가 아니면 invalid
		return items.filter(x => x.status !== 'CONFIRMED');
	}

	function syncButtonState() {
		if (!$btnBulkDone) return;

		if (!canBulkComplete) {
			$btnBulkDone.disabled = true;
			if ($checkAll) $checkAll.disabled = true;
			return;
		}

		const checked = getCheckedItems();
		$btnBulkDone.disabled = (checked.length === 0);
	}

	function syncCheckAllState() {
		if (!$checkAll) return;

		if (!canBulkComplete) {
			$checkAll.checked = false;
			$checkAll.disabled = true;
			return;
		}

		const enabledItems = $items.filter(cb => cb && !cb.disabled);
		if (enabledItems.length === 0) {
			$checkAll.checked = false;
			$checkAll.indeterminate = false;
			return;
		}
		const allChecked = enabledItems.every(cb => cb.checked);
		const noneChecked = enabledItems.every(cb => !cb.checked);

		$checkAll.indeterminate = (!allChecked && !noneChecked);
		$checkAll.checked = allChecked;
	}

	// ===== 정렬 기능 (항상 동작) =====
	function submitWithSort(nextKey, nextDir) {
		if (!$form) return;
		if ($sortKey) $sortKey.value = nextKey || '';
		if ($sortDir) $sortDir.value = nextDir || '';
		// 정렬 변경 시 첫 페이지로
		const pageInput = $form.querySelector('input[name="page"]');
		if (pageInput) pageInput.value = '0';
		$form.submit();
	}

	function getCurrentSort() {
		const key = ($sortKey?.value || '').trim();
		const dir = ($sortDir?.value || '').trim().toUpperCase();
		return { key, dir };
	}

	function computeNextDir(clickedKey) {
		const cur = getCurrentSort();
		if (!cur.key || cur.key !== clickedKey) return 'ASC';
		return (cur.dir === 'ASC') ? 'DESC' : 'ASC';
	}

	$sortBtns.forEach(btn => {
		btn.addEventListener('click', function () {
			const key = btn.getAttribute('data-sort-key');
			if (!key) return;

			const nextDir = computeNextDir(key);
			submitWithSort(key, nextDir);
		});
	});

	// ===== 벌크 기능 (권한 있을 때만 활성) =====
	if (!canBulkComplete) {
		$items.forEach(cb => {
			if (!cb) return;
			cb.checked = false;
			cb.disabled = true;
		});
		if ($checkAll) {
			$checkAll.checked = false;
			$checkAll.indeterminate = false;
			$checkAll.disabled = true;
		}
		if ($btnBulkDone) $btnBulkDone.disabled = true;
	} else {
		// 전체선택(현재 페이지)
		if ($checkAll) {
			$checkAll.addEventListener('change', function () {
				const checked = $checkAll.checked;
				$items.forEach(cb => {
					if (cb && !cb.disabled) cb.checked = checked;
				});
				syncButtonState();
				syncCheckAllState();
			});
		}

		// 개별 체크
		$items.forEach(cb => {
			if (!cb) return;
			cb.addEventListener('change', function () {
				syncButtonState();
				syncCheckAllState();
			});
		});

		// 생산완료처리
		if ($btnBulkDone) {
			$btnBulkDone.addEventListener('click', async function () {
				const checkedItems = getCheckedItems();
				if (checkedItems.length === 0) return;

				// ✅ (5) 검증: CONFIRMED가 아닌 오더가 하나라도 있으면 메시지만 출력하고 종료
				const invalid = getInvalidForComplete(checkedItems);
				if (invalid.length > 0) {
					// 요구사항: "__번 오더는 완료처리할 수 없다"
					// 여러 개일 수 있으니 첫 번째를 대표로 알림 + 필요 시 개수도 함께
					const first = invalid[0];
					const extra = (invalid.length > 1) ? ` (총 ${invalid.length}건)` : '';
					alert(`${first.id}번 오더는 완료처리할 수 없습니다.${extra}\nCONFIRMED(승인 완료) 상태만 생산완료 처리 가능합니다.\n체크 해제 후 다시 시도해주세요.`);
					return;
				}

				const ids = checkedItems.map(x => x.id);

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
		}
	}

	// 초기 상태
	syncButtonState();
	syncCheckAllState();

})();
