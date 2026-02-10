/* =========================
 * analytics.js (FULL)
 * - Y축: 업체별/제품별
 * - Y-Sub: 주소(모달), 시간대(prompt)
 * - X축: 지표(매출/주문건수/주문수량), 제품 분류(규격/비규격 레벨 모달)
 * - Sortable Drag
 * - 테이블 정렬/페이징 더보기
 * - 차트(Chart.js)
 * - 엑셀/ A4 출력
 * ========================= */

"use strict";

/** =========================
 *  State
 * ========================= */
let yPrimary = null; // "COMPANY" | "PRODUCT"
let ySubs = [];      // ["ADDRESS","HOUR"]
let xMetrics = [];   // ["SALES","TASK_COUNT","QTY"]
let xDims = [];      // ["STANDARD","NONSTANDARD"]

let page = 0;
const size = 50;
let totalRows = 0;

let sortKey = "";
let sortDir = "desc";

let chartInstance = null;

// ✅ 서버가 내려주는 columns 메타(키/한글/설명)
let currentColumns = [];

/** ===== 기존 설정 값들 ===== */
let currentAddressFilter = null; // {province, city, district} (문자열 name 기준)
let currentHourRanges = [];      // [{startHour,endHour},...]

let standardLevel = "CATEGORY";
let nonStandardLevel = "SORT";

/** ===== 모달 인스턴스 ===== */
let addressModal = null;
let standardLevelModal = null;
let nonStandardLevelModal = null;

/** =========================
 *  DOM Ready
 * ========================= */
document.addEventListener("DOMContentLoaded", () => {
	// Sortable
	initSortable();

	// Axis buttons
	bindAxisButtons();

	// Search / Load more
	document.getElementById("analyticsSearchBtn").addEventListener("click", () => {
		page = 0;
		queryAndRender(true);
	});

	document.getElementById("analyticsLoadMoreBtn").addEventListener("click", () => {
		page += 1;
		queryAndRender(false);
	});

	// Export
	document.getElementById("analyticsExcelBtn").addEventListener("click", exportExcel);
	document.getElementById("analyticsA4Btn").addEventListener("click", exportA4);

	// Default dates = today
	const today = new Date().toISOString().slice(0, 10);
	document.getElementById("fromDate").value = today;
	document.getElementById("toDate").value = today;

	// Modals init
	initAddressModal();
	initLevelModals();

	// Help tooltip/popup for "?"
	initHelpBubbles();

	// Initial render
	renderAxisUI();
});

/** =========================
 *  Bindings
 * ========================= */
function bindAxisButtons() {
	document.getElementById("btnAddYCompany").addEventListener("click", () => setYPrimary("COMPANY"));
	document.getElementById("btnAddYProduct").addEventListener("click", () => setYPrimary("PRODUCT"));

	document.getElementById("btnAddYAddress").addEventListener("click", () => addYSub("ADDRESS"));
	document.getElementById("btnAddYHour").addEventListener("click", () => addYSub("HOUR"));

	document.getElementById("btnAddMetricSales").addEventListener("click", () => addMetric("SALES"));
	document.getElementById("btnAddMetricTaskCount").addEventListener("click", () => addMetric("TASK_COUNT"));
	document.getElementById("btnAddMetricQty").addEventListener("click", () => addMetric("QTY"));

	document.getElementById("btnAddDimStandard").addEventListener("click", () => addDim("STANDARD"));
	document.getElementById("btnAddDimNonStandard").addEventListener("click", () => addDim("NONSTANDARD"));
}

/** =========================
 *  Sortable
 * ========================= */
function initSortable() {
	const yBox = document.getElementById("yAxisBox");
	const ySubBox = document.getElementById("ySubAxisBox");
	const xBox = document.getElementById("xAxisBox");

	if (yBox) new Sortable(yBox, { handle: ".handle", animation: 150 });
	if (ySubBox) new Sortable(ySubBox, { handle: ".handle", animation: 150 });
	if (xBox) new Sortable(xBox, { handle: ".handle", animation: 150 });
}

/** =========================
 *  Axis State handlers
 * ========================= */
function setYPrimary(v) {
	yPrimary = v;
	ySubs = [];
	xDims = [];
	renderAxisUI();
}

function addYSub(v) {
	if (yPrimary !== "COMPANY") return;
	if (!ySubs.includes(v)) ySubs.push(v);
	renderAxisUI();
}

function addMetric(m) {
	if (!xMetrics.includes(m)) xMetrics.push(m);
	renderAxisUI();
}

function addDim(d) {
	if (yPrimary !== "PRODUCT") return;
	if (!xDims.includes(d)) xDims.push(d);
	renderAxisUI();
}

function removeItem(list, v) {
	const idx = list.indexOf(v);
	if (idx >= 0) list.splice(idx, 1);
}

/** =========================
 *  Axis UI
 * ========================= */
function renderAxisUI() {
	const yBox = document.getElementById("yAxisBox");
	const ySubBox = document.getElementById("ySubAxisBox");
	const xBox = document.getElementById("xAxisBox");

	if (!yBox || !ySubBox || !xBox) return;

	yBox.innerHTML = "";
	ySubBox.innerHTML = "";
	xBox.innerHTML = "";

	// Y primary
	if (yPrimary) {
		yBox.appendChild(axisItem(
			`Y:${yPrimary === "COMPANY" ? "업체별" : "제품별"}`,
			() => {
				yPrimary = null;
				ySubs = [];
				xDims = [];
				renderAxisUI();
			},
			null,
			yPrimary === "COMPANY"
				? "업체 단위로 집계합니다. 주소/시간대 옵션을 사용할 수 있습니다."
				: "제품 옵션(JSON) 기반으로 제품/시리즈/카테고리 등으로 집계합니다."
		));
	}

	// Enable/disable buttons
	const btnAddYAddress = document.getElementById("btnAddYAddress");
	const btnAddYHour = document.getElementById("btnAddYHour");
	const btnAddDimStandard = document.getElementById("btnAddDimStandard");
	const btnAddDimNonStandard = document.getElementById("btnAddDimNonStandard");

	if (btnAddYAddress) btnAddYAddress.disabled = (yPrimary !== "COMPANY");
	if (btnAddYHour) btnAddYHour.disabled = (yPrimary !== "COMPANY");

	if (btnAddDimStandard) btnAddDimStandard.disabled = (yPrimary !== "PRODUCT");
	if (btnAddDimNonStandard) btnAddDimNonStandard.disabled = (yPrimary !== "PRODUCT");

	// Y sub items
	ySubs.forEach(s => {
		ySubBox.appendChild(axisItem(
			`Y-Sub:${s === "ADDRESS" ? "주소" : "시간대"}`,
			() => { removeItem(ySubs, s); renderAxisUI(); },
			() => openSubConfigModal(s),
			s === "ADDRESS"
				? "업체 주소(도/시/구) 기준으로 필터/표시를 추가합니다."
				: "Task 생성 시간 기준으로 시간대 필터를 추가합니다."
		));
	});

	// X metrics
	xMetrics.forEach(m => {
		const label = m === "SALES" ? "매출액" : (m === "TASK_COUNT" ? "주문건수" : "주문수량");
		const help = m === "SALES"
			? "Task.totalPrice 합계 기준입니다."
			: (m === "TASK_COUNT"
				? "Task(업무) 건수 기준입니다."
				: "Order.quantity 합계 기준입니다.");

		xBox.appendChild(axisItem(
			`X:${label}`,
			() => { removeItem(xMetrics, m); renderAxisUI(); },
			null,
			help
		));
	});

	// X dims (product breakdown)
	xDims.forEach(d => {
		const label = d === "STANDARD" ? "규격 분류" : "비규격 분류";
		const help = d === "STANDARD"
			? "규격 제품을 어떤 레벨(CATEGORY/SERIES/PRODUCT/COLOR)로 묶어서 볼지 설정합니다."
			: "비규격 제품을 어떤 레벨(SORT/SERIES/PRODUCT/COLOR)로 묶어서 볼지 설정합니다.";

		xBox.appendChild(axisItem(
			`X:${label}`,
			() => { removeItem(xDims, d); renderAxisUI(); },
			() => openDimConfigModal(d),
			help
		));
	});

	// Axis 변경으로 추가된 help-q에도 이벤트 적용
	refreshHelpBubbles();
}

function axisItem(text, onRemove, onConfig, helpText) {
	const div = document.createElement("div");
	div.className = "axis-item";

	const handle = document.createElement("span");
	handle.className = "handle";
	handle.textContent = "☰";

	const label = document.createElement("span");
	label.textContent = text;

	div.appendChild(handle);
	div.appendChild(label);

	if (helpText) {
		const q = document.createElement("span");
		q.className = "help-q";
		q.setAttribute("data-help", helpText);
		q.textContent = "?";
		div.appendChild(q);
	}

	if (onConfig) {
		const btn = document.createElement("button");
		btn.className = "btn btn-outline-secondary btn-mini";
		btn.type = "button";
		btn.textContent = "입력/선택";
		btn.addEventListener("click", onConfig);
		div.appendChild(btn);
	}

	const rm = document.createElement("button");
	rm.className = "btn btn-outline-danger btn-mini";
	rm.type = "button";
	rm.textContent = "삭제";
	rm.addEventListener("click", onRemove);

	div.appendChild(rm);
	return div;
}

/** =========================
 *  Config modals
 * ========================= */
function openSubConfigModal(type) {
	if (type === "HOUR") {
		const input = prompt("시간대 범위를 입력하세요. 예) 0-8,10-16", "0-23");
		if (!input) return;

		const parsed = parseHourRanges(input);
		if (!parsed.ok) {
			alert(parsed.message);
			return;
		}
		currentHourRanges = parsed.ranges;
		alert("시간대 설정이 저장되었습니다.");
		return;
	}

	// ✅ ADDRESS: prompt 제거 → 모달 오픈
	if (type === "ADDRESS") {
		openAddressModal();
	}
}

function openDimConfigModal(type) {
	if (type === "STANDARD") {
		if (!standardLevelModal) {
			alert("규격 레벨 모달 초기화 실패(bootstrap/HTML id 확인 필요)");
			return;
		}
		const sel = document.getElementById("standardLevelSelect");
		if (sel) sel.value = standardLevel || "CATEGORY";
		standardLevelModal.show();
		return;
	}

	if (type === "NONSTANDARD") {
		if (!nonStandardLevelModal) {
			alert("비규격 레벨 모달 초기화 실패(bootstrap/HTML id 확인 필요)");
			return;
		}
		const sel = document.getElementById("nonStandardLevelSelect");
		if (sel) sel.value = nonStandardLevel || "SORT";
		nonStandardLevelModal.show();
	}
}

/** =========================
 *  Level Modals init
 * ========================= */
function initLevelModals() {
	const stdEl = document.getElementById("standardLevelModal");
	const nonEl = document.getElementById("nonStandardLevelModal");

	if (stdEl) standardLevelModal = new bootstrap.Modal(stdEl);
	if (nonEl) nonStandardLevelModal = new bootstrap.Modal(nonEl);

	const stdApply = document.getElementById("standardLevelApplyBtn");
	if (stdApply) {
		stdApply.addEventListener("click", () => {
			const sel = document.getElementById("standardLevelSelect");
			standardLevel = sel ? sel.value : standardLevel;
			alert("규격 분류 레벨 저장됨: " + standardLevel);
			if (standardLevelModal) standardLevelModal.hide();
		});
	}

	const nonApply = document.getElementById("nonStandardLevelApplyBtn");
	if (nonApply) {
		nonApply.addEventListener("click", () => {
			const sel = document.getElementById("nonStandardLevelSelect");
			nonStandardLevel = sel ? sel.value : nonStandardLevel;
			alert("비규격 분류 레벨 저장됨: " + nonStandardLevel);
			if (nonStandardLevelModal) nonStandardLevelModal.hide();
		});
	}
}

/** =========================
 *  Address Modal (3-level selects)
 * ========================= */
function initAddressModal() {
	const modalEl = document.getElementById("addressSelectModal");
	if (!modalEl) return;

	addressModal = new bootstrap.Modal(modalEl);

	const provinceSel = document.getElementById("addrProvinceSelect");
	const citySel = document.getElementById("addrCitySelect");
	const districtSel = document.getElementById("addrDistrictSelect");

	if (!provinceSel || !citySel || !districtSel) return;

	provinceSel.addEventListener("change", async () => {
		const provinceId = provinceSel.value || "";
		await onProvinceChanged(provinceId);
	});

	citySel.addEventListener("change", async () => {
		const cityId = citySel.value || "";
		await onCityChanged(cityId);
	});

	const clearBtn = document.getElementById("addrClearBtn");
	if (clearBtn) {
		clearBtn.addEventListener("click", async () => {
			currentAddressFilter = null;
			await resetAddressSelects();
			alert("주소 필터가 초기화되었습니다.");
		});
	}

	const applyBtn = document.getElementById("addrApplyBtn");
	if (applyBtn) {
		applyBtn.addEventListener("click", () => {
			applyAddressSelection();
		});
	}
}

async function openAddressModal() {
	if (!addressModal) {
		alert("주소 모달 초기화 실패(bootstrap 로딩/HTML id 확인 필요)");
		return;
	}

	await resetAddressSelects();
	await loadProvinces();

	if (currentAddressFilter && currentAddressFilter.province) {
		await restoreAddressSelection(currentAddressFilter);
	}

	addressModal.show();
}

async function resetAddressSelects() {
	setSelectOptions(document.getElementById("addrProvinceSelect"), [{ value: "", label: "전체" }], "");
	setSelectOptions(document.getElementById("addrCitySelect"), [{ value: "", label: "전체" }], "");
	setSelectOptions(document.getElementById("addrDistrictSelect"), [{ value: "", label: "전체" }], "");

	const citySel = document.getElementById("addrCitySelect");
	const districtSel = document.getElementById("addrDistrictSelect");
	const cityHint = document.getElementById("addrCityHint");

	if (citySel) citySel.disabled = true;
	if (districtSel) districtSel.disabled = true;
	if (cityHint) cityHint.style.display = "none";
}

async function loadProvinces() {
	const provinceSel = document.getElementById("addrProvinceSelect");
	if (!provinceSel) return;

	const res = await fetch("/api/v1/provinces");
	if (!res.ok) {
		alert("Province 목록 조회 실패");
		return;
	}

	const provinces = await res.json(); // [{id,name},...]
	const options = [{ value: "", label: "전체" }].concat(
		(provinces || []).map(p => ({ value: String(p.id), label: p.name }))
	);

	setSelectOptions(provinceSel, options, provinceSel.value || "");
}

async function onProvinceChanged(provinceId) {
	const citySel = document.getElementById("addrCitySelect");
	const districtSel = document.getElementById("addrDistrictSelect");
	const cityHint = document.getElementById("addrCityHint");

	if (!citySel || !districtSel || !cityHint) return;

	// 초기화
	setSelectOptions(citySel, [{ value: "", label: "전체" }], "");
	setSelectOptions(districtSel, [{ value: "", label: "전체" }], "");
	citySel.disabled = true;
	districtSel.disabled = true;
	cityHint.style.display = "none";

	if (!provinceId) return;

	// 1) Province -> City
	const resCity = await fetch(`/api/v1/province/${provinceId}/cities`);
	if (!resCity.ok) {
		alert("City 목록 조회 실패");
		return;
	}
	const cities = await resCity.json();

	if (cities && cities.length > 0) {
		const cityOptions = [{ value: "", label: "전체" }].concat(
			cities.map(c => ({ value: String(c.id), label: c.name }))
		);
		setSelectOptions(citySel, cityOptions, "");
		citySel.disabled = false;
		return;
	}

	// 2) City가 없으면 Province 직결 District
	cityHint.style.display = "block";

	const resDist = await fetch(`/api/v1/province/${provinceId}/districts`);
	if (!resDist.ok) {
		alert("District 목록 조회 실패");
		return;
	}
	const districts = await resDist.json();

	const distOptions = [{ value: "", label: "전체" }].concat(
		(districts || []).map(d => ({ value: String(d.id), label: d.name }))
	);
	setSelectOptions(districtSel, distOptions, "");
	districtSel.disabled = false;
}

async function onCityChanged(cityId) {
	const districtSel = document.getElementById("addrDistrictSelect");
	if (!districtSel) return;

	setSelectOptions(districtSel, [{ value: "", label: "전체" }], "");
	districtSel.disabled = true;

	if (!cityId) return;

	const res = await fetch(`/api/v1/city/${cityId}/districts`);
	if (!res.ok) {
		alert("District 목록 조회 실패");
		return;
	}
	const districts = await res.json();

	const distOptions = [{ value: "", label: "전체" }].concat(
		(districts || []).map(d => ({ value: String(d.id), label: d.name }))
	);
	setSelectOptions(districtSel, distOptions, "");
	districtSel.disabled = false;
}

function setSelectOptions(selectEl, options, selectedValue) {
	if (!selectEl) return;
	selectEl.innerHTML = "";
	options.forEach(opt => {
		const o = document.createElement("option");
		o.value = opt.value;
		o.textContent = opt.label;
		selectEl.appendChild(o);
	});
	selectEl.value = selectedValue || "";
}

function applyAddressSelection() {
	const provinceSel = document.getElementById("addrProvinceSelect");
	const citySel = document.getElementById("addrCitySelect");
	const districtSel = document.getElementById("addrDistrictSelect");

	if (!provinceSel || !citySel || !districtSel) return;

	const provinceId = provinceSel.value || "";
	const cityId = citySel.value || "";
	const districtId = districtSel.value || "";

	const provinceName = provinceId ? provinceSel.options[provinceSel.selectedIndex].text : null;
	const cityName = (cityId && !citySel.disabled) ? citySel.options[citySel.selectedIndex].text : null;
	const districtName = (districtId && !districtSel.disabled) ? districtSel.options[districtSel.selectedIndex].text : null;

	currentAddressFilter = {
		province: provinceName || null,
		city: cityName || null,
		district: districtName || null
	};

	alert("주소 필터가 저장되었습니다.");
	if (addressModal) addressModal.hide();
}

async function restoreAddressSelection(filter) {
	const provinceSel = document.getElementById("addrProvinceSelect");
	const citySel = document.getElementById("addrCitySelect");
	const districtSel = document.getElementById("addrDistrictSelect");

	if (!provinceSel || !citySel || !districtSel) return;

	if (filter.province) {
		const provOpt = Array.from(provinceSel.options).find(o => o.text === filter.province);
		if (provOpt) {
			provinceSel.value = provOpt.value;
			await onProvinceChanged(provOpt.value);
		}
	}

	if (filter.city && !citySel.disabled) {
		const cityOpt = Array.from(citySel.options).find(o => o.text === filter.city);
		if (cityOpt) {
			citySel.value = cityOpt.value;
			await onCityChanged(cityOpt.value);
		}
	}

	if (filter.district && !districtSel.disabled) {
		const distOpt = Array.from(districtSel.options).find(o => o.text === filter.district);
		if (distOpt) {
			districtSel.value = distOpt.value;
		}
	}
}

/** =========================
 *  Request builders
 * ========================= */
function buildRequestBody() {
	const fromDateVal = document.getElementById("fromDate").value;
	const toDateVal = document.getElementById("toDate").value;

	const body = {
		fromDate: fromDateVal ? fromDateVal : null,
		toDate: toDateVal ? toDateVal : null,
		primaryY: yPrimary,
		metrics: xMetrics,
		sortKey,
		sortDir,
		page,
		size
	};

	if (yPrimary === "COMPANY") {
		if (ySubs.includes("ADDRESS")) body.addressFilter = currentAddressFilter;
		if (ySubs.includes("HOUR")) body.hourRanges = currentHourRanges;
	}

	if (yPrimary === "PRODUCT") {
		body.productBreakdown = {
			includeStandard: xDims.includes("STANDARD") || xDims.length === 0,
			includeNonStandard: xDims.includes("NONSTANDARD") || xDims.length === 0,
			standardLevel,
			nonStandardLevel
		};
	}

	return body;
}

function buildRequestBodyForExport() {
	const b = buildRequestBody();
	b.page = 0;
	b.size = 2147483647;
	return b;
}

/** =========================
 *  Query & Render
 * ========================= */
async function queryAndRender(reset) {
	if (!yPrimary) {
		alert("Y축(업체별/제품별)을 먼저 선택해주세요.");
		return;
	}

	const res = await fetch("/analytics/api/query", {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify(buildRequestBody())
	});

	if (!res.ok) {
		alert("조회 실패");
		return;
	}

	const data = await res.json();
	totalRows = data.totalRows || 0;

	currentColumns = Array.isArray(data.columns) ? data.columns : [];

	document.getElementById("analyticsTotalText").textContent = `${totalRows} rows`;
	document.getElementById("analyticsExcelBtn").disabled = (totalRows === 0);
	document.getElementById("analyticsA4Btn").disabled = (totalRows === 0);

	renderTable(data.rows || [], reset);

	const loaded = (page + 1) * size;
	document.getElementById("analyticsLoadMoreBtn").style.display = (loaded < totalRows) ? "inline-block" : "none";

	renderChart(data.chart);
	refreshHelpBubbles();
}

/** =========================
 *  Table
 * ========================= */
function renderTable(rows, reset) {
	const thead = document.getElementById("analyticsThead");
	const tbody = document.getElementById("analyticsTbody");

	if (!thead || !tbody) return;

	if (reset) {
		thead.innerHTML = "";
		tbody.innerHTML = "";
	}

	if (!rows || rows.length === 0) {
		if (reset) {
			tbody.innerHTML = `<tr><td class="text-center p-4" colspan="30">데이터가 없습니다.</td></tr>`;
		}
		return;
	}

	// ✅ columns 메타 기준으로 헤더 구성 (한글 + ? 도움말)
	const cols = (currentColumns && currentColumns.length > 0)
		? currentColumns
		: Object.keys(rows[0]).map(k => ({ key: k, label: k, help: "" }));

	if (reset) {
		const tr = document.createElement("tr");

		cols.forEach(c => {
			const th = document.createElement("th");
			th.className = "th-sort";

			const labelSpan = document.createElement("span");
			labelSpan.textContent = c.label || c.key;
			th.appendChild(labelSpan);

			if (c.help) {
				const q = document.createElement("span");
				q.className = "help-q th-help";
				q.setAttribute("data-help", c.help);
				q.textContent = "?";
				th.appendChild(q);
			}

			const arrows = document.createElement("span");
			arrows.className = "arrows";
			arrows.textContent = (sortKey === c.key) ? (sortDir === "asc" ? "▲" : "▼") : "↕";
			th.appendChild(arrows);

			th.addEventListener("click", (e) => {
				// help-q 클릭은 정렬 방지
				if (e.target && e.target.classList && e.target.classList.contains("help-q")) return;

				if (sortKey === c.key) {
					sortDir = (sortDir === "asc") ? "desc" : "asc";
				} else {
					sortKey = c.key;
					sortDir = "desc";
				}
				page = 0;
				queryAndRender(true);
			});

			tr.appendChild(th);
		});

		thead.appendChild(tr);
	}

	rows.forEach(r => {
		const tr = document.createElement("tr");
		cols.forEach(c => {
			const td = document.createElement("td");
			td.textContent = (r[c.key] == null) ? "" : String(r[c.key]);
			tr.appendChild(td);
		});
		tbody.appendChild(tr);
	});
}

/** =========================
 *  Chart
 * ========================= */
function renderChart(chart) {
	const ctx = document.getElementById("analyticsChart");
	if (!ctx) return;

	if (!chart || !chart.labels || !chart.values) {
		if (chartInstance) {
			chartInstance.destroy();
			chartInstance = null;
		}
		return;
	}

	if (chartInstance) chartInstance.destroy();

	chartInstance = new Chart(ctx, {
		type: chart.type || "bar",
		data: {
			labels: chart.labels,
			datasets: [{
				label: chart.title || chart.valueKey || "value",
				data: chart.values
			}]
		},
		options: {
			responsive: true,
			maintainAspectRatio: false
		}
	});
}

/** =========================
 *  Export
 * ========================= */
async function exportExcel() {
	const res = await fetch("/analytics/api/export/excel", {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify(buildRequestBodyForExport())
	});

	if (!res.ok) {
		alert("엑셀 출력 실패");
		return;
	}

	const blob = await res.blob();
	const url = window.URL.createObjectURL(blob);

	const a = document.createElement("a");
	a.href = url;
	a.download = "analytics.xlsx";
	a.click();

	window.URL.revokeObjectURL(url);
}

function exportA4() {
	fetch("/analytics/api/export/a4", {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify(buildRequestBodyForExport())
	})
		.then(res => res.text())
		.then(html => {
			const w = window.open("", "_blank");
			if (!w) {
				alert("팝업이 차단되었습니다. 팝업 허용 후 다시 시도해주세요.");
				return;
			}
			w.document.open();
			w.document.write(html);
			w.document.close();
			w.focus();
			w.print();
		})
		.catch(() => alert("A4 출력 실패"));
}

/** =========================
 *  Help bubbles ( ? 클릭 시 작은 안내 박스 )
 *  - Bootstrap Tooltip/Popover로 안하고, 충돌 적은 순수 DOM 방식
 * ========================= */
let helpBubbleEl = null;

function initHelpBubbles() {
	// 전역 클릭: 바깥 누르면 닫기
	document.addEventListener("click", (e) => {
		if (!helpBubbleEl) return;

		const t = e.target;
		if (t && t.classList && t.classList.contains("help-q")) return;

		// bubble 내부 클릭은 유지
		if (helpBubbleEl.contains(t)) return;

		closeHelpBubble();
	});

	// help-q 클릭 핸들러 (동적 요소까지)
	document.addEventListener("click", (e) => {
		const t = e.target;
		if (!t || !t.classList || !t.classList.contains("help-q")) return;

		e.preventDefault();
		e.stopPropagation();

		const help = t.getAttribute("data-help") || "";
		if (!help) return;

		toggleHelpBubble(t, help);
	});
}

function refreshHelpBubbles() {
	// 특별히 할 건 없고, bubble이 떠있을 때 DOM 변경되면 닫는 정도만
	if (helpBubbleEl) closeHelpBubble();
}

function toggleHelpBubble(anchorEl, text) {
	if (helpBubbleEl && helpBubbleEl.__anchor === anchorEl) {
		closeHelpBubble();
		return;
	}
	showHelpBubble(anchorEl, text);
}

function showHelpBubble(anchorEl, text) {
	closeHelpBubble();

	const rect = anchorEl.getBoundingClientRect();

	const div = document.createElement("div");
	div.className = "analytics-help-bubble";
	div.textContent = text;

	// 최소 스타일 (CSS가 없더라도 뜨게)
	div.style.position = "fixed";
	div.style.zIndex = "9999";
	div.style.maxWidth = "360px";
	div.style.padding = "10px 12px";
	div.style.border = "1px solid #ddd";
	div.style.borderRadius = "10px";
	div.style.background = "#fff";
	div.style.boxShadow = "0 6px 18px rgba(0,0,0,.12)";
	div.style.fontSize = "13px";
	div.style.lineHeight = "1.4";
	div.style.color = "#111";

	document.body.appendChild(div);

	// 위치 계산 (anchor 아래쪽 우선)
	const bubbleRect = div.getBoundingClientRect();
	let top = rect.bottom + 8;
	let left = rect.left;

	// 화면 밖이면 조정
	if (left + bubbleRect.width > window.innerWidth - 8) {
		left = window.innerWidth - bubbleRect.width - 8;
	}
	if (left < 8) left = 8;

	if (top + bubbleRect.height > window.innerHeight - 8) {
		// 아래에 못두면 위로
		top = rect.top - bubbleRect.height - 8;
		if (top < 8) top = 8;
	}

	div.style.top = `${top}px`;
	div.style.left = `${left}px`;

	div.__anchor = anchorEl;
	helpBubbleEl = div;
}

function closeHelpBubble() {
	if (helpBubbleEl && helpBubbleEl.parentNode) {
		helpBubbleEl.parentNode.removeChild(helpBubbleEl);
	}
	helpBubbleEl = null;
}

/** =========================
 *  Utils: hour range parser
 * ========================= */
function parseHourRanges(input) {
	const raw = String(input || "").trim();
	if (!raw) return { ok: false, message: "시간대 입력이 비었습니다.", ranges: [] };

	const parts = raw.split(",").map(x => x.trim()).filter(Boolean);
	if (parts.length === 0) return { ok: false, message: "시간대 입력이 올바르지 않습니다.", ranges: [] };

	const ranges = [];
	for (const p of parts) {
		const [a, b] = p.split("-").map(v => v.trim());
		if (a == null || b == null || a === "" || b === "") {
			return { ok: false, message: `형식 오류: "${p}" (예: 0-8,10-16)`, ranges: [] };
		}
		const startHour = parseInt(a, 10);
		const endHour = parseInt(b, 10);

		if (!Number.isFinite(startHour) || !Number.isFinite(endHour)) {
			return { ok: false, message: `숫자 오류: "${p}"`, ranges: [] };
		}
		if (startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23) {
			return { ok: false, message: `범위 오류(0~23): "${p}"`, ranges: [] };
		}

		ranges.push({ startHour, endHour });
	}

	return { ok: true, message: "OK", ranges };
}
