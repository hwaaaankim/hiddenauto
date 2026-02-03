/* /front/page/myInfo/myInfo.js */
/* global daum, bootstrap */

(function () {
  'use strict';

  // =========================
  // DOM
  // =========================
  function qs(id) { return document.getElementById(id); }

  var phoneEl = qs('myinfo-added-phone');
  var phoneHelp = qs('myinfo-added-phone-help');

  var bizEl = qs('myinfo-added-businessNumber');
  var bizHelp = qs('myinfo-added-businessNumber-help');

  var submitBtn = qs('myinfo-added-submit');

  // 사업자등록증(대표만 DOM 존재)
  var licenseInput = qs('myinfo-added-license-input');
  var existingBox = qs('myinfo-added-license-existing');
  var existingImg = qs('myinfo-added-license-existing-img');
  var existingRemoveBtn = qs('myinfo-added-license-existing-remove');

  var newBox = qs('myinfo-added-license-new');
  var newImg = qs('myinfo-added-license-new-img');
  var newRemoveBtn = qs('myinfo-added-license-new-remove');

  var removeHidden = qs('removeBusinessLicense');
  var licenseHelp = qs('myinfo-added-license-help');

  // 서버 주입 값
  var existingLicenseUrl = window.__MYINFO_EXISTING_LICENSE_URL__ || null;
  var isRepresentative = !!window.__MYINFO_IS_REPRESENTATIVE__;

  // =========================
  // State
  // =========================
  var state = {
    phoneOk: true,
    bizOk: true,
    licenseOk: true,

    // 등록증 상태
    hasExisting: !!existingLicenseUrl,
    existingRemoved: false, // 기존 X 눌러 삭제 플래그
    hasNewFile: false       // 신규 파일 선택 상태
  };

  // =========================
  // Utils
  // =========================
  function onlyDigits(v) {
    return (v || '').toString().replace(/[^0-9]/g, '');
  }

  function setHelp(el, msg, type) {
    if (!el) return;
    el.textContent = msg || '';
    el.classList.remove('ok', 'bad');
    if (type === 'ok') el.classList.add('ok');
    if (type === 'bad') el.classList.add('bad');
  }

  function formatPhone(digits) {
    // 11자리: 000-0000-0000
    if (digits.length >= 11) {
      return digits.substring(0, 3) + '-' + digits.substring(3, 7) + '-' + digits.substring(7, 11);
    }
    // 10자리: 000-000-0000
    if (digits.length === 10) {
      return digits.substring(0, 3) + '-' + digits.substring(3, 6) + '-' + digits.substring(6, 10);
    }
    // 그 미만은 가능한 범위에서 점진 표시
    if (digits.length <= 3) return digits;
    if (digits.length <= 7) return digits.substring(0, 3) + '-' + digits.substring(3);
    return digits.substring(0, 3) + '-' + digits.substring(3, 7) + '-' + digits.substring(7);
  }

  function formatBusinessNumber(digits) {
    // 10자리: 000-00-00000
    if (digits.length >= 10) {
      return digits.substring(0, 3) + '-' + digits.substring(3, 5) + '-' + digits.substring(5, 10);
    }
    if (digits.length <= 3) return digits;
    if (digits.length <= 5) return digits.substring(0, 3) + '-' + digits.substring(3);
    return digits.substring(0, 3) + '-' + digits.substring(3, 5) + '-' + digits.substring(5);
  }

  function updateSubmitState() {
    if (!submitBtn) return;

    // 사업자등록증 필수: "기존이 있고 삭제 안됨" OR "신규 파일 있음"
    if (isRepresentative && (licenseInput || existingBox || newBox)) {
      var hasLicenseNow = (!state.existingRemoved && state.hasExisting) || state.hasNewFile;
      state.licenseOk = hasLicenseNow;

      if (!hasLicenseNow) {
        setHelp(licenseHelp, '사업자등록증은 필수입니다. 업로드 후 저장해 주세요.', 'bad');
      } else {
        setHelp(licenseHelp, '', null);
      }
    }

    var ok = state.phoneOk && state.bizOk && state.licenseOk;
    submitBtn.disabled = !ok;
  }

  // =========================
  // Duplicate check (API)
  // =========================
  function checkPhoneDup() {
    if (!phoneEl) return;

    var digits = onlyDigits(phoneEl.value);
    var formatted = formatPhone(digits);
    phoneEl.value = formatted;

    // 형식 체크
    if (!(digits.length === 10 || digits.length === 11)) {
      state.phoneOk = false;
      setHelp(phoneHelp, '휴대폰 번호 형식이 올바르지 않습니다.', 'bad');
      updateSubmitState();
      return;
    }

    fetch('/customer/api/dup-check/phone?phone=' + encodeURIComponent(formatted), {
      method: 'GET',
      headers: { 'Accept': 'application/json' }
    })
      .then(function (r) { return r.json(); })
      .then(function (data) {
        if (!data || data.ok === false) {
          state.phoneOk = false;
          setHelp(phoneHelp, (data && data.message) ? data.message : '중복체크 실패', 'bad');
          updateSubmitState();
          return;
        }

        if (data.duplicate) {
          state.phoneOk = false;
          setHelp(phoneHelp, '중복임', 'bad');
        } else {
          state.phoneOk = true;
          setHelp(phoneHelp, '중복 아님', 'ok');
        }
        updateSubmitState();
      })
      .catch(function () {
        state.phoneOk = false;
        setHelp(phoneHelp, '중복체크 실패(네트워크)', 'bad');
        updateSubmitState();
      });
  }

  function checkBizDup() {
    if (!bizEl) return;

    var digits = onlyDigits(bizEl.value);
    bizEl.value = formatBusinessNumber(digits);

    if (digits.length !== 10) {
      state.bizOk = false;
      setHelp(bizHelp, '사업자등록번호는 숫자 10자리여야 합니다.', 'bad');
      updateSubmitState();
      return;
    }

    fetch('/customer/api/dup-check/business-number?businessNumber=' + encodeURIComponent(digits), {
      method: 'GET',
      headers: { 'Accept': 'application/json' }
    })
      .then(function (r) { return r.json(); })
      .then(function (data) {
        if (!data || data.ok === false) {
          state.bizOk = false;
          setHelp(bizHelp, (data && data.message) ? data.message : '중복체크 실패', 'bad');
          updateSubmitState();
          return;
        }

        if (data.duplicate) {
          state.bizOk = false;
          setHelp(bizHelp, '중복임', 'bad');
        } else {
          state.bizOk = true;
          setHelp(bizHelp, '중복 아님', 'ok');
        }
        updateSubmitState();
      })
      .catch(function () {
        state.bizOk = false;
        setHelp(bizHelp, '중복체크 실패(네트워크)', 'bad');
        updateSubmitState();
      });
  }

  // =========================
  // License preview logic
  // =========================
  function showExisting() {
    if (!existingBox) return;
    existingBox.classList.remove('d-none');
    if (newBox) newBox.classList.add('d-none');
  }

  function hideExisting() {
    if (!existingBox) return;
    existingBox.classList.add('d-none');
  }

  function showNewPreview(dataUrl) {
    if (!newBox || !newImg) return;
    newImg.src = dataUrl;
    newBox.classList.remove('d-none');
    if (existingBox) existingBox.classList.add('d-none');
  }

  function hideNewPreview() {
    if (!newBox) return;
    newBox.classList.add('d-none');
    if (newImg) newImg.src = '';
  }

  function clearNewFile() {
    if (!licenseInput) return;
    licenseInput.value = '';
    state.hasNewFile = false;
  }

  function onExistingRemoveClick() {
    // 기존 삭제: removeBusinessLicense=true
    state.existingRemoved = true;
    if (removeHidden) removeHidden.value = 'true';

    // 기존 프리뷰 숨김
    hideExisting();

    // 신규가 없으면 필수 조건 위반 상태
    updateSubmitState();
  }

  function onNewRemoveClick() {
    // 신규 선택 취소:
    // - 기존이 있는 경우: 기존으로 복귀
    // - 기존이 없거나 이미 삭제 상태라면: 프리뷰 비우고 필수 위반
    clearNewFile();
    hideNewPreview();

    if (state.hasExisting && !state.existingRemoved) {
      showExisting();
    }
    updateSubmitState();
  }

  function onLicenseInputChange() {
    if (!licenseInput) return;

    var file = licenseInput.files && licenseInput.files[0];
    if (!file) return;

    // 신규 파일 선택 상태
    state.hasNewFile = true;

    // "기존 삭제 플래그"는 신규 업로드로 교체하려는 것과 충돌하므로 false로 되돌림
    // (기존을 '삭제 후 업로드'가 아니라 '교체 업로드'로 처리)
    state.existingRemoved = false;
    if (removeHidden) removeHidden.value = 'false';

    var reader = new FileReader();
    reader.onload = function (e) {
      showNewPreview(e.target.result);
      updateSubmitState();
    };
    reader.readAsDataURL(file);
  }

  // =========================
  // Number input handlers
  // =========================
  function bindPhoneInput() {
    if (!phoneEl) return;

    // 입력 중에는 숫자만 유지하고 하이픈 자동
    phoneEl.addEventListener('input', function () {
      var digits = onlyDigits(phoneEl.value);
      phoneEl.value = formatPhone(digits);
      // 입력 중에는 확정 메시지 초기화(blur에서 체크)
      state.phoneOk = true;
      setHelp(phoneHelp, '', null);
      updateSubmitState();
    });

    phoneEl.addEventListener('blur', function () {
      checkPhoneDup();
    });
  }

  function bindBizInput() {
    if (!bizEl) return;

    // 페이지 로드시 표시 포맷 정리
    (function initBizFormat() {
      var digits = onlyDigits(bizEl.value);
      if (digits) bizEl.value = formatBusinessNumber(digits);
    })();

    bizEl.addEventListener('input', function () {
      var digits = onlyDigits(bizEl.value);
      bizEl.value = formatBusinessNumber(digits);
      state.bizOk = true;
      setHelp(bizHelp, '', null);
      updateSubmitState();
    });

    bizEl.addEventListener('blur', function () {
      // readonly면 굳이 체크하지 않아도 되지만, 값 변경이 가능한 대표일 때는 반드시 체크
      checkBizDup();
    });
  }

  // =========================
  // Daum postcode (기존 기능 유지)
  // =========================
  window.execDaumPostcode = function execDaumPostcode() {
    new daum.Postcode({
      oncomplete: function (data) {
        var fullRoadAddr = data.roadAddress;
        var zonecode = data.zonecode;

        var addrParts = (fullRoadAddr || '').split(' ');
        var doName = addrParts[0] || '';
        var siName = '';
        var guName = '';

        if (addrParts.length >= 2) {
          if (addrParts[1].endsWith('시') || addrParts[1].endsWith('군')) {
            siName = addrParts[1];
            guName = addrParts[2] || '';
          } else {
            siName = '';
            guName = addrParts[1] || '';
          }
        }

        qs('searchAddress').value = fullRoadAddr || '';
        qs('zipCode').value = zonecode || '';
        qs('doName').value = doName;
        qs('siName').value = siName;
        qs('guName').value = guName;

        qs('detailAddress').focus();
      }
    }).open();
  };

  // =========================
  // 추가 배송지 UI (질문 코드 유지)
  // - 기존 제공하신 모달/리스트 방식 그대로 포함 필요
  // =========================
  function escapeHtml(s) {
    return (s || '').toString().replace(/[&<>"']/g, function (m) {
      switch (m) {
        case '&': return '&amp;';
        case '<': return '&lt;';
        case '>': return '&gt;';
        case '"': return '&quot;';
        case "'": return '&#39;';
        default: return m;
      }
    });
  }

  function setupCompanyDeliveryUI() {
    var addBtn = qs('companyAddDeliveryBtn');
    var listEl = qs('companyDeliveryList');
    var hiddenEl = qs('companyDeliveryAddressesJson');
    if (!addBtn || !listEl || !hiddenEl) return;

    var modalEl = qs('companyDeliveryDetailModal');
    var selectedAddrEl = qs('companyDeliverySelectedAddress');
    var detailInputEl = qs('companyDeliveryDetailInput');
    var saveBtn = qs('companyDeliveryDetailSaveBtn');

    var modalInstance = null;
    if (modalEl && window.bootstrap && bootstrap.Modal) {
      modalInstance = bootstrap.Modal.getOrCreateInstance(modalEl, {
        backdrop: 'static',
        keyboard: true
      });
    }

    var pending = null;

    var items = Array.isArray(window.__COMPANY_DELIVERY_ADDRESSES__)
      ? window.__COMPANY_DELIVERY_ADDRESSES__.map(function (x) {
        return {
          id: x.id || null,
          zipCode: x.zipCode || '',
          doName: x.doName || '',
          siName: x.siName || '',
          guName: x.guName || '',
          roadAddress: x.roadAddress || '',
          detailAddress: x.detailAddress || ''
        };
      })
      : [];

    function syncHidden() {
      hiddenEl.value = JSON.stringify(items);
    }

    function render() {
      listEl.innerHTML = '';

      if (!items.length) {
        var empty = document.createElement('div');
        empty.className = 'p-2 border rounded-sm';
        empty.textContent = '등록된 추가 배송지가 없습니다.';
        listEl.appendChild(empty);
        return;
      }

      items.forEach(function (it, idx) {
        var wrap = document.createElement('div');
        wrap.className = 'd-flex align-items-center justify-content-between p-2 mb-1 rounded-sm border';

        var left = document.createElement('div');
        left.className = 'me-2';

        var text = (it.roadAddress || '');
        if (it.detailAddress) text += ' ' + it.detailAddress;

        var prefix = [it.doName, it.siName, it.guName].filter(Boolean).join(' ');
        var display = (prefix ? (prefix + ' ') : '') + text;

        left.innerHTML = '<div class="font-600">' + escapeHtml(display) + '</div>';

        var right = document.createElement('button');
        right.type = 'button';
        right.className = 'btn btn-sm btn-outline-danger';
        right.textContent = 'X';
        right.addEventListener('click', function () {
          items.splice(idx, 1);
          syncHidden();
          render();
        });

        wrap.appendChild(left);
        wrap.appendChild(right);
        listEl.appendChild(wrap);
      });
    }

    function openDetailModal(pendingItem) {
      pending = pendingItem;

      if (selectedAddrEl) {
        var prefix = [pending.doName, pending.siName, pending.guName].filter(Boolean).join(' ');
        var base = (prefix ? (prefix + ' ') : '') + (pending.roadAddress || '');
        selectedAddrEl.innerHTML = escapeHtml(base);
      }

      if (detailInputEl) detailInputEl.value = '';

      if (modalInstance) {
        modalInstance.show();
        setTimeout(function () {
          if (detailInputEl) detailInputEl.focus();
        }, 150);
        return;
      }

      alert('모달 라이브러리가 없어 상세주소 모달을 열 수 없습니다. (bootstrap 미로드)');
    }

    function addPendingToItems(detailText) {
      if (!pending) return;

      var newItem = {
        id: null,
        zipCode: pending.zipCode || '',
        doName: pending.doName || '',
        siName: pending.siName || '',
        guName: pending.guName || '',
        roadAddress: pending.roadAddress || '',
        detailAddress: (detailText || '').trim()
      };

      if (!newItem.roadAddress) {
        pending = null;
        return;
      }

      var dup = items.some(function (x) {
        return (x.roadAddress === newItem.roadAddress) && (x.detailAddress === newItem.detailAddress);
      });
      if (dup) {
        alert('이미 추가된 배송지입니다.');
        pending = null;
        return;
      }

      items.push(newItem);
      pending = null;
      syncHidden();
      render();
    }

    function openDaumAndAdd() {
      new daum.Postcode({
        oncomplete: function (data) {
          var road = (data.userSelectedType === 'R') ? data.roadAddress : data.jibunAddress;

          var pendingItem = {
            zipCode: data.zonecode || '',
            doName: data.sido || '',
            siName: data.sigungu || '',
            guName: data.bname || '',
            roadAddress: road || ''
          };

          if (!pendingItem.roadAddress) return;
          openDetailModal(pendingItem);
        }
      }).open();
    }

    if (saveBtn) {
      saveBtn.addEventListener('click', function () {
        var detailText = detailInputEl ? detailInputEl.value : '';
        addPendingToItems(detailText);
        if (modalInstance) modalInstance.hide();
      });
    }

    if (modalEl) {
      modalEl.addEventListener('hidden.bs.modal', function () {
        pending = null;
        if (detailInputEl) detailInputEl.value = '';
        if (selectedAddrEl) selectedAddrEl.innerHTML = '';
      });
    }

    addBtn.addEventListener('click', openDaumAndAdd);

    syncHidden();
    render();
  }

  // 대표 코드 생성(기존 유지)
  window.generateKey = function generateKey() {
    fetch('/customer/generateRegistrationKey', { method: 'POST' })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        alert('새 코드가 생성되었습니다: ' + data.key);
        location.reload();
      });
  };

  // =========================
  // Init
  // =========================
  document.addEventListener('DOMContentLoaded', function () {
    bindPhoneInput();
    bindBizInput();
    setupCompanyDeliveryUI();

    // 초기 중복체크 메시지는 비워두되, 제출 상태는 계산
    // 사업자등록증 초기 상태
    if (isRepresentative) {
      state.hasExisting = !!existingLicenseUrl;
      state.existingRemoved = false;
      state.hasNewFile = false;

      // 기존 URL 없으면 existing 숨김
      if (!existingLicenseUrl && existingBox) existingBox.classList.add('d-none');

      if (licenseInput) {
        licenseInput.addEventListener('change', onLicenseInputChange);
      }
      if (existingRemoveBtn) {
        existingRemoveBtn.addEventListener('click', onExistingRemoveClick);
      }
      if (newRemoveBtn) {
        newRemoveBtn.addEventListener('click', onNewRemoveClick);
      }
    }

    updateSubmitState();
  });

})();
