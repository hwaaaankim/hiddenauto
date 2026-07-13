/* /administration/assets/team/production/productionList.js */

(function () {
	'use strict';

	const config = window.teamProductionOverviewConfig || {};

	// ===== DOM =====
	const $form = document.getElementById('team-production-filter-form');

	const $sortKey = document.getElementById('team-production-sortKey');
	const $sortDir = document.getElementById('team-production-sortDir');
	const $sortBtns = Array.from(document.querySelectorAll('.team-production-sort-btn'));

	const canBulkComplete = (document.getElementById('team-production-can-bulk-complete')?.value === 'true');
	const $btnBulkDone = document.getElementById('team-production-bulk-done-btn');

	const $checkAll = document.getElementById('team-production-check-all');
	const $items = Array.from(document.querySelectorAll('.team-production-check-item'));

	const pendingSingleCompleteIds = new Set();

	// ===== util =====
	function getCsrf() {
		const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
		const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
		return { token, header };
	}

	function buildJsonHeaders() {
		const csrf = getCsrf();
		const headers = {
			'Accept': 'application/json'
		};

		if (csrf.token && csrf.header) {
			headers[csrf.header] = csrf.token;
		}

		return headers;
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

	function getInvalidForComplete(items) {
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

	function syncSortIcons() {
		const cur = getCurrentSort();

		$sortBtns.forEach(btn => {
			const key = btn.getAttribute('data-sort-key');
			const icon = btn.querySelector('.team-production-sort-icon');
			if (!icon) return;

			btn.classList.toggle('is-active', !!cur.key && cur.key === key);

			if (cur.key === key) {
				icon.textContent = cur.dir === 'DESC' ? '▼' : '▲';
			} else {
				icon.textContent = '▲▼';
			}
		});
	}

	function normalizeOrderId(value) {
		const id = Number(value);
		return Number.isInteger(id) && id > 0 ? id : null;
	}

	function cssEscape(value) {
		const text = String(value == null ? '' : value);

		if (window.CSS && typeof window.CSS.escape === 'function') {
			return window.CSS.escape(text);
		}

		return text.replace(/([ #;?%&,.+*~':"!^$[\]()=>|/@])/g, '\\$1');
	}

	async function readResponsePayload(response) {
		const contentType = String(response.headers.get('content-type') || '').toLowerCase();

		if (contentType.includes('application/json')) {
			try {
				return await response.json();
			} catch (error) {
				return null;
			}
		}

		try {
			const text = await response.text();
			return text ? { message: text } : null;
		} catch (error) {
			return null;
		}
	}

	function resolveResponseMessage(payload, fallback) {
		if (payload && typeof payload === 'object') {
			const message = payload.message || payload.error || payload.detail;
			if (message) return String(message);
		}

		return fallback;
	}

	function buildSingleCompleteUrl(orderId) {
		const prefix = String(config.completeUrlPrefix || '/team/productionList/');
		const normalizedPrefix = prefix.endsWith('/') ? prefix : prefix + '/';
		return normalizedPrefix + encodeURIComponent(String(orderId)) + '/complete';
	}

	function normalizeCompleteResult(orderId, payload) {
		const source = payload && typeof payload === 'object' ? payload : {};

		return {
			orderId: normalizeOrderId(source.orderId) || orderId,
			status: String(source.status || 'PRODUCTION_DONE').trim().toUpperCase(),
			statusLabel: String(source.statusLabel || '생산 완료').trim(),
			message: String(source.message || '생산완료 처리되었습니다.').trim(),
			raw: source
		};
	}

	function applyCompletedStateToPage(result) {
		if (!result) return;

		const orderId = normalizeOrderId(result.orderId);
		if (!orderId) return;

		const idSelector = cssEscape(orderId);
		const status = String(result.status || 'PRODUCTION_DONE').trim().toUpperCase();
		const statusLabel = String(result.statusLabel || '생산 완료').trim();

		document.querySelectorAll('[data-overview-order-id="' + idSelector + '"]').forEach(row => {
			row.setAttribute('data-overview-status', status);
			row.setAttribute('data-production-status-label', statusLabel);
			row.classList.add('team-production-row-production-done');
		});

		document.querySelectorAll('.team-production-check-item[data-order-id="' + idSelector + '"]').forEach(checkbox => {
			checkbox.setAttribute('data-status', status);
			checkbox.checked = false;
		});

		document.querySelectorAll('[data-inline-complete-order-id="' + idSelector + '"]').forEach(button => {
			button.disabled = true;
			button.title = '이미 생산 완료 처리된 주문입니다.';
		});

		syncButtonState();
		syncCheckAllState();
	}

	function dispatchCompletedEvent(result) {
		document.dispatchEvent(new CustomEvent('team-production:order-completed', {
			detail: result
		}));
	}

	async function completeSingleOrder(orderId) {
		const id = normalizeOrderId(orderId);

		if (!id) {
			throw new Error('올바른 주문 ID가 아닙니다.');
		}

		if (!canBulkComplete) {
			throw new Error('생산완료 처리 권한이 없습니다.');
		}

		if (pendingSingleCompleteIds.has(id)) {
			throw new Error('해당 주문을 이미 처리 중입니다.');
		}

		pendingSingleCompleteIds.add(id);

		try {
			const response = await fetch(buildSingleCompleteUrl(id), {
				method: 'POST',
				credentials: 'same-origin',
				headers: buildJsonHeaders()
			});

			const payload = await readResponsePayload(response);

			if (!response.ok) {
				throw new Error(resolveResponseMessage(payload, '생산완료 처리에 실패했습니다.'));
			}

			const result = normalizeCompleteResult(id, payload);
			applyCompletedStateToPage(result);
			dispatchCompletedEvent(result);

			return result;
		} finally {
			pendingSingleCompleteIds.delete(id);
		}
	}

	window.TeamProductionCompletion = Object.assign(window.TeamProductionCompletion || {}, {
		completeOrder: completeSingleOrder,
		markCompleted: applyCompletedStateToPage,
		isPending: function (orderId) {
			const id = normalizeOrderId(orderId);
			return id ? pendingSingleCompleteIds.has(id) : false;
		}
	});

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

				const invalid = getInvalidForComplete(checkedItems);
				if (invalid.length > 0) {
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
	syncSortIcons();

})();
