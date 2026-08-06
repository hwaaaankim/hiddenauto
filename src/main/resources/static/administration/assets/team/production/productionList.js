/* /administration/assets/team/production/productionList.js */

(function () {
	'use strict';

	const config = window.teamProductionOverviewConfig || {};

	// ===== DOM =====
	const $form = document.getElementById('team-production-filter-form');

	const $sortSpec = document.getElementById('team-production-sortSpec');
	const $sortBtns = Array.from(document.querySelectorAll('.team-production-sort-btn'));
	const $sortControls = Array.from(document.querySelectorAll('.team-production-sort-controls'));
	const $searchResetBtn = document.getElementById('team-production-search-reset-btn');
	const $sortResetBtn = document.getElementById('team-production-sort-reset-btn');
	const $orderIdFrom = document.getElementById('team-production-orderIdFrom');
	const $orderIdTo = document.getElementById('team-production-orderIdTo');

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
		const sortOrders = getCurrentSortOrders();
		const activeMap = new Map();

		sortOrders.forEach((order, index) => {
			activeMap.set(order.key, { dir: order.dir, priority: index + 1 });
		});

		$sortBtns.forEach(btn => {
			const key = String(btn.getAttribute('data-sort-key') || '').trim();
			const dir = String(btn.getAttribute('data-sort-dir') || '').trim().toUpperCase();
			const active = activeMap.get(key);

			btn.classList.toggle('is-active', !!active && active.dir === dir);
			btn.setAttribute('aria-pressed', active && active.dir === dir ? 'true' : 'false');

			if (active && active.dir === dir) {
				btn.title = `${active.priority}순위 ${dir === 'ASC' ? '오름차순' : '내림차순'} - 다시 누르면 이 조건만 해제`;
			} else {
				btn.title = `${dir === 'ASC' ? '오름차순' : '내림차순'} 정렬 추가`;
			}
		});

		$sortControls.forEach(control => {
			const key = String(control.getAttribute('data-sort-key') || '').trim();
			const priority = control.querySelector('.team-production-sort-priority');
			const active = activeMap.get(key);

			if (!priority) return;

			priority.textContent = active ? String(active.priority) : '';
			priority.classList.toggle('is-visible', !!active);
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
		const feedback = window.TeamActionFeedback || null;
		const feedbackToken = feedback ? feedback.begin({
			title: id + '번 오더 생산완료 처리 중',
			message: '발주 상태와 생산 담당자, 알림 내역을 반영하고 있습니다.',
			detail: '처리가 끝날 때까지 현재 화면을 유지해 주세요.'
		}) : null;

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

			if (feedback) {
				await feedback.success({
					title: id + '번 오더 생산완료',
					message: result.message || '생산완료 처리되었습니다.',
					detail: '현재 화면에 변경된 상태를 반영했습니다.'
				}, feedbackToken);
			}

			return result;
		} catch (error) {
			if (feedback) {
				await feedback.error({
					title: '생산완료 처리 실패',
					message: error && error.message ? error.message : '생산완료 처리 중 오류가 발생했습니다.',
					detail: '주문 상태와 생산 카테고리 권한을 확인해 주세요.'
				}, feedbackToken);
			}
			throw error;
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

	// ===== 다중 정렬 / 초기화 =====
	const allowedSortKeys = new Set(['id', 'productName', 'productSeries', 'deliveryDate', 'checked']);

	function normalizeSortDirection(value) {
		const normalized = String(value || '').trim().toUpperCase();
		return normalized === 'ASC' || normalized === 'DESC' ? normalized : null;
	}

	function parseSortSpec(value) {
		const result = [];
		const indexByKey = new Map();

		String(value || '').split(',').forEach(token => {
			const parts = token.trim().split(':');
			const key = String(parts[0] || '').trim();
			const dir = normalizeSortDirection(parts[1]);

			if (!allowedSortKeys.has(key) || !dir) return;

			if (indexByKey.has(key)) {
				result[indexByKey.get(key)] = { key, dir };
				return;
			}

			indexByKey.set(key, result.length);
			result.push({ key, dir });
		});

		return result;
	}

	function serializeSortOrders(sortOrders) {
		return (sortOrders || [])
			.filter(order => order && allowedSortKeys.has(order.key) && normalizeSortDirection(order.dir))
			.map(order => `${order.key}:${normalizeSortDirection(order.dir)}`)
			.join(',');
	}

	function getCurrentSortOrders() {
		return parseSortSpec($sortSpec?.value || '');
	}

	function submitWithSortOrders(sortOrders) {
		if (!$form) return;
		if ($sortSpec) $sortSpec.value = serializeSortOrders(sortOrders);

		const pageInput = $form.querySelector('input[name="page"]');
		if (pageInput) pageInput.value = '0';

		$form.submit();
	}

	function toggleSortCondition(clickedKey, clickedDir) {
		const key = String(clickedKey || '').trim();
		const dir = normalizeSortDirection(clickedDir);

		if (!allowedSortKeys.has(key) || !dir) return;

		const current = getCurrentSortOrders();
		const index = current.findIndex(order => order.key === key);

		if (index < 0) {
			current.push({ key, dir });
		} else if (current[index].dir === dir) {
			// 같은 화살표를 다시 누르면 해당 정렬 조건만 제거합니다.
			current.splice(index, 1);
		} else {
			// 반대 화살표는 기존 우선순위를 유지한 채 방향만 변경합니다.
			current[index] = { key, dir };
		}

		submitWithSortOrders(current);
	}

	function setFormValue(selector, value) {
		const element = $form?.querySelector(selector);
		if (element) element.value = value;
	}

	function parsePositiveIntegerInput(input) {
		if (!input) return null;

		const raw = String(input.value || '').trim();
		if (!raw) return null;

		const value = Number(raw);
		return Number.isInteger(value) && value > 0 ? value : null;
	}

	function validateOrderIdRange() {
		const fromRaw = String($orderIdFrom?.value || '').trim();
		const toRaw = String($orderIdTo?.value || '').trim();
		const from = parsePositiveIntegerInput($orderIdFrom);
		const to = parsePositiveIntegerInput($orderIdTo);

		if (fromRaw && from == null) {
			alert('오더 ID FROM은 1 이상의 정수로 입력해 주세요.');
			$orderIdFrom?.focus();
			return false;
		}

		if (toRaw && to == null) {
			alert('오더 ID TO는 1 이상의 정수로 입력해 주세요.');
			$orderIdTo?.focus();
			return false;
		}

		if (from != null && to != null && from > to) {
			alert('오더 ID TO는 FROM보다 크거나 같아야 합니다.\n단건 조회는 FROM과 TO에 같은 값을 입력해 주세요.');
			$orderIdTo?.focus();
			return false;
		}

		return true;
	}

	function resetSearchFilters() {
		if (!$form) return;

		// 정렬(sortSpec)과 표시 개수(size)는 유지하고 검색 조건만 최초 상태로 복원합니다.
		setFormValue('[name="orderIdFrom"]', '');
		setFormValue('[name="orderIdTo"]', '');
		setFormValue('[name="productName"]', '');
		setFormValue('[name="productCategoryId"]', '');
		setFormValue('[name="dateType"]', 'preferred');
		setFormValue('[name="startDate"]', '');
		setFormValue('[name="endDate"]', '');
		setFormValue('[name="statusFilter"]', 'CONFIRMED');
		setFormValue('[name="standardType"]', 'ALL');

		const pageInput = $form.querySelector('input[name="page"]');
		if (pageInput) pageInput.value = '0';

		$form.submit();
	}

	function resetAllSortFilters() {
		if (!$form) return;
		if ($sortSpec) $sortSpec.value = '';

		const pageInput = $form.querySelector('input[name="page"]');
		if (pageInput) pageInput.value = '0';

		$form.submit();
	}

	$sortBtns.forEach(btn => {
		btn.addEventListener('click', function () {
			toggleSortCondition(
				btn.getAttribute('data-sort-key'),
				btn.getAttribute('data-sort-dir')
			);
		});
	});

	if ($form) {
		$form.addEventListener('submit', function (event) {
			if (!validateOrderIdRange()) {
				event.preventDefault();
				event.stopPropagation();
			}
		});
	}

	if ($searchResetBtn) {
		$searchResetBtn.addEventListener('click', resetSearchFilters);
	}

	if ($sortResetBtn) {
		$sortResetBtn.addEventListener('click', resetAllSortFilters);
	}

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

				const feedback = window.TeamActionFeedback || null;
				const feedbackToken = feedback ? feedback.begin({
					title: ids.length + '건 생산완료 처리 중',
					message: '선택한 발주의 상태와 담당자, 알림 내역을 일괄 반영하고 있습니다.',
					detail: '완료 후 현재 조건으로 화면을 새로고침합니다.'
				}) : null;

				$btnBulkDone.disabled = true;

				try {
					const res = await fetch('/api/team/production/orders/complete', {
						method: 'POST',
						headers,
						body: JSON.stringify({ orderIds: ids })
					});

					if (!res.ok) {
						const text = await res.text();
						throw new Error(text || '처리에 실패했습니다.');
					}

					if (feedback) {
						await feedback.success({
							title: '생산완료 처리가 끝났습니다.',
							message: ids.length + '건이 정상적으로 생산완료 처리되었습니다.',
							detail: '최신 상태를 다시 불러옵니다.'
						}, feedbackToken);
					} else {
						alert('생산완료 처리되었습니다.');
					}

					window.location.reload();
				} catch (e) {
					if (feedback) {
						await feedback.error({
							title: '생산완료 처리 실패',
							message: e && e.message ? e.message : '네트워크 오류가 발생했습니다.',
							detail: '실패한 주문은 변경되지 않았습니다.'
						}, feedbackToken);
					} else {
						alert(e?.message || '네트워크 오류가 발생했습니다.');
					}
				} finally {
					syncButtonState();
				}
			});
		}
	}

	// 초기 상태
	syncButtonState();
	syncCheckAllState();
	syncSortIcons();

})();
