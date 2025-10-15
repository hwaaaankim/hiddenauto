// /administration/assets/js/pages/employeeDetail.js
(function() {
	const $ = (sel) => document.querySelector(sel);
	const memberId = +$("#employDetail-memberId").value;
	const curTeamId = $("#employDetail-currentTeamId").value ? +$("#employDetail-currentTeamId").value : null;
	const curTeamCategoryId = $("#employDetail-currentTeamCategoryId").value ? +$("#employDetail-currentTeamCategoryId").value : null;
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

	// 미저장 선택 버퍼
	const pending = []; // {provinceId, provinceName, cityId?, cityName?, districtId?, districtName?}

	// employeeDetail.js
	function api(url, opt) {
		return fetch(url, Object.assign({
			headers: {
				"Accept": "application/json",
				"Content-Type": "application/json"
			},
			credentials: "same-origin" // 세션 쓰면 유지, 아니면 제거 가능
		}, opt)).then(async (res) => {
			const ct = res.headers.get("content-type") || "";
			const text = await res.text();

			if (!ct.includes("application/json")) {
				console.error("[API] Non-JSON response:", url, "\n", text.slice(0, 1200));
				throw new Error("NON_JSON_RESPONSE");
			}

			try {
				const data = JSON.parse(text);
				// 스프링 표준 ApiResponse 대비
				if (!res.ok || (data && data.success === false)) {
					console.error("[API] Server returned error:", data);
				}
				return data;
			} catch (e) {
				console.error("[API] JSON parse error:", e, "\nraw:", text.slice(0, 1200));
				throw new Error("INVALID_JSON");
			}
		});
	}

	function optionHtml(val, text, selected) {
		return `<option value="${val}" ${selected ? 'selected' : ''}>${text}</option>`;
	}

	// ===== 초기 로딩 =====
	(async function init() {
		await loadRoles();
		await loadTeamsAndSelect();
		await loadTeamCategories(curTeamId, curTeamCategoryId);
		await loadProvinces();
		await loadAssignedRegions();
	})();

	async function loadRoles() {
		const res = await api(`/management/memberRoles`);
		const roles = res.data || [];
		selRole.innerHTML = roles.map(r => optionHtml(r, r, r === curRole)).join("");
	}

	async function loadTeamsAndSelect() {
		const res = await api(`/management/teams`);
		const teams = res.data || [];
		selTeam.innerHTML = teams.map(t => optionHtml(t.id, t.name, t.id === curTeamId)).join("");
	}

	async function loadTeamCategories(teamId, selectedId) {
		if (!teamId) {
			selTeamCat.innerHTML = "";
			return;
		}
		const res = await api(`/management/teamCategories?teamId=` + teamId);
		const cats = res.data || [];
		selTeamCat.innerHTML = cats.map(c => optionHtml(c.id, c.name, c.id === selectedId)).join("");
	}

	selTeam.addEventListener("change", async (e) => {
		const tId = +e.target.value;
		await loadTeamCategories(tId, null);
	});

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
		const pid = selProvince.value ? +selProvince.value : null;
		selDistrict.innerHTML = "";
		selDistrict.disabled = true;
		if (!pid) {
			selCity.innerHTML = "";
			selCity.disabled = true;
			return;
		}
		// city 목록
		const resCity = await api(`/management/regions/cities?provinceId=` + pid);
		const cities = resCity.data || [];
		if (cities.length > 0) {
			selCity.innerHTML = `<option value="">-- 전체(시 미선택) --</option>` + cities.map(c => optionHtml(c.id, c.name)).join("");
			selCity.disabled = false;
			selDistrict.innerHTML = "";
			selDistrict.disabled = true;
		} else {
			// city가 없으면 district를 province 기준으로
			const resDist = await api(`/management/regions/districts?provinceId=${pid}`);
			const dists = resDist.data || [];
			selCity.innerHTML = "";
			selCity.disabled = true;
			selDistrict.innerHTML = `<option value="">-- 전체(구 미선택) --</option>` + dists.map(d => optionHtml(d.id, d.name)).join("");
			selDistrict.disabled = false;
		}
	});

	selCity.addEventListener("change", async () => {
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
		assignedListEl.innerHTML = list.map((r, idx) => {
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
	btnSaveRegions.addEventListener("click", async () => {
		if (pending.length === 0) { alert("추가할 항목이 없습니다."); return; }
		const payload = {
			memberId,
			selections: pending.map(p => ({
				provinceId: p.provinceId,
				cityId: p.cityId,
				districtId: p.districtId
			}))
		};
		const res = await api(`/management/member/regions/bulk`, { method: "POST", body: JSON.stringify(payload) });
		if (res.success) {
			msgEl.innerHTML = `<span class="text-success">저장되었습니다.</span>`;
			pending.length = 0;
			renderPending();
			await loadAssignedRegions();
		} else {
			// 충돌 상세 표시
			const lines = (res.data || []).map(c => `- ${c.conflictPath} (담당: ${c.conflictMemberName} #${c.conflictMemberId})`).join("<br>");
			msgEl.innerHTML = `<div class="text-danger">중복된 담당 구역이 있어 저장되지 않았습니다.<br>${lines}</div>`;
		}
	});

	// ===== 멤버 저장 =====
	btnSaveMember.addEventListener("click", async () => {
		// 필수값 체크 (유선/이메일 제외)
		const name = inName.value.trim();
		const phone = inPhone.value.trim();
		const role = selRole.value;
		const teamId = selTeam.value ? +selTeam.value : null;
		const teamCategoryId = selTeamCat.value ? +selTeamCat.value : null;

		if (!name || !phone || !role || !teamId || !teamCategoryId) {
			alert("필수 항목을 확인해 주세요.");
			return;
		}

		const payload = {
			memberId,
			name,
			phone,
			telephone: inTel.value.trim() || null,
			email: inEmail.value.trim() || null,
			role,
			teamId,
			teamCategoryId
		};

		const res = await api(`/management/employeeUpdate`, { method: "POST", body: JSON.stringify(payload) });
		if (res.success) {
			alert("저장되었습니다.");
			// 필요 시 새로고침 또는 updatedAt 반영
		} else {
			alert(res.message || "저장에 실패했습니다.");
		}
	});
})();
