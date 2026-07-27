/* deliveryRoute.js */
/* 업체별 배송 묶음 화면 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', init);

    function init() {
        const page = document.getElementById('delivery-route-page');
        if (!page) return;

        const groups = Array.from(document.querySelectorAll('.delivery-route-group'));
        const toggleAllButton = document.getElementById('delivery-route-toggle-all');

        /*
         * 서버도 미완료 묶음 -> 완료 묶음 순서로 내려주지만,
         * 과거 캐시/부분 갱신 상태에서도 화면 순서를 확실하게 보정합니다.
         */
        normalizeRenderedRoute(groups);

        initGroupToggles(groups, toggleAllButton);
        initExportControls(page);
        initStatementControls(page);
        initCompletionControls(page, groups, toggleAllButton);
        refreshToggleAllButton(groups, toggleAllButton);
    }

    function initGroupToggles(groups, toggleAllButton) {
        groups.forEach(group => {
            const toggle = group.querySelector('[data-delivery-route-toggle]');
            if (!toggle) return;

            toggle.addEventListener('click', function () {
                setGroupExpanded(group, toggle.getAttribute('aria-expanded') !== 'true');
                refreshToggleAllButton(groups, toggleAllButton);
            });
        });

        if (toggleAllButton) {
            toggleAllButton.addEventListener('click', function () {
                const shouldOpen = !areAllGroupsExpanded(groups);
                groups.forEach(group => setGroupExpanded(group, shouldOpen));
                refreshToggleAllButton(groups, toggleAllButton);
            });
        }
    }

    function initExportControls(page) {
        const excelButton = document.getElementById('delivery-route-excel-button');
        const printButton = document.getElementById('delivery-route-print-button');
        const printForm = document.getElementById('delivery-route-print-form');
        const exportForm = document.getElementById('delivery-route-export-form');

        if (excelButton) {
            excelButton.addEventListener('click', async function () {
                const handlerId = Number(page.dataset.handlerId);
                const deliveryDate = String(page.dataset.deliveryDate || '').trim();
                const orderedOrderIds = getAllRouteOrderIdsInCurrentDomOrder();

                if (!Number.isSafeInteger(handlerId) || handlerId <= 0) {
                    await showMessage('담당자 정보가 없습니다.', '로그인한 배송 담당자 정보를 다시 확인해 주세요.', 'warning');
                    return;
                }

                if (!deliveryDate) {
                    await showMessage('배송일이 없습니다.', '엑셀로 출력할 배송 날짜를 선택해 주세요.', 'warning');
                    return;
                }

                if (orderedOrderIds.length === 0) {
                    await showMessage('출력할 데이터가 없습니다.', '현재 조회된 배송 주문이 없습니다.', 'warning');
                    return;
                }

                try {
                    setExportButtonBusy(excelButton, true);

                    const headers = { 'Content-Type': 'application/json' };
                    applyCsrfHeader(headers, exportForm);

                    const action = exportForm && exportForm.dataset.excelAction
                        ? exportForm.dataset.excelAction
                        : '/team/deliveryRoute/excel';

                    const response = await fetch(action, {
                        method: 'POST',
                        headers: headers,
                        credentials: 'same-origin',
                        body: JSON.stringify({
                            deliveryHandlerId: handlerId,
                            fromDate: deliveryDate,
                            toDate: deliveryDate,
                            orderedOrderIds: orderedOrderIds
                        })
                    });

                    if (!response.ok) {
                        const errorBody = await parseResponseBody(response);
                        throw new Error(errorBody.message || `엑셀 출력에 실패했습니다. (${response.status})`);
                    }

                    const blob = await response.blob();
                    const filename = resolveDownloadFilename(
                        response.headers.get('content-disposition'),
                        `배송리스트_${deliveryDate}.xlsx`
                    );

                    downloadBlob(blob, filename);

                } catch (error) {
                    await showMessage(
                        '엑셀 출력 실패',
                        error && error.message ? error.message : '엑셀 파일 생성 중 오류가 발생했습니다.',
                        'error'
                    );
                } finally {
                    setExportButtonBusy(excelButton, false);
                }
            });
        }

        if (printButton) {
            printButton.addEventListener('click', async function () {
                const deliveryDate = String(page.dataset.deliveryDate || '').trim();
                const orderedOrderIds = getAllRouteOrderIdsInCurrentDomOrder();

                if (!deliveryDate) {
                    await showMessage('배송일이 없습니다.', '인쇄할 배송 날짜를 선택해 주세요.', 'warning');
                    return;
                }

                if (orderedOrderIds.length === 0) {
                    await showMessage('인쇄할 데이터가 없습니다.', '현재 조회된 배송 주문이 없습니다.', 'warning');
                    return;
                }

                if (orderedOrderIds.length > 1000) {
                    await showMessage('인쇄 대상이 너무 많습니다.', '한 번에 인쇄할 수 있는 주문은 최대 1,000건입니다.', 'warning');
                    return;
                }

                if (printForm) {
                    submitRoutePrintForm(printForm, deliveryDate, orderedOrderIds);
                    return;
                }

                const query = new URLSearchParams();
                query.set('deliveryDate', deliveryDate);
                query.set('orderIds', orderedOrderIds.join(','));

                const printWindow = window.open(`/team/deliveryPrint?${query.toString()}`, '_blank');

                if (!printWindow) {
                    await showMessage('인쇄 창이 차단되었습니다.', '브라우저의 팝업 허용 설정을 확인해 주세요.', 'warning');
                    return;
                }

                try {
                    printWindow.opener = null;
                } catch (ignored) {
                    // 브라우저 정책상 opener 변경이 불가능해도 인쇄 창 자체는 정상 동작합니다.
                }
            });
        }
    }


    function initStatementControls(page) {
        const bindings = [
            ['delivery-route-statement-site-horizontal-print-button', function (button) {
                return printDeliveryStatementLayout('HORIZONTAL', 'SITE', button);
            }],
            ['delivery-route-statement-parcel-horizontal-print-button', function (button) {
                return printDeliveryStatementLayout('HORIZONTAL', 'PARCEL', button);
            }],
            ['delivery-route-statement-site-horizontal-download-button', function (button) {
                return downloadDeliveryStatementLayout('HORIZONTAL', 'SITE', button);
            }],
            ['delivery-route-statement-parcel-horizontal-download-button', function (button) {
                return downloadDeliveryStatementLayout('HORIZONTAL', 'PARCEL', button);
            }],
            ['delivery-route-statement-site-vertical-print-button', function (button) {
                return printDeliveryStatementLayout('VERTICAL', 'SITE', button);
            }],
            ['delivery-route-statement-parcel-vertical-print-button', function (button) {
                return printDeliveryStatementLayout('VERTICAL', 'PARCEL', button);
            }],
            ['delivery-route-statement-site-vertical-download-button', function (button) {
                return downloadDeliveryStatementLayout('VERTICAL', 'SITE', button);
            }],
            ['delivery-route-statement-parcel-vertical-download-button', function (button) {
                return downloadDeliveryStatementLayout('VERTICAL', 'PARCEL', button);
            }]
        ];

        bindings.forEach(function (binding) {
            const button = document.getElementById(binding[0]);
            bindStatementButton(button, function () {
                binding[1](button);
            });
        });

        const hasOrders = getAllRouteOrderIdsInCurrentDomOrder().length > 0;
        statementLayoutButtons().forEach(function (button) {
            button.disabled = !hasOrders;
        });

        if (page) {
            page.dataset.statementInitialized = 'true';
        }
    }

    function buildDeliveryRouteStatementHeaders() {
        const headers = {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'X-Requested-With': 'fetch'
        };

        applyCsrfHeader(headers, document.getElementById('delivery-route-export-form'));
        return headers;
    }

    function getDeliveryRouteStatementDate() {
        const page = document.getElementById('delivery-route-page');
        return page ? String(page.dataset.deliveryDate || '').trim() : '';
    }

    function bindStatementButton(button, handler) {
    	if (button && typeof handler === 'function') {
    		button.addEventListener('click', handler);
    	}
    }

    function selectedStatementOrderIds() {
        return getAllRouteOrderIdsInCurrentDomOrder();
    }

    function statementLayoutButtons() {
        return [
            document.getElementById('delivery-route-statement-site-horizontal-print-button'),
            document.getElementById('delivery-route-statement-parcel-horizontal-print-button'),
            document.getElementById('delivery-route-statement-site-horizontal-download-button'),
            document.getElementById('delivery-route-statement-parcel-horizontal-download-button'),
            document.getElementById('delivery-route-statement-site-vertical-print-button'),
            document.getElementById('delivery-route-statement-parcel-vertical-print-button'),
            document.getElementById('delivery-route-statement-site-vertical-download-button'),
            document.getElementById('delivery-route-statement-parcel-vertical-download-button')
        ].filter(function (button) {
            return Boolean(button);
        });
    }

    async function printDeliveryStatementLayout(layoutType, statementType, button) {
    	const orderIds = selectedStatementOrderIds();

    	if (orderIds.length === 0) {
    		showMessage('출력할 데이터가 없습니다.', '명세서로 출력할 현재 조회 주문이 없습니다.', 'warning');
    		return;
    	}

    	const normalizedStatementType = normalizeStatementType(statementType);
    	const statementLabel = statementTypeLabel(normalizedStatementType);
    	const printWindow = window.open('', '_blank');

    	if (!printWindow) {
    		showMessage('인쇄 창이 차단되었습니다.', '브라우저의 팝업 허용 후 다시 시도해 주세요.', 'warning');
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

    		const response = await fetch('/api/internal/delivery-statement/layout/data', {
    			method: 'POST',
    			headers: buildDeliveryRouteStatementHeaders(),
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

    		showMessage(statementLabel + ' 출력 실패', error.message || statementLabel + ' 출력 준비 중 오류가 발생했습니다.', 'error');
    	} finally {
    		setStatementLayoutButtonsBusy(false, button, originalText);
    	}
    }

    async function downloadDeliveryStatementLayout(layoutType, statementType, button) {
    	const orderIds = selectedStatementOrderIds();

    	if (orderIds.length === 0) {
    		showMessage('다운로드할 데이터가 없습니다.', '명세서로 다운로드할 현재 조회 주문이 없습니다.', 'warning');
    		return;
    	}

    	const normalizedLayoutType = normalizeStatementLayoutType(layoutType);
    	const normalizedStatementType = normalizeStatementType(statementType);
    	const statementLabel = statementTypeLabel(normalizedStatementType);
    	const originalText = button ? button.innerHTML : '';

    	try {
    		setStatementLayoutButtonsBusy(true, button, originalText, '엑셀 생성 중');

    		const response = await fetch('/api/internal/delivery-statement/layout/excel', {
    			method: 'POST',
    			headers: buildDeliveryRouteStatementHeaders(),
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
    			(getDeliveryRouteStatementDate() || new Date().toISOString().slice(0, 10)) + '.xlsx'
    		);

    		downloadBlob(blob, filename);
    	} catch (error) {
    		console.error(error);
    		showMessage(statementLabel + ' 다운로드 실패', error.message || statementLabel + ' 엑셀 다운로드 중 오류가 발생했습니다.', 'error');
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
        statementLayoutButtons().forEach(function (button) {
            if (isBusy) {
                button.disabled = true;

                if (button === activeButton) {
                    button.innerHTML = '<i class="ri-loader-4-line me-1"></i>'
                        + escapeHtml(busyText || '명세서 생성 중');
                }
                return;
            }

            if (button === activeButton && originalText) {
                button.innerHTML = originalText;
            }
        });

        if (!isBusy) {
            const hasOrders = selectedStatementOrderIds().length > 0;
            statementLayoutButtons().forEach(function (button) {
                button.disabled = !hasOrders;
            });
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
    			statementMetaValueHtml(page && page.deliveryMethodName) +
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


    function getAllRouteOrderIdsInCurrentDomOrder() {
        const result = [];
        const seen = new Set();
        const cards = document.querySelectorAll(
            '#delivery-route-direct-section .delivery-route-order-card[data-order-id], ' +
            '#delivery-route-freight-section .delivery-route-order-card[data-order-id]'
        );

        cards.forEach(card => {
            const orderId = Number(card.getAttribute('data-order-id'));

            if (!Number.isSafeInteger(orderId) || orderId <= 0 || seen.has(orderId)) {
                return;
            }

            seen.add(orderId);
            result.push(orderId);
        });

        return result;
    }

    function submitRoutePrintForm(form, deliveryDate, orderedOrderIds) {
        const targetName = `hiddenbath_delivery_route_print_${Date.now()}`;
        const printWindow = window.open('', targetName);

        if (!printWindow) {
            showMessage('인쇄 창이 차단되었습니다.', '브라우저의 팝업 허용 설정을 확인해 주세요.', 'warning');
            return;
        }

        try {
            printWindow.opener = null;
        } catch (ignored) {
            // 브라우저 정책상 opener 변경이 불가능해도 인쇄 창 자체는 정상 동작합니다.
        }

        form.querySelectorAll('.delivery-route-export-dynamic-field').forEach(field => field.remove());
        form.target = targetName;
        form.appendChild(createRouteHiddenField('deliveryDate', deliveryDate));

        orderedOrderIds.forEach(orderId => {
            form.appendChild(createRouteHiddenField('orderIds', String(orderId)));
        });

        form.submit();

        window.setTimeout(function () {
            form.querySelectorAll('.delivery-route-export-dynamic-field').forEach(field => field.remove());
        }, 0);
    }

    function createRouteHiddenField(name, value) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        input.className = 'delivery-route-export-dynamic-field';
        return input;
    }

    function applyCsrfHeader(headers, form) {
        if (!headers || !form) return;

        const csrfInput = form.querySelector('input[type="hidden"]');
        if (!csrfInput || !csrfInput.value) return;

        const headerName = csrfInput.dataset.csrfHeader;
        if (headerName) {
            headers[headerName] = csrfInput.value;
        }
    }

    function setExportButtonBusy(button, busy) {
        if (!button) return;

        if (!button.dataset.originalHtml) {
            button.dataset.originalHtml = button.innerHTML;
        }

        button.disabled = Boolean(busy);
        button.innerHTML = busy
            ? '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span>생성 중'
            : button.dataset.originalHtml;
    }

    function resolveDownloadFilename(contentDisposition, fallback) {
        const value = String(contentDisposition || '');
        const utf8Match = value.match(/filename\*=UTF-8''([^;]+)/i);

        if (utf8Match && utf8Match[1]) {
            try {
                return decodeURIComponent(utf8Match[1].trim().replace(/^"|"$/g, ''));
            } catch (ignored) {
                return utf8Match[1].trim().replace(/^"|"$/g, '');
            }
        }

        const filenameMatch = value.match(/filename="?([^";]+)"?/i);
        return filenameMatch && filenameMatch[1]
            ? filenameMatch[1].trim()
            : fallback;
    }

    function downloadBlob(blob, filename) {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');

        link.href = url;
        link.download = filename || '배송리스트.xlsx';
        document.body.appendChild(link);
        link.click();
        link.remove();

        window.setTimeout(function () {
            window.URL.revokeObjectURL(url);
        }, 0);
    }

    function initCompletionControls(page, groups, toggleAllButton) {
        const modalElement = document.getElementById('delivery-route-complete-modal');
        const completeForm = document.getElementById('delivery-route-complete-form');

        if (!modalElement || !completeForm || !window.bootstrap || !window.bootstrap.Modal) {
            return;
        }

        const modal = window.bootstrap.Modal.getOrCreateInstance
            ? window.bootstrap.Modal.getOrCreateInstance(modalElement)
            : new window.bootstrap.Modal(modalElement);

        const cameraButton = document.getElementById('delivery-route-camera-button');
        const galleryButton = document.getElementById('delivery-route-gallery-button');
        const cameraInput = document.getElementById('delivery-route-camera-input');
        const galleryInput = document.getElementById('delivery-route-gallery-input');
        const previewList = document.getElementById('delivery-route-image-preview-list');
        const emptyPreview = document.getElementById('delivery-route-image-empty');
        const orderCountElement = document.getElementById('delivery-route-modal-order-count');
        const imageCountElement = document.getElementById('delivery-route-modal-image-count');
        const selectedOrderIdsElement = document.getElementById('delivery-route-selected-order-ids');
        const feedbackElement = document.getElementById('delivery-route-complete-feedback');
        const submitButton = document.getElementById('delivery-route-submit-complete');

        let activeGroup = null;
        let activeOrderIds = [];
        let selectedFiles = [];
        let fileSequence = 0;
        let submitting = false;

        groups.forEach(group => {
            const orderChecks = getCompletableOrderChecks(group);
            const selectAll = group.querySelector('.delivery-route-group-select-all');
            const completeButton = group.querySelector('[data-delivery-route-complete-button]');

            orderChecks.forEach(checkbox => {
                checkbox.addEventListener('change', function () {
                    refreshGroupSelection(group);
                });
            });

            if (selectAll) {
                selectAll.addEventListener('change', function () {
                    getCompletableOrderChecks(group).forEach(checkbox => {
                        checkbox.checked = selectAll.checked;
                    });
                    refreshGroupSelection(group);
                });
            }

            if (completeButton) {
                completeButton.addEventListener('click', function () {
                    const selectedOrderIds = getSelectedOrderIds(group);
                    if (selectedOrderIds.length === 0) {
                        showMessage('선택된 주문이 없습니다.', '배송완료 처리할 주문을 1개 이상 선택해 주세요.', 'warning');
                        return;
                    }

                    activeGroup = group;
                    activeOrderIds = selectedOrderIds;
                    resetSelectedFiles();
                    clearCompletionFeedback();
                    renderModalState();
                    modal.show();
                });
            }

            refreshGroupSelection(group);
        });

        if (cameraButton && cameraInput) {
            cameraButton.addEventListener('click', function () {
                if (!submitting) cameraInput.click();
            });

            cameraInput.addEventListener('change', function () {
                appendFiles(cameraInput.files);
                cameraInput.value = '';
            });
        }

        if (galleryButton && galleryInput) {
            galleryButton.addEventListener('click', function () {
                if (!submitting) galleryInput.click();
            });

            galleryInput.addEventListener('change', function () {
                appendFiles(galleryInput.files);
                galleryInput.value = '';
            });
        }

        if (previewList) {
            previewList.addEventListener('click', function (event) {
                const removeButton = event.target.closest('[data-delivery-route-remove-file]');
                if (!removeButton || submitting) return;

                const fileId = removeButton.getAttribute('data-delivery-route-remove-file');
                removeSelectedFile(fileId);
            });
        }

        if (submitButton) {
            submitButton.addEventListener('click', async function () {
                if (submitting) return;

                if (activeOrderIds.length === 0) {
                    showCompletionFeedback('배송완료 처리할 주문을 다시 선택해 주세요.', 'warning');
                    return;
                }

                if (selectedFiles.length === 0) {
                    showCompletionFeedback('배송완료 이미지를 1장 이상 등록해 주세요.', 'warning');
                    return;
                }

                const requestedOrderIds = activeOrderIds.slice();
                const requestedImageCount = selectedFiles.length;
                const targetGroup = activeGroup;

                try {
                    clearCompletionFeedback();
                    setSubmitting(true);

                    const responseBody = await submitCompletion(
                        completeForm,
                        page.dataset.deliveryDate,
                        requestedOrderIds,
                        selectedFiles.map(item => item.file)
                    );

                    const snapshot = normalizeCompletionSnapshot(responseBody.completionSnapshot);
                    const deliveryDoneOrderIds = snapshot && snapshot.deliveryDoneOrderIds.length > 0
                        ? snapshot.deliveryDoneOrderIds
                        : normalizePositiveIds(responseBody.completedOrderIds || requestedOrderIds);

                    applyCompletionState(
                        targetGroup,
                        deliveryDoneOrderIds,
                        snapshot,
                        groups,
                        toggleAllButton
                    );

                    modal.hide();

                    await showMessage(
                        '배송완료 처리되었습니다.',
                        responseBody.message
                            || `${requestedOrderIds.length}건을 ${requestedImageCount}장의 이미지로 배송완료 처리했습니다.`,
                        'success'
                    );

                } catch (error) {
                    const message = error && error.message
                        ? error.message
                        : '요청 처리 중 오류가 발생했습니다.';

                    showCompletionFeedback(message, 'error');
                    await showMessage('배송완료 처리 실패', message, 'error');
                } finally {
                    setSubmitting(false);
                }
            });
        }

        modalElement.addEventListener('show.bs.modal', function () {
            document.body.classList.add('delivery-route-completion-modal-open');
        });

        modalElement.addEventListener('hidden.bs.modal', function () {
            document.body.classList.remove('delivery-route-completion-modal-open');

            if (submitting) return;

            activeGroup = null;
            activeOrderIds = [];
            resetSelectedFiles();
            clearCompletionFeedback();
            renderModalState();
        });

        window.addEventListener('beforeunload', revokeAllPreviewUrls);

        function appendFiles(fileList) {
            const files = Array.from(fileList || []);
            const invalidFiles = files.filter(file => !isImageFile(file));
            const imageFiles = files.filter(isImageFile);

            imageFiles.forEach(file => {
                const id = `delivery-route-file-${Date.now()}-${++fileSequence}`;
                selectedFiles.push({
                    id: id,
                    file: file,
                    previewUrl: URL.createObjectURL(file)
                });
            });

            if (imageFiles.length > 0) {
                clearCompletionFeedback();
            }

            renderModalState();

            if (invalidFiles.length > 0) {
                showCompletionFeedback(
                    `이미지 파일이 아닌 ${invalidFiles.length}개 파일은 제외했습니다.`,
                    'warning'
                );
            }
        }

        function removeSelectedFile(fileId) {
            const index = selectedFiles.findIndex(item => item.id === fileId);
            if (index < 0) return;

            const removed = selectedFiles.splice(index, 1)[0];
            if (removed && removed.previewUrl) {
                URL.revokeObjectURL(removed.previewUrl);
            }

            renderModalState();
        }

        function resetSelectedFiles() {
            revokeAllPreviewUrls();
            selectedFiles = [];
            if (cameraInput) cameraInput.value = '';
            if (galleryInput) galleryInput.value = '';
        }

        function revokeAllPreviewUrls() {
            selectedFiles.forEach(item => {
                if (item.previewUrl) URL.revokeObjectURL(item.previewUrl);
            });
        }

        function renderModalState() {
            if (orderCountElement) orderCountElement.textContent = String(activeOrderIds.length);
            if (imageCountElement) imageCountElement.textContent = String(selectedFiles.length);

            if (selectedOrderIdsElement) {
                selectedOrderIdsElement.textContent = activeOrderIds.length > 0
                    ? `선택 오더: ${activeOrderIds.map(orderId => `#${orderId}`).join(', ')}`
                    : '-';
            }

            if (previewList) {
                previewList.innerHTML = '';
                selectedFiles.forEach(item => {
                    previewList.appendChild(createPreviewElement(item));
                });
            }

            if (emptyPreview) {
                emptyPreview.hidden = selectedFiles.length > 0;
            }

            if (submitButton) {
                submitButton.disabled = submitting
                    || activeOrderIds.length === 0
                    || selectedFiles.length === 0;

                const label = submitButton.querySelector('.delivery-route-submit-label');
                if (label && !submitting) {
                    label.textContent = activeOrderIds.length > 0
                        ? `${activeOrderIds.length}건 배송완료`
                        : '배송완료';
                }
            }
        }

        function createPreviewElement(item) {
            const wrapper = document.createElement('div');
            wrapper.className = 'delivery-route-image-preview-item';

            const image = document.createElement('img');
            image.src = item.previewUrl;
            image.alt = item.file.name || '배송완료 이미지 미리보기';

            const removeButton = document.createElement('button');
            removeButton.type = 'button';
            removeButton.className = 'delivery-route-image-remove';
            removeButton.setAttribute('aria-label', `${item.file.name || '이미지'} 삭제`);
            removeButton.setAttribute('data-delivery-route-remove-file', item.id);
            removeButton.innerHTML = '<i class="ri-close-line" aria-hidden="true"></i>';

            const meta = document.createElement('div');
            meta.className = 'delivery-route-image-preview-meta';

            const name = document.createElement('span');
            name.className = 'delivery-route-image-preview-name';
            name.textContent = item.file.name || 'delivery-image';

            const size = document.createElement('span');
            size.className = 'delivery-route-image-preview-size';
            size.textContent = formatFileSize(item.file.size);

            meta.append(name, size);
            wrapper.append(image, removeButton, meta);
            return wrapper;
        }

        function setSubmitting(value) {
            submitting = Boolean(value);

            [cameraButton, galleryButton].forEach(button => {
                if (button) button.disabled = submitting;
            });

            modalElement.querySelectorAll('[data-bs-dismiss="modal"]').forEach(button => {
                button.disabled = submitting;
            });

            if (submitButton) {
                const spinner = submitButton.querySelector('.spinner-border');
                const label = submitButton.querySelector('.delivery-route-submit-label');

                if (spinner) spinner.classList.toggle('d-none', !submitting);
                if (label) {
                    label.textContent = submitting
                        ? '처리 중'
                        : (activeOrderIds.length > 0 ? `${activeOrderIds.length}건 배송완료` : '배송완료');
                }
            }

            renderModalState();
        }

        function clearCompletionFeedback() {
            if (!feedbackElement) return;

            feedbackElement.hidden = true;
            feedbackElement.textContent = '';
            feedbackElement.classList.remove('is-warning', 'is-error', 'is-success');
        }

        function showCompletionFeedback(message, type) {
            if (!feedbackElement) {
                showMessage('확인해 주세요.', message, type || 'warning');
                return;
            }

            feedbackElement.hidden = false;
            feedbackElement.textContent = String(message || '요청 내용을 확인해 주세요.');
            feedbackElement.classList.remove('is-warning', 'is-error', 'is-success');
            feedbackElement.classList.add(`is-${normalizeMessageType(type)}`);
            feedbackElement.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
        }
    }

    function normalizeCompletionSnapshot(value) {
        if (!value || typeof value !== 'object') {
            return null;
        }

        const groupOrderIds = normalizePositiveIds(value.groupOrderIds);
        const deliveryDoneOrderIds = normalizePositiveIds(value.deliveryDoneOrderIds);
        const groupOrderCount = toNonNegativeInteger(value.groupOrderCount, groupOrderIds.length);
        const groupDeliveryDoneCount = toNonNegativeInteger(
            value.groupDeliveryDoneCount,
            deliveryDoneOrderIds.length
        );
        const groupCompletableOrderCount = toNonNegativeInteger(value.groupCompletableOrderCount, 0);
        const pageDeliveryDoneCount = toNonNegativeInteger(value.pageDeliveryDoneCount, -1);
        const groupFullyCompleted = Boolean(value.groupFullyCompleted)
            || (groupOrderCount > 0 && groupDeliveryDoneCount >= groupOrderCount);

        return {
            groupOrderIds: groupOrderIds,
            deliveryDoneOrderIds: deliveryDoneOrderIds,
            groupOrderCount: groupOrderCount,
            groupDeliveryDoneCount: Math.min(groupDeliveryDoneCount, groupOrderCount || groupDeliveryDoneCount),
            groupCompletableOrderCount: groupCompletableOrderCount,
            pageDeliveryDoneCount: pageDeliveryDoneCount,
            groupFullyCompleted: groupFullyCompleted
        };
    }

    function applyCompletionState(group, deliveryDoneOrderIds, snapshot, groups, toggleAllButton) {
        if (!group) return;

        const doneIds = new Set(normalizePositiveIds(deliveryDoneOrderIds));

        doneIds.forEach(orderId => {
            const card = findOrderCard(group, orderId);
            if (card) markOrderCardAsDone(card);
        });

        const orderCards = getOrderCards(group);
        const calculatedOrderCount = orderCards.length;
        const calculatedDoneCount = orderCards.filter(isDeliveryDoneCard).length;
        const calculatedCompletableCount = orderCards.filter(card => card.dataset.completable === 'true').length;

        const orderCount = snapshot && snapshot.groupOrderCount > 0
            ? snapshot.groupOrderCount
            : calculatedOrderCount;
        const doneCount = snapshot
            ? Math.min(snapshot.groupDeliveryDoneCount, orderCount)
            : calculatedDoneCount;
        const completableCount = snapshot
            ? snapshot.groupCompletableOrderCount
            : calculatedCompletableCount;
        const allCompleted = snapshot
            ? snapshot.groupFullyCompleted
            : orderCount > 0 && doneCount >= orderCount;

        group.dataset.orderCount = String(orderCount);
        group.dataset.deliveryDoneCount = String(doneCount);
        group.dataset.completableCount = String(completableCount);
        group.dataset.allCompleted = allCompleted ? 'true' : 'false';

        updateGroupCompletionCounters(group, doneCount, orderCount);
        updateGroupCompletionBadge(group, doneCount, orderCount, allCompleted);
        updateGroupBulkState(group, completableCount, allCompleted);

        group.classList.toggle('is-fully-completed', allCompleted);
        group.classList.remove('has-selection');
        group.classList.add('is-completion-updated');

        window.setTimeout(function () {
            group.classList.remove('is-completion-updated');
        }, 760);

        if (allCompleted) {
            forceGroupCollapsed(group);
        }

        updatePageDeliveryDoneCount(snapshot);
        normalizeRenderedRoute(groups);
        refreshGroupSelection(group);
        refreshToggleAllButton(groups, toggleAllButton);
    }

    function findOrderCard(group, orderId) {
        const cards = getOrderCards(group);
        return cards.find(card => Number(card.dataset.orderId) === Number(orderId)) || null;
    }

    function getOrderCards(group) {
        return Array.from(group.querySelectorAll('.delivery-route-order-card[data-order-id]'));
    }

    function markOrderCardAsDone(card) {
        if (!card) return;

        card.dataset.completable = 'false';
        card.dataset.deliveryDone = 'true';
        card.classList.remove('is-selected-for-completion', 'is-not-completable');
        card.classList.add('is-delivery-done');

        const checkbox = card.querySelector('.delivery-route-complete-check');
        if (checkbox) {
            checkbox.checked = false;
            checkbox.disabled = true;
        }

        const checkLabel = card.querySelector('.delivery-route-check-label');
        if (checkLabel) {
            checkLabel.classList.add('is-disabled');
            const text = checkLabel.querySelector('span');
            if (text) text.textContent = '배송완료';
        }

        const statusBadge = card.querySelector('[data-delivery-route-status-badge]');
        if (statusBadge) {
            statusBadge.classList.remove(
                'bg-info',
                'bg-secondary',
                'bg-warning',
                'bg-warning-subtle',
                'text-dark',
                'text-warning'
            );
            statusBadge.classList.add('bg-success');
            statusBadge.textContent = '배송완료';
        }

        const orderNumberMeta = card.querySelector('.delivery-route-order-number small');
        if (orderNumberMeta) {
            orderNumberMeta.textContent = '배송완료';
        }
    }

    function updateGroupCompletionCounters(group, doneCount, orderCount) {
        group.querySelectorAll('[data-delivery-route-done-count]').forEach(element => {
            element.textContent = String(doneCount);
        });

        group.querySelectorAll('[data-delivery-route-total-count]').forEach(element => {
            element.textContent = String(orderCount);
        });
    }

    function updateGroupCompletionBadge(group, doneCount, orderCount, allCompleted) {
        const badge = group.querySelector('[data-delivery-route-group-completion]');
        if (!badge) return;

        badge.classList.remove(
            'bg-success',
            'text-white',
            'bg-warning-subtle',
            'text-warning',
            'bg-light',
            'text-dark'
        );

        if (allCompleted) {
            badge.classList.add('bg-success', 'text-white');
        } else if (doneCount > 0) {
            badge.classList.add('bg-warning-subtle', 'text-warning');
        } else {
            badge.classList.add('bg-light', 'text-dark');
        }

        const doneElement = badge.querySelector('[data-delivery-route-done-count]');
        const totalElement = badge.querySelector('[data-delivery-route-total-count]');
        if (doneElement) doneElement.textContent = String(doneCount);
        if (totalElement) totalElement.textContent = String(orderCount);
    }

    function updateGroupBulkState(group, completableCount, allCompleted) {
        const bulkBar = group.querySelector('.delivery-route-bulk-bar');
        const selectAllLabel = group.querySelector('.delivery-route-select-all-label');
        const selectAll = group.querySelector('.delivery-route-group-select-all');
        const completeButton = group.querySelector('[data-delivery-route-complete-button]');
        const progress = getGroupProgressElement(group);

        if (bulkBar) {
            bulkBar.classList.toggle('is-completed', allCompleted);
        }

        if (selectAllLabel) {
            selectAllLabel.classList.toggle('is-disabled', allCompleted || completableCount === 0);

            const strong = selectAllLabel.querySelector('strong');
            const small = selectAllLabel.querySelector('small');

            if (strong) {
                strong.textContent = allCompleted
                    ? '모든 주문 배송완료'
                    : '완료 대상 전체 선택';
            }

            if (small) {
                small.textContent = allCompleted
                    ? '추가 완료처리할 주문이 없습니다.'
                    : `생산완료 처리 가능 ${completableCount}건`;
            }
        }

        if (selectAll) {
            selectAll.checked = false;
            selectAll.indeterminate = false;
            selectAll.disabled = allCompleted || completableCount === 0;
        }

        if (completeButton) {
            completeButton.disabled = true;

            const icon = completeButton.querySelector('i');
            const label = completeButton.querySelector('[data-delivery-route-complete-label]');
            const countBadge = completeButton.querySelector('[data-delivery-route-selected-count]');

            if (icon) {
                icon.className = allCompleted
                    ? 'ri-checkbox-circle-line me-1'
                    : 'ri-camera-line me-1';
            }

            if (label) {
                label.textContent = allCompleted ? '전체 배송완료' : '배송완료처리';
            }

            if (countBadge) {
                countBadge.textContent = '0';
                countBadge.hidden = allCompleted;
            }
        }

        if (progress && !group.classList.contains('is-freight')) {
            progress.textContent = allCompleted
                ? '전체 완료'
                : (completableCount > 0 ? `선택 0/${completableCount}` : '완료 대기');
        }
    }

    function updatePageDeliveryDoneCount(snapshot) {
        const summary = document.getElementById('delivery-route-summary-done-count');
        if (!summary) return;

        if (snapshot && snapshot.pageDeliveryDoneCount >= 0) {
            summary.textContent = String(snapshot.pageDeliveryDoneCount);
            return;
        }

        const doneCount = document.querySelectorAll('.delivery-route-order-card.is-delivery-done').length;
        summary.textContent = String(doneCount);
    }

    function normalizeRenderedRoute(groups) {
        document.querySelectorAll('[data-delivery-route-group-list]').forEach(list => {
            reorderGroupList(list);
        });

        groups.forEach(group => {
            reorderOrderCards(group);
        });

        refreshRouteSequences();
    }

    function reorderGroupList(list) {
        if (!list) return;

        const groups = Array.from(list.children)
            .filter(element => element.classList && element.classList.contains('delivery-route-group'));
        const pendingGroups = groups.filter(group => group.dataset.allCompleted !== 'true'
            && !group.classList.contains('is-fully-completed'));
        const completedGroups = groups.filter(group => !pendingGroups.includes(group));

        const oldDivider = Array.from(list.children).find(element =>
            element.hasAttribute && element.hasAttribute('data-delivery-route-completed-divider'));
        if (oldDivider) oldDivider.remove();

        pendingGroups.forEach(group => list.appendChild(group));

        if (completedGroups.length > 0) {
            const divider = createCompletedGroupDivider(completedGroups.length);
            list.appendChild(divider);
            completedGroups.forEach(group => list.appendChild(group));
        }
    }

    function createCompletedGroupDivider(count) {
        const divider = document.createElement('div');
        divider.className = 'delivery-route-completed-divider';
        divider.setAttribute('data-delivery-route-completed-divider', 'true');
        divider.innerHTML = '<span><i class="ri-checkbox-circle-line" aria-hidden="true"></i>배송완료 묶음</span>'
            + `<b>${count}곳</b>`;
        return divider;
    }

    function reorderOrderCards(group) {
        if (!group) return;

        const list = group.querySelector('.delivery-route-order-list');
        if (!list) return;

        const cards = Array.from(list.children)
            .filter(element => element.classList && element.classList.contains('delivery-route-order-card'));
        const pendingCards = cards.filter(card => !isDeliveryDoneCard(card));
        const completedCards = cards.filter(isDeliveryDoneCard);

        const oldDivider = Array.from(list.children).find(element =>
            element.hasAttribute && element.hasAttribute('data-delivery-route-order-completed-divider'));
        if (oldDivider) oldDivider.remove();

        pendingCards.forEach(card => list.appendChild(card));

        if (pendingCards.length > 0 && completedCards.length > 0) {
            const divider = document.createElement('div');
            divider.className = 'delivery-route-order-completed-divider';
            divider.setAttribute('data-delivery-route-order-completed-divider', 'true');
            divider.innerHTML = '<i class="ri-checkbox-circle-line" aria-hidden="true"></i>'
                + `<span>이 묶음의 배송완료 주문 ${completedCards.length}건</span>`;
            list.appendChild(divider);
        }

        completedCards.forEach(card => list.appendChild(card));
    }

    function isDeliveryDoneCard(card) {
        return Boolean(card)
            && (card.dataset.deliveryDone === 'true' || card.classList.contains('is-delivery-done'));
    }

    function refreshRouteSequences() {
        let sequence = 1;

        ['direct', 'freight'].forEach(section => {
            const list = document.querySelector(`[data-delivery-route-group-list="${section}"]`);
            if (!list) return;

            Array.from(list.children)
                .filter(element => element.classList && element.classList.contains('delivery-route-group'))
                .forEach(group => {
                    const sequenceElement = group.querySelector('.delivery-route-sequence');
                    if (sequenceElement) sequenceElement.textContent = String(sequence);
                    sequence += 1;
                });
        });
    }

    function getCompletableOrderChecks(group) {
        return Array.from(group.querySelectorAll('.delivery-route-complete-check:not(:disabled)'))
            .filter(checkbox => {
                const card = checkbox.closest('.delivery-route-order-card');
                return card && card.dataset.completable === 'true';
            });
    }

    function getSelectedOrderIds(group) {
        const result = [];
        const seen = new Set();

        getCompletableOrderChecks(group)
            .filter(checkbox => checkbox.checked)
            .forEach(checkbox => {
                const orderId = Number(checkbox.dataset.orderId);
                if (!Number.isSafeInteger(orderId) || orderId <= 0 || seen.has(orderId)) return;
                seen.add(orderId);
                result.push(orderId);
            });

        return result;
    }

    function refreshGroupSelection(group) {
        const boxes = getCompletableOrderChecks(group);
        const selectedBoxes = boxes.filter(checkbox => checkbox.checked);
        const selectedCount = selectedBoxes.length;
        const totalCount = boxes.length;
        const allCompleted = group.dataset.allCompleted === 'true';
        const selectAll = group.querySelector('.delivery-route-group-select-all');
        const completeButton = group.querySelector('[data-delivery-route-complete-button]');
        const countBadge = completeButton
            ? completeButton.querySelector('[data-delivery-route-selected-count]')
            : null;
        const progress = getGroupProgressElement(group);

        if (selectAll) {
            selectAll.checked = !allCompleted && totalCount > 0 && selectedCount === totalCount;
            selectAll.indeterminate = !allCompleted && selectedCount > 0 && selectedCount < totalCount;
            selectAll.disabled = allCompleted || totalCount === 0;
        }

        if (completeButton) {
            completeButton.disabled = allCompleted || selectedCount === 0;
        }

        if (countBadge) {
            countBadge.textContent = String(selectedCount);
            countBadge.hidden = allCompleted;
        }

        if (progress && !group.classList.contains('is-freight')) {
            progress.textContent = allCompleted
                ? '전체 완료'
                : (totalCount > 0 ? `선택 ${selectedCount}/${totalCount}` : '완료 대기');
        }

        group.classList.toggle('has-selection', selectedCount > 0);

        boxes.forEach(checkbox => {
            const card = checkbox.closest('.delivery-route-order-card');
            if (card) {
                card.classList.toggle('is-selected-for-completion', checkbox.checked);
            }
        });
    }

    function getGroupProgressElement(group) {
        if (!group) return null;

        const groupId = group.dataset.groupId || '';
        if (!groupId) return group.querySelector('.delivery-route-selection-progress');

        return document.querySelector(`[data-progress-for="${cssEscape(groupId)}"]`)
            || group.querySelector('.delivery-route-selection-progress');
    }

    async function submitCompletion(form, deliveryDate, orderIds, files) {
        const action = form.getAttribute('action') || '/team/deliveryRoute/complete';
        const formData = new FormData();
        const csrfInput = form.querySelector('input[type="hidden"]');
        const headers = { 'X-Requested-With': 'fetch' };

        formData.append('deliveryDate', String(deliveryDate || ''));
        orderIds.forEach(orderId => formData.append('orderIds', String(orderId)));
        files.forEach(file => formData.append('files', file, file.name));

        if (csrfInput && csrfInput.name && csrfInput.value) {
            formData.append(csrfInput.name, csrfInput.value);

            const headerName = csrfInput.dataset.csrfHeader;
            if (headerName) headers[headerName] = csrfInput.value;
        }

        const response = await fetch(action, {
            method: 'POST',
            headers: headers,
            body: formData,
            credentials: 'same-origin'
        });

        const responseBody = await parseResponseBody(response);

        if (!response.ok || responseBody.success === false) {
            throw new Error(responseBody.message || `배송완료 처리에 실패했습니다. (${response.status})`);
        }

        return responseBody;
    }

    async function parseResponseBody(response) {
        const contentType = response.headers.get('content-type') || '';

        if (contentType.includes('application/json')) {
            return response.json();
        }

        const text = await response.text();
        return { success: response.ok, message: text };
    }

    async function showMessage(title, text, icon) {
        const toastElement = document.getElementById('delivery-route-toast');
        const titleElement = document.getElementById('delivery-route-toast-title');
        const bodyElement = document.getElementById('delivery-route-toast-body');
        const iconElement = document.querySelector('#delivery-route-toast-icon i');
        const type = normalizeMessageType(icon);

        if (toastElement && titleElement && bodyElement
            && window.bootstrap && window.bootstrap.Toast) {

            titleElement.textContent = String(title || '알림');
            bodyElement.textContent = String(text || '');

            toastElement.classList.remove('is-success', 'is-warning', 'is-error', 'is-info');
            toastElement.classList.add(`is-${type}`);

            if (iconElement) {
                iconElement.className = resolveToastIconClass(type);
            }

            const options = {
                autohide: true,
                delay: type === 'error' ? 7000 : 4500
            };

            const toast = window.bootstrap.Toast.getOrCreateInstance
                ? window.bootstrap.Toast.getOrCreateInstance(toastElement, options)
                : new window.bootstrap.Toast(toastElement, options);

            toast.show();
            return null;
        }

        window.alert(`${title || '알림'}\n${text || ''}`);
        return null;
    }

    function normalizeMessageType(type) {
        const value = String(type || 'info').toLowerCase();
        if (value === 'success' || value === 'warning' || value === 'error') return value;
        return 'info';
    }

    function resolveToastIconClass(type) {
        switch (type) {
            case 'success':
                return 'ri-checkbox-circle-line';
            case 'warning':
                return 'ri-alert-line';
            case 'error':
                return 'ri-error-warning-line';
            default:
                return 'ri-information-line';
        }
    }

    function isImageFile(file) {
        return Boolean(file && file.type && file.type.toLowerCase().startsWith('image/'));
    }

    function formatFileSize(bytes) {
        const value = Number(bytes || 0);
        if (value < 1024) return `${value} B`;
        if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
        return `${(value / (1024 * 1024)).toFixed(1)} MB`;
    }

    function setGroupExpanded(group, expanded) {
        if (!group) return;

        const toggle = group.querySelector('[data-delivery-route-toggle]');
        if (!toggle) return;

        const bodyId = toggle.getAttribute('aria-controls');
        const body = bodyId ? document.getElementById(bodyId) : null;

        toggle.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        group.classList.toggle('is-open', expanded);

        if (body) {
            animateGroupBody(body, expanded);
        }
    }

    function forceGroupCollapsed(group) {
        if (!group) return;

        const toggle = group.querySelector('[data-delivery-route-toggle]');
        const bodyId = toggle ? toggle.getAttribute('aria-controls') : null;
        const body = bodyId ? document.getElementById(bodyId) : null;

        if (toggle) toggle.setAttribute('aria-expanded', 'false');
        group.classList.remove('is-open');

        if (body) {
            if (body._deliveryRouteTransitionHandler) {
                body.removeEventListener('transitionend', body._deliveryRouteTransitionHandler);
                body._deliveryRouteTransitionHandler = null;
            }

            body.hidden = true;
            body.style.height = '';
            body.style.overflow = '';
            body.style.transition = '';
        }
    }

    function animateGroupBody(body, expanded) {
        if (!body) return;

        const reduceMotion = window.matchMedia
            && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

        if (body._deliveryRouteTransitionHandler) {
            body.removeEventListener('transitionend', body._deliveryRouteTransitionHandler);
            body._deliveryRouteTransitionHandler = null;
        }

        if (reduceMotion) {
            body.hidden = !expanded;
            body.style.height = '';
            body.style.overflow = '';
            body.style.transition = '';
            return;
        }

        body.style.overflow = 'hidden';
        body.style.transition = 'height 220ms ease';

        if (expanded) {
            body.hidden = false;
            body.style.height = '0px';
            void body.offsetHeight;
            body.style.height = `${body.scrollHeight}px`;
        } else {
            body.hidden = false;
            body.style.height = `${body.scrollHeight}px`;
            void body.offsetHeight;
            body.style.height = '0px';
        }

        const onTransitionEnd = function (event) {
            if (event.target !== body || event.propertyName !== 'height') return;

            body.removeEventListener('transitionend', onTransitionEnd);
            body._deliveryRouteTransitionHandler = null;
            body.hidden = !expanded;
            body.style.height = '';
            body.style.overflow = '';
            body.style.transition = '';
        };

        body._deliveryRouteTransitionHandler = onTransitionEnd;
        body.addEventListener('transitionend', onTransitionEnd);
    }

    function areAllGroupsExpanded(groups) {
        return groups.length > 0 && groups.every(group => {
            const toggle = group.querySelector('[data-delivery-route-toggle]');
            return toggle && toggle.getAttribute('aria-expanded') === 'true';
        });
    }

    function refreshToggleAllButton(groups, button) {
        if (!button) return;

        if (groups.length === 0) {
            button.disabled = true;
            setButtonLabel(button, '열 항목 없음', 'ri-forbid-line');
            return;
        }

        button.disabled = false;

        if (areAllGroupsExpanded(groups)) {
            button.setAttribute('aria-label', '모든 업체 묶음 닫기');
            setButtonLabel(button, '전체 닫기', 'ri-contract-up-down-line');
        } else {
            button.setAttribute('aria-label', '모든 업체 묶음 열기');
            setButtonLabel(button, '전체 열기', 'ri-expand-up-down-line');
        }
    }

    function setButtonLabel(button, label, iconClass) {
        const icon = button.querySelector('i');
        const text = button.querySelector('span');

        if (icon) icon.className = iconClass;
        if (text) text.textContent = label;
    }

    function normalizePositiveIds(values) {
        const source = Array.isArray(values) ? values : [];
        const result = [];
        const seen = new Set();

        source.forEach(value => {
            const id = Number(value);
            if (!Number.isSafeInteger(id) || id <= 0 || seen.has(id)) return;
            seen.add(id);
            result.push(id);
        });

        return result;
    }

    function toNonNegativeInteger(value, fallback) {
        const number = Number(value);
        if (Number.isSafeInteger(number) && number >= 0) return number;
        return fallback;
    }

    function cssEscape(value) {
        const text = String(value || '');

        if (window.CSS && typeof window.CSS.escape === 'function') {
            return window.CSS.escape(text);
        }

        return text.replace(/([ #;?%&,+*~':"!^$[\]()=>|/@])/g, '\\$1');
    }

    function toText(value) {
        if (value === undefined || value === null) return '';
        return String(value);
    }

    function escapeHtml(value) {
        return toText(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

})();
