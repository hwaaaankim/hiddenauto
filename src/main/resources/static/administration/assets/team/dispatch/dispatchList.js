/* dispatchList.js */
(function() {
	'use strict';

	const API = {
		search: '/team/dispatchList/api/orders/search',
		excel: '/team/dispatchList/api/orders/excel',
		deliveryStatementLayoutData: '/api/internal/delivery-statement/layout/data',
		deliveryStatementLayoutExcel: '/api/internal/delivery-statement/layout/excel',
		complete: '/team/dispatchList/api/orders/complete',
		bulkHandlerPreview: '/team/dispatchList/api/orders/bulk-handler/preview',
		bulkHandler: '/team/dispatchList/api/orders/bulk-handler',
		bulkDeliveryMethodPreview: '/team/dispatchList/api/orders/bulk-delivery-method/preview',
		bulkDeliveryMethod: '/team/dispatchList/api/orders/bulk-delivery-method',
		updateDeliveryMethod: function(orderId) {
			return '/team/dispatchList/api/orders/' + encodeURIComponent(orderId) + '/delivery-method';
		},
		provinceChildren: function(provinceId) {
			return '/team/dispatchList/api/regions/provinces/' + encodeURIComponent(provinceId) + '/children';
		},
		cityDistricts: function(cityId) {
			return '/team/dispatchList/api/regions/cities/' + encodeURIComponent(cityId) + '/districts';
		}
	};

	const state = {
		loading: false,
		hasNext: true,
		lastStatusSort: null,
		lastOrderId: null,
		loadedOrderIds: [],
		selectedOrderIds: new Set(),
		completeBusy: false,
		activeDeliveryOrderId: null,
		activeDeliveryHandlerId: null,
		bulkHandlerOrderIds: [],
		bulkHandlerPreview: null,
		bulkHandlerExclusionAcknowledged: false,
		bulkHandlerBusy: false,
		bulkHandlerPreviewSequence: 0,
		bulkMethodOrderIds: [],
		bulkMethodPreview: null,
		bulkMethodRemovalAcknowledged: false,
		bulkMethodBusy: false,
		bulkMethodPreviewSequence: 0,
	};
	let pendingConfirmResolver = null;

	const els = {};

	document.addEventListener('DOMContentLoaded', function() {
		bindElements();
		initializeOrderIdRangeFromUrl();
		bindEvents();
		initFloatingButtonObserver();
		searchOrders(true);
	});

	function bindElements() {
		els.csrfToken = document.getElementById('dispatch-list-csrf-token');
		els.csrfHeader = document.getElementById('dispatch-list-csrf-header');
		els.today = document.getElementById('dispatch-list-today');

		els.filterCard = document.getElementById('dispatch-list-filter-card');
		els.toolbar = document.getElementById('dispatch-list-toolbar');

		els.keywordType = document.getElementById('dispatch-list-keyword-type');
		els.keyword = document.getElementById('dispatch-list-keyword');
		els.productCategoryId = document.getElementById('dispatch-list-product-category-id');
		els.standard = document.getElementById('dispatch-list-standard');
		els.orderDate = document.getElementById('dispatch-list-order-date');
		els.orderIdFrom = document.getElementById('dispatch-list-order-id-from');
		els.orderIdTo = document.getElementById('dispatch-list-order-id-to');
		els.provinceId = document.getElementById('dispatch-list-province-id');
		els.cityId = document.getElementById('dispatch-list-city-id');
		els.districtId = document.getElementById('dispatch-list-district-id');
		els.deliveryMethodId = document.getElementById('dispatch-list-delivery-method-id');

		els.searchBtn = document.getElementById('dispatch-list-search-btn');
		els.resetBtn = document.getElementById('dispatch-list-reset-btn');
		els.bulkCompleteBtn = document.getElementById('dispatch-list-bulk-complete-btn');
		els.bulkHandlerBtn = document.getElementById('dispatch-list-bulk-handler-btn');
		els.bulkDeliveryMethodBtn = document.getElementById('dispatch-list-bulk-delivery-method-btn');

		els.tbody = document.getElementById('dispatch-list-tbody');
		els.checkAll = document.getElementById('dispatch-list-check-all');
		els.loadedCount = document.getElementById('dispatch-list-loaded-count');
		els.selectedCount = document.getElementById('dispatch-list-selected-count');
		els.moreStatus = document.getElementById('dispatch-list-more-status');

		els.floatingBtn = document.getElementById('dispatch-list-floating-control-btn');
		els.floatingSelectedCount = document.getElementById('dispatch-list-floating-selected-count');

		els.modalKeywordType = document.getElementById('dispatch-list-modal-keyword-type');
		els.modalKeyword = document.getElementById('dispatch-list-modal-keyword');
		els.modalProductCategoryId = document.getElementById('dispatch-list-modal-product-category-id');
		els.modalStandard = document.getElementById('dispatch-list-modal-standard');
		els.modalOrderDate = document.getElementById('dispatch-list-modal-order-date');
		els.modalOrderIdFrom = document.getElementById('dispatch-list-modal-order-id-from');
		els.modalOrderIdTo = document.getElementById('dispatch-list-modal-order-id-to');
		els.modalProvinceId = document.getElementById('dispatch-list-modal-province-id');
		els.modalCityId = document.getElementById('dispatch-list-modal-city-id');
		els.modalDistrictId = document.getElementById('dispatch-list-modal-district-id');
		els.modalDeliveryMethodId = document.getElementById('dispatch-list-modal-delivery-method-id');
		els.modalSearchBtn = document.getElementById('dispatch-list-modal-search-btn');
		els.modalBulkCompleteBtn = document.getElementById('dispatch-list-modal-bulk-complete-btn');
		els.modalBulkHandlerBtn = document.getElementById('dispatch-list-modal-bulk-handler-btn');
		els.modalBulkDeliveryMethodBtn = document.getElementById('dispatch-list-modal-bulk-delivery-method-btn');
		els.modalSelectedCount = document.getElementById('dispatch-list-modal-selected-count');

		els.modalLoadedCount = document.getElementById('dispatch-list-modal-loaded-count');
		els.modalSelectedCountInfo = document.getElementById('dispatch-list-modal-selected-count-info');
		els.modalCompleteCount = document.getElementById('dispatch-list-modal-complete-count');
		els.modalMoreStatus = document.getElementById('dispatch-list-modal-more-status');

		els.controlModal = document.getElementById('dispatch-list-control-modal');

		els.deliveryModal = document.getElementById('dispatch-list-delivery-method-modal');
		els.deliveryModalOrderId = document.getElementById('dispatch-list-delivery-modal-order-id');
		els.confirmModal = document.getElementById('dispatch-list-confirm-modal');
		els.confirmMessage = document.getElementById('dispatch-list-confirm-message');
		els.confirmOkBtn = document.getElementById('dispatch-list-confirm-ok-btn');

		els.alertModal = document.getElementById('dispatch-list-alert-modal');
		els.alertMessage = document.getElementById('dispatch-list-alert-message');
		els.excelBtn = document.getElementById('dispatch-list-excel-btn');
		els.statementSiteHorizontalPrintBtn = document.getElementById('dispatch-list-statement-site-horizontal-print-btn');
		els.statementParcelHorizontalPrintBtn = document.getElementById('dispatch-list-statement-parcel-horizontal-print-btn');
		els.statementSiteHorizontalDownloadBtn = document.getElementById('dispatch-list-statement-site-horizontal-download-btn');
		els.statementParcelHorizontalDownloadBtn = document.getElementById('dispatch-list-statement-parcel-horizontal-download-btn');

		/*
		 * 세로형 버튼은 dispatchList.html에서 주석으로 숨겨져 있습니다.
		 * HTML 주석을 제거하면 아래 바인딩과 이벤트가 그대로 활성화됩니다.
		 */
		els.statementSiteVerticalPrintBtn = document.getElementById('dispatch-list-statement-site-vertical-print-btn');
		els.statementParcelVerticalPrintBtn = document.getElementById('dispatch-list-statement-parcel-vertical-print-btn');
		els.statementSiteVerticalDownloadBtn = document.getElementById('dispatch-list-statement-site-vertical-download-btn');
		els.statementParcelVerticalDownloadBtn = document.getElementById('dispatch-list-statement-parcel-vertical-download-btn');

		els.deliveryModalSelectedMethodId = document.getElementById('dispatch-list-delivery-modal-selected-method-id');
		els.directHandlerArea = document.getElementById('dispatch-list-direct-handler-area');
		els.deliveryHandlerId = document.getElementById('dispatch-list-delivery-handler-id');
		els.deliveryMethodSaveBtn = document.getElementById('dispatch-list-delivery-method-save-btn');

		els.bulkHandlerModal = document.getElementById('dispatch-list-bulk-handler-modal');
		els.bulkHandlerRequestedCount = document.getElementById('dispatch-list-bulk-handler-requested-count');
		els.bulkHandlerChangeableCount = document.getElementById('dispatch-list-bulk-handler-changeable-count');
		els.bulkHandlerExcludedCount = document.getElementById('dispatch-list-bulk-handler-excluded-count');
		els.bulkHandlerLoading = document.getElementById('dispatch-list-bulk-handler-loading');
		els.bulkHandlerBlockingArea = document.getElementById('dispatch-list-bulk-handler-blocking-area');
		els.bulkHandlerBlockingList = document.getElementById('dispatch-list-bulk-handler-blocking-list');
		els.bulkHandlerExcludedArea = document.getElementById('dispatch-list-bulk-handler-excluded-area');
		els.bulkHandlerExcludedMessageCount = document.getElementById('dispatch-list-bulk-handler-excluded-message-count');
		els.bulkHandlerExcludedList = document.getElementById('dispatch-list-bulk-handler-excluded-list');
		els.bulkHandlerExcludeConfirmBtn = document.getElementById('dispatch-list-bulk-handler-exclude-confirm-btn');
		els.bulkHandlerExcludeConfirmed = document.getElementById('dispatch-list-bulk-handler-exclude-confirmed');
		els.bulkHandlerChangeableArea = document.getElementById('dispatch-list-bulk-handler-changeable-area');
		els.bulkHandlerChangeableList = document.getElementById('dispatch-list-bulk-handler-changeable-list');
		els.bulkHandlerId = document.getElementById('dispatch-list-bulk-handler-id');
		els.bulkHandlerSaveBtn = document.getElementById('dispatch-list-bulk-handler-save-btn');

		els.bulkMethodModal = document.getElementById('dispatch-list-bulk-delivery-method-modal');
		els.bulkMethodId = document.getElementById('dispatch-list-bulk-delivery-method-id');
		els.bulkMethodRequestedCount = document.getElementById('dispatch-list-bulk-method-requested-count');
		els.bulkMethodRequiredCount = document.getElementById('dispatch-list-bulk-method-required-count');
		els.bulkMethodRemovalCount = document.getElementById('dispatch-list-bulk-method-removal-count');
		els.bulkMethodPlaceholder = document.getElementById('dispatch-list-bulk-method-placeholder');
		els.bulkMethodLoading = document.getElementById('dispatch-list-bulk-method-loading');
		els.bulkMethodBlockingArea = document.getElementById('dispatch-list-bulk-method-blocking-area');
		els.bulkMethodBlockingList = document.getElementById('dispatch-list-bulk-method-blocking-list');
		els.bulkMethodRemovalArea = document.getElementById('dispatch-list-bulk-method-removal-area');
		els.bulkMethodRemovalMessageCount = document.getElementById('dispatch-list-bulk-method-removal-message-count');
		els.bulkMethodRemovalList = document.getElementById('dispatch-list-bulk-method-removal-list');
		els.bulkMethodRemovalConfirmBtn = document.getElementById('dispatch-list-bulk-method-removal-confirm-btn');
		els.bulkMethodRemovalConfirmed = document.getElementById('dispatch-list-bulk-method-removal-confirmed');
		els.bulkMethodAssignmentArea = document.getElementById('dispatch-list-bulk-method-assignment-area');
		els.bulkMethodAssignmentMessageCount = document.getElementById('dispatch-list-bulk-method-assignment-message-count');
		els.bulkMethodAssignmentList = document.getElementById('dispatch-list-bulk-method-assignment-list');
		els.bulkMethodPreservedArea = document.getElementById('dispatch-list-bulk-method-preserved-area');
		els.bulkMethodPreservedList = document.getElementById('dispatch-list-bulk-method-preserved-list');
		els.bulkHandlerOptionTemplate = document.getElementById('dispatch-list-bulk-handler-option-template');
		els.bulkMethodSaveBtn = document.getElementById('dispatch-list-bulk-method-save-btn');
	}

	function bindEvents() {
		els.searchBtn.addEventListener('click', function() {
			searchOrders(true);
		});

		els.resetBtn.addEventListener('click', resetFilters);

		els.keyword.addEventListener('keydown', function(event) {
			if (event.key === 'Enter') {
				event.preventDefault();
				searchOrders(true);
			}
		});

		els.provinceId.addEventListener('change', function() {
			handleProvinceChange('', els.provinceId, els.cityId, els.districtId);
		});

		els.cityId.addEventListener('change', function() {
			handleCityChange('', els.cityId, els.districtId);
		});

		els.checkAll.addEventListener('change', handleCheckAllChange);

		els.bulkCompleteBtn.addEventListener('click', function() {
			completeSelectedOrders();
		});

		if (els.bulkHandlerBtn) {
			els.bulkHandlerBtn.addEventListener('click', openBulkHandlerModal);
		}

		if (els.bulkDeliveryMethodBtn) {
			els.bulkDeliveryMethodBtn.addEventListener('click', openBulkDeliveryMethodModal);
		}

		els.tbody.addEventListener('change', function(event) {
			if (event.target.classList.contains('dispatch-list-row-check')) {
				handleRowCheckChange(event.target);
			}
		});

		els.tbody.addEventListener('click', function(event) {
			const completeBtn = event.target.closest('.dispatch-list-complete-btn');
			if (completeBtn) {
				const orderId = completeBtn.getAttribute('data-order-id');
				completeOrders([Number(orderId)]);
				return;
			}

			const methodBtn = event.target.closest('.dispatch-list-delivery-method-btn');
			if (methodBtn) {
				openDeliveryMethodModal(methodBtn);
			}
		});

		els.modalProvinceId.addEventListener('change', function() {
			handleProvinceChange('modal', els.modalProvinceId, els.modalCityId, els.modalDistrictId);
		});

		els.modalCityId.addEventListener('change', function() {
			handleCityChange('modal', els.modalCityId, els.modalDistrictId);
		});

		els.modalSearchBtn.addEventListener('click', function() {
			copyModalFiltersToMain();
			hideModal(els.controlModal);
			searchOrders(true);
		});

		els.modalBulkCompleteBtn.addEventListener('click', function() {
			hideModal(els.controlModal);

			window.setTimeout(function() {
				completeSelectedOrders();
			}, 180);
		});


		if (els.modalBulkHandlerBtn) {
			els.modalBulkHandlerBtn.addEventListener('click', function() {
				hideModal(els.controlModal);
				window.setTimeout(openBulkHandlerModal, 180);
			});
		}

		if (els.modalBulkDeliveryMethodBtn) {
			els.modalBulkDeliveryMethodBtn.addEventListener('click', function() {
				hideModal(els.controlModal);
				window.setTimeout(openBulkDeliveryMethodModal, 180);
			});
		}

		if (els.controlModal) {
			els.controlModal.addEventListener('show.bs.modal', function() {
				copyMainFiltersToModal();
			});
		}
		if (els.excelBtn) {
			els.excelBtn.addEventListener('click', downloadExcel);
		}

		bindStatementButton(
			els.statementSiteHorizontalPrintBtn,
			function() {
				printDeliveryStatementLayout('HORIZONTAL', 'SITE', els.statementSiteHorizontalPrintBtn);
			}
		);
		bindStatementButton(
			els.statementParcelHorizontalPrintBtn,
			function() {
				printDeliveryStatementLayout('HORIZONTAL', 'PARCEL', els.statementParcelHorizontalPrintBtn);
			}
		);
		bindStatementButton(
			els.statementSiteHorizontalDownloadBtn,
			function() {
				downloadDeliveryStatementLayout('HORIZONTAL', 'SITE', els.statementSiteHorizontalDownloadBtn);
			}
		);
		bindStatementButton(
			els.statementParcelHorizontalDownloadBtn,
			function() {
				downloadDeliveryStatementLayout('HORIZONTAL', 'PARCEL', els.statementParcelHorizontalDownloadBtn);
			}
		);

		/* 세로형 버튼은 현재 HTML 주석 상태지만 기능은 완성되어 있습니다. */
		bindStatementButton(
			els.statementSiteVerticalPrintBtn,
			function() {
				printDeliveryStatementLayout('VERTICAL', 'SITE', els.statementSiteVerticalPrintBtn);
			}
		);
		bindStatementButton(
			els.statementParcelVerticalPrintBtn,
			function() {
				printDeliveryStatementLayout('VERTICAL', 'PARCEL', els.statementParcelVerticalPrintBtn);
			}
		);
		bindStatementButton(
			els.statementSiteVerticalDownloadBtn,
			function() {
				downloadDeliveryStatementLayout('VERTICAL', 'SITE', els.statementSiteVerticalDownloadBtn);
			}
		);
		bindStatementButton(
			els.statementParcelVerticalDownloadBtn,
			function() {
				downloadDeliveryStatementLayout('VERTICAL', 'PARCEL', els.statementParcelVerticalDownloadBtn);
			}
		);
		if (els.deliveryModal) {
			els.deliveryModal.addEventListener('click', function(event) {
				const btn = event.target.closest('.dispatch-list-delivery-method-option');
				if (!btn) {
					return;
				}

				selectDeliveryMethodInModal(btn);
			});
		}

		if (els.deliveryHandlerId) {
			els.deliveryHandlerId.addEventListener('change', updateDeliveryMethodSaveButtonState);
		}

		if (els.deliveryMethodSaveBtn) {
			els.deliveryMethodSaveBtn.addEventListener('click', function() {
				const orderId = Number(els.deliveryModalOrderId.value);
				const deliveryMethodId = Number(els.deliveryModalSelectedMethodId.value);
				const deliveryHandlerId = numberOrNull(valueOf(els.deliveryHandlerId));

				updateDeliveryMethod(orderId, deliveryMethodId, deliveryHandlerId);
			});
		}

		if (els.bulkHandlerId) {
			els.bulkHandlerId.addEventListener('change', updateBulkHandlerSaveState);
		}

		if (els.bulkHandlerExcludeConfirmBtn) {
			els.bulkHandlerExcludeConfirmBtn.addEventListener('click', function() {
				state.bulkHandlerExclusionAcknowledged = true;
				els.bulkHandlerExcludeConfirmBtn.disabled = true;
				setVisible(els.bulkHandlerExcludeConfirmed, true);
				updateBulkHandlerSaveState();
			});
		}

		if (els.bulkHandlerSaveBtn) {
			els.bulkHandlerSaveBtn.addEventListener('click', submitBulkHandlerChange);
		}

		if (els.bulkHandlerModal) {
			els.bulkHandlerModal.addEventListener('hidden.bs.modal', resetBulkHandlerModal);
		}

		if (els.bulkMethodId) {
			els.bulkMethodId.addEventListener('change', loadBulkDeliveryMethodPreview);
		}

		if (els.bulkMethodRemovalConfirmBtn) {
			els.bulkMethodRemovalConfirmBtn.addEventListener('click', function() {
				state.bulkMethodRemovalAcknowledged = true;
				els.bulkMethodRemovalConfirmBtn.disabled = true;
				setVisible(els.bulkMethodRemovalConfirmed, true);
				updateBulkDeliveryMethodSaveState();
			});
		}

		if (els.bulkMethodAssignmentList) {
			els.bulkMethodAssignmentList.addEventListener('change', function(event) {
				if (event.target.classList.contains('dispatch-list-bulk-method-handler-select')) {
					updateBulkDeliveryMethodSaveState();
				}
			});
		}

		if (els.bulkMethodSaveBtn) {
			els.bulkMethodSaveBtn.addEventListener('click', submitBulkDeliveryMethodChange);
		}

		if (els.bulkMethodModal) {
			els.bulkMethodModal.addEventListener('hidden.bs.modal', resetBulkDeliveryMethodModal);
		}

		if (els.confirmOkBtn) {
			els.confirmOkBtn.addEventListener('click', function() {
				if (pendingConfirmResolver) {
					const resolver = pendingConfirmResolver;
					pendingConfirmResolver = null;
					resolver(true);
				}

				hideModal(els.confirmModal);
			});
		}

		if (els.confirmModal) {
			els.confirmModal.addEventListener('hidden.bs.modal', function() {
				if (pendingConfirmResolver) {
					const resolver = pendingConfirmResolver;
					pendingConfirmResolver = null;
					resolver(false);
				}
			});
		}
		window.addEventListener('scroll', throttle(function() {
			if (state.loading || !state.hasNext) {
				return;
			}

			const scrollBottom = window.innerHeight + window.scrollY;
			const documentHeight = document.documentElement.scrollHeight;

			if (documentHeight - scrollBottom < 420) {
				searchOrders(false);
			}
		}, 180));
	}

	async function searchOrders(reset) {
		if (state.loading) {
			return;
		}

		if (reset) {
			resetListState();
			renderLoadingRow('출고 데이터를 불러오는 중입니다.');
		}

		state.loading = true;
		updateMoreStatus('조회 중입니다.');

		try {
			const payload = buildSearchPayload();

			if (!reset) {
				payload.lastStatusSort = state.lastStatusSort;
				payload.lastOrderId = state.lastOrderId;
				payload.loadedOrderIds = state.loadedOrderIds.slice();
			}

			const response = await fetch(API.search, {
				method: 'POST',
				headers: buildJsonHeaders(),
				body: JSON.stringify(payload)
			});

			const data = await parseJsonResponse(response);

			if (reset) {
				els.tbody.innerHTML = '';
			}

			appendRows(data.orders || []);

			state.hasNext = !!data.hasNext;
			state.lastStatusSort = data.nextLastStatusSort || null;
			state.lastOrderId = data.nextLastOrderId || null;

			if (!data.orders || data.orders.length === 0) {
				if (state.loadedOrderIds.length === 0) {
					renderEmptyRow('조회된 출고 대상 주문이 없습니다.');
				}
			}

			updateCounts();
			updateCheckAllState();

			if (state.hasNext) {
				updateMoreStatus('스크롤하면 50개씩 추가 조회됩니다.');
			} else {
				updateMoreStatus('마지막 데이터까지 조회되었습니다.');
			}

		} catch (error) {
			console.error(error);

			if (state.loadedOrderIds.length === 0) {
				renderEmptyRow(error.message || '조회 중 오류가 발생했습니다.');
			}

			updateMoreStatus(error.message || '조회 중 오류가 발생했습니다.');
		} finally {
			state.loading = false;
		}
	}

	function buildSearchPayload() {
		const orderIdFrom = validatePositiveOrderId(valueOf(els.orderIdFrom), 'Order ID From');
		const orderIdTo = validatePositiveOrderId(valueOf(els.orderIdTo), 'Order ID To');
		if (orderIdFrom !== null && orderIdTo !== null && orderIdFrom > orderIdTo) {
			throw new Error('Order ID From은 To보다 클 수 없습니다.');
		}

		return {
			keywordType: valueOf(els.keywordType),
			keyword: valueOf(els.keyword),
			productCategoryId: numberOrNull(valueOf(els.productCategoryId)),
			standard: valueOf(els.standard) || 'ALL',
			doName: selectedOptionName(els.provinceId),
			siName: selectedOptionName(els.cityId),
			guName: selectedOptionName(els.districtId),
			orderDate: valueOf(els.orderDate),
			orderIdFrom: orderIdFrom,
			orderIdTo: orderIdTo,
			deliveryMethodId: numberOrNull(valueOf(els.deliveryMethodId)),
			size: 50,
			lastStatusSort: null,
			lastOrderId: null,
			loadedOrderIds: []
		};
	}

	function resetListState() {
		state.loading = false;
		state.hasNext = true;
		state.lastStatusSort = null;
		state.lastOrderId = null;
		state.loadedOrderIds = [];
		state.selectedOrderIds.clear();
		els.checkAll.checked = false;
		els.checkAll.indeterminate = false;
		updateCounts();
	}

	function appendRows(rows) {
		if (!rows || rows.length === 0) {
			return;
		}

		const fragment = document.createDocumentFragment();

		rows.forEach(function(row) {
			if (row && row.orderId !== undefined && row.orderId !== null) {
				state.loadedOrderIds.push(Number(row.orderId));
			}

			fragment.appendChild(buildRow(row));
		});

		els.tbody.appendChild(fragment);
	}

	function buildRow(row) {
		const tr = document.createElement('tr');
		tr.className = 'dispatch-list-row ' + rowClassByStatus(row.status);
		tr.setAttribute('data-order-id', row.orderId);
		tr.setAttribute('data-status', row.status || '');
		tr.setAttribute('data-status-sort', row.statusSort || '');
		tr.setAttribute('data-dispatch-completable', row.dispatchCompletable ? 'true' : 'false');

		const deliveryCompleted = normalizeStatus(row.status) === 'DELIVERY_DONE';
		const checkboxDisabled = deliveryCompleted ? 'disabled' : '';
		const completeDisabled = !row.dispatchCompletable ? 'disabled' : '';
		const deliveryMethodDisabled = deliveryCompleted ? 'disabled' : '';
		const adminRequestButton = '<button type="button" class="btn btn-outline-danger dispatch-list-admin-request-btn"' +
			' data-order-admin-request data-order-id="' + escapeAttr(row.orderId) + '"' +
			' data-admin-request-message="출고 절차 오류 또는 출고 확인이 필요합니다."' +
			' title="발주 상태와 무관하게 이 발주의 관리자 담당자에게 긴급 확인을 요청합니다.">관리자요청</button>';

		tr.innerHTML = [
			'<td>',
			'  <div class="dispatch-list-order-cell">',
			'    <input type="checkbox" class="dispatch-list-row-check" data-order-id="' + escapeAttr(row.orderId) + '" ' + checkboxDisabled + '>',
			'    <span class="dispatch-list-order-id">#' + escapeHtml(row.orderId) + '</span>',
			'  </div>',
			'</td>',

			'<td>',
			'  <span class="dispatch-list-cell-main" title="' + escapeAttr(row.productCategoryName) + '">' + escapeHtml(row.productCategoryName) + '</span>',
			'  <span class="dispatch-list-cell-sub">' + escapeHtml(row.standardLabel) + '</span>',
			'</td>',

			'<td>',
			'  <span class="dispatch-list-cell-main" title="' + escapeAttr(row.companyName) + '">' + escapeHtml(row.companyName) + '</span>',
			'  <span class="dispatch-list-cell-sub" title="' + escapeAttr(buildMemberTooltip(row)) + '">' + escapeHtml(row.memberName) + ' / ' + escapeHtml(row.memberUsername) + '</span>',
			'</td>',

			'<td>',
			'  <span class="dispatch-list-cell-main" title="' + escapeAttr(row.productName) + '">' + escapeHtml(row.productName) + '</span>',
			'  <span class="dispatch-list-cell-sub">' + escapeHtml(row.createdAtText) + '</span>',
			'</td>',

			'<td>',
			'  <span class="dispatch-list-cell-main" title="' + escapeAttr(row.color) + '">' + escapeHtml(row.color) + '</span>',
			'</td>',

			'<td>',
			'  <span class="dispatch-list-cell-main" title="' + escapeAttr(row.sizeText) + '">' + escapeHtml(row.sizeText) + '</span>',
			'  <span class="dispatch-list-cell-sub" title="' + escapeAttr(row.fullAddress) + '">' + escapeHtml(row.fullAddress) + '</span>',
			'</td>',

			'<td class="text-center">',
			'  <span class="dispatch-list-cell-main">' + escapeHtml(row.quantity) + '</span>',
			'</td>',

			'<td>',
			'  <div class="dispatch-list-admin-memo" title="' + escapeAttr(row.adminMemo) + '">' + escapeHtml(row.adminMemo) + '</div>',
			'</td>',

			'<td>',
			'  <button type="button" class="btn btn-outline-primary dispatch-list-delivery-method-btn"',
			'    data-order-id="' + escapeAttr(row.orderId) + '"',
			'    data-delivery-method-id="' + escapeAttr(row.deliveryMethodId || '') + '"',
			'    data-delivery-method-name="' + escapeAttr(row.deliveryMethodName || '') + '"',
			'    data-delivery-handler-id="' + escapeAttr(row.deliveryHandlerId || '') + '"',
			'    data-delivery-handler-name="' + escapeAttr(row.deliveryHandlerName || '') + '"',
			'    data-delivery-order-index="' + escapeAttr(row.deliveryOrderIndex || '') + '" ' + deliveryMethodDisabled,
			'    title="' + escapeAttr(deliveryCompleted
				? '배송완료된 발주는 배송수단을 변경할 수 없습니다.'
				: buildDeliveryMethodTitleFromRow(row)) + '">',
			'    ' + escapeHtml(row.deliveryMethodName),
			'  </button>',
			'</td>',

			'<td class="text-center">',
			'  <button type="button" class="btn btn-success dispatch-list-complete-btn"',
			'    data-order-id="' + escapeAttr(row.orderId) + '" ' + completeDisabled + '>',
			'    출고완료(' + escapeHtml(row.statusLabel || '-') + ')',
			'  </button>',
			'  ' + adminRequestButton,
			'</td>'
		].join('');

		return tr;
	}

	function buildStatusBadge(row) {
		return '<span class="dispatch-list-status-badge ' + statusBadgeClass(row.status) + '">' +
			escapeHtml(row.statusLabel || '-') +
			'</span>';
	}

	function buildMemberTooltip(row) {
		return [
			'이름: ' + toText(row.memberName),
			'아이디: ' + toText(row.memberUsername),
			'휴대폰: ' + toText(row.memberPhone),
			'이메일: ' + toText(row.memberEmail)
		].join('\n');
	}

	function handleCheckAllChange() {
		const checked = els.checkAll.checked;

		document.querySelectorAll('.dispatch-list-row-check:not(:disabled)').forEach(function(checkbox) {
			checkbox.checked = checked;

			const orderId = Number(checkbox.getAttribute('data-order-id'));
			if (checked) {
				state.selectedOrderIds.add(orderId);
			} else {
				state.selectedOrderIds.delete(orderId);
			}
		});

		updateCounts();
		updateCheckAllState();
	}

	function handleRowCheckChange(checkbox) {
		const orderId = Number(checkbox.getAttribute('data-order-id'));

		if (checkbox.checked) {
			state.selectedOrderIds.add(orderId);
		} else {
			state.selectedOrderIds.delete(orderId);
		}

		updateCounts();
		updateCheckAllState();
	}

	function updateCheckAllState() {
		const enabledCheckboxes = Array.from(document.querySelectorAll('.dispatch-list-row-check:not(:disabled)'));

		if (enabledCheckboxes.length === 0) {
			els.checkAll.checked = false;
			els.checkAll.indeterminate = false;
			els.checkAll.disabled = true;
			return;
		}

		els.checkAll.disabled = false;

		const checkedCount = enabledCheckboxes.filter(function(checkbox) {
			return checkbox.checked;
		}).length;

		els.checkAll.checked = checkedCount === enabledCheckboxes.length;
		els.checkAll.indeterminate = checkedCount > 0 && checkedCount < enabledCheckboxes.length;
	}

	function updateCounts() {
		const loadedCount = state.loadedOrderIds.length;
		const selectedCount = state.selectedOrderIds.size;
		const completableSelectedCount = selectedCompletableOrderIds().length;

		setText(els.loadedCount, loadedCount);
		setText(els.selectedCount, selectedCount);
		setText(els.floatingSelectedCount, selectedCount);
		setText(els.modalSelectedCount, completableSelectedCount);

		setText(els.modalLoadedCount, loadedCount);
		setText(els.modalSelectedCountInfo, selectedCount);
		setText(els.modalCompleteCount, completableSelectedCount);

		setDisabled(els.bulkCompleteBtn, state.completeBusy || completableSelectedCount === 0);
		setDisabled(els.modalBulkCompleteBtn, state.completeBusy || completableSelectedCount === 0);
		setDisabled(els.bulkHandlerBtn, selectedCount === 0);
		setDisabled(els.bulkDeliveryMethodBtn, selectedCount === 0);
		setDisabled(els.modalBulkHandlerBtn, selectedCount === 0);
		setDisabled(els.modalBulkDeliveryMethodBtn, selectedCount === 0);
		statementLayoutButtons().forEach(function(button) {
			setDisabled(button, selectedCount === 0);
		});
	}

	function completeSelectedOrders() {
		const orderIds = selectedCompletableOrderIds();

		if (orderIds.length === 0) {
			alertMessage('선택한 주문 중 출고완료 처리 가능한 주문이 없습니다.');
			return;
		}

		completeOrders(orderIds);
	}

	async function completeOrders(orderIds) {
		if (state.completeBusy) {
			return;
		}

		const normalizedOrderIds = (orderIds || [])
			.map(function(id) {
				return Number(id);
			})
			.filter(function(id) {
				return Number.isFinite(id) && id > 0;
			});

		if (normalizedOrderIds.length === 0) {
			alertMessage('출고완료 처리할 주문이 없습니다.');
			return;
		}

		const confirmed = await confirmMessage(
			'선택한 ' + normalizedOrderIds.length + '건을 출고완료 처리하시겠습니까?'
		);

		if (!confirmed) {
			return;
		}

		state.completeBusy = true;
		setDispatchCompleteControlsBusy(true);

		const actionFeedback = window.TeamActionFeedback || null;
		const feedbackToken = actionFeedback
			? actionFeedback.begin({
				eyebrow: '출고팀 작업 처리 중',
				title: '출고완료를 반영하고 있습니다.',
				message: normalizedOrderIds.length + '건의 주문 상태와 알림을 처리하고 있습니다.',
				detail: '처리가 끝날 때까지 같은 버튼을 다시 누르지 마세요.'
			})
			: null;

		try {
			const response = await fetch(API.complete, {
				method: 'POST',
				headers: buildJsonHeaders(),
				body: JSON.stringify({
					orderIds: normalizedOrderIds
				})
			});

			const data = await parseJsonResponse(response);

			(data.updatedRows || []).forEach(function(row) {
				applyUpdatedRow(row);
				state.selectedOrderIds.delete(Number(row.orderId));
			});

			updateCounts();
			updateCheckAllState();

			let message = '출고완료 처리되었습니다.';

			if (data.failedCount && data.failedCount > 0) {
				message += '\n실패 ' + data.failedCount + '건이 있습니다.';
			}

			if (actionFeedback) {
				await actionFeedback.success({
					title: '출고완료 처리되었습니다.',
					message: message.replace(/\n/g, ' · '),
					detail: data.failedCount && data.failedCount > 0
						? '실패 항목은 현재 화면에 남아 있습니다. 내용을 확인해 다시 처리해 주세요.'
						: '현재 화면의 주문 상태와 선택 항목을 갱신했습니다.'
				}, feedbackToken);
			} else {
				alertMessage(message);
			}

		} catch (error) {
			console.error(error);
			const message = error.message || '출고완료 처리 중 오류가 발생했습니다.';

			if (actionFeedback) {
				await actionFeedback.error({
					title: '출고완료 처리에 실패했습니다.',
					message: message,
					detail: '선택 상태는 유지됩니다. 오류 내용을 확인한 뒤 다시 시도해 주세요.'
				}, feedbackToken);
			} else {
				alertMessage(message);
			}
		} finally {
			state.completeBusy = false;
			setDispatchCompleteControlsBusy(false);
		}
	}

	function setDispatchCompleteControlsBusy(busy) {
		updateCounts();

		document.querySelectorAll('.dispatch-list-complete-btn').forEach(function(button) {
			const row = button.closest('.dispatch-list-row');
			const completable = row && row.getAttribute('data-dispatch-completable') === 'true';
			button.disabled = Boolean(busy) || !completable;
		});
	}

	function applyUpdatedRow(row) {
		if (!row || row.orderId === undefined || row.orderId === null) {
			return;
		}

		const tr = document.querySelector('.dispatch-list-row[data-order-id="' + cssEscape(row.orderId) + '"]');
		if (!tr) {
			return;
		}

		tr.setAttribute('data-status', row.status || '');
		tr.setAttribute('data-status-sort', row.statusSort || '');
		tr.setAttribute('data-dispatch-completable', row.dispatchCompletable ? 'true' : 'false');
		tr.classList.remove('dispatch-list-row-confirmed', 'dispatch-list-row-production-done', 'dispatch-list-row-dispatch-done');
		tr.classList.add(rowClassByStatus(row.status));

		const checkbox = tr.querySelector('.dispatch-list-row-check');
		if (checkbox) {
			checkbox.checked = false;
			checkbox.disabled = false;
		}

		const completeBtn = tr.querySelector('.dispatch-list-complete-btn');
		if (completeBtn) {
			completeBtn.disabled = true;
			completeBtn.textContent = '출고완료(' + toText(row.statusLabel || '-') + ')';
		}
	}

	function openDeliveryMethodModal(button) {
		const orderId = button.getAttribute('data-order-id');

		state.activeDeliveryOrderId = Number(orderId);
		state.activeDeliveryHandlerId = numberOrNull(button.getAttribute('data-delivery-handler-id'));
		els.deliveryModalOrderId.value = orderId;

		if (els.deliveryModalSelectedMethodId) {
			els.deliveryModalSelectedMethodId.value = '';
		}

		if (els.deliveryHandlerId) {
			els.deliveryHandlerId.value = state.activeDeliveryHandlerId || '';
		}

		if (els.directHandlerArea) {
			els.directHandlerArea.classList.add('d-none');
		}

		if (els.deliveryMethodSaveBtn) {
			els.deliveryMethodSaveBtn.disabled = true;
		}

		document.querySelectorAll('.dispatch-list-delivery-method-option').forEach(function(optionBtn) {
			optionBtn.classList.remove('active');
		});

		showModal(els.deliveryModal);
	}

	function selectDeliveryMethodInModal(button) {
		const deliveryMethodId = button.getAttribute('data-delivery-method-id');
		const handlerRequired = isHandlerRequiredMethodName(
			button.getAttribute('data-delivery-method-name')
		);

		document.querySelectorAll('.dispatch-list-delivery-method-option').forEach(function(optionBtn) {
			optionBtn.classList.remove('active');
		});

		button.classList.add('active');
		els.deliveryModalSelectedMethodId.value = deliveryMethodId;

		if (handlerRequired) {
			els.directHandlerArea.classList.remove('d-none');
			if (!valueOf(els.deliveryHandlerId) && state.activeDeliveryHandlerId) {
				els.deliveryHandlerId.value = state.activeDeliveryHandlerId;
			}
		} else {
			els.directHandlerArea.classList.add('d-none');
			els.deliveryHandlerId.value = '';
		}

		updateDeliveryMethodSaveButtonState();
	}

	function updateDeliveryMethodSaveButtonState() {
		const deliveryMethodId = numberOrNull(valueOf(els.deliveryModalSelectedMethodId));

		if (!deliveryMethodId) {
			els.deliveryMethodSaveBtn.disabled = true;
			return;
		}

		const selectedButton = document.querySelector('.dispatch-list-delivery-method-option.active');
		const handlerRequired = selectedButton
			&& isHandlerRequiredMethodName(selectedButton.getAttribute('data-delivery-method-name'));

		els.deliveryMethodSaveBtn.disabled = !!(
			handlerRequired && !numberOrNull(valueOf(els.deliveryHandlerId))
		);
	}

	async function updateDeliveryMethod(orderId, deliveryMethodId, deliveryHandlerId) {
		if (!orderId || !deliveryMethodId) {
			alertMessage('배송수단 변경 정보가 올바르지 않습니다.');
			return;
		}

		const originalHtml = els.deliveryMethodSaveBtn ? els.deliveryMethodSaveBtn.innerHTML : '';
		const actionFeedback = window.TeamActionFeedback || null;
		const feedbackToken = actionFeedback
			? actionFeedback.begin({
				eyebrow: '출고팀 설정 변경 중',
				title: '배송수단을 변경하고 있습니다.',
				message: '주문 정보와 담당자 배정 내용을 안전하게 반영하고 있습니다.'
			})
			: null;

		try {
			if (els.deliveryMethodSaveBtn) {
				els.deliveryMethodSaveBtn.disabled = true;
				els.deliveryMethodSaveBtn.innerHTML = '<i class="ri-loader-4-line me-1"></i>변경 중';
			}

			const response = await fetch(API.updateDeliveryMethod(orderId), {
				method: 'POST',
				headers: buildJsonHeaders(),
				body: JSON.stringify({
					deliveryMethodId: deliveryMethodId,
					deliveryHandlerId: deliveryHandlerId || null
				})
			});

			const data = await parseJsonResponse(response);
			applyDeliveryMethodDto(orderId, data);
			hideModal(els.deliveryModal);

			let message = (data.methodName || '배송수단') + '으로 변경되었습니다.';
			if (data.deliveryHandlerId || data.deliveryHandlerName) {
				message += '\n담당자: ' + (data.deliveryHandlerName || '-');
				message += '\n배송순번: ' + (data.deliveryOrderIndex || '-');
			}

			if (actionFeedback) {
				await actionFeedback.success({
					title: '배송수단이 변경되었습니다.',
					message: message.replace(/\n/g, ' · '),
					detail: '현재 목록의 배송수단과 담당자 정보를 갱신했습니다.'
				}, feedbackToken);
			} else {
				window.setTimeout(function() {
					alertMessage(message);
				}, 180);
			}
		} catch (error) {
			console.error(error);
			const message = error.message || '배송수단 변경 중 오류가 발생했습니다.';

			if (actionFeedback) {
				await actionFeedback.error({
					title: '배송수단 변경에 실패했습니다.',
					message: message,
					detail: '선택한 배송수단과 담당자를 확인한 뒤 다시 시도해 주세요.'
				}, feedbackToken);
			} else {
				alertMessage(message);
			}
		} finally {
			if (els.deliveryMethodSaveBtn && originalHtml) {
				els.deliveryMethodSaveBtn.innerHTML = originalHtml;
				updateDeliveryMethodSaveButtonState();
			}
		}
	}

	function applyDeliveryMethodDto(orderId, data) {
		document.querySelectorAll('.dispatch-list-delivery-method-btn[data-order-id="' + cssEscape(orderId) + '"]')
			.forEach(function(button) {
				button.setAttribute('data-delivery-method-id', data.id || '');
				button.setAttribute('data-delivery-method-name', data.methodName || '');
				button.setAttribute('data-delivery-handler-id', data.deliveryHandlerId || '');
				button.setAttribute('data-delivery-handler-name', data.deliveryHandlerName || '');
				button.setAttribute('data-delivery-order-index', data.deliveryOrderIndex || '');
				button.setAttribute('title', buildDeliveryMethodTitle(data));
				button.textContent = data.methodName || '미지정';
			});
	}

	function applyDeliveryRowUpdate(row) {
		if (!row || row.orderId === undefined || row.orderId === null) {
			return;
		}

		document.querySelectorAll('.dispatch-list-delivery-method-btn[data-order-id="' + cssEscape(row.orderId) + '"]')
			.forEach(function(button) {
				button.setAttribute('data-delivery-method-id', row.deliveryMethodId || '');
				button.setAttribute('data-delivery-method-name', row.deliveryMethodName || '');
				button.setAttribute('data-delivery-handler-id', row.deliveryHandlerId || '');
				button.setAttribute('data-delivery-handler-name', row.deliveryHandlerName || '');
				button.setAttribute('data-delivery-order-index', row.deliveryOrderIndex || '');
				button.setAttribute('title', buildDeliveryMethodTitleFromRow(row));
				button.textContent = row.deliveryMethodName || '미지정';
			});
	}

	function buildDeliveryMethodTitle(data) {
		if (!data) {
			return '미지정';
		}

		const methodName = data.methodName || data.deliveryMethodName || '미지정';
		const handlerName = data.deliveryHandlerName || '';
		const orderIndex = data.deliveryOrderIndex;

		if (!handlerName && !data.deliveryHandlerId) {
			return methodName;
		}

		return methodName
			+ ' / 담당자: ' + (handlerName || '-')
			+ ' / 순번: ' + (orderIndex || '-');
	}

	function buildDeliveryMethodTitleFromRow(row) {
		return buildDeliveryMethodTitle({
			methodName: row ? row.deliveryMethodName : '',
			deliveryHandlerId: row ? row.deliveryHandlerId : null,
			deliveryHandlerName: row ? row.deliveryHandlerName : null,
			deliveryOrderIndex: row ? row.deliveryOrderIndex : null
		});
	}

	function openBulkHandlerModal() {
		const orderIds = selectedOrderIdsSnapshot();

		if (orderIds.length === 0) {
			alertMessage('담당자를 변경할 주문을 1건 이상 선택해 주세요.');
			return;
		}

		resetBulkHandlerModal();
		state.bulkHandlerOrderIds = orderIds;
		setText(els.bulkHandlerRequestedCount, orderIds.length);
		showModal(els.bulkHandlerModal);
		loadBulkHandlerPreview();
	}

	function resetBulkHandlerModal() {
		state.bulkHandlerPreview = null;
		state.bulkHandlerExclusionAcknowledged = false;
		state.bulkHandlerBusy = false;
		state.bulkHandlerPreviewSequence++;
		state.bulkHandlerOrderIds = [];

		setText(els.bulkHandlerRequestedCount, 0);
		setText(els.bulkHandlerChangeableCount, 0);
		setText(els.bulkHandlerExcludedCount, 0);
		setText(els.bulkHandlerExcludedMessageCount, 0);
		setVisible(els.bulkHandlerLoading, true);
		setVisible(els.bulkHandlerBlockingArea, false);
		setVisible(els.bulkHandlerExcludedArea, false);
		setVisible(els.bulkHandlerChangeableArea, false);
		setVisible(els.bulkHandlerExcludeConfirmed, false);
		clearElement(els.bulkHandlerBlockingList);
		clearElement(els.bulkHandlerExcludedList);
		clearElement(els.bulkHandlerChangeableList);

		if (els.bulkHandlerExcludeConfirmBtn) {
			els.bulkHandlerExcludeConfirmBtn.disabled = false;
		}
		if (els.bulkHandlerId) {
			els.bulkHandlerId.value = '';
			els.bulkHandlerId.disabled = true;
		}
		if (els.bulkHandlerSaveBtn) {
			els.bulkHandlerSaveBtn.disabled = true;
			els.bulkHandlerSaveBtn.innerHTML = '<i class="ri-user-settings-line me-1"></i>변경';
		}
	}

	async function loadBulkHandlerPreview() {
		const requestSequence = ++state.bulkHandlerPreviewSequence;

		try {
			const response = await fetch(API.bulkHandlerPreview, {
				method: 'POST',
				headers: buildJsonHeaders(),
				body: JSON.stringify({
					orderIds: state.bulkHandlerOrderIds
				})
			});

			const data = await parseJsonResponse(response);

			if (requestSequence !== state.bulkHandlerPreviewSequence) {
				return;
			}

			state.bulkHandlerPreview = data;
			renderBulkHandlerPreview(data);
		} catch (error) {
			if (requestSequence !== state.bulkHandlerPreviewSequence) {
				return;
			}

			console.error(error);
			setVisible(els.bulkHandlerLoading, false);
			showBulkInlineError(
				els.bulkHandlerBlockingArea,
				els.bulkHandlerBlockingList,
				error.message || '선택 주문을 확인하지 못했습니다.'
			);
			updateBulkHandlerSaveState();
		}
	}

	function renderBulkHandlerPreview(data) {
		const changeableOrders = data.changeableOrders || [];
		const excludedOrders = data.excludedOrders || [];
		const blockingOrders = data.blockingOrders || [];

		setVisible(els.bulkHandlerLoading, false);
		setText(els.bulkHandlerRequestedCount, data.requestedCount || state.bulkHandlerOrderIds.length);
		setText(els.bulkHandlerChangeableCount, data.changeableCount || 0);
		setText(els.bulkHandlerExcludedCount, data.excludedCount || 0);
		setText(els.bulkHandlerExcludedMessageCount, data.excludedCount || 0);

		renderBulkOrderInfoList(els.bulkHandlerBlockingList, blockingOrders);
		renderBulkOrderInfoList(els.bulkHandlerExcludedList, excludedOrders);
		renderBulkOrderInfoList(els.bulkHandlerChangeableList, changeableOrders);

		setVisible(els.bulkHandlerBlockingArea, blockingOrders.length > 0);
		setVisible(els.bulkHandlerExcludedArea, excludedOrders.length > 0);
		setVisible(els.bulkHandlerChangeableArea, changeableOrders.length > 0);

		state.bulkHandlerExclusionAcknowledged = excludedOrders.length === 0;
		setVisible(els.bulkHandlerExcludeConfirmed, false);
		if (els.bulkHandlerExcludeConfirmBtn) {
			els.bulkHandlerExcludeConfirmBtn.disabled = false;
		}

		if (els.bulkHandlerId) {
			els.bulkHandlerId.disabled = blockingOrders.length > 0 || changeableOrders.length === 0;
		}

		updateBulkHandlerSaveState();
	}

	function updateBulkHandlerSaveState() {
		if (!els.bulkHandlerSaveBtn) {
			return;
		}

		const preview = state.bulkHandlerPreview;
		const hasHandler = !!numberOrNull(valueOf(els.bulkHandlerId));
		const canProceed = !!preview
			&& Number(preview.changeableCount || 0) > 0
			&& Number(preview.blockingCount || 0) === 0
			&& (Number(preview.excludedCount || 0) === 0 || state.bulkHandlerExclusionAcknowledged)
			&& hasHandler
			&& !state.bulkHandlerBusy;

		els.bulkHandlerSaveBtn.disabled = !canProceed;
	}

	async function submitBulkHandlerChange() {
		updateBulkHandlerSaveState();
		if (!els.bulkHandlerSaveBtn || els.bulkHandlerSaveBtn.disabled) {
			return;
		}

		state.bulkHandlerBusy = true;
		els.bulkHandlerSaveBtn.disabled = true;
		els.bulkHandlerSaveBtn.innerHTML = '<i class="ri-loader-4-line me-1"></i>변경 중';

		const actionFeedback = window.TeamActionFeedback || null;
		const requestedCount = Number(
			state.bulkHandlerPreview && state.bulkHandlerPreview.changeableCount
			|| state.bulkHandlerOrderIds.length
			|| 0
		);
		const feedbackToken = actionFeedback
			? actionFeedback.begin({
				eyebrow: '출고팀 설정 변경 중',
				title: '담당자를 일괄 변경하고 있습니다.',
				message: requestedCount + '건의 담당자 배정과 배송순서를 다시 계산하고 있습니다.'
			})
			: null;

		try {
			const response = await fetch(API.bulkHandler, {
				method: 'POST',
				headers: buildJsonHeaders(),
				body: JSON.stringify({
					orderIds: state.bulkHandlerOrderIds,
					deliveryHandlerId: numberOrNull(valueOf(els.bulkHandlerId)),
					excludeUnavailable: state.bulkHandlerExclusionAcknowledged,
					acknowledgedExcludedOrderIds: state.bulkHandlerExclusionAcknowledged
						? orderIdsFromBulkItems(state.bulkHandlerPreview && state.bulkHandlerPreview.excludedOrders)
						: []
				})
			});

			const data = await parseJsonResponse(response);
			(data.updatedRows || []).forEach(applyDeliveryRowUpdate);
			clearSelectionForIds(data.updatedOrderIds || []);
			hideModal(els.bulkHandlerModal);

			let message = '담당자 ' + (data.updatedCount || 0) + '건이 변경되었습니다.';
			if (data.excludedCount) {
				message += '\n담당자 지정 불가 ' + data.excludedCount + '건은 제외되었습니다.';
			}
			if (actionFeedback) {
				await actionFeedback.success({
					title: '담당자 변경이 완료되었습니다.',
					message: message.replace(/\n/g, ' · '),
					detail: '현재 목록의 담당자와 배송순서 정보를 갱신했습니다.'
				}, feedbackToken);
			} else {
				window.setTimeout(function() {
					alertMessage(message);
				}, 180);
			}
		} catch (error) {
			console.error(error);
			state.bulkHandlerPreview = null;
			const message = error.message || '일괄 담당자 변경 중 오류가 발생했습니다.';
			showBulkInlineError(
				els.bulkHandlerBlockingArea,
				els.bulkHandlerBlockingList,
				message
			);

			if (actionFeedback) {
				await actionFeedback.error({
					title: '담당자 변경에 실패했습니다.',
					message: message,
					detail: '오류 내용을 모달에 표시했습니다. 대상 주문과 담당자를 다시 확인해 주세요.'
				}, feedbackToken);
			}
		} finally {
			state.bulkHandlerBusy = false;
			if (els.bulkHandlerSaveBtn) {
				els.bulkHandlerSaveBtn.innerHTML = '<i class="ri-user-settings-line me-1"></i>변경';
			}
			updateBulkHandlerSaveState();
		}
	}

	function openBulkDeliveryMethodModal() {
		const orderIds = selectedOrderIdsSnapshot();

		if (orderIds.length === 0) {
			alertMessage('배송수단을 변경할 주문을 1건 이상 선택해 주세요.');
			return;
		}

		resetBulkDeliveryMethodModal();
		state.bulkMethodOrderIds = orderIds;
		setText(els.bulkMethodRequestedCount, orderIds.length);
		showModal(els.bulkMethodModal);
	}

	function resetBulkDeliveryMethodModal() {
		state.bulkMethodPreview = null;
		state.bulkMethodRemovalAcknowledged = false;
		state.bulkMethodBusy = false;
		state.bulkMethodPreviewSequence++;
		state.bulkMethodOrderIds = [];

		if (els.bulkMethodId) {
			els.bulkMethodId.value = '';
			els.bulkMethodId.disabled = false;
		}

		setText(els.bulkMethodRequestedCount, 0);
		setText(els.bulkMethodRequiredCount, 0);
		setText(els.bulkMethodRemovalCount, 0);
		setText(els.bulkMethodRemovalMessageCount, 0);
		setText(els.bulkMethodAssignmentMessageCount, 0);
		setVisible(els.bulkMethodPlaceholder, true);
		setVisible(els.bulkMethodLoading, false);
		setVisible(els.bulkMethodBlockingArea, false);
		setVisible(els.bulkMethodRemovalArea, false);
		setVisible(els.bulkMethodAssignmentArea, false);
		setVisible(els.bulkMethodPreservedArea, false);
		setVisible(els.bulkMethodRemovalConfirmed, false);
		clearElement(els.bulkMethodBlockingList);
		clearElement(els.bulkMethodRemovalList);
		clearElement(els.bulkMethodAssignmentList);
		clearElement(els.bulkMethodPreservedList);

		if (els.bulkMethodRemovalConfirmBtn) {
			els.bulkMethodRemovalConfirmBtn.disabled = false;
		}
		if (els.bulkMethodSaveBtn) {
			els.bulkMethodSaveBtn.disabled = true;
			els.bulkMethodSaveBtn.innerHTML = '<i class="ri-truck-line me-1"></i>최종 변경';
		}
	}

	function resetBulkDeliveryMethodPreviewUi() {
		state.bulkMethodPreview = null;
		state.bulkMethodRemovalAcknowledged = false;
		setText(els.bulkMethodRequiredCount, 0);
		setText(els.bulkMethodRemovalCount, 0);
		setText(els.bulkMethodRemovalMessageCount, 0);
		setText(els.bulkMethodAssignmentMessageCount, 0);
		setVisible(els.bulkMethodBlockingArea, false);
		setVisible(els.bulkMethodRemovalArea, false);
		setVisible(els.bulkMethodAssignmentArea, false);
		setVisible(els.bulkMethodPreservedArea, false);
		setVisible(els.bulkMethodRemovalConfirmed, false);
		clearElement(els.bulkMethodBlockingList);
		clearElement(els.bulkMethodRemovalList);
		clearElement(els.bulkMethodAssignmentList);
		clearElement(els.bulkMethodPreservedList);
		if (els.bulkMethodRemovalConfirmBtn) {
			els.bulkMethodRemovalConfirmBtn.disabled = false;
		}
		updateBulkDeliveryMethodSaveState();
	}

	async function loadBulkDeliveryMethodPreview() {
		const requestSequence = ++state.bulkMethodPreviewSequence;
		const deliveryMethodId = numberOrNull(valueOf(els.bulkMethodId));
		resetBulkDeliveryMethodPreviewUi();

		if (!deliveryMethodId) {
			setVisible(els.bulkMethodPlaceholder, true);
			setVisible(els.bulkMethodLoading, false);
			return;
		}

		setVisible(els.bulkMethodPlaceholder, false);
		setVisible(els.bulkMethodLoading, true);

		try {
			const response = await fetch(API.bulkDeliveryMethodPreview, {
				method: 'POST',
				headers: buildJsonHeaders(),
				body: JSON.stringify({
					orderIds: state.bulkMethodOrderIds,
					deliveryMethodId: deliveryMethodId
				})
			});

			const data = await parseJsonResponse(response);

			if (requestSequence !== state.bulkMethodPreviewSequence) {
				return;
			}

			state.bulkMethodPreview = data;
			renderBulkDeliveryMethodPreview(data);
		} catch (error) {
			if (requestSequence !== state.bulkMethodPreviewSequence) {
				return;
			}

			console.error(error);
			setVisible(els.bulkMethodLoading, false);
			showBulkInlineError(
				els.bulkMethodBlockingArea,
				els.bulkMethodBlockingList,
				error.message || '배송수단 변경 내용을 확인하지 못했습니다.'
			);
			updateBulkDeliveryMethodSaveState();
		}
	}

	function renderBulkDeliveryMethodPreview(data) {
		const blockingOrders = data.blockingOrders || [];
		const removalOrders = data.assignmentRemovalOrders || [];
		const requiredOrders = data.assignmentRequiredOrders || [];
		const preservedOrders = data.preservedAssignmentOrders || [];

		setVisible(els.bulkMethodLoading, false);
		setText(els.bulkMethodRequestedCount, data.requestedCount || state.bulkMethodOrderIds.length);
		setText(els.bulkMethodRequiredCount, data.assignmentRequiredCount || 0);
		setText(els.bulkMethodRemovalCount, data.assignmentRemovalCount || 0);
		setText(els.bulkMethodRemovalMessageCount, data.assignmentRemovalCount || 0);
		setText(els.bulkMethodAssignmentMessageCount, data.assignmentRequiredCount || 0);

		renderBulkOrderInfoList(els.bulkMethodBlockingList, blockingOrders);
		renderBulkOrderInfoList(els.bulkMethodRemovalList, removalOrders);
		renderBulkMethodAssignmentList(requiredOrders);
		renderBulkOrderInfoList(els.bulkMethodPreservedList, preservedOrders);

		setVisible(els.bulkMethodBlockingArea, blockingOrders.length > 0);
		setVisible(els.bulkMethodRemovalArea, removalOrders.length > 0);
		setVisible(els.bulkMethodAssignmentArea, requiredOrders.length > 0);
		setVisible(els.bulkMethodPreservedArea, preservedOrders.length > 0);

		state.bulkMethodRemovalAcknowledged = removalOrders.length === 0;
		setVisible(els.bulkMethodRemovalConfirmed, false);
		if (els.bulkMethodRemovalConfirmBtn) {
			els.bulkMethodRemovalConfirmBtn.disabled = false;
		}

		updateBulkDeliveryMethodSaveState();
	}

	function renderBulkMethodAssignmentList(items) {
		if (!els.bulkMethodAssignmentList) {
			return;
		}

		const optionsHtml = els.bulkHandlerOptionTemplate
			? els.bulkHandlerOptionTemplate.innerHTML
			: '<option value="">담당자를 선택하세요</option>';

		els.bulkMethodAssignmentList.innerHTML = (items || []).map(function(item) {
			return [
				'<div class="dispatch-list-bulk-assignment-item">',
				'  <div>' + buildBulkOrderInfoContent(item) + '</div>',
				'  <select class="form-select dispatch-list-form-select dispatch-list-bulk-method-handler-select"',
				'    data-order-id="' + escapeAttr(item.orderId) + '">',
				optionsHtml,
				'  </select>',
				'</div>'
			].join('');
		}).join('');
	}

	function updateBulkDeliveryMethodSaveState() {
		if (!els.bulkMethodSaveBtn) {
			return;
		}

		const preview = state.bulkMethodPreview;
		const handlerSelects = Array.from(document.querySelectorAll(
			'#dispatch-list-bulk-method-assignment-list .dispatch-list-bulk-method-handler-select'
		));
		const allHandlersSelected = handlerSelects.every(function(select) {
			return !!numberOrNull(valueOf(select));
		});

		const canProceed = !!preview
			&& Number(preview.blockingCount || 0) === 0
			&& (Number(preview.assignmentRemovalCount || 0) === 0 || state.bulkMethodRemovalAcknowledged)
			&& allHandlersSelected
			&& !!numberOrNull(valueOf(els.bulkMethodId))
			&& !state.bulkMethodBusy;

		els.bulkMethodSaveBtn.disabled = !canProceed;
	}

	async function submitBulkDeliveryMethodChange() {
		updateBulkDeliveryMethodSaveState();
		if (!els.bulkMethodSaveBtn || els.bulkMethodSaveBtn.disabled) {
			return;
		}

		const assignments = Array.from(document.querySelectorAll(
			'#dispatch-list-bulk-method-assignment-list .dispatch-list-bulk-method-handler-select'
		)).map(function(select) {
			return {
				orderId: Number(select.getAttribute('data-order-id')),
				deliveryHandlerId: numberOrNull(valueOf(select))
			};
		});

		state.bulkMethodBusy = true;
		els.bulkMethodSaveBtn.disabled = true;
		els.bulkMethodSaveBtn.innerHTML = '<i class="ri-loader-4-line me-1"></i>변경 중';
		if (els.bulkMethodId) {
			els.bulkMethodId.disabled = true;
		}

		const actionFeedback = window.TeamActionFeedback || null;
		const feedbackToken = actionFeedback
			? actionFeedback.begin({
				eyebrow: '출고팀 설정 변경 중',
				title: '배송수단을 일괄 변경하고 있습니다.',
				message: state.bulkMethodOrderIds.length + '건의 배송수단과 담당자 배정을 반영하고 있습니다.',
				detail: '담당자 배정 생성·삭제가 포함된 경우 처리 시간이 더 걸릴 수 있습니다.'
			})
			: null;

		try {
			const response = await fetch(API.bulkDeliveryMethod, {
				method: 'POST',
				headers: buildJsonHeaders(),
				body: JSON.stringify({
					orderIds: state.bulkMethodOrderIds,
					deliveryMethodId: numberOrNull(valueOf(els.bulkMethodId)),
					assignments: assignments,
					confirmAssignmentRemoval: state.bulkMethodRemovalAcknowledged,
					acknowledgedAssignmentRemovalOrderIds: state.bulkMethodRemovalAcknowledged
						? orderIdsFromBulkItems(state.bulkMethodPreview && state.bulkMethodPreview.assignmentRemovalOrders)
						: []
				})
			});

			const data = await parseJsonResponse(response);
			(data.updatedRows || []).forEach(applyDeliveryRowUpdate);
			clearSelectionForIds(data.updatedOrderIds || []);
			hideModal(els.bulkMethodModal);

			let message = '배송수단 ' + (data.updatedCount || 0) + '건이 변경되었습니다.';
			if (data.assignmentCreatedCount) {
				message += '\n담당자 신규 배정 ' + data.assignmentCreatedCount + '건';
			}
			if (data.assignmentRemovedCount) {
				message += '\n기존 배정업무 삭제 ' + data.assignmentRemovedCount + '건';
			}
			if (actionFeedback) {
				await actionFeedback.success({
					title: '배송수단 변경이 완료되었습니다.',
					message: message.replace(/\n/g, ' · '),
					detail: '현재 목록의 배송수단과 담당자 배정 정보를 갱신했습니다.'
				}, feedbackToken);
			} else {
				window.setTimeout(function() {
					alertMessage(message);
				}, 180);
			}
		} catch (error) {
			console.error(error);
			state.bulkMethodPreview = null;
			const message = error.message || '일괄 배송수단 변경 중 오류가 발생했습니다.';
			showBulkInlineError(
				els.bulkMethodBlockingArea,
				els.bulkMethodBlockingList,
				message
			);

			if (actionFeedback) {
				await actionFeedback.error({
					title: '배송수단 변경에 실패했습니다.',
					message: message,
					detail: '오류 내용을 모달에 표시했습니다. 변경 대상과 담당자 배정을 확인해 주세요.'
				}, feedbackToken);
			}
		} finally {
			state.bulkMethodBusy = false;
			if (els.bulkMethodId) {
				els.bulkMethodId.disabled = false;
			}
			if (els.bulkMethodSaveBtn) {
				els.bulkMethodSaveBtn.innerHTML = '<i class="ri-truck-line me-1"></i>최종 변경';
			}
			updateBulkDeliveryMethodSaveState();
		}
	}

	function orderIdsFromBulkItems(items) {
		return (items || []).map(function(item) {
			return Number(item && item.orderId);
		}).filter(function(orderId) {
			return Number.isFinite(orderId) && orderId > 0;
		});
	}

	function selectedCompletableOrderIds() {
		return selectedOrderIdsSnapshot().filter(function(orderId) {
			const row = document.querySelector(
				'.dispatch-list-row[data-order-id="' + cssEscape(orderId) + '"]'
			);

			return row && row.getAttribute('data-dispatch-completable') === 'true';
		});
	}

	function selectedOrderIdsSnapshot() {
		return Array.from(state.selectedOrderIds)
			.map(function(orderId) {
				return Number(orderId);
			})
			.filter(function(orderId) {
				return Number.isFinite(orderId) && orderId > 0;
			});
	}

	function clearSelectionForIds(orderIds) {
		(orderIds || []).forEach(function(orderId) {
			const numericOrderId = Number(orderId);
			state.selectedOrderIds.delete(numericOrderId);

			const checkbox = document.querySelector(
				'.dispatch-list-row-check[data-order-id="' + cssEscape(numericOrderId) + '"]'
			);
			if (checkbox) {
				checkbox.checked = false;
			}
		});

		updateCounts();
		updateCheckAllState();
	}

	function renderBulkOrderInfoList(container, items) {
		if (!container) {
			return;
		}

		container.innerHTML = (items || []).map(function(item) {
			return '<div class="dispatch-list-bulk-order-item">'
				+ buildBulkOrderInfoContent(item)
				+ '</div>';
		}).join('');
	}

	function buildBulkOrderInfoContent(item) {
		const orderId = item && item.orderId !== undefined && item.orderId !== null
			? '#' + item.orderId
			: '#-';
		const companyName = item && item.companyName ? item.companyName : '-';
		const productName = item && item.productName ? item.productName : '-';
		const methodName = item && item.deliveryMethodName ? item.deliveryMethodName : '미확인';
		const handlerName = item && item.deliveryHandlerName ? item.deliveryHandlerName : '미배정';
		const deliveryDate = item && item.deliveryDate ? item.deliveryDate : '미지정';
		const reason = item && item.reason ? item.reason : '';

		return [
			'<div class="dispatch-list-bulk-order-head">',
			'  <span class="dispatch-list-bulk-order-id">' + escapeHtml(orderId) + '</span>',
			'  <span>' + escapeHtml(companyName) + '</span>',
			'  <span>' + escapeHtml(productName) + '</span>',
			'</div>',
			'<div class="dispatch-list-bulk-order-meta">',
			'배송수단: ' + escapeHtml(methodName),
			' · 담당자: ' + escapeHtml(handlerName),
			' · 배송일: ' + escapeHtml(deliveryDate),
			'</div>',
			reason
				? '<div class="dispatch-list-bulk-order-reason">' + escapeHtml(reason) + '</div>'
				: ''
		].join('');
	}

	function showBulkInlineError(area, list, message) {
		setVisible(area, true);
		if (list) {
			list.innerHTML = '<div class="dispatch-list-bulk-order-item">'
				+ '<div class="dispatch-list-bulk-order-reason">'
				+ escapeHtml(message || '요청 처리 중 오류가 발생했습니다.')
				+ '</div></div>';
		}
	}

	function setVisible(element, visible) {
		if (!element) {
			return;
		}
		element.classList.toggle('d-none', !visible);
	}

	function clearElement(element) {
		if (element) {
			element.innerHTML = '';
		}
	}

	async function handleProvinceChange(scope, provinceSelect, citySelect, districtSelect) {
		resetSelect(citySelect, '전체');
		resetSelect(districtSelect, '전체');

		citySelect.disabled = true;
		districtSelect.disabled = true;

		const provinceId = valueOf(provinceSelect);

		if (!provinceId) {
			return;
		}

		try {
			const response = await fetch(API.provinceChildren(provinceId), {
				method: 'GET',
				headers: buildJsonHeaders(false)
			});

			const data = await parseJsonResponse(response);

			if (data.cities && data.cities.length > 0) {
				fillSelect(citySelect, data.cities, '전체');
				citySelect.disabled = false;
				districtSelect.disabled = true;
				return;
			}

			if (data.districts && data.districts.length > 0) {
				fillSelect(districtSelect, data.districts, '전체');
				districtSelect.disabled = false;
			}

		} catch (error) {
			console.error(error);
			alertMessage(error.message || '지역 정보를 불러오지 못했습니다.');
		}
	}


	function bindStatementButton(button, handler) {
		if (button && typeof handler === 'function') {
			button.addEventListener('click', handler);
		}
	}

	function selectedStatementOrderIds() {
		return selectedOrderIdsSnapshot();
	}

	function statementLayoutButtons() {
		return [
			els.statementSiteHorizontalPrintBtn,
			els.statementParcelHorizontalPrintBtn,
			els.statementSiteHorizontalDownloadBtn,
			els.statementParcelHorizontalDownloadBtn,
			els.statementSiteVerticalPrintBtn,
			els.statementParcelVerticalPrintBtn,
			els.statementSiteVerticalDownloadBtn,
			els.statementParcelVerticalDownloadBtn
		].filter(function(button) {
			return !!button;
		});
	}

	async function printDeliveryStatementLayout(layoutType, statementType, button) {
		const orderIds = selectedStatementOrderIds();

		if (orderIds.length === 0) {
			alertMessage('명세서로 출력할 주문을 하나 이상 선택해 주세요.');
			return;
		}

		const normalizedStatementType = normalizeStatementType(statementType);
		const statementLabel = statementTypeLabel(normalizedStatementType);
		const printWindow = window.open('', '_blank');

		if (!printWindow) {
			alertMessage('인쇄창이 차단되었습니다. 브라우저의 팝업 허용 후 다시 시도해 주세요.');
			return;
		}

		printWindow.document.open();
		printWindow.document.write(
			'<!doctype html><html lang="ko"><head><meta charset="utf-8">' +
			'<title>' + escapeHtml(statementLabel) + ' 출력 준비</title></head>' +
			'<body style="font-family:Malgun Gothic,Apple SD Gothic Neo,sans-serif;padding:32px;">' +
			escapeHtml(statementLabel) + ' 출력 데이터를 준비하고 있습니다.</body></html>'
		);
		printWindow.document.close();

		const originalText = button ? button.innerHTML : '';

		try {
			setStatementLayoutButtonsBusy(true, button, originalText, '출력 준비 중');

			const response = await fetch(API.deliveryStatementLayoutData, {
				method: 'POST',
				headers: buildJsonHeaders(),
				body: JSON.stringify({
					layoutType: normalizeStatementLayoutType(layoutType),
					statementType: normalizedStatementType,
					orderIds: orderIds
				})
			});

			const data = await parseStatementJsonResponse(response);

			if (!data.pages || data.pages.length === 0) {
				throw new Error('출력할 ' + statementLabel + ' 데이터가 없습니다.');
			}

			printWindow.document.open();
			printWindow.document.write(buildStatementPrintDocument(data));
			printWindow.document.close();
		} catch (error) {
			console.error(error);

			try {
				printWindow.close();
			} catch (closeError) {
				console.warn(closeError);
			}

			alertMessage(error.message || statementLabel + ' 출력 준비 중 오류가 발생했습니다.');
		} finally {
			setStatementLayoutButtonsBusy(false, button, originalText);
		}
	}

	async function downloadDeliveryStatementLayout(layoutType, statementType, button) {
		const orderIds = selectedStatementOrderIds();

		if (orderIds.length === 0) {
			alertMessage('명세서로 다운로드할 주문을 하나 이상 선택해 주세요.');
			return;
		}

		const normalizedLayoutType = normalizeStatementLayoutType(layoutType);
		const normalizedStatementType = normalizeStatementType(statementType);
		const statementLabel = statementTypeLabel(normalizedStatementType);
		const originalText = button ? button.innerHTML : '';

		try {
			setStatementLayoutButtonsBusy(true, button, originalText, '엑셀 생성 중');

			const response = await fetch(API.deliveryStatementLayoutExcel, {
				method: 'POST',
				headers: buildJsonHeaders(),
				body: JSON.stringify({
					layoutType: normalizedLayoutType,
					statementType: normalizedStatementType,
					orderIds: orderIds
				})
			});

			if (!response.ok) {
				const errorText = await response.text();
				throw new Error(errorText || statementLabel + ' 엑셀 생성 중 오류가 발생했습니다.');
			}

			const blob = await response.blob();
			const contentDisposition = response.headers.get('Content-Disposition');
			const layoutLabel = normalizedLayoutType === 'HORIZONTAL' ? '가로형' : '세로형';
			const filename = resolveDownloadFilename(
				contentDisposition,
				statementLabel + '_' + layoutLabel + '_' +
				(valueOf(els.today) || new Date().toISOString().slice(0, 10)) + '.xlsx'
			);

			downloadBlob(blob, filename);
		} catch (error) {
			console.error(error);
			alertMessage(error.message || statementLabel + ' 엑셀 다운로드 중 오류가 발생했습니다.');
		} finally {
			setStatementLayoutButtonsBusy(false, button, originalText);
		}
	}

	function parseStatementJsonResponse(response) {
		return response.text().then(function(text) {
			let data = null;

			if (text) {
				try {
					data = JSON.parse(text);
				} catch (ignore) {
					data = null;
				}
			}

			if (!response.ok) {
				const message = data && data.message
					? data.message
					: (text || '명세서 요청 처리 중 오류가 발생했습니다.');
				throw new Error(message);
			}

			return data || {};
		});
	}

	function setStatementLayoutButtonsBusy(isBusy, activeButton, originalText, busyText) {
		statementLayoutButtons().forEach(function(btn) {
			if (isBusy) {
				btn.disabled = true;

				if (btn === activeButton) {
					btn.innerHTML = '<i class="ri-loader-4-line me-1"></i>' +
						escapeHtml(busyText || '명세서 생성 중');
				}
				return;
			}

			if (btn === activeButton && originalText) {
				btn.innerHTML = originalText;
			}
		});

		if (!isBusy) {
			updateCounts();
		}
	}

	function normalizeStatementLayoutType(layoutType) {
		return toText(layoutType).replace(/\s+/g, '').toUpperCase() === 'VERTICAL'
			? 'VERTICAL'
			: 'HORIZONTAL';
	}

	function normalizeStatementType(statementType) {
		return toText(statementType).replace(/\s+/g, '').toUpperCase() === 'PARCEL'
			? 'PARCEL'
			: 'SITE';
	}

	function statementTypeLabel(statementType) {
		return normalizeStatementType(statementType) === 'PARCEL'
			? '택배명세서'
			: '현장명세서';
	}

	function buildStatementPrintDocument(data) {
		const layoutType = normalizeStatementLayoutType(data && data.layoutType);
		const statementType = normalizeStatementType(data && data.statementType);
		const layoutClass = layoutType === 'HORIZONTAL' ? 'layout-horizontal' : 'layout-vertical';
		const statementClass = statementType === 'PARCEL' ? 'statement-parcel' : 'statement-site';
		const title = statementTypeLabel(statementType);
		const pages = data && Array.isArray(data.pages) ? data.pages : [];
		const pageHtml = pages.map(function(page) {
			return buildStatementPrintPage(page, layoutType);
		}).join('');

		return [
			'<!doctype html>',
			'<html lang="ko">',
			'<head>',
			'<meta charset="utf-8">',
			'<meta name="viewport" content="width=device-width, initial-scale=1">',
			'<title>' + escapeHtml(title) + '</title>',
			'<style>' + buildStatementPrintStyles() + '</style>',
			'</head>',
			'<body class="statement-print-body ' + layoutClass + ' ' + statementClass + '">',
			pageHtml,
			'<script>',
			'(function(){',
			'var printed=false;',
			'function printNow(){',
			'if(printed){return;} printed=true;',
			'setTimeout(function(){window.focus();window.print();},250);',
			'}',
			'if(document.readyState==="complete"){printNow();}',
			'else{window.addEventListener("load",printNow);}',
			'})();',
			'<\/script>',
			'</body>',
			'</html>'
		].join('');
	}

	function buildStatementPrintPage(page, layoutType) {
		const storageCopy = buildStatementCopyHtml(page, '보관용', layoutType);
		const customerCopy = buildStatementCopyHtml(page, '고객용', layoutType);
		const splitClass = layoutType === 'HORIZONTAL'
			? 'statement-split statement-split-horizontal'
			: 'statement-split statement-split-vertical';
		const splitHtml = '<div class="' + splitClass + '">' + storageCopy + customerCopy + '</div>';

		/*
		 * 가로형은 프린터 설정을 건드리지 않고 A4 세로로 출력되도록
		 * 297mm x 210mm 캔버스를 오른쪽으로 90도 회전해 210mm x 297mm 인쇄면에 넣습니다.
		 */
		if (layoutType === 'HORIZONTAL') {
			return [
				'<section class="statement-paper">',
				'  <div class="statement-landscape-canvas">',
				splitHtml,
				'  </div>',
				'</section>'
			].join('');
		}

		return '<section class="statement-paper">' + splitHtml + '</section>';
	}

	function buildStatementCopyHtml(page, copyLabel, layoutType) {
		if (normalizeStatementType(page && page.documentType) === 'PARCEL') {
			return buildParcelStatementCopyHtml(page, copyLabel, layoutType);
		}

		return buildSiteStatementCopyHtml(page, copyLabel, layoutType);
	}

	function buildStatementHeaderHtml(page, copyLabel) {
		const partText = Number(page && page.pageCount || 0) > 1
			? escapeHtml(page.pageNumber || 1) + ' / ' + escapeHtml(page.pageCount || 1)
			: '';

		return [
			'<div class="statement-copy-header">',
			'  <div class="statement-page-part">' + partText + '</div>',
			'  <div class="statement-title">' +
				escapeHtml(toText(page && page.documentTypeLabel) || '명세서') +
			'</div>',
			'  <div class="statement-copy-label">' + escapeHtml(copyLabel) + '</div>',
			'</div>'
		].join('');
	}

	function buildStatementDeliveryMethodText(page) {
		const methodName = toText(page && page.deliveryMethodName) || '-';
		const contactName = toText(page && page.deliveryContactName) || '-';
		const contactPhone = toText(page && page.deliveryContactPhone) || '-';

		return methodName + ' / 담당자: ' + contactName + ' / ' + contactPhone;
	}

	function buildSiteStatementCopyHtml(page, copyLabel, layoutType) {
		const fixedRows = layoutType === 'VERTICAL' ? 5 : 8;
		const items = page && Array.isArray(page.items) ? page.items : [];
		const itemRows = buildFixedStatementItemRows(items, fixedRows, true);
		const lastPage = !page || page.lastPage !== false;
		const footerHtml = lastPage
			? [
				'<div class="statement-acceptance">',
				statementMultilineHtml(page && page.acceptanceText),
				'</div>',
				'<div class="statement-signature">' +
				statementMultilineHtml(page && page.signatureText) +
				'</div>'
			].join('')
			: '<div class="statement-continuation">품목 계속 - 확인란은 마지막 페이지에 표시됩니다.</div>';

		return [
			'<article class="statement-copy statement-copy-site">',
			buildStatementHeaderHtml(page, copyLabel),
			'<table class="statement-meta-table">',
			'  <colgroup><col class="statement-meta-label-col"><col><col class="statement-meta-label-col"><col></colgroup>',
			'  <tbody>',
			'    <tr>',
			'      <th>거래처명</th><td>' + statementMetaValueHtml(page && page.companyName) + '</td>',
			'      <th>주문번호</th><td>' + statementMetaValueHtml(page && page.orderIdsText) + '</td>',
			'    </tr>',
			'    <tr>',
			'      <th>하차지 담당자</th><td>' + statementMetaValueHtml(page && page.recipientName) + '</td>',
			'      <th>연락처</th><td>' + statementMetaValueHtml(page && page.recipientPhone) + '</td>',
			'    </tr>',
			'    <tr>',
			'      <th>하차지 주소</th><td colspan="3">' + statementMetaValueHtml(buildStatementAddressText(page)) + '</td>',
			'    </tr>',
			'    <tr>',
			'      <th>출고일</th><td>' + statementMetaValueHtml(page && page.dateText) + '</td>',
			'      <th>배송수단</th><td class="statement-delivery-method-value">' +
				statementMetaValueHtml(buildStatementDeliveryMethodText(page)) +
			'</td>',
			'    </tr>',
			'  </tbody>',
			'</table>',
			'<table class="statement-item-table statement-site-item-table">',
			'  <colgroup>',
			'    <col class="statement-col-no">',
			'    <col class="statement-col-product">',
			'    <col class="statement-col-size">',
			'    <col class="statement-col-color">',
			'    <col class="statement-col-quantity">',
			'    <col class="statement-col-memo">',
			'  </colgroup>',
			'  <thead><tr>',
			'    <th>NO</th><th>품명</th><th>규격</th><th>색상</th><th>수량</th><th>비고</th>',
			'  </tr></thead>',
			'  <tbody>' + itemRows + '</tbody>',
			'</table>',
			footerHtml,
			'</article>'
		].join('');
	}

	function buildParcelStatementCopyHtml(page, copyLabel, layoutType) {
		const fixedRows = layoutType === 'VERTICAL' ? 5 : 8;
		const items = page && Array.isArray(page.items) ? page.items : [];
		const itemRows = buildFixedStatementItemRows(items, fixedRows, true);
		const pageText = Number(page && page.pageCount || 0) > 1
			? '품목 ' + escapeHtml(page.pageNumber || 1) + ' / ' + escapeHtml(page.pageCount || 1)
			: '';

		return [
			'<article class="statement-copy statement-copy-parcel">',
			buildStatementHeaderHtml(page, copyLabel),
			'<table class="statement-meta-table">',
			'  <colgroup><col class="statement-meta-label-col"><col><col class="statement-meta-label-col"><col></colgroup>',
			'  <tbody>',
			'    <tr>',
			'      <th>발송일</th><td>' + statementMetaValueHtml(page && page.dateText) + '</td>',
			'      <th>운송장번호</th><td>' + statementMetaValueHtml(page && page.trackingNumber, true) + '</td>',
			'    </tr>',
			'    <tr>',
			'      <th>운임 구분</th><td>' + statementMetaValueHtml(page && page.freightType, true) + '</td>',
			'      <th>포장 수단</th><td>' + statementMetaValueHtml(page && page.packingMethod, true) + '</td>',
			'    </tr>',
			'    <tr>',
			'      <th>받는분</th><td>' + statementMetaValueHtml(page && page.recipientName) + '</td>',
			'      <th>연락처</th><td>' + statementMetaValueHtml(page && page.recipientPhone) + '</td>',
			'    </tr>',
			'    <tr>',
			'      <th>주소</th><td colspan="3">' + statementMetaValueHtml(buildStatementAddressText(page)) + '</td>',
			'    </tr>',
			'    <tr>',
			'      <th>거래처명</th><td>' + statementMetaValueHtml(page && page.companyName) + '</td>',
			'      <th>담당자</th><td>' + statementMetaValueHtml(page && page.managerName) + '</td>',
			'    </tr>',
			'  </tbody>',
			'</table>',
			'<table class="statement-item-table statement-parcel-item-table">',
			'  <colgroup>',
			'    <col class="statement-col-no">',
			'    <col class="statement-col-product">',
			'    <col class="statement-col-size">',
			'    <col class="statement-col-color">',
			'    <col class="statement-col-quantity">',
			'    <col class="statement-col-memo">',
			'  </colgroup>',
			'  <thead><tr>',
			'    <th>NO</th><th>품명</th><th>규격</th><th>색상</th><th>수량</th><th>비고</th>',
			'  </tr></thead>',
			'  <tbody>' + itemRows + '</tbody>',
			'</table>',
			'<div class="statement-parcel-footer">' + pageText + '</div>',
			'</article>'
		].join('');
	}

	function statementMetaValueHtml(value, allowBlank) {
		const text = toText(value);

		if (allowBlank && !text) {
			return '&nbsp;';
		}

		return statementMultilineHtml(text);
	}


	function buildFixedStatementItemRows(items, fixedRows, includeMemo) {
		const normalizedItems = Array.isArray(items) ? items : [];
		const rows = [];

		for (let index = 0; index < fixedRows; index++) {
			const item = index < normalizedItems.length ? normalizedItems[index] : null;
			rows.push(buildStatementItemRowHtml(item, index, includeMemo));
		}

		return rows.join('');
	}

	function buildStatementItemRowHtml(item, index, includeMemo) {
		const cells = [
			'<td class="text-center">' +
				(item ? escapeHtml(item.no || index + 1) : '&nbsp;') +
			'</td>',
			'<td>' + (item ? statementCellHtml(item.productName) : '&nbsp;') + '</td>',
			'<td>' + (item ? statementCellHtml(item.sizeText) : '&nbsp;') + '</td>',
			'<td>' + (item ? statementCellHtml(item.color) : '&nbsp;') + '</td>',
			'<td class="text-center">' +
				(item && item.quantity !== undefined ? escapeHtml(item.quantity) : '&nbsp;') +
			'</td>'
		];

		if (includeMemo) {
			cells.push('<td>' + (item ? statementCellHtml(item.memo) : '&nbsp;') + '</td>');
		}

		return '<tr>' + cells.join('') + '</tr>';
	}

	function buildStatementAddressText(page) {
		const postalCode = toText(page && page.postalCode);
		const addressText = toText(page && page.addressText) || '-';
		return postalCode ? '[' + postalCode + '] ' + addressText : addressText;
	}

	function statementCellHtml(value) {
		const text = toText(value);
		return text ? escapeHtml(text).replace(/\r?\n/g, '<br>') : '&nbsp;';
	}

	function statementMultilineHtml(value) {
		const text = toText(value);
		return escapeHtml(text || '-').replace(/\r?\n/g, '<br>');
	}

	function buildStatementPrintStyles() {
		return [
			'@page{size:A4 portrait;margin:0;}',
			'*{box-sizing:border-box;}',
			'html,body{margin:0;padding:0;}',
			'body{background:#e5e7eb;color:#111;font-family:"Malgun Gothic","Apple SD Gothic Neo",Arial,sans-serif;-webkit-print-color-adjust:exact;print-color-adjust:exact;}',
			'.statement-paper{position:relative;width:210mm;height:297mm;margin:8mm auto;background:#fff;overflow:hidden;box-shadow:0 2mm 8mm rgba(15,23,42,.18);page-break-after:always;break-after:page;}',
			'.statement-paper:last-child{page-break-after:auto;break-after:auto;}',
			'.statement-landscape-canvas{position:absolute;left:0;top:0;width:297mm;height:210mm;transform:translateX(210mm) rotate(90deg);transform-origin:0 0;}',
			'.statement-split{width:100%;height:100%;}',
			'.statement-split-horizontal{display:grid;grid-template-columns:1fr 1fr;}',
			'.statement-split-vertical{display:grid;grid-template-rows:1fr 1fr;}',
			'.statement-copy{position:relative;overflow:hidden;background:#fff;color:#111;}',
			'.statement-split-horizontal>.statement-copy{width:148.5mm;height:210mm;padding:5mm 5.5mm;}',
			'.statement-split-horizontal>.statement-copy:first-child{border-right:.35mm dashed #6b7280;}',
			'.statement-split-vertical>.statement-copy{width:210mm;height:148.5mm;padding:4.2mm 6mm;}',
			'.statement-split-vertical>.statement-copy:first-child{border-bottom:.35mm dashed #6b7280;}',
			'.statement-copy-header{display:grid;grid-template-columns:1fr auto 1fr;align-items:end;gap:2mm;margin-bottom:2mm;padding-bottom:1.5mm;border-bottom:.65mm solid #111827;}',
			'.statement-page-part{font-size:6.6pt;color:#64748b;}',
			'.statement-title{text-align:center;font-weight:900;letter-spacing:.14em;font-size:15pt;line-height:1.1;white-space:nowrap;}',
			'.statement-copy-label{justify-self:end;display:inline-flex;align-items:center;justify-content:center;min-width:18mm;padding:1mm 2mm;border:.35mm solid #111827;border-radius:1.2mm;font-size:8pt;font-weight:900;}',
			'.statement-meta-table{width:100%;border-collapse:collapse;table-layout:fixed;margin-bottom:2mm;font-size:7.8pt;}',
			'.statement-meta-table .statement-meta-label-col{width:22mm;}',
			'.statement-meta-table th,.statement-meta-table td{border:.25mm solid #475569;min-height:7.2mm;padding:.7mm 1.1mm;vertical-align:middle;line-height:1.2;overflow-wrap:anywhere;}',
			'.statement-meta-table th{background:#eef2f6;font-size:7.5pt;font-weight:900;text-align:center;padding:.7mm .8mm;}',
			'.statement-meta-table td{font-size:7.8pt;}',
			'.statement-delivery-method-value{font-weight:900;}',
			'.statement-item-table{width:100%;border-collapse:collapse;table-layout:fixed;margin-bottom:1.8mm;font-size:8pt;}',
			'.statement-item-table th,.statement-item-table td{border:.25mm solid #475569;padding:.75mm .9mm;vertical-align:middle;line-height:1.2;overflow-wrap:anywhere;}',
			'.statement-item-table th{height:8mm;background:#dfe6ee;text-align:center;font-weight:900;}',
			'.statement-item-table td{height:12mm;}',
			'.statement-site-item-table .statement-col-no{width:7%;}',
			'.statement-site-item-table .statement-col-product{width:31%;}',
			'.statement-site-item-table .statement-col-size{width:18%;}',
			'.statement-site-item-table .statement-col-color{width:12%;}',
			'.statement-site-item-table .statement-col-quantity{width:9%;}',
			'.statement-site-item-table .statement-col-memo{width:23%;}',
			'.statement-parcel-item-table .statement-col-no{width:7%;}',
			'.statement-parcel-item-table .statement-col-product{width:31%;}',
			'.statement-parcel-item-table .statement-col-size{width:18%;}',
			'.statement-parcel-item-table .statement-col-color{width:12%;}',
			'.statement-parcel-item-table .statement-col-quantity{width:9%;}',
			'.statement-parcel-item-table .statement-col-memo{width:23%;}',
			'.text-center{text-align:center;}',
			'.statement-acceptance{display:flex;align-items:center;justify-content:center;min-height:8mm;border:.25mm solid #475569;border-bottom:0;font-size:7.8pt;font-weight:800;text-align:center;padding:1mm;}',
			'.statement-signature{display:flex;align-items:center;justify-content:flex-end;min-height:9mm;border:.25mm solid #475569;padding:1mm 2mm;font-size:7.8pt;font-weight:900;}',
			'.statement-continuation{display:flex;align-items:center;justify-content:center;min-height:17mm;border:.25mm solid #475569;background:#f8fafc;font-size:7pt;font-weight:800;text-align:center;padding:1mm;}',
			'.statement-parcel-footer{display:flex;align-items:center;justify-content:flex-end;min-height:6mm;border-top:.25mm solid #475569;font-size:6.5pt;color:#64748b;}',
			'.layout-vertical .statement-title{font-size:13pt;}',
			'.layout-vertical .statement-copy-header{margin-bottom:1.5mm;padding-bottom:1.1mm;}',
			'.layout-vertical .statement-meta-table{margin-bottom:1.4mm;font-size:6.9pt;}',
			'.layout-vertical .statement-meta-table .statement-meta-label-col{width:21mm;}',
			'.layout-vertical .statement-meta-table th{font-size:6.7pt;padding:.55mm .7mm;}',
			'.layout-vertical .statement-meta-table td{font-size:6.9pt;padding:.55mm .9mm;}',
			'.layout-vertical .statement-item-table{font-size:6.8pt;margin-bottom:1.2mm;}',
			'.layout-vertical .statement-item-table th{height:6mm;padding:.5mm .6mm;}',
			'.layout-vertical .statement-item-table td{height:7mm;padding:.5mm .6mm;}',
			'.layout-vertical .statement-acceptance{min-height:7mm;font-size:6.8pt;}',
			'.layout-vertical .statement-signature{min-height:8mm;font-size:6.8pt;}',
			'.layout-vertical .statement-continuation{min-height:15mm;font-size:6.7pt;}',
			'@media print{',
			'html,body{width:210mm;height:auto;background:#fff;}',
			'.statement-paper{margin:0;box-shadow:none;}',
			'}'
		].join('');
	}


	function downloadBlob(blob, filename) {
		const url = window.URL.createObjectURL(blob);
		const link = document.createElement('a');

		link.href = url;
		link.download = filename;

		document.body.appendChild(link);
		link.click();
		link.remove();

		window.URL.revokeObjectURL(url);
	}

	function resolveDownloadFilename(contentDisposition, fallbackFilename) {
		if (contentDisposition) {
			const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
			if (utf8Match && utf8Match[1]) {
				return decodeURIComponent(utf8Match[1].replace(/"/g, ''));
			}

			const asciiMatch = contentDisposition.match(/filename="?([^";]+)"?/i);
			if (asciiMatch && asciiMatch[1]) {
				return asciiMatch[1];
			}
		}

		return fallbackFilename;
	}

	async function downloadExcel() {
		if (els.excelBtn) {
			els.excelBtn.disabled = true;
			els.excelBtn.innerHTML = '<i class="ri-loader-4-line me-1"></i>엑셀 생성 중';
		}

		try {
			const payload = buildSearchPayload();

			payload.size = null;
			payload.lastStatusSort = null;
			payload.lastOrderId = null;
			payload.loadedOrderIds = [];

			const response = await fetch(API.excel, {
				method: 'POST',
				headers: buildJsonHeaders(),
				body: JSON.stringify(payload)
			});

			if (!response.ok) {
				const errorData = await parseJsonResponse(response);
				throw new Error(errorData.message || '엑셀 출력 중 오류가 발생했습니다.');
			}

			const blob = await response.blob();

			const today = valueOf(els.today) || new Date().toISOString().slice(0, 10);
			const filename = '출고팀_업무현황_' + today + '.xlsx';

			const url = window.URL.createObjectURL(blob);
			const link = document.createElement('a');

			link.href = url;
			link.download = filename;

			document.body.appendChild(link);
			link.click();
			link.remove();

			window.URL.revokeObjectURL(url);

		} catch (error) {
			console.error(error);
			alertMessage(error.message || '엑셀 출력 중 오류가 발생했습니다.');
		} finally {
			if (els.excelBtn) {
				els.excelBtn.disabled = false;
				els.excelBtn.innerHTML = '<i class="ri-file-excel-2-line me-1"></i>엑셀출력';
			}
		}
	}
	async function handleCityChange(scope, citySelect, districtSelect) {
		resetSelect(districtSelect, '전체');
		districtSelect.disabled = true;

		const cityId = valueOf(citySelect);

		if (!cityId) {
			return;
		}

		try {
			const response = await fetch(API.cityDistricts(cityId), {
				method: 'GET',
				headers: buildJsonHeaders(false)
			});

			const data = await parseJsonResponse(response);

			if (data && data.length > 0) {
				fillSelect(districtSelect, data, '전체');
				districtSelect.disabled = false;
			}

		} catch (error) {
			console.error(error);
			alertMessage(error.message || '구/군 정보를 불러오지 못했습니다.');
		}
	}

	function resetFilters() {
		els.keywordType.value = 'COMPANY_NAME';
		els.keyword.value = '';
		els.productCategoryId.value = '';
		els.standard.value = 'ALL';
		els.orderDate.value = valueOf(els.today) || '';
		els.orderIdFrom.value = '';
		els.orderIdTo.value = '';
		els.provinceId.value = '';
		resetSelect(els.cityId, '전체');
		resetSelect(els.districtId, '전체');
		els.cityId.disabled = true;
		els.districtId.disabled = true;
		els.deliveryMethodId.value = '';

		searchOrders(true);
	}

	function copyMainFiltersToModal() {
		els.modalKeywordType.value = valueOf(els.keywordType);
		els.modalKeyword.value = valueOf(els.keyword);
		els.modalProductCategoryId.value = valueOf(els.productCategoryId);
		els.modalStandard.value = valueOf(els.standard);
		els.modalOrderDate.value = valueOf(els.orderDate);
		els.modalOrderIdFrom.value = valueOf(els.orderIdFrom);
		els.modalOrderIdTo.value = valueOf(els.orderIdTo);
		els.modalDeliveryMethodId.value = valueOf(els.deliveryMethodId);

		copySelectOptions(els.provinceId, els.modalProvinceId);
		copySelectOptions(els.cityId, els.modalCityId);
		copySelectOptions(els.districtId, els.modalDistrictId);

		els.modalProvinceId.value = valueOf(els.provinceId);
		els.modalCityId.value = valueOf(els.cityId);
		els.modalDistrictId.value = valueOf(els.districtId);

		els.modalCityId.disabled = els.cityId.disabled;
		els.modalDistrictId.disabled = els.districtId.disabled;
	}

	function copyModalFiltersToMain() {
		els.keywordType.value = valueOf(els.modalKeywordType);
		els.keyword.value = valueOf(els.modalKeyword);
		els.productCategoryId.value = valueOf(els.modalProductCategoryId);
		els.standard.value = valueOf(els.modalStandard);
		els.orderDate.value = valueOf(els.modalOrderDate);
		els.orderIdFrom.value = valueOf(els.modalOrderIdFrom);
		els.orderIdTo.value = valueOf(els.modalOrderIdTo);
		els.deliveryMethodId.value = valueOf(els.modalDeliveryMethodId);

		copySelectOptions(els.modalProvinceId, els.provinceId);
		copySelectOptions(els.modalCityId, els.cityId);
		copySelectOptions(els.modalDistrictId, els.districtId);

		els.provinceId.value = valueOf(els.modalProvinceId);
		els.cityId.value = valueOf(els.modalCityId);
		els.districtId.value = valueOf(els.modalDistrictId);

		els.cityId.disabled = els.modalCityId.disabled;
		els.districtId.disabled = els.modalDistrictId.disabled;
	}

	function copySelectOptions(source, target) {
		target.innerHTML = source.innerHTML;
	}

	function fillSelect(select, items, placeholder) {
		resetSelect(select, placeholder);

		(items || []).forEach(function(item) {
			const option = document.createElement('option');
			option.value = item.id;
			option.textContent = item.name;
			option.setAttribute('data-name', item.name || '');
			select.appendChild(option);
		});
	}

	function resetSelect(select, placeholder) {
		select.innerHTML = '';

		const option = document.createElement('option');
		option.value = '';
		option.textContent = placeholder || '전체';
		option.setAttribute('data-name', '');

		select.appendChild(option);
	}

	function renderLoadingRow(message) {
		els.tbody.innerHTML = '<tr class="dispatch-list-loading-row"><td colspan="10">' + escapeHtml(message) + '</td></tr>';
	}

	function renderEmptyRow(message) {
		els.tbody.innerHTML = '<tr><td colspan="10" class="dispatch-list-empty">' + escapeHtml(message) + '</td></tr>';
	}

	function updateMoreStatus(message) {
		const text = message || '';

		setText(els.moreStatus, text);
		setText(els.modalMoreStatus, text);
	}

	function initFloatingButtonObserver() {
		if (!els.toolbar || !els.floatingBtn) {
			return;
		}

		if (!window.IntersectionObserver) {
			els.floatingBtn.classList.add('is-visible');
			return;
		}

		const observer = new IntersectionObserver(function(entries) {
			const entry = entries[0];

			if (!entry) {
				return;
			}

			if (entry.isIntersecting) {
				els.floatingBtn.classList.remove('is-visible');
			} else {
				els.floatingBtn.classList.add('is-visible');
			}
		}, {
			threshold: 0.01
		});

		observer.observe(els.toolbar);
	}

	function rowClassByStatus(status) {
		const normalized = normalizeStatus(status);

		if (normalized === 'CONFIRMED') {
			return 'dispatch-list-row-confirmed';
		}

		if (normalized === 'PRODUCTION_DONE') {
			return 'dispatch-list-row-production-done';
		}

		if (normalized === 'DISPATCH_DONE' || normalized === 'DELIVERY_DONE') {
			return 'dispatch-list-row-dispatch-done';
		}

		return '';
	}

	function statusBadgeClass(status) {
		const normalized = normalizeStatus(status);

		if (normalized === 'CONFIRMED') {
			return 'dispatch-list-status-confirmed';
		}

		if (normalized === 'PRODUCTION_DONE') {
			return 'dispatch-list-status-production-done';
		}

		if (normalized === 'DISPATCH_DONE' || normalized === 'DELIVERY_DONE') {
			return 'dispatch-list-status-dispatch-done';
		}

		return '';
	}

	function normalizeStatus(status) {
		return toText(status).replace(/\s+/g, '').toUpperCase();
	}

	function selectedOptionName(select) {
		if (!select || !select.value) {
			return '';
		}

		const option = select.options[select.selectedIndex];

		if (!option) {
			return '';
		}

		return option.getAttribute('data-name') || option.textContent || '';
	}

	function isHandlerRequiredMethodName(methodName) {
		const normalized = toText(methodName)
			.replace(/\(금액:.*?\)/g, '')
			.replace(/\s+/g, '');

		return normalized.indexOf('직배송') >= 0
			|| normalized.indexOf('현장배송') >= 0;
	}

	function initializeOrderIdRangeFromUrl() {
		const params = new URLSearchParams(window.location.search || '');
		const from = params.get('orderIdFrom');
		const to = params.get('orderIdTo');
		if (from && els.orderIdFrom) els.orderIdFrom.value = from;
		if (to && els.orderIdTo) els.orderIdTo.value = to;
		/* 알림 바로가기에서는 날짜 기본값 때문에 과거 발주가 누락되지 않도록 ID 검색을 우선합니다. */
		if ((from || to) && els.orderDate && !params.get('orderDate')) {
			els.orderDate.value = '';
		}
	}

	function validatePositiveOrderId(value, label) {
		if (value == null || String(value).trim() === '') return null;
		const parsed = Number(value);
		if (!Number.isInteger(parsed) || parsed <= 0) {
			throw new Error(label + '은 1 이상의 정수여야 합니다.');
		}
		return parsed;
	}

	function valueOf(element) {
		return element ? String(element.value || '').trim() : '';
	}

	function numberOrNull(value) {
		if (value === undefined || value === null || value === '') {
			return null;
		}

		const number = Number(value);

		return Number.isFinite(number) ? number : null;
	}

	function buildJsonHeaders(includeContentType) {
		const headers = {
			'Accept': 'application/json',
			'X-Requested-With': 'fetch'
		};

		if (includeContentType !== false) {
			headers['Content-Type'] = 'application/json';
		}

		const csrfToken = valueOf(els.csrfToken);
		const csrfHeader = valueOf(els.csrfHeader);

		if (csrfToken && csrfHeader) {
			headers[csrfHeader] = csrfToken;
		}

		return headers;
	}

	async function parseJsonResponse(response) {
		const text = await response.text();
		let data = null;

		if (text) {
			try {
				data = JSON.parse(text);
			} catch (e) {
				data = null;
			}
		}

		if (!response.ok) {
			const message = data && data.message
				? data.message
				: '요청 처리 중 오류가 발생했습니다.';

			throw new Error(message);
		}

		return data || {};
	}

	function showModal(modalEl) {
		if (!modalEl) {
			return;
		}

		if (window.bootstrap && window.bootstrap.Modal) {
			window.bootstrap.Modal.getOrCreateInstance(modalEl).show();
			return;
		}

		modalEl.classList.add('show');
		modalEl.style.display = 'block';
		document.body.classList.add('modal-open');
	}

	function hideModal(modalEl) {
		if (!modalEl) {
			return;
		}

		if (window.bootstrap && window.bootstrap.Modal) {
			window.bootstrap.Modal.getOrCreateInstance(modalEl).hide();
			return;
		}

		modalEl.classList.remove('show');
		modalEl.style.display = 'none';
		document.body.classList.remove('modal-open');
	}

	function alertMessage(message) {
		const text = message || '처리되었습니다.';

		if (els.alertModal && els.alertMessage && window.bootstrap && window.bootstrap.Modal) {
			els.alertMessage.textContent = text;
			window.bootstrap.Modal.getOrCreateInstance(els.alertModal).show();
			return;
		}

		alert(text);
	}

	function confirmMessage(message) {
		const text = message || '처리하시겠습니까?';

		if (!els.confirmModal || !els.confirmMessage || !window.bootstrap || !window.bootstrap.Modal) {
			return Promise.resolve(confirm(text));
		}

		if (pendingConfirmResolver) {
			pendingConfirmResolver(false);
			pendingConfirmResolver = null;
		}

		els.confirmMessage.textContent = text;

		return new Promise(function(resolve) {
			pendingConfirmResolver = resolve;
			window.bootstrap.Modal.getOrCreateInstance(els.confirmModal, {
				backdrop: 'static',
				keyboard: true
			}).show();
		});
	}

	function throttle(fn, wait) {
		let locked = false;

		return function() {
			if (locked) {
				return;
			}

			locked = true;

			window.setTimeout(function() {
				fn();
				locked = false;
			}, wait);
		};
	}

	function cssEscape(value) {
		if (window.CSS && window.CSS.escape) {
			return window.CSS.escape(String(value));
		}

		return String(value).replace(/"/g, '\\"');
	}

	function escapeHtml(value) {
		const text = toText(value);

		return text
			.replace(/&/g, '&amp;')
			.replace(/</g, '&lt;')
			.replace(/>/g, '&gt;')
			.replace(/"/g, '&quot;')
			.replace(/'/g, '&#039;');
	}

	function escapeAttr(value) {
		return escapeHtml(value);
	}

	function setText(element, value) {
		if (!element) {
			return;
		}

		element.textContent = String(value);
	}

	function setDisabled(element, disabled) {
		if (!element) {
			return;
		}

		element.disabled = !!disabled;
	}

	function toText(value) {
		if (value === undefined || value === null) {
			return '';
		}

		return String(value);
	}
})();