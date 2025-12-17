/* /administration/assets/js/page/asList.js */

(function () {
    'use strict';

    const provinceSelect = document.getElementById('as-province-select');

    const childWrapper = document.getElementById('as-child-wrapper');
    const childLabel = document.getElementById('as-child-label');

    const citySelect = document.getElementById('as-city-select');
    const districtDirectSelect = document.getElementById('as-district-direct-select'); // city 없는 province용

    const districtWrapper = document.getElementById('as-district-wrapper');
    const districtSelect = document.getElementById('as-district-select'); // city 있는 경우 city 다음 단계

    const districtHidden = document.getElementById('as-district-hidden'); // ✅ 실제 전송용

    const selected = window.__AS_SELECTED__ || {};

    function resetSelect(selectEl, placeholderText) {
        selectEl.innerHTML = '';
        const opt = document.createElement('option');
        opt.value = '';
        opt.textContent = placeholderText || '전체';
        selectEl.appendChild(opt);
    }

    function show(el) { el.style.display = ''; }
    function hide(el) { el.style.display = 'none'; }

    async function fetchJson(url) {
        const res = await fetch(url, { headers: { 'Accept': 'application/json' } });
        if (!res.ok) throw new Error('API 요청 실패: ' + url);
        return res.json();
    }

    function fillOptions(selectEl, items, selectedId) {
        items.forEach(item => {
            const opt = document.createElement('option');
            opt.value = String(item.id);
            opt.textContent = item.name;
            if (selectedId != null && String(selectedId) === String(item.id)) {
                opt.selected = true;
            }
            selectEl.appendChild(opt);
        });
    }

    function setDistrictHidden(v) {
        if (!districtHidden) return;
        districtHidden.value = (v == null) ? '' : String(v);
    }

    function getDistrictHidden() {
        return districtHidden ? districtHidden.value : '';
    }

    function hideAllRegionControls() {
        hide(childWrapper);
        hide(citySelect);
        hide(districtDirectSelect);

        hide(districtWrapper);
        hide(districtSelect);
    }

    async function onProvinceChange(isInit) {
        const provinceId = provinceSelect.value;

        // 초기화
        resetSelect(citySelect, '전체');
        resetSelect(districtDirectSelect, '전체');
        resetSelect(districtSelect, '전체');

        hideAllRegionControls();

        // ✅ province 미선택이면 끝
        if (!provinceId) {
            setDistrictHidden('');
            return;
        }

        // province 자식 조회
        const data = await fetchJson(`/api/regions/provinces/${provinceId}/children`);

        // ✅ child wrapper는 무조건 표시(아무것도 없지는 않으므로)
        show(childWrapper);

        if (data.type === 'CITY') {
            // City 단계 표시
            childLabel.textContent = '시/군';

            show(citySelect);
            fillOptions(citySelect, data.items, isInit ? selected.cityId : null);

            // direct district는 사용 안 함
            hide(districtDirectSelect);

            // init이고 city가 선택되어 있었다면 district까지 이어서 복원
            if (isInit && selected.cityId) {
                await onCityChange(true);
            } else {
                // city 미선택 상태면 districtWrapper 숨김 유지 + hidden도 비움
                hide(districtWrapper);
                hide(districtSelect);
                setDistrictHidden('');
            }

        } else if (data.type === 'DISTRICT') {
            // City가 없는 Province(서울/세종 등): 바로 district 선택
            childLabel.textContent = '구/군';

            hide(citySelect);

            show(districtDirectSelect);
            fillOptions(districtDirectSelect, data.items, isInit ? selected.districtId : null);

            // ✅ 3단계 districtWrapper는 이 케이스는 사용 안 함
            hide(districtWrapper);
            hide(districtSelect);

            // hidden 값 동기화(초기 복원 포함)
            if (isInit && selected.districtId) {
                setDistrictHidden(selected.districtId);
            } else {
                // 선택값은 direct select 변경 이벤트에서 세팅됨
                const v = districtDirectSelect.value;
                setDistrictHidden(v || '');
            }
        }
    }

    async function onCityChange(isInit) {
        const cityId = citySelect.value;

        resetSelect(districtSelect, '전체');
        hide(districtWrapper);
        hide(districtSelect);

        if (!cityId) {
            setDistrictHidden('');
            return;
        }

        const items = await fetchJson(`/api/regions/cities/${cityId}/districts`);

        // ✅ label도 같이 보이게 wrapper show
        show(districtWrapper);
        show(districtSelect);

        fillOptions(districtSelect, items, isInit ? selected.districtId : null);

        // hidden 값 동기화(초기 복원 포함)
        if (isInit && selected.districtId) {
            setDistrictHidden(selected.districtId);
        } else {
            const v = districtSelect.value;
            setDistrictHidden(v || '');
        }
    }

    function bind() {
        if (!provinceSelect) return;

        provinceSelect.addEventListener('change', () => {
            // province 변경 시 기존 선택값 의미 없어지므로 초기화
            selected.cityId = null;
            selected.districtId = null;

            // 화면/hidden 동기화
            setDistrictHidden('');
            onProvinceChange(false).catch(console.error);
        });

        citySelect.addEventListener('change', () => {
            selected.districtId = null;
            setDistrictHidden('');
            onCityChange(false).catch(console.error);
        });

        // ✅ direct district 변경 시 hidden 동기화
        districtDirectSelect.addEventListener('change', () => {
            const v = districtDirectSelect.value;
            selected.districtId = v ? Number(v) : null;
            setDistrictHidden(v || '');
        });

        // ✅ city 기반 district 변경 시 hidden 동기화
        districtSelect.addEventListener('change', () => {
            const v = districtSelect.value;
            selected.districtId = v ? Number(v) : null;
            setDistrictHidden(v || '');
        });
    }

    // 초기 로딩 시 선택값 복원
    document.addEventListener('DOMContentLoaded', () => {
        bind();

        // 서버에서 districtId는 hidden에 이미 세팅되어 있음(th:value).
        // JS는 select 복원 후 hidden도 정확히 재동기화함.
        onProvinceChange(true).catch(console.error);
    });

})();
