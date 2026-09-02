/* addressRegion.js */
(function(window, document) {
    'use strict';

    var PROVINCE_ALIASES = {
        '서울': '서울특별시',
        '서울시': '서울특별시',
        '서울특별시': '서울특별시',
        '부산': '부산광역시',
        '부산시': '부산광역시',
        '부산광역시': '부산광역시',
        '대구': '대구광역시',
        '대구시': '대구광역시',
        '대구광역시': '대구광역시',
        '인천': '인천광역시',
        '인천시': '인천광역시',
        '인천광역시': '인천광역시',
        '광주': '광주광역시',
        '광주시': '광주광역시',
        '광주광역시': '광주광역시',
        '전남광주통합특별시': '광주광역시',
        '광주전남통합특별시': '광주광역시',
        '대전': '대전광역시',
        '대전시': '대전광역시',
        '대전광역시': '대전광역시',
        '울산': '울산광역시',
        '울산시': '울산광역시',
        '울산광역시': '울산광역시',
        '세종': '세종특별자치시',
        '세종시': '세종특별자치시',
        '세종특별자치시': '세종특별자치시',
        '경기': '경기도',
        '경기도': '경기도',
        '강원': '강원특별자치도',
        '강원도': '강원특별자치도',
        '강원특별자치도': '강원특별자치도',
        '충북': '충청북도',
        '충청북도': '충청북도',
        '충남': '충청남도',
        '충청남도': '충청남도',
        '전북': '전북특별자치도',
        '전라북도': '전북특별자치도',
        '전북특별자치도': '전북특별자치도',
        '전남': '전라남도',
        '전라남도': '전라남도',
        '경북': '경상북도',
        '경상북도': '경상북도',
        '경남': '경상남도',
        '경상남도': '경상남도',
        '제주': '제주특별자치도',
        '제주도': '제주특별자치도',
        '제주특별자치도': '제주특별자치도'
    };

    function clean(value) {
        return (value == null ? '' : String(value))
            .replace(/\u00a0/g, ' ')
            .trim()
            .replace(/\s+/g, ' ');
    }

    function provinceCanonical(value) {
        var cleaned = clean(value);
        var compact = cleaned.replace(/\s+/g, '');
        if (PROVINCE_ALIASES[compact]) {
            return PROVINCE_ALIASES[compact];
        }
        if (compact.indexOf('광주') !== -1 && compact.indexOf('통합특별시') !== -1) {
            return '광주광역시';
        }
        return cleaned;
    }

    function split(value) {
        var cleaned = clean(value);
        return cleaned ? cleaned.split(/\s+/) : [];
    }

    function endsWithAny(value, suffixes) {
        var cleaned = clean(value);
        for (var i = 0; i < suffixes.length; i += 1) {
            if (cleaned.slice(-suffixes[i].length) === suffixes[i]) {
                return true;
            }
        }
        return false;
    }

    function pushUnique(target, value, province) {
        var cleaned = clean(value);
        if (!cleaned) return;
        if (provinceCanonical(cleaned) === provinceCanonical(province)) return;
        if (target.indexOf(cleaned) === -1) target.push(cleaned);
    }

    function isProvinceToken(value) {
        var cleaned = clean(value);
        var compact = cleaned.replace(/\s+/g, '');
        if (!cleaned) return false;
        if (Object.prototype.hasOwnProperty.call(PROVINCE_ALIASES, compact)) return true;

        var canonical = provinceCanonical(cleaned);
        return endsWithAny(canonical, ['특별자치도', '특별자치시', '광역시', '특별시', '도']);
    }

    function resolveProvince(doName, roadAddress) {
        var roadTokens = split(roadAddress);
        var limit = Math.min(roadTokens.length, 2);
        for (var i = 0; i < limit; i += 1) {
            if (isProvinceToken(roadTokens[i])) {
                return provinceCanonical(roadTokens[i]);
            }
        }

        var province = provinceCanonical(doName);
        if (province) return province;

        return roadTokens.length ? provinceCanonical(roadTokens[0]) : '';
    }

    function fieldTokens(value, province) {
        var result = [];
        split(value).forEach(function(token) {
            pushUnique(result, token, province);
        });
        return result;
    }

    function roadPrefixTokens(value, province) {
        var result = [];
        split(value).slice(0, 5).forEach(function(token) {
            pushUnique(result, token, province);
        });
        return result;
    }

    function startsWithProvince(roadAddress, province) {
        var expected = provinceCanonical(province);
        var tokens = split(roadAddress);
        var limit = Math.min(tokens.length, 2);
        for (var i = 0; i < limit; i += 1) {
            if (provinceCanonical(tokens[i]) === expected) {
                return true;
            }
        }
        return false;
    }

    function firstEndingWith(values, suffixes) {
        for (var i = 0; i < values.length; i += 1) {
            if (endsWithAny(values[i], suffixes)) {
                return values[i];
            }
        }
        return '';
    }

    /**
     * 프런트 1차 정규화입니다.
     * 최종 확정은 서버의 AddressRegionResolver가 행정구역 DB 기준으로 다시 수행합니다.
     */
    function normalize(doName, siName, guName, roadAddress) {
        var province = resolveProvince(doName, roadAddress);
        var provinceKey = provinceCanonical(province);
        var siTokens = fieldTokens(siName, provinceKey);
        var guTokens = fieldTokens(guName, provinceKey);
        var roadTokens = roadPrefixTokens(roadAddress, provinceKey);
        var authoritativeRoad = startsWithProvince(roadAddress, provinceKey);

        var isDoLevel = provinceKey.slice(-1) === '도';
        var city = '';
        var district = '';

        if (isDoLevel) {
            // 도 아래의 시/군: 이천시, 용인시, 양평군, 제주시 등
            city = firstEndingWith(roadTokens, ['시', '군']);
            if (!city && !authoritativeRoad) {
                city = firstEndingWith(siTokens, ['시', '군']);
            }
            if (!city && !authoritativeRoad) {
                city = firstEndingWith(guTokens, ['시', '군']);
            }

            // 시 아래의 구: 수지구, 분당구, 완산구 등
            district = firstEndingWith(roadTokens, ['구']);
            if (!district && !authoritativeRoad) {
                district = firstEndingWith(guTokens, ['구']);
            }
            if (!district && !authoritativeRoad) {
                district = firstEndingWith(siTokens, ['구']);
            }
        } else {
            // 특별시/광역시 아래에는 City 없이 District가 바로 연결됩니다.
            // 서울 관악구, 부산 기장군, 인천 강화군 등
            district = firstEndingWith(roadTokens, ['구', '군']);
            if (!district && !authoritativeRoad) {
                district = firstEndingWith(guTokens, ['구', '군']);
            }
            if (!district && !authoritativeRoad) {
                district = firstEndingWith(siTokens, ['구', '군']);
            }
        }

        return {
            doName: provinceKey,
            siName: city,
            guName: district,
            roadAddress: clean(roadAddress)
        };
    }

    function fromDaum(data) {
        data = data || {};
        var address = data.userSelectedType === 'J'
            ? (data.jibunAddress || data.roadAddress || '')
            : (data.roadAddress || data.jibunAddress || '');

        var region = normalize(
            data.sido || '',
            data.sigungu || '',
            data.bname || '',
            address
        );

        region.zipCode = clean(data.zonecode);
        return region;
    }

    function normalizeAddressObject(value) {
        if (!value || typeof value !== 'object') return value;

        var region = normalize(
            value.doName,
            value.siName,
            value.guName,
            value.roadAddress
        );

        var copied = {};
        Object.keys(value).forEach(function(key) {
            copied[key] = value[key];
        });

        copied.doName = region.doName;
        copied.siName = region.siName;
        copied.guName = region.guName;
        copied.roadAddress = region.roadAddress;
        copied.detailAddress = clean(value.detailAddress);
        copied.zipCode = clean(value.zipCode);
        return copied;
    }

    function normalizeJsonInput(input) {
        if (!input || !clean(input.value)) return;

        try {
            var values = JSON.parse(input.value);
            if (!Array.isArray(values)) return;
            input.value = JSON.stringify(values.map(normalizeAddressObject));
        } catch (error) {
            // JSON 오류는 서버 검증에서 최종 차단합니다.
            console.error('주소 JSON 1차 정규화 실패', error);
        }
    }

    function normalizeBasicAddressFields() {
        var doInput = document.getElementById('doName');
        var siInput = document.getElementById('siName');
        var guInput = document.getElementById('guName');
        var roadInput = document.getElementById('searchAddress');

        if (!doInput || !siInput || !guInput || !roadInput) return;

        var region = normalize(
            doInput.value,
            siInput.value,
            guInput.value,
            roadInput.value
        );

        doInput.value = region.doName;
        siInput.value = region.siName;
        guInput.value = region.guName;
        roadInput.value = region.roadAddress;
    }

    // 기존 myInfo.js의 submit 처리보다 먼저 실행되도록 캡처 단계에서 보정합니다.
    document.addEventListener('submit', function() {
        normalizeBasicAddressFields();
        normalizeJsonInput(document.getElementById('repDeliveryAddressesJson'));
        normalizeJsonInput(document.getElementById('empDeliveryAddressesJson'));
        normalizeJsonInput(document.getElementById('companyDeliveryAddressesJson'));
    }, true);

    window.HiddenAutoAddressRegion = {
        normalize: normalize,
        fromDaum: fromDaum,
        normalizeAddressObject: normalizeAddressObject,
        normalizeJsonInput: normalizeJsonInput
    };

})(window, document);
