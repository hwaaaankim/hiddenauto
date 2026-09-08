(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        const province = document.getElementById('client-list-region-province');
        const city = document.getElementById('client-list-region-city');
        const district = document.getElementById('client-list-region-district');
        const cityWrap = document.getElementById('client-list-region-city-wrap');
        const districtWrap = document.getElementById('client-list-region-district-wrap');

        if (!province || !city || !district || !cityWrap || !districtWrap) {
            return;
        }

		let provinceRequestSequence = 0;
		let cityRequestSequence = 0;

        province.addEventListener('change', async function () {
			const requestSequence = ++provinceRequestSequence;
			cityRequestSequence++;
            resetSelect(city, '시·군 전체');
            resetSelect(district, '구·군 전체');
            cityWrap.classList.add('d-none');
            districtWrap.classList.add('d-none');

            if (!province.value) {
                return;
            }

            try {
                const response = await fetch(
                    '/management/clientList/api/regions/province/'
                        + encodeURIComponent(province.value)
                        + '/children',
                    { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' }
                );
                const data = await readJson(response);
				if (requestSequence !== provinceRequestSequence) {
					return;
				}

                if (data.mode === 'CITY') {
                    fillSelect(city, data.items, '시·군 전체');
                    cityWrap.classList.remove('d-none');
                } else if (data.mode === 'DISTRICT') {
                    fillSelect(district, data.items, '구·군 전체');
                    districtWrap.classList.remove('d-none');
                }
            } catch (error) {
				if (requestSequence !== provinceRequestSequence) {
					return;
				}
                alert(error.message || '하위 행정구역을 불러오지 못했습니다.');
            }
        });

        city.addEventListener('change', async function () {
			const requestSequence = ++cityRequestSequence;
            resetSelect(district, '구·군 전체');
            districtWrap.classList.add('d-none');

            if (!city.value) {
                return;
            }

            try {
                const response = await fetch(
                    '/management/clientList/api/regions/city/'
                        + encodeURIComponent(city.value)
                        + '/districts',
                    { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' }
                );
                const data = await readJson(response);
				if (requestSequence !== cityRequestSequence) {
					return;
				}

                if (data.mode === 'DISTRICT' && Array.isArray(data.items) && data.items.length > 0) {
                    fillSelect(district, data.items, '구·군 전체');
                    districtWrap.classList.remove('d-none');
                }
            } catch (error) {
				if (requestSequence !== cityRequestSequence) {
					return;
				}
                alert(error.message || '하위 행정구역을 불러오지 못했습니다.');
            }
        });
    });

    async function readJson(response) {
        const text = await response.text();
        let data = null;

        try {
            data = text ? JSON.parse(text) : null;
        } catch (ignore) {
            data = null;
        }

        if (!response.ok) {
            throw new Error(data && data.message ? data.message : '지역 조회 요청에 실패했습니다.');
        }

        return data || {};
    }

    function resetSelect(select, label) {
        select.replaceChildren();
        const option = document.createElement('option');
        option.value = '';
        option.textContent = label;
        select.appendChild(option);
    }

    function fillSelect(select, items, firstLabel) {
        resetSelect(select, firstLabel);

        (Array.isArray(items) ? items : []).forEach(function (item) {
            const option = document.createElement('option');
            option.value = String(item.id);
            option.textContent = item.name || '-';
            select.appendChild(option);
        });
    }
})();
