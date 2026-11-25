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
const analyticsNumberFormat = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0 });
const analyticsMetricDefinitions = {
  "SALES": {
    "key": "sales",
    "label": "매출액",
    "unit": "원",
    "help": "저장된 부가세 포함 상품 합계금액입니다. 배송·포장비는 제외하고 회수 건도 저장된 금액 부호를 그대로 사용합니다."
  },
  "TASK_COUNT": {
    "key": "taskCount",
    "label": "전체 건수",
    "unit": "건",
    "help": "개별 품목(Order)의 전체 건수입니다. 유상 주문·0원 반품용·회수·기타 조정을 포함하고 취소는 제외합니다."
  },
  "QTY": {
    "key": "qty",
    "label": "순수량",
    "unit": "개",
    "help": "저장 수량의 합계입니다. 양수 수량에서 회수 수량을 뺀 값으로 생산 요청수량과 구별합니다."
  },
  "PAID_COUNT": {
    "key": "paidOrderCount",
    "label": "유상 주문건수",
    "unit": "건",
    "help": "수량과 상품 금액이 모두 양수인 개별 품목 건수입니다."
  },
  "PAID_QTY": {
    "key": "paidQty",
    "label": "유상 주문수량",
    "unit": "개",
    "help": "수량과 상품 금액이 모두 양수인 주문 수량입니다."
  },
  "PAID_SALES": {
    "key": "paidSales",
    "label": "유상 상품금액",
    "unit": "원",
    "help": "유상 주문의 부가세 포함 상품 합계금액입니다."
  },
  "RETURN_COUNT": {
    "key": "returnOrderCount",
    "label": "0원 반품용 건수",
    "unit": "건",
    "help": "상품 금액이 0원이고 수량이 양수인 반품 대응 제작 품목의 건수입니다."
  },
  "RETURN_QTY": {
    "key": "returnQty",
    "label": "0원 반품용 수량",
    "unit": "개",
    "help": "상품 금액이 0원이고 수량이 양수인 반품 대응 제작 수량입니다. 생산 요청·완료 수량에 포함합니다."
  },
  "PRODUCTION_REQUEST_QTY": {
    "key": "productionRequestQty",
    "label": "생산 요청수량",
    "unit": "개",
    "help": "양수 수량 전체입니다. 유상 주문과 0원 반품용을 포함하고 회수는 차감하지 않습니다."
  },
  "PRODUCTION_DONE_QTY": {
    "key": "productionDoneQty",
    "label": "생산 완료수량",
    "unit": "개",
    "help": "조회한 발주건 중 현재 생산완료·출고완료·배송완료 상태인 양수 수량입니다. 생산 완료일 기준 집계가 아닙니다."
  },
  "PRODUCTION_PENDING_QTY": {
    "key": "productionPendingQty",
    "label": "생산 대기수량",
    "unit": "개",
    "help": "생산 요청수량에서 생산 완료수량을 뺀 값입니다."
  },
  "COLLECTION_COUNT": {
    "key": "collectionOrderCount",
    "label": "회수건수",
    "unit": "건",
    "help": "수량이 음수인 개별 품목 건수입니다. 상품 금액이 0원이어도 반품용 건수와 중복 집계하지 않습니다."
  },
  "COLLECTION_QTY": {
    "key": "collectionQty",
    "label": "회수수량",
    "unit": "개",
    "help": "음수 수량의 절댓값 합계입니다. 생산 요청수량과 별도로 표시합니다."
  },
  "COLLECTION_SALES": {
    "key": "collectionSales",
    "label": "회수건 금액",
    "unit": "원",
    "help": "회수 건에 저장된 상품 금액을 부호 그대로 합산합니다. 회수라는 이유로 자동으로 매출에서 차감하지 않습니다."
  },
  "ADJUSTMENT_COUNT": {
    "key": "adjustmentOrderCount",
    "label": "기타 조정건수",
    "unit": "건",
    "help": "수량이 0이거나 양수 수량에 음수 상품 금액이 있는 품목입니다. 유상·반품용·회수와 구별합니다."
  },
  "ADJUSTMENT_QTY": {
    "key": "adjustmentQty",
    "label": "기타 조정수량",
    "unit": "개",
    "help": "기타 조정 품목의 양수 수량입니다. 생산 요청수량과 유상·0원 반품용 수량의 차이를 확인할 수 있습니다."
  },
  "ADJUSTMENT_SALES": {
    "key": "adjustmentSales",
    "label": "기타 조정금액",
    "unit": "원",
    "help": "기타 조정 품목의 저장 상품 금액 합계입니다."
  }
};
const numericColumnKeys = new Set(["rank", ...Object.values(analyticsMetricDefinitions).map(metric => metric.key)]);
function formatAnalyticsNumber(value) {
    if (value == null || value === "") return "";
    const number = Number(value);
    return Number.isFinite(number) ? analyticsNumberFormat.format(number) : String(value);
}


// ✅ 서버가 내려주는 columns 메타(키/한글/설명)
let currentColumns = [];
let appliedRequest = null;
let querySequence = 0;
let queryController = null;
let loadedRowCount = 0;

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
	initExtraMetricButtons();
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
		queryAndRender(false);
	});

	// Export
	document.getElementById("analyticsExcelBtn").addEventListener("click", exportExcel);
	document.getElementById("analyticsA4Btn").addEventListener("click", exportA4);

	// Default dates = today
	const now = new Date();
	const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
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
function initExtraMetricButtons() {
    const box = document.getElementById("analyticsExtraMetricButtons");
    if (box) for (const [code, metric] of Object.entries(analyticsMetricDefinitions)) {
        if (["SALES", "TASK_COUNT", "QTY"].includes(code)) continue;
        const button = document.createElement("button"); button.type = "button";
        button.className = "btn btn-sm btn-outline-success";
        button.textContent = metric.label; button.title = metric.help; button.dataset.addMetric = code;
        button.onclick = () => addMetric(code); box.append(button);
    }
    const presets = {
        analyticsSalesPreset: ["SALES", "PAID_COUNT", "PAID_QTY", "RETURN_QTY", "COLLECTION_QTY"],
        analyticsProductionPreset: ["PRODUCTION_REQUEST_QTY", "PRODUCTION_DONE_QTY", "PRODUCTION_PENDING_QTY", "PAID_QTY", "RETURN_QTY", "COLLECTION_QTY", "PAID_COUNT", "RETURN_COUNT", "COLLECTION_COUNT", "SALES"]
    };
    for (const [id, metrics] of Object.entries(presets)) document.getElementById(id)?.addEventListener("click", () => {
        xMetrics = [...metrics]; sortKey = ""; renderAxisUI();
    });
}

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
	if (xBox) new Sortable(xBox, { handle: ".handle", animation: 150, onEnd: syncAxisOrder });
}

/** =========================
 *  Axis State handlers
 * ========================= */
function setYPrimary(v) {
	sortKey = "";
	page = 0;
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
				: "발주일의 등록 시간 기준으로 시간대 필터를 추가합니다."
		));
	});

	// X metrics
	xMetrics.forEach(m => {
        const definition = analyticsMetricDefinitions[m];
        if (!definition) return;
        const label = definition.label, help = definition.help;

		xBox.appendChild(axisItem(
			`X:${label}`,
			() => { removeItem(xMetrics, m); renderAxisUI(); },
			null,
			help, "metric", m
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
			help, "dimension", d
		));
	});

	// Axis 변경으로 추가된 help-q에도 이벤트 적용
	refreshHelpBubbles();
}

function axisItem(text, onRemove, onConfig, helpText, kind, value) {
	const div = document.createElement("div");
	div.className = "axis-item";
	if (kind) div.dataset[kind] = value;

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
function syncAxisOrder() {
    const box = document.getElementById("xAxisBox");
    if (!box) return;
    xMetrics = Array.from(box.querySelectorAll("[data-metric]")).map(node => node.dataset.metric);
    xDims = Array.from(box.querySelectorAll("[data-dimension]")).map(node => node.dataset.dimension);
}
function buildRequestBody() {
    syncAxisOrder();
	const fromDateVal = document.getElementById("fromDate").value;
	const toDateVal = document.getElementById("toDate").value;

	const body = {
		fromDate: fromDateVal ? fromDateVal : null,
		toDate: toDateVal ? toDateVal : null,
		primaryY: yPrimary,
		metrics: [...xMetrics],
		sortKey,
		sortDir,
		page,
		size
	};

	if (yPrimary === "COMPANY") {
		if (ySubs.includes("ADDRESS")) body.addressFilter = currentAddressFilter || {};
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

function requestSignature(body) {
    const { page: ignoredPage, size: ignoredSize, ...conditions } = body;
    return JSON.stringify(conditions);
}
function buildRequestBodyForExport() {
    if (!appliedRequest || !totalRows) throw new Error("먼저 조회하여 결과를 확인해 주세요.");
    if (requestSignature(buildRequestBody()) !== requestSignature(appliedRequest))
        throw new Error("조회 조건이 변경되었습니다. 조회를 눌러 결과를 갱신한 뒤 출력해 주세요.");
    return { ...appliedRequest, page: 0, size: 2147483647 };
}
function setQueryBusy(busy) {
    const search = document.getElementById("analyticsSearchBtn");
    search.disabled = busy;
    search.setAttribute("aria-busy", String(busy));
    document.getElementById("analyticsLoadMoreBtn").disabled = busy;
    document.getElementById("analyticsExcelBtn").disabled = busy || totalRows === 0;
    document.getElementById("analyticsA4Btn").disabled = busy || totalRows === 0;
}
async function responseError(response, fallback) {
    try { const body = await response.json(); return body.message || body.detail || fallback; }
    catch { return fallback; }
}
async function queryAndRender(reset) {
    if (!yPrimary) { alert("Y축(업체별/제품별)을 먼저 선택해 주세요."); return; }
    let body = buildRequestBody();
    if (body.fromDate && body.toDate && body.fromDate > body.toDate) {
        alert("발주 시작일은 종료일보다 늦을 수 없습니다."); return;
    }
    if (!reset && (!appliedRequest || requestSignature(body) !== requestSignature(appliedRequest))) reset = true;
    body.page = reset ? 0 : page + 1;
    const id = ++querySequence;
    queryController?.abort();
    const controller = new AbortController(); queryController = controller;
    setQueryBusy(true);
    try {
        const response = await fetch("/analytics/api/query", {
            method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body), signal: controller.signal
        });
        if (!response.ok) throw new Error(await responseError(response, "조회하지 못했습니다. 다시 시도해 주세요."));
        const data = await response.json();
        if (id !== querySequence) return;
        totalRows = Number(data.totalRows) || 0;
        currentColumns = Array.isArray(data.columns) ? data.columns : [];
        page = body.page;
        appliedRequest = JSON.parse(JSON.stringify(body));
        const rows = Array.isArray(data.rows) ? data.rows : [];
        loadedRowCount = reset ? rows.length : loadedRowCount + rows.length;
        document.getElementById("analyticsTotalText").textContent = `${formatAnalyticsNumber(totalRows)}행`;
        renderTable(rows, reset);
        document.getElementById("analyticsLoadMoreBtn").style.display = loadedRowCount < totalRows ? "inline-block" : "none";
        renderResultContext(data);
        renderChart(data.chart);
        refreshHelpBubbles();
    } catch (error) {
        if (id === querySequence && error.name !== "AbortError") alert(error.message || "조회하지 못했습니다.");
    } finally {
        if (id === querySequence) { queryController = null; setQueryBusy(false); }
    }
}
function renderResultContext(data) {
    const box = document.getElementById("analyticsResultContext"); if (!box) return;
    box.replaceChildren(); box.hidden = false;
    const meta = data.meta || {};
    for (const content of [meta.periodText, [meta.yAxisText, meta.xDimText, meta.xAxisText].filter(Boolean).join(" · "), [meta.addressText, meta.hourText].filter(Boolean).join(" · "), meta.basisText, meta.chartBasisText]) {
        if (!content) continue;
        const line = document.createElement("div"); line.textContent = content; box.append(line);
    }
    const totals = data.totals || {};
    const line = document.createElement("div"); line.className = "mt-2 fw-semibold";
    line.textContent = currentColumns.filter(c => c.unit || numericColumnKeys.has(c.key) && c.key !== "rank")
        .map(c => `${c.label} 합계 ${formatAnalyticsNumber(totals.metrics?.[c.key] ?? totals[c.key] ?? 0)}${c.unit || (c.key === "sales" ? "원" : c.key === "qty" ? "개" : "건")}`).join(" · ");
    box.append(line);
    if (totals.metrics) {
        const m = totals.metrics, comparison = document.createElement("div"); comparison.className = "row g-2 mt-2";
        const n = key => formatAnalyticsNumber(m[key] || 0);
        for (const [title, value, note] of [
            ["유상 주문", `${n("paidQty")}개`, `${n("paidOrderCount")}건`],
            ["0원 반품용", `${n("returnQty")}개`, `${n("returnOrderCount")}건 · 생산수량 포함`],
            ["생산 요청", `${n("productionRequestQty")}개`, `완료 ${n("productionDoneQty")}개 · 대기 ${n("productionPendingQty")}개`],
            ["회수", `${n("collectionQty")}개`, `${n("collectionOrderCount")}건 · 금액 ${n("collectionSales")}원`]
        ]) {
            const column = document.createElement("div"); column.className = "col-6 col-xl-3";
            const card = document.createElement("div"); card.className = "border rounded p-3 bg-white h-100";
            const label = document.createElement("div"); label.className = "text-muted small"; label.textContent = title;
            const amount = document.createElement("strong"); amount.className = "d-block fs-5 mt-1"; amount.textContent = value;
            const detail = document.createElement("div"); detail.className = "small text-muted mt-1"; detail.textContent = note;
            card.append(label, amount, detail); column.append(card); comparison.append(column);
        }
        box.append(comparison);
    }
    for (const warning of data.warnings || []) {
        const node = document.createElement("div"); node.className = "text-danger mt-1"; node.textContent = warning; box.append(node);
    }
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
			th.className = c.key === "rank" ? "" : "th-sort";

			const labelSpan = document.createElement("span");
			labelSpan.textContent = (c.label || c.key) + (c.unit ? ` (${c.unit})` : "");
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
			arrows.textContent = c.key === "rank" ? "" : (sortKey === c.key) ? (sortDir === "asc" ? "▲" : "▼") : "↕";
			th.appendChild(arrows);

			th.addEventListener("click", (e) => {
				// help-q 클릭은 정렬 방지
                if (c.key === "rank") return;
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
			td.textContent = numericColumnKeys.has(c.key)
                ? formatAnalyticsNumber(r[c.key])
                : (r[c.key] == null ? "" : String(r[c.key]));
            if (numericColumnKeys.has(c.key)) td.classList.add("text-end");
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
			maintainAspectRatio: false,
            scales: { y: { ticks: { callback: value => formatAnalyticsNumber(value) } } },
            plugins: { tooltip: { callbacks: { label: context =>
                `${context.dataset.label}: ${formatAnalyticsNumber(context.parsed.y)}${chart.unit || (chart.valueKey === "sales" ? "원" : chart.valueKey === "qty" ? "개" : "건")}`
            } } }
		}
	});
}

/** =========================
 *  Export
 * ========================= */
async function exportExcel() {
    try {
        const body = buildRequestBodyForExport();
        const response = await fetch("/analytics/api/export/excel", {
            method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
        });
        if (!response.ok) throw new Error(await responseError(response, "엑셀 출력에 실패했습니다."));
        const blob = await response.blob(), url = URL.createObjectURL(blob), link = document.createElement("a");
        link.href = url; link.download = "analytics.xlsx"; link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) { alert(error.message || "엑셀 출력에 실패했습니다."); }
}
async function exportA4() {
    let report;
    try {
        const body = buildRequestBodyForExport();
        report = window.open("", "_blank");
        if (!report) throw new Error("팝업 허용 후 다시 출력해 주세요.");
        report.document.body.textContent = "보고서를 준비하고 있습니다.";
        const response = await fetch("/analytics/api/export/a4", {
            method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
        });
        if (!response.ok) throw new Error(await responseError(response, "A4 출력에 실패했습니다."));
        const html = await response.text();
        report.document.open(); report.document.write(html); report.document.close();
        const ready = report.document.fonts?.ready || Promise.resolve();
        await ready;
        report.focus(); report.print();
    } catch (error) { report?.close(); alert(error.message || "A4 출력에 실패했습니다."); }
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
