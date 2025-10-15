document.addEventListener("DOMContentLoaded", function() {

	const form = document.getElementById("employee-insert-form");

	const teamSelect = document.getElementById("teamSelect");
	const categoryWrapper = document.getElementById("teamCategoryWrapper");
	const categorySelect = document.getElementById("teamCategorySelect");
	const provinceWrapper = document.getElementById("provinceWrapper");
	const provinceSelect = document.getElementById("provinceSelect");
	const cityWrapper = document.getElementById("cityWrapper");
	const citySelect = document.getElementById("citySelect");
	const districtWrapper = document.getElementById("districtWrapper");
	const districtSelect = document.getElementById("districtSelect");

	const regionListContainer = document.createElement('div');
	regionListContainer.id = 'regionListContainer';
	districtWrapper.insertAdjacentElement('afterend', regionListContainer);

	const districtRegisterButton = document.createElement('button');
	districtRegisterButton.className = 'btn btn-outline-primary btn-sm mt-2 mb-2';
	districtRegisterButton.type = 'button';
	districtRegisterButton.innerText = '지역 등록';

	const selectedRegions = [];
	const regionInput = document.getElementById('regionJsonInput');

	const updateRegionInput = () => {
		regionInput.value = JSON.stringify(selectedRegions);
		console.log("📦 현재 지역 JSON:", regionInput.value);
	};

	const renderRegionList = () => {
		regionListContainer.innerHTML = '';
		selectedRegions.forEach((region, index) => {
			const regionRow = document.createElement('div');
			regionRow.className = 'd-flex justify-content-between align-items-center border p-2 mb-2';
			regionRow.innerHTML = `
        <span>${region.provinceName} ${region.cityName || ''} ${region.districtName || ''}</span>
        <button type="button" class="btn btn-sm btn-outline-danger" data-index="${index}">삭제</button>
      `;
			regionRow.querySelector('button').addEventListener('click', () => {
				selectedRegions.splice(index, 1);
				renderRegionList();
				updateRegionInput();
			});
			regionListContainer.appendChild(regionRow);
		});
	};

	teamSelect.addEventListener("change", function() {
		const selectedTeamName = this.options[this.selectedIndex]?.text || "";

		// 초기화
		categoryWrapper.style.display = "none";
		provinceWrapper.style.display = "none";
		cityWrapper.style.display = "none";
		districtWrapper.style.display = "none";
		regionListContainer.innerHTML = '';
		selectedRegions.length = 0;

		provinceSelect.value = "";
		citySelect.innerHTML = "";
		districtSelect.innerHTML = "";

		if (districtRegisterButton.parentElement) {
			districtRegisterButton.remove();
		}

		if (selectedTeamName === "생산팀") {
			categoryWrapper.style.display = "block";
		} else if (selectedTeamName === "AS팀" || selectedTeamName === "배송팀") {
			provinceWrapper.style.display = "block";
			setTimeout(() => {
				districtWrapper.insertAdjacentElement('afterend', districtRegisterButton);
			}, 100);
		}
	});

	provinceSelect.addEventListener("change", function() {
		const provinceId = this.value;
		if (!provinceId) return;

		fetch(`/api/v1/province/${provinceId}/cities`)
			.then(res => res.json())
			.then(cities => {
				if (cities.length > 0) {
					citySelect.innerHTML = '<option value="">시 선택</option>';
					cities.forEach(city => {
						citySelect.innerHTML += `<option value="${city.id}" data-name="${city.name}">${city.name}</option>`;
					});
					cityWrapper.style.display = "block";
					districtWrapper.style.display = "none";
					districtSelect.innerHTML = '';
				} else {
					fetch(`/api/v1/province/${provinceId}/districts`)
						.then(res => res.json())
						.then(districts => {
							districtSelect.innerHTML = '<option value="">구 선택</option>';
							districts.forEach(d => {
								districtSelect.innerHTML += `<option value="${d.id}" data-name="${d.name}">${d.name}</option>`;
							});
							cityWrapper.style.display = "none";
							districtWrapper.style.display = "block";
						});
				}
			});
	});

	citySelect.addEventListener("change", function() {
		const cityId = this.value;
		if (!cityId) return;

		fetch(`/api/v1/city/${cityId}/districts`)
			.then(res => res.json())
			.then(data => {
				districtSelect.innerHTML = '<option value="">구 선택</option>';
				data.forEach(d => {
					districtSelect.innerHTML += `<option value="${d.id}" data-name="${d.name}">${d.name}</option>`;
				});
				districtWrapper.style.display = "block";
			});
	});

	districtRegisterButton.addEventListener('click', () => {

		const provinceName = provinceSelect.options[provinceSelect.selectedIndex]?.text;
		const provinceId = provinceSelect.value;
		const cityName = citySelect.options[citySelect.selectedIndex]?.text || null;
		const cityId = citySelect.value || null;
		const districtName = districtSelect.options[districtSelect.selectedIndex]?.text || null;
		const districtId = districtSelect.value || null;

		if (!provinceId || !provinceName) return alert("도는 반드시 선택해야 합니다.");

		const isDuplicate = selectedRegions.some(r => {
			return r.provinceId === provinceId &&
				(r.cityId === cityId || r.cityId === null || cityId === null) &&
				(r.districtId === districtId || r.districtId === null || districtId === null);
		});

		if (isDuplicate) return alert("이미 추가된 지역입니다.");

		selectedRegions.push({ provinceId, cityId, districtId, provinceName, cityName, districtName });
		renderRegionList();
		updateRegionInput();

		provinceSelect.value = '';
		citySelect.innerHTML = '';
		districtSelect.innerHTML = '';
		cityWrapper.style.display = 'none';
		districtWrapper.style.display = 'none';
		console.log(selectedRegions);
	});

	// ===== 폼 제출: 비동기 검증을 기다렸다가 통과 시에만 제출 =====
	form.addEventListener('submit', async (e) => {
		e.preventDefault();
		const ok = await validateRegionBeforeSubmit();
		if (ok) form.submit();
	});
});

// ---- 폼 제출 전 검증(팀/카테고리/지역 + 서버 충돌 검사) ----
async function validateRegionBeforeSubmit() {
	const teamSelect = document.getElementById("teamSelect");
	const teamValue = teamSelect.value;
	const teamName = teamSelect.selectedOptions[0]?.text || "";

	if (!teamValue) {
		alert("팀을 선택해주세요.");
		return false;
	}

	// 생산팀이면 카테고리 선택 필수
	if (teamName === "생산팀") {
		const categoryValue = document.getElementById("teamCategorySelect").value;
		if (!categoryValue) {
			alert("생산팀은 카테고리 선택이 필수입니다.");
			return false;
		}
	}

	// AS팀 또는 배송팀이면 지역 등록 필수 + 서버 충돌 검사
	if (teamName === "배송팀" || teamName === "AS팀") {
		const json = document.getElementById('regionJsonInput').value;
		if (!json || json === "[]" || json.trim() === "") {
			alert("지역이 등록되지 않았습니다. 반드시 [지역 등록] 버튼을 눌러야 합니다.");
			return false;
		}

		let regions;
		try {
			regions = JSON.parse(json);
		} catch (e) {
			alert("지역 데이터 파싱 오류가 발생했습니다.");
			return false;
		}

		const selections = regions.map(r => ({
			provinceId: r.provinceId ? Number(r.provinceId) : null,
			cityId: r.cityId ? Number(r.cityId) : null,
			districtId: r.districtId ? Number(r.districtId) : null
		}));

		try {
			const res = await fetch('/api/v1/region/conflicts/check-new', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ teamId: Number(teamValue), selections })
			});

			if (!res.ok) {
				alert("서버 검증 중 오류가 발생했습니다.");
				return false;
			}

			const conflicts = await res.json();
			if (Array.isArray(conflicts) && conflicts.length > 0) {
				const msg = conflicts.map(c => `- [${c.conflictMemberName}] ${c.conflictPath}`).join('\n');
				alert("다음 담당구역과 충돌합니다. 영역을 조정해주세요.\n\n" + msg);
				return false;
			}
		} catch (err) {
			console.error(err);
			alert("서버와 통신 중 문제가 발생했습니다.");
			return false;
		}
	}

	return true;
}
