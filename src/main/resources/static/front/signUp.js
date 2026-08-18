/* signUp.js - modern auth layout */
(function () {
    'use strict';

    function qs(id) {
        return document.getElementById(id);
    }

    function onlyDigits(value) {
        return String(value || '').replace(/\D/g, '');
    }

    function formatPhone(value) {
        const digits = onlyDigits(value).slice(0, 11);
        if (digits.length <= 3) return digits;
        if (digits.length <= 7) return digits.slice(0, 3) + '-' + digits.slice(3);
        return digits.slice(0, 3) + '-' + digits.slice(3, 7) + '-' + digits.slice(7);
    }

    function escapeHtml(value) {
        return String(value || '').replace(/[&<>"']/g, function (character) {
            return {
                '&': '&amp;',
                '<': '&lt;',
                '>': '&gt;',
                '"': '&quot;',
                "'": '&#39;'
            }[character];
        });
    }

    function focusLater(element) {
        window.setTimeout(function () {
            if (element) element.focus();
        }, 0);
    }

    function requireDaumPostcode() {
        if (!window.daum || !window.daum.Postcode) {
            window.alert('주소 검색 서비스를 불러오지 못했습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.');
            return false;
        }
        return true;
    }

    function resolveDaumRegion(data) {
        if (window.HiddenAutoAddressRegion && typeof window.HiddenAutoAddressRegion.fromDaum === 'function') {
            return window.HiddenAutoAddressRegion.fromDaum(data);
        }

        return {
            doName: data.sido || '',
            siName: data.sigungu || '',
            guName: '',
            roadAddress: data.userSelectedType === 'R' ? data.roadAddress : data.jibunAddress,
            zipCode: data.zonecode || ''
        };
    }

    // ---------------------------------------------------------
    // 대표자 사업자등록증 파일 표시 / 삭제
    // ---------------------------------------------------------
    const businessLicenseInput = qs('sign-up-rep-business-license');
    const businessLicenseName = qs('sign-up-rep-business-license-name');
    const businessLicenseRemove = qs('sign-up-rep-business-license-remove');

    function renderBusinessLicenseFile() {
        if (!businessLicenseInput || !businessLicenseName || !businessLicenseRemove) return;
        const file = businessLicenseInput.files && businessLicenseInput.files[0];
        businessLicenseName.textContent = file ? file.name : '선택된 파일이 없습니다.';
        businessLicenseRemove.classList.toggle('d-none', !file);
    }

    if (businessLicenseInput) {
        businessLicenseInput.addEventListener('change', renderBusinessLicenseFile);
    }

    if (businessLicenseRemove) {
        businessLicenseRemove.addEventListener('click', function () {
            if (businessLicenseInput) businessLicenseInput.value = '';
            renderBusinessLicenseFile();
        });
    }

    // ---------------------------------------------------------
    // 대표자 기본 주소 검색
    // ---------------------------------------------------------
    function openRepresentativeAddressSearch() {
        if (!requireDaumPostcode()) return;

        new window.daum.Postcode({
            oncomplete: function (data) {
                const region = resolveDaumRegion(data);
                const roadAddress = qs('sign-up-rep-road-address');
                const doName = qs('sign-up-rep-do-name');
                const siName = qs('sign-up-rep-si-name');
                const guName = qs('sign-up-rep-gu-name');
                const zipCode = qs('sign-up-rep-zip-code');
                const detailAddress = qs('sign-up-rep-detail-address');

                if (roadAddress) roadAddress.value = region.roadAddress || '';
                if (doName) doName.value = region.doName || '';
                if (siName) siName.value = region.siName || '';
                if (guName) guName.value = region.guName || '';
                if (zipCode) zipCode.value = region.zipCode || '';
                if (detailAddress) detailAddress.focus();
            }
        }).open();
    }

    const representativeAddressButton = qs('sign-up-rep-address-search-btn');
    if (representativeAddressButton) {
        representativeAddressButton.addEventListener('click', openRepresentativeAddressSearch);
    }

    // 과거 템플릿/캐시가 남아 있는 환경에서 호출되어도 기능이 끊기지 않도록 호환 함수는 유지합니다.
    window.signUpExecDaumPostcode = openRepresentativeAddressSearch;
    window.execDaumPostcode = openRepresentativeAddressSearch;

    // ---------------------------------------------------------
    // 아이디 중복 확인
    // ---------------------------------------------------------
    ['sign-up-rep-username', 'sign-up-emp-username'].forEach(function (id) {
        const input = qs(id);
        if (!input) return;

        input.addEventListener('change', async function () {
            const username = String(input.value || '').trim();
            if (!username) return;

            try {
                const response = await fetch('/api/v1/validate/username?username=' + encodeURIComponent(username), {
                    headers: { 'Accept': 'application/json' }
                });
                if (!response.ok) throw new Error('아이디 중복 확인 요청 실패');
                const data = await response.json();

                if (data.duplicate) {
                    window.alert('이미 사용 중인 아이디입니다.');
                    input.value = '';
                    input.focus();
                }
            } catch (error) {
                console.error('[sign-up] username validation failed', error);
            }
        });
    });

    // ---------------------------------------------------------
    // 비밀번호 일치 확인
    // ---------------------------------------------------------
    [
        { passwordId: 'sign-up-rep-password', checkId: 'sign-up-rep-password-check' },
        { passwordId: 'sign-up-emp-password', checkId: 'sign-up-emp-password-check' }
    ].forEach(function (pair) {
        const password = qs(pair.passwordId);
        const check = qs(pair.checkId);
        if (!password || !check) return;

        check.addEventListener('blur', function () {
            if (password.value && check.value && password.value !== check.value) {
                window.alert('비밀번호가 일치하지 않습니다.');
                check.value = '';
                check.focus();
            }
        });
    });

    // ---------------------------------------------------------
    // 연락처 포맷 + 중복 확인
    // ---------------------------------------------------------
    document.querySelectorAll('.sign-up-phone-input').forEach(function (input) {
        input.addEventListener('input', function () {
            input.value = formatPhone(input.value);
        });

        input.addEventListener('blur', async function () {
            const rawNumber = onlyDigits(input.value);
            if (rawNumber.length !== 11) return;

            try {
                const response = await fetch('/api/v1/validate/phone?phone=' + encodeURIComponent(rawNumber), {
                    headers: { 'Accept': 'application/json' }
                });
                if (!response.ok) throw new Error('연락처 중복 확인 요청 실패');
                const data = await response.json();

                if (data.duplicate) {
                    window.alert('이미 등록된 연락처입니다.');
                    input.value = '';
                    input.focus();
                }
            } catch (error) {
                console.error('[sign-up] phone validation failed', error);
            }
        });
    });

    // ---------------------------------------------------------
    // 사업자등록번호 중복 확인 + 제출 가드
    // ---------------------------------------------------------
    const businessNumberInput = qs('sign-up-rep-business-number');
    let businessNumberValid = false;
    let businessNumberChecking = false;
    let businessNumberSuppressBlur = false;
    let businessNumberLastChecked = '';

    if (businessNumberInput) {
        businessNumberInput.addEventListener('input', function () {
            const digits = onlyDigits(businessNumberInput.value).slice(0, 10);
            businessNumberInput.value = digits;
            businessNumberValid = false;

            if (digits !== businessNumberLastChecked) {
                businessNumberLastChecked = '';
            }
        });

        businessNumberInput.addEventListener('blur', async function () {
            if (businessNumberSuppressBlur) return;

            const digits = onlyDigits(businessNumberInput.value);
            if (!digits) {
                businessNumberValid = false;
                return;
            }

            if (digits.length !== 10) {
                businessNumberValid = false;
                window.alert('사업자등록번호는 숫자 10자리로 입력해 주세요.');
                businessNumberSuppressBlur = true;
                focusLater(businessNumberInput);
                window.setTimeout(function () { businessNumberSuppressBlur = false; }, 200);
                return;
            }

            if (businessNumberValid && businessNumberLastChecked === digits) return;
            if (businessNumberChecking) return;

            businessNumberChecking = true;
            try {
                const response = await fetch('/api/v1/validate/businessNumber?businessNumber=' + encodeURIComponent(digits), {
                    headers: { 'Accept': 'application/json' }
                });
                if (!response.ok) throw new Error('사업자등록번호 중복 확인 요청 실패');
                const data = await response.json();

                if (data.duplicate) {
                    businessNumberValid = false;
                    businessNumberLastChecked = '';
                    window.alert('이미 등록된 사업자등록번호입니다.');
                    businessNumberInput.value = '';
                    businessNumberSuppressBlur = true;
                    focusLater(businessNumberInput);
                    window.setTimeout(function () { businessNumberSuppressBlur = false; }, 200);
                    return;
                }

                businessNumberValid = true;
                businessNumberLastChecked = digits;
            } catch (error) {
                console.error('[sign-up] business number validation failed', error);
                businessNumberValid = false;
                businessNumberLastChecked = '';
                window.alert('사업자등록번호 중복 확인에 실패했습니다. 잠시 후 다시 시도해 주세요.');
                businessNumberSuppressBlur = true;
                focusLater(businessNumberInput);
                window.setTimeout(function () { businessNumberSuppressBlur = false; }, 200);
            } finally {
                businessNumberChecking = false;
            }
        });
    }

    const representativeForm = qs('sign-up-representative-form');
    if (representativeForm) {
        representativeForm.addEventListener('submit', function (event) {
            const password = qs('sign-up-rep-password');
            const passwordCheck = qs('sign-up-rep-password-check');
            if (password && passwordCheck && password.value !== passwordCheck.value) {
                event.preventDefault();
                window.alert('비밀번호가 일치하지 않습니다.');
                passwordCheck.focus();
                return;
            }

            if (!businessNumberInput) return;
            const digits = onlyDigits(businessNumberInput.value);
            if (digits.length !== 10) {
                event.preventDefault();
                window.alert('사업자등록번호는 숫자 10자리로 입력해 주세요.');
                businessNumberInput.focus();
                return;
            }

            if (!businessNumberValid || businessNumberLastChecked !== digits) {
                event.preventDefault();
                window.alert('사업자등록번호 중복 확인이 필요합니다. 입력 후 다른 항목으로 이동해 검증을 완료해 주세요.');
                businessNumberInput.focus();
            }
        });
    }

    const employeeForm = qs('sign-up-employee-form');
    if (employeeForm) {
        employeeForm.addEventListener('submit', function (event) {
            const password = qs('sign-up-emp-password');
            const passwordCheck = qs('sign-up-emp-password-check');
            if (password && passwordCheck && password.value !== passwordCheck.value) {
                event.preventDefault();
                window.alert('비밀번호가 일치하지 않습니다.');
                passwordCheck.focus();
            }
        });
    }

    // ---------------------------------------------------------
    // 추가 배송지: 대표/직원 공통 모달 1개 사용
    // ---------------------------------------------------------
    const deliveryModalElement = qs('sign-up-delivery-detail-modal');
    const deliverySelectedAddress = qs('sign-up-delivery-selected-address');
    const deliveryDetailInput = qs('sign-up-delivery-detail-input');
    const deliverySaveButton = qs('sign-up-delivery-detail-save-btn');
    let deliveryModal = null;
    let deliveryPending = null;

    if (deliveryModalElement && window.bootstrap && window.bootstrap.Modal) {
        deliveryModal = window.bootstrap.Modal.getOrCreateInstance(deliveryModalElement, {
            backdrop: 'static',
            keyboard: true
        });
    }

    function openDeliveryDetailModal(baseItem, addFunction) {
        deliveryPending = { item: baseItem, addFunction: addFunction };
        if (deliverySelectedAddress) deliverySelectedAddress.textContent = baseItem.roadAddress || '-';
        if (deliveryDetailInput) deliveryDetailInput.value = '';

        if (!deliveryModal) {
            window.alert('상세주소 입력 모달을 열 수 없습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.');
            return;
        }

        deliveryModal.show();
        window.setTimeout(function () {
            if (deliveryDetailInput) deliveryDetailInput.focus();
        }, 180);
    }

    if (deliverySaveButton) {
        deliverySaveButton.addEventListener('click', function () {
            if (!deliveryPending || typeof deliveryPending.addFunction !== 'function') return;
            const detail = deliveryDetailInput ? String(deliveryDetailInput.value || '').trim() : '';
            deliveryPending.addFunction(detail);
            if (deliveryModal) deliveryModal.hide();
        });
    }

    if (deliveryModalElement) {
        deliveryModalElement.addEventListener('hidden.bs.modal', function () {
            deliveryPending = null;
            if (deliveryDetailInput) deliveryDetailInput.value = '';
            if (deliverySelectedAddress) deliverySelectedAddress.textContent = '-';
        });
    }

    function setupDeliveryUi(options) {
        const addButton = qs(options.addButtonId);
        const listElement = qs(options.listId);
        const hiddenInput = qs(options.hiddenId);
        if (!addButton || !listElement || !hiddenInput) return;

        const items = [];

        function syncHidden() {
            hiddenInput.value = items.length ? JSON.stringify(items) : '';
        }

        function render() {
            listElement.innerHTML = '';

            if (items.length === 0) {
                const empty = document.createElement('div');
                empty.className = 'sign-up-delivery-empty';
                empty.textContent = '추가 등록된 배송지가 없습니다.';
                listElement.appendChild(empty);
                return;
            }

            items.forEach(function (item, index) {
                const wrapper = document.createElement('div');
                wrapper.className = 'sign-up-delivery-item';

                const text = document.createElement('div');
                text.className = 'sign-up-delivery-item-text';
                text.innerHTML = escapeHtml(
                    (item.roadAddress || '') + (item.detailAddress ? ' ' + item.detailAddress : '')
                );

                const remove = document.createElement('button');
                remove.type = 'button';
                remove.className = 'sign-up-delivery-remove-btn';
                remove.setAttribute('aria-label', '추가 배송지 삭제');
                remove.innerHTML = '<i class="fa-solid fa-xmark"></i>';
                remove.addEventListener('click', function () {
                    items.splice(index, 1);
                    syncHidden();
                    render();
                });

                wrapper.appendChild(text);
                wrapper.appendChild(remove);
                listElement.appendChild(wrapper);
            });
        }

        function addItem(baseItem, detailText) {
            const item = {
                zipCode: baseItem.zipCode || '',
                doName: baseItem.doName || '',
                siName: baseItem.siName || '',
                guName: baseItem.guName || '',
                roadAddress: baseItem.roadAddress || '',
                detailAddress: String(detailText || '').trim()
            };

            if (!item.roadAddress) return;

            const duplicate = items.some(function (existing) {
                return existing.roadAddress === item.roadAddress
                    && existing.detailAddress === item.detailAddress;
            });

            if (duplicate) {
                window.alert('이미 추가된 배송지입니다.');
                return;
            }

            items.push(item);
            syncHidden();
            render();
        }

        addButton.addEventListener('click', function () {
            if (!requireDaumPostcode()) return;

            new window.daum.Postcode({
                oncomplete: function (data) {
                    const region = resolveDaumRegion(data);
                    const baseItem = {
                        zipCode: region.zipCode || '',
                        doName: region.doName || '',
                        siName: region.siName || '',
                        guName: region.guName || '',
                        roadAddress: region.roadAddress || ''
                    };

                    if (!baseItem.roadAddress) return;
                    openDeliveryDetailModal(baseItem, function (detailText) {
                        addItem(baseItem, detailText);
                    });
                }
            }).open();
        });

        syncHidden();
        render();
    }

    setupDeliveryUi({
        addButtonId: 'sign-up-rep-add-delivery-btn',
        listId: 'sign-up-rep-delivery-list',
        hiddenId: 'sign-up-rep-delivery-addresses-json'
    });

    setupDeliveryUi({
        addButtonId: 'sign-up-emp-add-delivery-btn',
        listId: 'sign-up-emp-delivery-list',
        hiddenId: 'sign-up-emp-delivery-addresses-json'
    });
})();
