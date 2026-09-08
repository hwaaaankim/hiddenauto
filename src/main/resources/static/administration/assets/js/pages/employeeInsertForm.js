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
	const dynamicGuide = document.getElementById("employee-insert-dynamic-guide");
	const dynamicGuideTitle = document.getElementById("employee-insert-dynamic-guide-title");
	const dynamicGuideText = document.getElementById("employee-insert-dynamic-guide-text");

    const CATEGORY_REQUIRED_TEAMS = new Set(["생산팀", "출고팀"]);
    const REGION_REQUIRED_TEAMS = new Set(["배송팀", "AS팀"]);

	const renderRegionOptions = (select, placeholder, items) => {
		select.replaceChildren();
		const first = document.createElement("option");
		first.value = "";
		first.textContent = placeholder;
		select.appendChild(first);

		(Array.isArray(items) ? items : []).forEach(item => {
			const option = document.createElement("option");
			option.value = String(item.id);
			option.textContent = item.name || "-";
			option.dataset.name = item.name || "";
			select.appendChild(option);
		});
	};

    const allCategoryOptions = Array.from(categorySelect.querySelectorAll("option"))
        .filter(option => option.value)
        .map(option => ({
            value: option.value,
            text: option.textContent,
            teamId: option.dataset.teamId
        }));

    const resetCategorySelect = () => {
        categorySelect.innerHTML = '<option value="">카테고리 선택</option>';
        categorySelect.value = "";
    };

    const renderCategoryOptionsByTeam = (teamId) => {
        resetCategorySelect();

        const matchedOptions = allCategoryOptions.filter(option => option.teamId === String(teamId));

        matchedOptions.forEach(option => {
            const newOption = document.createElement("option");
            newOption.value = option.value;
            newOption.textContent = option.text;
            newOption.dataset.teamId = option.teamId;
            categorySelect.appendChild(newOption);
        });

        return matchedOptions.length;
    };

    const regionListContainer = document.createElement("div");
    regionListContainer.id = "regionListContainer";
	regionListContainer.className = "d-none";
    districtWrapper.insertAdjacentElement("afterend", regionListContainer);

    const districtRegisterButton = document.createElement("button");
    districtRegisterButton.className = "btn btn-outline-primary btn-sm mt-2 mb-2";
    districtRegisterButton.type = "button";
    districtRegisterButton.innerText = "지역 등록";

    const selectedRegions = [];
    const regionInput = document.getElementById("regionJsonInput");

    const updateRegionInput = () => {
        regionInput.value = JSON.stringify(selectedRegions);
    };

	const updateDynamicGuide = (teamName, mode) => {
		if (!dynamicGuide || !dynamicGuideTitle || !dynamicGuideText) return;

		dynamicGuide.classList.toggle("is-active", !!teamName);
		const guideIcon = document.createElement("i");
		guideIcon.className = "ri-layout-grid-line me-1";
		dynamicGuideTitle.replaceChildren(
			guideIcon,
			document.createTextNode(teamName ? `${teamName} 추가 설정` : "팀별 추가 설정")
		);

		if (mode === "category") {
			dynamicGuideText.textContent = "선택한 팀에서 사용할 업무 카테고리를 지정해 주세요.";
		} else if (mode === "region") {
			dynamicGuideText.textContent = "광역시·도부터 필요한 범위까지 선택하고 [지역 등록]을 눌러 담당 지역을 추가해 주세요.";
		} else if (teamName) {
			dynamicGuideText.textContent = "이 팀은 별도의 카테고리나 담당 지역 설정 없이 등록할 수 있습니다.";
		} else {
			dynamicGuideText.textContent = "팀을 선택하면 필요한 카테고리 또는 담당 지역 입력란이 이 영역에 표시됩니다.";
		}
	};
    const renderRegionList = () => {
		regionListContainer.replaceChildren();

		if (selectedRegions.length === 0) {
			const empty = document.createElement("div");
			empty.className = "text-muted small py-2 text-center";
			empty.textContent = "등록할 담당 지역이 아직 없습니다.";
			regionListContainer.appendChild(empty);
			return;
		}

        selectedRegions.forEach((region, index) => {
            const regionRow = document.createElement("div");
			regionRow.className = "d-flex justify-content-between align-items-center border rounded p-2 mb-2 bg-white";

			const label = document.createElement("span");
			label.textContent = [region.provinceName, region.cityName, region.districtName]
				.filter(Boolean).join(" ");

			const removeButton = document.createElement("button");
			removeButton.type = "button";
			removeButton.className = "btn btn-sm btn-outline-danger";
			removeButton.dataset.index = String(index);
			removeButton.textContent = "삭제";
			removeButton.addEventListener("click", () => {
                selectedRegions.splice(index, 1);
                renderRegionList();
                updateRegionInput();
            });

			regionRow.appendChild(label);
			regionRow.appendChild(removeButton);
            regionListContainer.appendChild(regionRow);
        });
    };

    teamSelect.addEventListener("change", function() {
        const selectedTeamId = this.value;
        const selectedTeamName = this.options[this.selectedIndex]?.text?.trim() || "";

        categoryWrapper.style.display = "none";
        provinceWrapper.style.display = "none";
        cityWrapper.style.display = "none";
        districtWrapper.style.display = "none";
		regionListContainer.classList.add("d-none");

        resetCategorySelect();

        selectedRegions.length = 0;
		renderRegionList();
        updateRegionInput();

        provinceSelect.value = "";
        citySelect.innerHTML = "";
        districtSelect.innerHTML = "";

        if (districtRegisterButton.parentElement) {
            districtRegisterButton.remove();
        }

        if (!selectedTeamId) {
			updateDynamicGuide("", "none");
            return;
        }

        if (CATEGORY_REQUIRED_TEAMS.has(selectedTeamName)) {
			updateDynamicGuide(selectedTeamName, "category");
            const categoryCount = renderCategoryOptionsByTeam(selectedTeamId);
            categoryWrapper.style.display = "block";

            if (categoryCount === 0) {
                alert(`${selectedTeamName}에 등록된 카테고리가 없습니다. 팀 카테고리 설정을 먼저 확인해주세요.`);
            }

            return;
        }

        if (REGION_REQUIRED_TEAMS.has(selectedTeamName)) {
			updateDynamicGuide(selectedTeamName, "region");
            provinceWrapper.style.display = "block";
			regionListContainer.classList.remove("d-none");
            districtWrapper.insertAdjacentElement("afterend", districtRegisterButton);
			return;
        }

		updateDynamicGuide(selectedTeamName, "none");
    });

    provinceSelect.addEventListener("change", function() {
        const provinceId = this.value;

        cityWrapper.style.display = "none";
        districtWrapper.style.display = "none";
        citySelect.innerHTML = "";
        districtSelect.innerHTML = "";

        if (!provinceId) {
            return;
        }

        fetch(`/api/v1/province/${provinceId}/cities`)
            .then(res => res.json())
            .then(cities => {
                if (cities.length > 0) {
					renderRegionOptions(citySelect, "시 선택", cities);

                    cityWrapper.style.display = "block";
                    districtWrapper.style.display = "none";
                    districtSelect.innerHTML = "";
                } else {
                    fetch(`/api/v1/province/${provinceId}/districts`)
                        .then(res => res.json())
                        .then(districts => {
							renderRegionOptions(districtSelect, "구 선택", districts);

                            cityWrapper.style.display = "none";
                            districtWrapper.style.display = "block";
                        });
                }
            });
    });

    citySelect.addEventListener("change", function() {
        const cityId = this.value;

        districtWrapper.style.display = "none";
        districtSelect.innerHTML = "";

        if (!cityId) {
            return;
        }

        fetch(`/api/v1/city/${cityId}/districts`)
            .then(res => res.json())
            .then(data => {
				renderRegionOptions(districtSelect, "구 선택", data);

                districtWrapper.style.display = "block";
            });
    });

    districtRegisterButton.addEventListener("click", () => {
        const provinceName = provinceSelect.options[provinceSelect.selectedIndex]?.text;
        const provinceId = provinceSelect.value;

        const cityName = citySelect.value
            ? citySelect.options[citySelect.selectedIndex]?.text
            : null;

        const cityId = citySelect.value || null;

        const districtName = districtSelect.value
            ? districtSelect.options[districtSelect.selectedIndex]?.text
            : null;

        const districtId = districtSelect.value || null;

        if (!provinceId || !provinceName) {
            alert("도는 반드시 선택해야 합니다.");
            return;
        }

        const isDuplicate = selectedRegions.some(r => {
            return r.provinceId === provinceId &&
                (r.cityId === cityId || r.cityId === null || cityId === null) &&
                (r.districtId === districtId || r.districtId === null || districtId === null);
        });

        if (isDuplicate) {
            alert("이미 추가된 지역입니다.");
            return;
        }

        selectedRegions.push({
            provinceId,
            cityId,
            districtId,
            provinceName,
            cityName,
            districtName
        });

        renderRegionList();
        updateRegionInput();

        provinceSelect.value = "";
        citySelect.innerHTML = "";
        districtSelect.innerHTML = "";
        cityWrapper.style.display = "none";
        districtWrapper.style.display = "none";
    });

    form.addEventListener("submit", async (e) => {
        e.preventDefault();

        const ok = await validateRegionBeforeSubmit();

        if (ok) {
            form.submit();
        }
    });
});

async function validateRegionBeforeSubmit() {
    const teamSelect = document.getElementById("teamSelect");
    const teamValue = teamSelect.value;
    const teamName = teamSelect.selectedOptions[0]?.text?.trim() || "";

    const CATEGORY_REQUIRED_TEAMS = new Set(["생산팀", "출고팀"]);
    const REGION_REQUIRED_TEAMS = new Set(["배송팀", "AS팀"]);

    if (!teamValue) {
        alert("팀을 선택해주세요.");
        return false;
    }

    if (CATEGORY_REQUIRED_TEAMS.has(teamName)) {
        const categorySelect = document.getElementById("teamCategorySelect");
        const categoryValue = categorySelect.value;
        const selectedCategoryTeamId = categorySelect.selectedOptions[0]?.dataset?.teamId;

        if (!categoryValue) {
            alert(`${teamName}은 카테고리 선택이 필수입니다.`);
            return false;
        }

        if (String(selectedCategoryTeamId) !== String(teamValue)) {
            alert("선택한 카테고리가 현재 선택한 팀과 일치하지 않습니다.");
            return false;
        }
    }

    if (REGION_REQUIRED_TEAMS.has(teamName)) {
        const json = document.getElementById("regionJsonInput").value;

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
            const res = await fetch("/api/v1/region/conflicts/check-new", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    teamId: Number(teamValue),
                    selections
                })
            });

            if (!res.ok) {
                alert("서버 검증 중 오류가 발생했습니다.");
                return false;
            }

            const conflicts = await res.json();

            if (Array.isArray(conflicts) && conflicts.length > 0) {
                const msg = conflicts
                    .map(c => `- [${c.conflictMemberName}] ${c.conflictPath}`)
                    .join("\n");

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
