/* dispatchList.js */
(function() {
	'use strict';

	const API = {
		search: '/team/dispatchList/api/orders/search',
		excel: '/team/dispatchList/api/orders/excel',
		deliveryStatementExcel: '/api/internal/delivery-statement/excel',
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
		els.siteStatementBtn = document.getElementById('dispatch-list-site-statement-btn');
		els.parcelStatementBtn = document.getElementById('dispatch-list-parcel-statement-btn');

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

		if (els.siteStatementBtn) {
			els.siteStatementBtn.addEventListener('click', function() {
				downloadDeliveryStatement('SITE', els.siteStatementBtn);
			});
		}

		if (els.parcelStatementBtn) {
			els.parcelStatementBtn.addEventListener('click', function() {
				downloadDeliveryStatement('PARCEL', els.parcelStatementBtn);
			});
		}
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
		return {
			keywordType: valueOf(els.keywordType),
			keyword: valueOf(els.keyword),
			productCategoryId: numberOrNull(valueOf(els.productCategoryId)),
			standard: valueOf(els.standard) || 'ALL',
			doName: selectedOptionName(els.provinceId),
			siName: selectedOptionName(els.cityId),
			guName: selectedOptionName(els.districtId),
			orderDate: valueOf(els.orderDate),
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

		const checkboxDisabled = '';
		const completeDisabled = !row.dispatchCompletable ? 'disabled' : '';

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
			'    data-delivery-order-index="' + escapeAttr(row.deliveryOrderIndex || '') + '"',
			'    title="' + escapeAttr(buildDeliveryMethodTitleFromRow(row)) + '">',
			'    ' + escapeHtml(row.deliveryMethodName),
			'  </button>',
			'</td>',

			'<td class="text-center">',
			'  <button type="button" class="btn btn-success dispatch-list-complete-btn"',
			'    data-order-id="' + escapeAttr(row.orderId) + '" ' + completeDisabled + '>',
			'    출고완료(' + escapeHtml(row.statusLabel || '-') + ')',
			'  </button>',
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

		setDisabled(els.bulkCompleteBtn, completableSelectedCount === 0);
		setDisabled(els.modalBulkCompleteBtn, completableSelectedCount === 0);
		setDisabled(els.bulkHandlerBtn, selectedCount === 0);
		setDisabled(els.bulkDeliveryMethodBtn, selectedCount === 0);
		setDisabled(els.modalBulkHandlerBtn, selectedCount === 0);
		setDisabled(els.modalBulkDeliveryMethodBtn, selectedCount === 0);
		setDisabled(els.siteStatementBtn, selectedCount === 0);
		setDisabled(els.parcelStatementBtn, selectedCount === 0);
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

			alertMessage(message);

		} catch (error) {
			console.error(error);
			alertMessage(error.message || '출고완료 처리 중 오류가 발생했습니다.');
		}
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

			window.setTimeout(function() {
				alertMessage(message);
			}, 180);
		} catch (error) {
			console.error(error);
			alertMessage(error.message || '배송수단 변경 중 오류가 발생했습니다.');
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
			window.setTimeout(function() {
				alertMessage(message);
			}, 180);
		} catch (error) {
			console.error(error);
			state.bulkHandlerPreview = null;
			showBulkInlineError(
				els.bulkHandlerBlockingArea,
				els.bulkHandlerBlockingList,
				error.message || '일괄 담당자 변경 중 오류가 발생했습니다.'
			);
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
			window.setTimeout(function() {
				alertMessage(message);
			}, 180);
		} catch (error) {
			console.error(error);
			state.bulkMethodPreview = null;
			showBulkInlineError(
				els.bulkMethodBlockingArea,
				els.bulkMethodBlockingList,
				error.message || '일괄 배송수단 변경 중 오류가 발생했습니다.'
			);
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


	async function downloadDeliveryStatement(statementType, button) {
		const orderIds = Array.from(state.selectedOrderIds)
			.filter(function(orderId) {
				return Number.isFinite(orderId) && orderId > 0;
			});

		if (orderIds.length === 0) {
			alertMessage('명세서로 출력할 주문을 하나 이상 선택해 주세요.');
			return;
		}

		const originalText = button ? button.innerHTML : '';

		try {
			setStatementButtonsBusy(true, button);

			const response = await fetch(API.deliveryStatementExcel, {
				method: 'POST',
				headers: buildJsonHeaders(),
				body: JSON.stringify({
					statementType: statementType,
					orderIds: orderIds
				})
			});

			if (!response.ok) {
				const errorText = await response.text();
				throw new Error(errorText || '명세서 출력 중 오류가 발생했습니다.');
			}

			const blob = await response.blob();
			const contentDisposition = response.headers.get('Content-Disposition');
			const filename = resolveDownloadFilename(
				contentDisposition,
				(statementType === 'SITE' ? '현장명세서' : '택배명세서') + '_' + (valueOf(els.today) || new Date().toISOString().slice(0, 10)) + '.xlsx'
			);

			downloadBlob(blob, filename);
		} catch (error) {
			console.error(error);
			alertMessage(error.message || '명세서 출력 중 오류가 발생했습니다.');
		} finally {
			setStatementButtonsBusy(false, button, originalText);
		}
	}

	function setStatementButtonsBusy(isBusy, activeButton, originalText) {
		[els.siteStatementBtn, els.parcelStatementBtn].forEach(function(btn) {
			if (!btn) {
				return;
			}

			if (isBusy) {
				btn.disabled = true;
				if (btn === activeButton) {
					btn.innerHTML = '<i class="ri-loader-4-line me-1"></i>명세서 생성 중';
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

		if (normalized === 'DISPATCH_DONE') {
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

		if (normalized === 'DISPATCH_DONE') {
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
			|| normalized.indexOf('현장배송') >= 0
			|| normalized.indexOf('화물') >= 0;
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