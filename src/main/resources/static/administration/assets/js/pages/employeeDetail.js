// /administration/assets/js/pages/employeeDetail.js
(function () {
	const $ = (sel) => document.querySelector(sel);

	const memberId = +$("#employDetail-memberId").value;

	const curTeamIdInit = $("#employDetail-currentTeamId").value ? +$("#employDetail-currentTeamId").value : null;
	const curTeamCategoryIdInit = $("#employDetail-currentTeamCategoryId").value ? +$("#employDetail-currentTeamCategoryId").value : null;
	const curRole = $("#employDetail-currentRole").value;

	const selRole = $("#employDetail-role-select");
	const selTeam = $("#employDetail-team-select");
	const selTeamCat = $("#employDetail-teamCategory-select");

	const inName = $("#employDetail-name");
	const inPhone = $("#employDetail-phone");
	const inTel = $("#employDetail-telephone");
	const inEmail = $("#employDetail-email");

	// 지역
	const selProvince = $("#employDetail-province");
	const selCity = $("#employDetail-city");
	const selDistrict = $("#employDetail-district");

	const btnAddSelection = $("#employDetail-addSelectionBtn");
	const pendingListEl = $("#employDetail-pendingList");
	const assignedListEl = $("#employDetail-assignedList");
	const btnSaveRegions = $("#employDetail-saveRegionsBtn");
	const msgEl = $("#employDetail-regionMsg");

	const btnSaveMember = $("#employDetail-saveMemberBtn");

	// 상태
	let currentTeamId = curTeamIdInit;
	let currentTeamName = null; // init 시 teams 로딩 후 채움
	let assignedRegions = []; // 서버에 이미 등록된 담당구역

	// 미저장 선택 버퍼
	const pending = []; // {key, provinceId, ...}

	function api(url, opt) {
		return fetch(url, Object.assign({
			headers: {
				"Accept": "application/json",
				"Content-Type": "application/json"
			},
			credentials: "same-origin"
		}, opt)).then(async (res) => {
			const ct = res.headers.get("content-type") || "";
			const text = await res.text();

			if (!ct.includes("application/json")) {
				console.error("[API] Non-JSON response:", url, "\n", text.slice(0, 1200));
				throw new Error("NON_JSON_RESPONSE");
			}

			const data = JSON.parse(text);

			if (!res.ok || (data && data.success === false)) {
				// 서버 메시지 노출용
				const msg = (data && data.message) ? data.message : "서버 오류";
				const err = new Error(msg);
				err.payload = data;
				throw err;
			}

			return data;
		});
	}

	function optionHtml(val, text, selected) {
		return `<option value="${val}" ${selected ? "selected" : ""}>${text}</option>`;
	}

	function getSelectedTeamName() {
		return selTeam.selectedOptions[0]?.text || "";
	}

	function isRegionTeam(teamName) {
		return teamName === "배송팀" || teamName === "AS팀";
	}

	function isProductionTeam(teamName) {
		return teamName === "생산팀";
	}

	function setTeamCategoryUiByTeam(teamName) {
		// 생산팀만 카테고리 선택 가능, 나머지는 고정이므로 disable
		if (isProductionTeam(teamName)) {
			selTeamCat.disabled = false;
		} else {
			selTeamCat.disabled = true;
		}
	}

	async function clearAllRegionsWithConfirm(reasonText) {
		const hasAny = (assignedRegions.length > 0) || (pending.length > 0);
		if (!hasAny) return true;

		const ok = confirm(
			`${reasonText}\n\n확인 시 기존 담당구역이 모두 삭제됩니다. 계속하시겠습니까?`
		);
		if (!ok) return false;

		// 서버 담당구역 전체 삭제
		await api(`/management/member/${memberId}/regions`, { method: "DELETE" });

		// 프론트 상태 초기화
		assignedRegions = [];
		pending.length = 0;
		renderPending();
		await loadAssignedRegions();

		msgEl.innerHTML = `<span class="text-warning">팀 변경으로 기존 담당구역이 초기화되었습니다.</span>`;
		return true;
	}

	// ===== 초기 로딩 =====
	(async function init() {
		await loadRoles();
		await loadTeamsAndSelect();           // 여기서 currentTeamName 세팅
		await loadTeamCategories(currentTeamId, curTeamCategoryIdInit);
		setTeamCategoryUiByTeam(currentTeamName);

		await loadProvinces();
		await loadAssignedRegions();

		// 초기 팀이 배송/AS가 아니라면 지역 UI는 “보이되” 기능은 막아둠(요청사항상 UI는 있어도 되지만 동작은 제한)
		applyRegionUiLock();
	})();

	async function loadRoles() {
		const res = await api(`/management/memberRoles`);
		const roles = res.data || [];
		selRole.innerHTML = roles.map(r => optionHtml(r, r, r === curRole)).join("");
	}

	async function loadTeamsAndSelect() {
		const res = await api(`/management/teams`);
		const teams = res.data || [];
		selTeam.innerHTML = teams.map(t => optionHtml(t.id, t.name, t.id === currentTeamId)).join("");

		currentTeamName = getSelectedTeamName();
	}

	async function loadTeamCategories(teamId, selectedId) {
		if (!teamId) {
			selTeamCat.innerHTML = "";
			return;
		}
		const res = await api(`/management/teamCategories?teamId=` + teamId);
		const cats = res.data || [];

		// 생산팀 외에는 보통 1개만 내려오는 것이 정상(고정)
		// selectedId가 없으면 첫번째를 자동 선택
		selTeamCat.innerHTML = cats.map((c, idx) => {
			const isSel = selectedId ? (c.id === selectedId) : (idx === 0);
			return optionHtml(c.id, c.name, isSel);
		}).join("");
	}

	// ✅ 팀 변경 처리
	selTeam.addEventListener("change", async (e) => {
		const newTeamId = +e.target.value;
		const newTeamName = getSelectedTeamName();

		// 팀카테고리 재로딩
		await loadTeamCategories(newTeamId, null);
		setTeamCategoryUiByTeam(newTeamName);

		const oldTeamName = currentTeamName;

		// 팀 변경 케이스별 처리
		const oldIsRegion = isRegionTeam(oldTeamName);
		const newIsRegion = isRegionTeam(newTeamName);

		// 1) 배송/AS -> 다른팀 : 담당구역 삭제 confirm
		if (oldIsRegion && !newIsRegion) {
			const ok = await clearAllRegionsWithConfirm("배송/AS팀에서 다른 팀으로 변경합니다.");
			if (!ok) {
				// revert
				selTeam.value = String(currentTeamId);
				await loadTeamCategories(currentTeamId, null);
				setTeamCategoryUiByTeam(currentTeamName);
				return;
			}
		}

		// 2) 배송 <-> AS : 담당구역 삭제 confirm(팀이 바뀌면 기존 지역 무효)
		if (oldIsRegion && newIsRegion && oldTeamName !== newTeamName) {
			const ok = await clearAllRegionsWithConfirm("배송팀/AS팀 간 팀 변경입니다.");
			if (!ok) {
				// revert
				selTeam.value = String(currentTeamId);
				await loadTeamCategories(currentTeamId, null);
				setTeamCategoryUiByTeam(currentTeamName);
				return;
			}
		}

		// 3) 다른팀 -> 배송/AS : 지역 필수 안내 (삭제는 없음)
		if (!oldIsRegion && newIsRegion) {
			msgEl.innerHTML = `<span class="text-info">배송팀/AS팀은 담당구역 등록이 필수입니다. 우측에서 담당구역을 추가 후 저장해 주세요.</span>`;
		}

		// 상태 갱신
		currentTeamId = newTeamId;
		currentTeamName = newTeamName;

		applyRegionUiLock();
	});

	function applyRegionUiLock() {
		const tn = getSelectedTeamName();
		const enable = isRegionTeam(tn);

		// 지역 체인 select / 버튼 제어
		selProvince.disabled = !enable;
		selCity.disabled = !enable || selCity.disabled;       // 기존 체인 로직 유지
		selDistrict.disabled = !enable || selDistrict.disabled;

		btnAddSelection.disabled = !enable;
		btnSaveRegions.disabled = !enable;

		if (!enable) {
			msgEl.innerHTML = `<span class="text-muted">현재 팀은 담당구역을 사용하지 않습니다.</span>`;
		}
	}

	// ===== 행정구역 체인 =====
	async function loadProvinces() {
		const res = await api(`/management/regions/provinces`);
		const provinces = res.data || [];
		selProvince.innerHTML = `<option value="">-- 선택 --</option>` +
			provinces.map(p => optionHtml(p.id, p.name)).join("");
		selCity.innerHTML = "";
		selCity.disabled = true;
		selDistrict.innerHTML = "";
		selDistrict.disabled = true;
	}

	selProvince.addEventListener("change", async () => {
		if (selProvince.disabled) return;

		const pid = selProvince.value ? +selProvince.value : null;
		selDistrict.innerHTML = "";
		selDistrict.disabled = true;
		if (!pid) {
			selCity.innerHTML = "";
			selCity.disabled = true;
			return;
		}

		const resCity = await api(`/management/regions/cities?provinceId=` + pid);
		const cities = resCity.data || [];

		if (cities.length > 0) {
			selCity.innerHTML = `<option value="">-- 전체(시 미선택) --</option>` + cities.map(c => optionHtml(c.id, c.name)).join("");
			selCity.disabled = false;
			selDistrict.innerHTML = "";
			selDistrict.disabled = true;
		} else {
			const resDist = await api(`/management/regions/districts?provinceId=${pid}`);
			const dists = resDist.data || [];
			selCity.innerHTML = "";
			selCity.disabled = true;
			selDistrict.innerHTML = `<option value="">-- 전체(구 미선택) --</option>` + dists.map(d => optionHtml(d.id, d.name)).join("");
			selDistrict.disabled = false;
		}
	});

	selCity.addEventListener("change", async () => {
		if (selCity.disabled) return;

		const pid = selProvince.value ? +selProvince.value : null;
		const cid = selCity.value ? +selCity.value : null;
		if (!pid) return;
		if (!cid) {
			selDistrict.innerHTML = "";
			selDistrict.disabled = true;
			return;
		}
		const res = await api(`/management/regions/districts?provinceId=${pid}&cityId=${cid}`);
		const dists = res.data || [];
		selDistrict.innerHTML = `<option value="">-- 전체(구 미선택) --</option>` + dists.map(d => optionHtml(d.id, d.name)).join("");
		selDistrict.disabled = false;
	});

	// ===== 선택 추가(버퍼) =====
	btnAddSelection.addEventListener("click", () => {
		if (btnAddSelection.disabled) return;

		const pid = selProvince.value ? +selProvince.value : null;
		const pname = selProvince.selectedOptions[0]?.text || "";
		if (!pid) { alert("광역시/도를 선택해 주세요."); return; }

		const cid = selCity.disabled ? null : (selCity.value ? +selCity.value : null);
		const cname = selCity.disabled ? null : (selCity.selectedOptions[0]?.text || null);

		const did = selDistrict.disabled ? null : (selDistrict.value ? +selDistrict.value : null);
		const dname = selDistrict.disabled ? null : (selDistrict.selectedOptions[0]?.text || null);

		const key = `${pid}-${cid || 'null'}-${did || 'null'}`;
		if (pending.some(p => p.key === key)) return;

		pending.push({ key, provinceId: pid, provinceName: pname, cityId: cid, cityName: cname, districtId: did, districtName: dname });
		renderPending();
	});

	function renderPending() {
		const items = pending.map((p, idx) => chipHtml(p, idx, true)).join("");
		pendingListEl.innerHTML = items || `<div class="text-muted">선택된 항목이 없습니다.</div>`;
	}

	function chipHtml(p, idx, isPending) {
		const path = [p.provinceName, p.cityName, p.districtName].filter(Boolean).join(" ");
		const removeBtn = `<button type="button" class="btn-close" aria-label="Remove" data-type="${isPending ? 'pending' : 'assigned'}" data-idx="${idx}"></button>`;
		return `<span class="employDetail-chip">${path || '(이름 없음)'} ${removeBtn}</span>`;
	}

	pendingListEl.addEventListener("click", (e) => {
		const btn = e.target.closest(".btn-close");
		if (!btn) return;
		const idx = +btn.dataset.idx;
		pending.splice(idx, 1);
		renderPending();
	});

	// ===== 기존 등록된 담당구역 로딩/표시/삭제 =====
	async function loadAssignedRegions() {
		const res = await api(`/management/member/${memberId}/regions`);
		const list = res.data || [];
		assignedRegions = list;

		assignedListEl.innerHTML = list.map((r) => {
			const path = [r.provinceName, r.cityName, r.districtName].filter(Boolean).join(" ");
			return `<span class="employDetail-chip">${path}
				<button type="button" class="btn-close" aria-label="Remove" data-type="assigned" data-id="${r.id}"></button>
			</span>`;
		}).join("") || `<div class="text-muted">등록된 담당 구역이 없습니다.</div>`;
	}

	assignedListEl.addEventListener("click", async (e) => {
		const btn = e.target.closest(".btn-close");
		if (!btn) return;
		const id = +btn.dataset.id;
		if (!confirm("해당 담당 구역을 삭제하시겠습니까?")) return;
		await api(`/management/member/${memberId}/regions/${id}`, { method: "DELETE" });
		await loadAssignedRegions();
	});

	// ===== 담당구역 저장(버퍼 -> 서버) =====
	async function saveRegionsInternal() {
		if (pending.length === 0) return;

		const payload = {
			memberId,
			selections: pending.map(p => ({
				provinceId: p.provinceId,
				cityId: p.cityId,
				districtId: p.districtId
			}))
		};

		const res = await api(`/management/member/regions/bulk`, {
			method: "POST",
			body: JSON.stringify(payload)
		});

		if (res.success) {
			msgEl.innerHTML = `<span class="text-success">담당구역이 저장되었습니다.</span>`;
			pending.length = 0;
			renderPending();
			await loadAssignedRegions();
		} else {
			const lines = (res.data || []).map(c => `- ${c.conflictPath} (담당: ${c.conflictMemberName} #${c.conflictMemberId})`).join("<br>");
			msgEl.innerHTML = `<div class="text-danger">중복된 담당 구역이 있어 저장되지 않았습니다.<br>${lines}</div>`;
			throw new Error("REGION_CONFLICT");
		}
	}

	btnSaveRegions.addEventListener("click", async () => {
		if (btnSaveRegions.disabled) return;

		if (pending.length === 0) { alert("추가할 항목이 없습니다."); return; }
		try {
			await saveRegionsInternal();
		} catch (e) {
			console.error(e);
		}
	});

	// ===== 멤버 저장 =====
	btnSaveMember.addEventListener("click", async () => {
		const name = inName.value.trim();
		const phone = inPhone.value.trim();
		const role = selRole.value;

		const teamId = selTeam.value ? +selTeam.value : null;
		const teamName = getSelectedTeamName();
		const teamCategoryId = selTeamCat.value ? +selTeamCat.value : null;

		if (!name || !phone || !role || !teamId) {
			alert("필수 항목을 확인해 주세요.");
			return;
		}

		// 1) 생산팀이면 카테고리 필수
		if (isProductionTeam(teamName)) {
			if (!teamCategoryId) {
				alert("생산팀은 카테고리 선택이 필수입니다.");
				return;
			}
		}

		// 2) 배송/AS팀이면 담당구역 필수
		if (isRegionTeam(teamName)) {
			const totalRegions = assignedRegions.length + pending.length;

			if (totalRegions <= 0) {
				alert("배송팀/AS팀은 담당구역 등록이 필수입니다. 우측에서 담당구역을 추가 후 저장해 주세요.");
				return;
			}

			// pending이 있으면 먼저 저장하도록 유도(팀 정책상 '최종 저장' 전에 담당구역 저장 권장)
			if (pending.length > 0) {
				const ok = confirm("추가 예정 담당구역이 있습니다. 먼저 [담당구역 저장]을 진행하시겠습니까?");
				if (ok) {
					try {
						await saveRegionsInternal();
					} catch (e) {
						alert("담당구역 저장에 실패했습니다. 중복 여부를 확인해 주세요.");
						return;
					}
				} else {
					// pending 미저장 상태로 멤버 저장을 허용하면, 사용자 입장에서 “등록한 줄 알았는데 반영 안됨” 이 발생합니다.
					alert("담당구역 저장 후 다시 멤버 저장을 진행해 주세요.");
					return;
				}
			}
		}

		const payload = {
			memberId,
			name,
			phone,
			telephone: inTel.value.trim() || null,
			email: inEmail.value.trim() || null,
			role,
			teamId,
			teamCategoryId: isProductionTeam(teamName) ? teamCategoryId : null // 서버가 생산팀 외에는 강제 카테고리로 처리
		};

		try {
			const res = await api(`/management/employeeUpdate`, {
				method: "POST",
				body: JSON.stringify(payload)
			});

			alert(res.data?.message || "저장되었습니다.");
		} catch (e) {
			alert(e.message || "저장에 실패했습니다.");
			console.error(e);
		}
	});
})();
