(() => {
  'use strict';
  const S = window.PMS;
  const root = '/customer/store-preview';
  const prefix = 'hb-preview:' + document.body.dataset.storeUser + ':';
  const main = document.querySelector('#hb-main');
  const searchBox = document.querySelector('#store-search');
  const form = searchBox.querySelector('form');
  const recent = document.querySelector('#store-recent');
  const esc = S.e;
  const won = value => Number(value || 0).toLocaleString('ko-KR', {maximumFractionDigits: 2}) + '원';
  const token = document.body.dataset.storeToken;
  const stored = (name, fallback, storage = localStorage) => {
    try { return JSON.parse(storage.getItem(prefix + name)) ?? fallback; }
    catch (_) { return fallback; }
  };
  const write = (name, value, storage = localStorage) => {
    storage.setItem(prefix + name, JSON.stringify(value));
  };
  const cart = () => stored('cart', []);
  const params = () => new URLSearchParams(location.search);
  const icons = {
    cart: '<path d="M3 3h2l2 12h12l2-8H6m3 13h.01m8 0h.01"/>',
    interest: '<path d="M20.8 8.5c0 4.5-8.8 9.5-8.8 9.5S3.2 13 3.2 8.5C3.2 4.4 8.8 3.2 12 7c3.2-3.8 8.8-2.6 8.8 1.5Z"/>',
    bookmark: '<path d="M6 4h12v16l-6-4-6 4V4Z"/>',
    order: '<path d="M4 12h15m-6-6 6 6-6 6"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>'
  };
  const icon = key => '<svg viewBox="0 0 24 24" aria-hidden="true">' + icons[key] + '</svg>';
  const button = (key, text, primary = false) => '<button type="button" class="store-btn' +
    (primary ? ' is-primary' : '') + '" data-action="' + key + '">' + icon(key) + esc(text) + '</button>';

  S.base = root + '/api';
  function replaceAssetUrls(data) {
    if (typeof data === 'string' && data.startsWith('/admin/api/product-master/studio/assets/')) {
      return data.replace('/admin/api/product-master/studio/assets/', S.base + '/assets/');
    }
    if (Array.isArray(data)) return data.map(replaceAssetUrls);
    if (data && typeof data === 'object') {
      return Object.fromEntries(Object.entries(data).map(([k, v]) => [k, replaceAssetUrls(v)]));
    }
    return data;
  }
  S.api = async (path, method = 'GET', body, signal) => {
    const headers = {Accept: 'application/json', 'X-Store-Token': token};
    const options = {method, credentials: 'same-origin', headers, signal: signal || AbortSignal.timeout(40000)};
    if (body instanceof FormData) options.body = body;
    else if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body);
    }
    const response = await fetch(path, options);
    const content = await response.json().catch(() => ({}));
    if (!response.ok || content.success === false || response.redirected) {
      const error = new Error(content.message || '요청을 처리하지 못했습니다. 다시 시도해 주세요.');
      error.status = response.status;
      throw error;
    }
    return replaceAssetUrls(content.data ?? content);
  };
  const call = (path, method, body) => S.api(S.base + path, method, body);

  function feedback(text, error = false) {
    let node = document.querySelector('#store-feedback');
    if (!node) {
      node = document.createElement('p');
      node.id = 'store-feedback';
      node.setAttribute('role', 'status');
      document.body.append(node);
    }
    node.textContent = text;
    node.classList.toggle('is-error', error);
    node.hidden = false;
    clearTimeout(feedback.timer);
    feedback.timer = setTimeout(() => { node.hidden = true; }, 5000);
  }

  function bindButtons(host, callback) {
    host.querySelectorAll('button[data-action]').forEach(el => {
      el.onclick = async () => {
        el.disabled = true;
        try { await callback(el.dataset.action); }
        catch (error) { feedback(error.message, true); }
        finally { el.disabled = false; }
      };
    });
  }
  const quote = (selection, bookmark = false) => call('/item', 'POST', {selection, bookmark});
  const check = (item, bookmark = false) => call('/item/check', 'POST', {item, bookmark});
  const save = (item, kind, note = '') => call('/saved', 'POST', {item, kind, note});

  function cartAdd(items) {
    const existing = cart();
    for (const item of items) {
      if (existing.some(record => record.item.stamp === item.stamp)) continue;
      if (existing.length >= 100) throw new Error('장바구니에는 최대 100개 구성을 담을 수 있습니다.');
      existing.push({key: crypto.randomUUID(), item, quantity: 1, fromCart: true});
    }
    write('cart', existing);
    feedback('장바구니에 담았습니다.');
  }
  function startCheckout(lines) {
    write('checkout', lines, sessionStorage);
    location.href = root + '?view=checkout';
  }
  function actions(host, selection, ready, confirmationMessage) {
    const wrapper = document.createElement('div');
    wrapper.className = 'store-actions store-product-actions';
    wrapper.innerHTML = button('order', '바로발주', true) + button('cart', '장바구니') +
      button('interest', '관심상품');
    host.append(wrapper);
    if (!ready) {
      wrapper.querySelectorAll('button').forEach(el => { el.disabled = true; });
      return;
    }
    bindButtons(wrapper, async action => {
      const message = confirmationMessage?.trim();
      if ((action === 'order' || action === 'cart') && message && !window.confirm(message)) return;
      const item = await quote(selection);
      if (action === 'cart') cartAdd([item]);
      if (action === 'interest') { await save(item, 'interest'); feedback('회사 관심상품에 저장했습니다.'); }
      if (action === 'order') startCheckout([{key: crypto.randomUUID(), item, quantity: 1}]);
    });
  }
  function bookmark(host, selection) {
    const wrapper = document.createElement('div');
    wrapper.className = 'store-actions store-bookmark';
    wrapper.innerHTML = button('bookmark', '즐겨찾기') +
      '<form hidden><label>남김말 <input name="note" maxlength="300" placeholder="다음에 이어서 볼 내용"></label>' +
      '<button class="store-btn is-primary" type="submit">저장</button></form>';
    host.append(wrapper);
    const trigger = wrapper.querySelector('[data-action]');
    trigger.disabled = !Object.keys(selection.answers).length;
    trigger.onclick = () => {
      const panel = wrapper.querySelector('form');
      panel.hidden = !panel.hidden;
      if (!panel.hidden) panel.elements.note.focus();
    };
    wrapper.querySelector('form').onsubmit = async event => {
      event.preventDefault();
      const panel = event.currentTarget;
      const submit = panel.querySelector('[type=submit]');
      submit.disabled = true;
      try {
        await save(await quote(selection, true), 'bookmark', panel.elements.note.value);
        panel.hidden = true;
        feedback('회사 즐겨찾기에 저장했습니다.');
      } catch (error) { feedback(error.message, true); }
      finally { submit.disabled = false; }
    };
  }

  function copySearch(url) {
    for (const key of ['subject', 'keyword']) {
      if (params().get(key)) url.searchParams.set(key, params().get(key));
    }
  }
  function catalogQuery(kind) {
    const url = new URL(root, location.origin);
    copySearch(url);
    url.searchParams.set('nonStandard', String(kind));
    return url.searchParams;
  }
  function remember(term) {
    const list = stored('recent', []).filter(x => x.keyword !== term.keyword || x.subject !== term.subject);
    write('recent', [term, ...list].slice(0, 10));
  }
  form.addEventListener('submit', () => {
    const keyword = form.elements.keyword.value.trim();
    form.elements.keyword.value = keyword;
    if (keyword) remember({keyword, subject: form.elements.subject.value, date: new Date().toISOString()});
  });
  function search(show, kind, resultCount) {
    searchBox.hidden = !show;
    if (!show) return;
    form.elements.kind.value = kind ? 'custom' : '';
    const keyword = params().get('keyword')?.trim() || '';
    const subject = params().get('subject') === 'series' ? 'series' : 'name';
    form.elements.keyword.value = keyword;
    form.elements.subject.value = subject;
    const state = document.querySelector('#store-search-state');
    state.hidden = !keyword;
    state.querySelector('p').textContent = keyword
      ? (subject === 'series' ? '시리즈' : '제품명') + ' · ' + keyword + ' · ' + (resultCount ?? 0) + '개 제품'
      : '';
    state.querySelector('a').href = root + (kind ? '?kind=custom' : '');
    const list = stored('recent', []);
    recent.hidden = !list.length;
    recent.querySelector('ul').replaceChildren(...list.map((term, index) => {
      const entry = document.createElement('li');
      const url = new URL(root, location.origin);
      if (kind) url.searchParams.set('kind', 'custom');
      url.searchParams.set('subject', term.subject);
      url.searchParams.set('keyword', term.keyword);
      entry.innerHTML = '<a href="' + esc(url.pathname + url.search) + '"><time>' +
        esc(new Date(term.date).toLocaleDateString('ko-KR')) + '</time><span>' +
        (term.subject === 'series' ? '시리즈' : '제품명') + '</span><strong>' + esc(term.keyword) +
        '</strong></a><button type="button" aria-label="최근 검색어 삭제">' + icon('close') + '</button>';
      entry.querySelector('a').onclick = () => remember({...term, date: new Date().toISOString()});
      entry.querySelector('button').onclick = () => {
        write('recent', list.filter((_, i) => i !== index));
        search(true, kind, resultCount);
      };
      return entry;
    }));
  }
  function takeRestore(id) {
    const item = stored('restore', null, sessionStorage);
    if (!item || (item.custom ? item.selection.productId : item.selection.seriesId) !== id) return null;
    sessionStorage.removeItem(prefix + 'restore');
    return item;
  }
  function removeCart(keys) {
    const set = new Set(keys);
    write('cart', cart().filter(row => !set.has(row.key)));
  }
  const img = item => '<img src="' + esc(item.image) + '" alt="" loading="lazy">';
  function shell(title, body) {
    main.innerHTML = '<section class="store-page"><header><h1>' + esc(title) + '</h1></header>' + body + '</section>';
  }
  function restore(row) {
    write('restore', row.item, sessionStorage);
    const cfg = row.item.selection;
    location.href = root + (row.item.custom ? '/products/' + cfg.productId + '?kind=custom' : '/series/' + cfg.seriesId);
  }
  async function verifyRow(row, viewName) {
    try { return {...row, item: await check(row.item, viewName === 'bookmark')}; }
    catch (error) {
      if (error.status !== 409) throw error;
      if (viewName === 'cart') removeCart([row.key]);
      else await call('/saved/' + row.id, 'DELETE');
      feedback(error.message, true);
      return null;
    }
  }
  function emptyList(title, description, destination = root, label = '쇼핑몰 둘러보기', symbol = 'cart') {
    return '<section class="store-empty">' + icon(symbol) + '<h2>' + esc(title) + '</h2><p>' +
      esc(description) + '</p><a class="store-btn" href="' + esc(destination) + '">' + esc(label) + '</a></section>';
  }
  let listObserver;
  async function listPage(mode, limit = 10, selectedKeys = new Set()) {
    listObserver?.disconnect();
    const names = {cart: '장바구니', interest: '관심상품', bookmark: '즐겨찾기'};
    const rows = mode === 'cart' ? cart() : (await call('/saved')).filter(row => row.kind === mode);
    let displayed = Math.min(limit, rows.length);
    const keyOf = row => mode === 'cart' ? row.key : row.id;
    const hints = {
      cart: '제품 옵션을 구성한 뒤 장바구니에 담아 주세요.',
      interest: '관심 있는 구성을 제품 상세에서 저장해 주세요.',
      bookmark: '비규격 제품을 구성하다가 즐겨찾기에 저장해 주세요.'
    };
    const renderRow = (row, i) => {
      const details = [
        mode === 'cart' ? '수량 ' + (row.quantity || 1) + '개' : '',
        row.item.series,
        row.createdAt && new Date(row.createdAt).toLocaleString('ko-KR'),
        row.note
      ].filter(Boolean);
      const options = row.item.summary || [];
      return '<article class="store-row store-saved-row">' +
        (mode === 'bookmark' ? '' : '<input type="checkbox" data-index="' + i + '" aria-label="품목 선택">') +
        img(row.item) + '<div><button type="button" class="store-link" data-open="' + i + '">' + esc(row.item.name) +
        '</button><div>' + (row.item.price == null ? '' : won(row.item.price)) + '</div>' +
        (details.length || options.length ? '<details class="store-row-details"' +
          '><summary>상세 정보</summary><div><small>' + esc(details.join(' · ')) + '</small>' +
          (options.length ? '<ul>' + options.map(text => '<li>' + esc(text) + '</li>').join('') + '</ul>' : '') +
          '</div></details>' : '') +
        '</div><button class="store-icon" type="button" data-remove="' + i +
        '" aria-label="삭제">' + icon('close') + '</button></article>';
    };
    shell(names[mode], rows.length
      ? '<div class="store-list"></div><div class="store-more"><p role="status"></p>' +
        '<button type="button" class="store-btn" hidden>더보기</button></div>'
      : emptyList('저장된 ' + names[mode] + ' 항목이 없습니다', hints[mode], root, '쇼핑몰 둘러보기', mode));
    if (!rows.length) return;

    const host = main.querySelector('.store-page');
    const list = host.querySelector('.store-list');
    const footer = host.querySelector('.store-more');
    const more = footer.querySelector('button');
    const status = footer.querySelector('p');
    let updateAll = () => {};
    let bindCheck = () => {};
    if (mode !== 'bookmark' && rows.length) {
      const bar = document.createElement('div');
      bar.className = 'store-actions store-list-actions';
      bar.innerHTML = '<label><input type="checkbox" id="store-select-all"> 표시된 품목 전체 선택</label>' +
        button('order', mode === 'interest' ? '바로 발주하기' : '발주하기', true) +
        (mode === 'interest' ? button('cart', '장바구니 담기') : '');
      host.querySelector('header').after(bar);
      const all = bar.querySelector('#store-select-all');
      const checks = () => [...list.querySelectorAll('[data-index]')];
      updateAll = () => {
        const visibleChecks = checks();
        all.checked = visibleChecks.length > 0 && visibleChecks.every(el => el.checked);
        all.indeterminate = !all.checked && visibleChecks.some(el => el.checked);
      };
      const updateSelection = el => {
        const key = keyOf(rows[Number(el.dataset.index)]);
        if (el.checked) selectedKeys.add(key);
        else selectedKeys.delete(key);
      };
      all.onchange = () => {
        checks().forEach(el => { el.checked = all.checked; updateSelection(el); });
        updateAll();
      };
      bindCheck = el => {
        if (!el) return;
        el.checked = selectedKeys.has(keyOf(rows[Number(el.dataset.index)]));
        el.onchange = () => { updateSelection(el); updateAll(); };
      };
      bindButtons(bar, async action => {
        const selected = rows.slice(0, displayed).filter(row => selectedKeys.has(keyOf(row)));
        if (!selected.length) throw new Error('품목을 선택해 주세요.');
        const valid = [];
        for (let offset = 0; offset < selected.length; offset += 4) {
          const batch = await Promise.all(selected.slice(offset, offset + 4).map(row => verifyRow(row, mode)));
          valid.push(...batch.filter(Boolean));
        }
        if (valid.length !== selected.length) { await listPage(mode, displayed, selectedKeys); return; }
        if (action === 'cart') cartAdd(valid.map(row => row.item));
        else startCheckout(valid.map(row => ({key: row.key || crypto.randomUUID(), item: row.item,
          quantity: row.quantity || 1, fromCart: mode === 'cart', savedId: mode === 'interest' ? row.id : undefined})));
      });
    }
    function appendRows(from, to) {
      const template = document.createElement('template');
      template.innerHTML = rows.slice(from, to).map((row, i) => renderRow(row, from + i)).join('');
      template.content.querySelectorAll('[data-index]').forEach(bindCheck);
      if (from > 0) template.content.querySelectorAll('.store-saved-row').forEach((row, index) => {
        row.classList.add('store-row-enter');
        row.style.setProperty('--store-enter-delay', Math.min(index, 4) * 18 + 'ms');
      });
      list.append(template.content);
      status.textContent = '전체 ' + rows.length + '개 중 ' + to + '개 표시';
      more.hidden = to >= rows.length;
      updateAll();
    }
    list.addEventListener('click', async event => {
      const action = event.target.closest('[data-remove],[data-open]');
      if (!action || action.disabled) return;
      action.disabled = true;
      try {
        if (action.hasAttribute('data-remove')) {
          const row = rows[Number(action.dataset.remove)];
          if (mode === 'cart') removeCart([row.key]);
          else await call('/saved/' + row.id, 'DELETE');
          selectedKeys.delete(keyOf(row));
          await listPage(mode, displayed, selectedKeys);
        } else {
          const row = await verifyRow(rows[Number(action.dataset.open)], mode);
          if (row) restore(row);
          else await listPage(mode, displayed, selectedKeys);
        }
      } catch (error) { feedback(error.message, true); }
      finally { action.disabled = false; }
    });
    const loadMore = manual => {
      if (!host.isConnected || displayed >= rows.length) return;
      const start = displayed;
      displayed = Math.min(start + 10, rows.length);
      appendRows(start, displayed);
      if (manual) list.querySelector('[data-open="' + start + '"]')?.focus();
      if (displayed >= rows.length) listObserver?.disconnect();
    };
    appendRows(0, displayed);
    more.onclick = () => loadMore(true);
    if (!more.hidden && 'IntersectionObserver' in window) {
      listObserver = new IntersectionObserver(entries => {
        if (entries.some(entry => entry.isIntersecting)) loadMore(false);
      });
      listObserver.observe(more);
    }
  }

  async function checkoutPage(previous = new Map()) {
    const rows = stored('checkout', [], sessionStorage);
    if (!rows.length) { shell('발주하기', emptyList('선택한 발주 품목이 없습니다', '장바구니에서 발주할 품목을 선택해 주세요.', root + '?view=cart', '장바구니로 이동')); return; }
    const options = await call('/checkout');
    if (!options.methods.length) {
      shell('발주하기', emptyList('등록된 배송수단이 없습니다',
        '관리자가 배송수단을 등록하면 발주를 진행할 수 있습니다. 선택한 품목은 유지됩니다.',
        root + '?view=cart', '장바구니로 이동'));
      return;
    }
    const methods = '<option value="">배송수단 선택</option>' + options.methods.map(m =>
      '<option value="' + esc(m.id) + '">' + esc(m.name) + '</option>').join('');
    const shipping = '<label>배송수단<select name="method" required>' + methods + '</select></label>';
    const address = '<label>배송 주소<input name="address" maxlength="400" required autocomplete="street-address"></label>';
    const date = '<label>희망 출고일<input type="text" name="date" placeholder="날짜 선택" required readonly></label>';
    const dates = rows.map(row => row.item.earliest);
    const latest = dates.every(Boolean) ? dates.sort().at(-1) : null;
    shell('발주하기', '<details class="store-validation"><summary>검증용 텍스트 · 발주 규칙</summary>' +
      '<p>오늘을 제외한 영업일로 계산합니다. 예를 들어 1.5일은 2영업일로 올립니다. 주말과 등록된 공휴일은 선택할 수 없습니다. 일괄 출고일은 품목별 가능일 중 가장 늦은 날짜부터 선택할 수 있습니다.</p>' +
      '<p>희망 출고일과 배송수단이 같으면 배송비와 포장비를 각각 한 번 부과합니다. 포인트는 상품금액에만 적용됩니다. 발주 결과는 저장되지 않습니다.</p></details>' +
      '<button type="button" class="store-btn" id="store-bulk-open">배송 정보 일괄 적용</button>' +
      '<dialog class="store-bulk store-bulk-dialog" aria-labelledby="store-bulk-title"><h2 id="store-bulk-title">배송 정보 일괄 적용</h2>' +
      '<p>입력한 항목만 모든 품목에 적용합니다.</p><div class="store-fields">' + address + date + shipping +
      '</div><p class="store-bulk-warning" role="status"></p><div class="store-actions"><button type="button" class="store-btn" id="store-bulk-close">닫기</button>' +
      '<button type="button" class="store-btn is-primary" id="store-apply">적용</button></div></dialog>' +
      '<form id="store-checkout"><div class="store-list">' + rows.map(row => '<article class="store-check-line"><div class="store-row">' +
      img(row.item) + '<div><strong>' + esc(row.item.name) + '</strong><div>' + won(row.item.price) +
      ' · 최초 가능일 ' + esc(row.item.earliest || '미등록') + '</div></div><span class="store-line-status"></span></div><details class="store-row-details store-check-details"><summary>수량 및 배송 정보</summary><div class="store-fields">' +
      '<label>수량<input type="number" name="quantity" min="1" max="99999" required value="' + esc(row.quantity || 1) + '"></label>' +
      address + date + shipping + '</div><small class="store-method-note" aria-live="polite"></small></details></article>').join('') +
      '</div><section class="store-payment"><h2>결제</h2><p>계좌결제 · 검증용</p><label>사용 포인트 <input type="number" name="points" value="0" min="0" max="' +
      esc(options.points) + '" required></label><small>보유 ' + won(options.points) + ' 상당</small></section>' +
      '<button type="submit" class="store-btn is-primary">발주하기</button></form>');
    const form = main.querySelector('#store-checkout');
    const lines = [...form.querySelectorAll('.store-check-line')];
    const bulk = main.querySelector('.store-bulk');
    const openBulk = main.querySelector('#store-bulk-open');
    main.querySelector('#store-bulk-close').onclick = () => bulk.close();
    openBulk.onclick = () => bulk.showModal();
    bulk.addEventListener('close', () => openBulk.focus());
    const holidays = new Set(options.holidays);
    const pick = (node, earliest) => {
      const input = node.querySelector('[name=date]');
      window.flatpickr(input, {dateFormat: 'Y-m-d', minDate: earliest || undefined, disableMobile: true, appendTo: node === bulk ? bulk : undefined,
        disable: [day => {
          const ym = [day.getFullYear(), day.getMonth() + 1, day.getDate()].map((n, i) =>
            i ? String(n).padStart(2, '0') : String(n)).join('-');
          return !earliest || day.getDay() === 0 || day.getDay() === 6 || holidays.has(ym);
        }]});
    };
    pick(bulk, latest);
    const updateLineStatus = line => {
      const addressInput = line.querySelector('[name=address]');
      const methodInput = line.querySelector('[name=method]');
      const dateInput = line.querySelector('[name=date]');
      const quantityInput = line.querySelector('[name=quantity]');
      const complete = Boolean(addressInput.value.trim() && methodInput.value && dateInput.value && quantityInput.validity.valid);
      const status = line.querySelector('.store-line-status');
      status.textContent = complete ? '입력 완료' : '배송 정보 입력 필요';
      status.classList.toggle('is-complete', complete);
    };
    form.addEventListener('input', event => {
      const line = event.target.closest('.store-check-line');
      if (line) updateLineStatus(line);
    });
    form.addEventListener('change', event => {
      const line = event.target.closest('.store-check-line');
      if (line) updateLineStatus(line);
    });
    lines.forEach((line, i) => {
      pick(line, rows[i].item.earliest);
      const method = line.querySelector('[name=method]');
      method.onchange = () => {
        const chosen = options.methods.find(value => String(value.id) === method.value);
        line.querySelector('.store-method-note').textContent = chosen ?
          [chosen.description, '배송비 ' + won(chosen.deliveryFee), '포장비 ' + won(chosen.packingFee)].filter(Boolean).join(' · ') : '';
      };
      const draft = previous.get(rows[i].key);
      if (draft) {
        line.querySelector('[name=address]').value = draft.address;
        line.querySelector('[name=quantity]').value = draft.quantity;
        line.querySelector('[name=date]')._flatpickr.setDate(draft.date, true);
        method.value = String(draft.methodId);
        method.onchange();
      }
      updateLineStatus(line);
    });
    main.querySelector('#store-apply').onclick = () => {
      const updates = ['address', 'date', 'method']
        .map(name => [name, bulk.querySelector('[name=' + name + ']').value.trim()])
        .filter(([, value]) => value);
      if (!updates.length) {
        bulk.querySelector('[name=address]').focus();
        bulk.querySelector('.store-bulk-warning').textContent = '적용할 배송 정보를 한 가지 이상 입력해 주세요.';
        return;
      }
      bulk.querySelector('.store-bulk-warning').textContent = '';
      for (const [name, value] of updates) {
        for (const line of lines) {
          const target = line.querySelector('[name=' + name + ']');
          if (name === 'date') target._flatpickr.setDate(value, true);
          else { target.value = value; target.dispatchEvent(new Event('change')); }
        }
      }
      lines.forEach(updateLineStatus);
      bulk.close();
      feedback('입력한 항목을 전체 품목에 적용했습니다.');
    };
    form.addEventListener('invalid', event => {
      const details = event.target.closest('.store-check-details');
      if (details) details.open = true;
    }, true);
    form.onsubmit = async event => {
      event.preventDefault();
      const submit = form.querySelector('button[type=submit]');
      submit.disabled = true;
      const payload = lines.map((line, i) => {
        const get = field => line.querySelector('[name=' + field + ']').value;
        return {...rows[i], quantity: Number(get('quantity')), address: get('address'),
          date: get('date'), methodId: Number(get('method'))};
      });
      try {
        const missingDate = payload.findIndex(row => !row.date);
        if (missingDate >= 0) {
          const line = lines[missingDate];
          line.querySelector('details').open = true;
          line.querySelector('[name=date]').focus();
          throw new Error('품목별 희망 출고일을 모두 선택해 주세요.');
        }
        const result = await call('/checkout', 'POST', {lines: payload.map(({key, item, quantity, address, date, methodId}) =>
          ({key, item, quantity, address, date, methodId})), points: Number(form.elements.points.value)});
        resultPage(result);
        const used = rows.filter(row => row.fromCart).map(row => row.key);
        if (used.length) removeCart(used);
        sessionStorage.removeItem(prefix + 'checkout');
      } catch (error) {
        if (error.status === 409) {
          const invalid = new Set();
          for (const row of rows) {
            try { await check(row.item); }
            catch (cause) {
              if (cause.status === 409) {
                invalid.add(row.key);
                if (row.fromCart) removeCart([row.key]);
                if (row.savedId) await call('/saved/' + row.savedId, 'DELETE');
              }
            }
          }
          if (invalid.size) {
            write('checkout', rows.filter(row => !invalid.has(row.key)), sessionStorage);
            await checkoutPage(new Map(payload.map(row => [row.key, row])));
          }
        }
        feedback(error.message, true);
      } finally { submit.disabled = false; }
    };
  }
  function resultPage(result) {
    main.querySelectorAll('[name=date]').forEach(input => input._flatpickr?.destroy());
    shell('발주 검증 결과', '<p class="store-validation">검증용 텍스트 · 결과가 저장되지 않으며 결제와 포인트 차감도 없습니다. 묶음 배송비와 포장비는 각 조합의 첫 품목에 표시합니다.</p>' +
      '<div class="store-list">' + result.lines.map(line => '<article class="store-row store-result">' + img(line.item) +
      '<div><strong>' + esc(line.item.name) + '</strong><p>' + esc(line.address) + '</p><p>' +
      esc(line.date) + ' · ' + esc(line.method) + '</p><p>상품 ' + won(line.item.price) + ' × ' +
      esc(line.quantity) + '개 · 배송비 ' + won(line.delivery) + ' · 포장비 ' + won(line.packing) + '</p></div></article>').join('') +
      '</div><dl class="store-totals"><dt>상품금액</dt><dd>' + won(result.products) +
      '</dd><dt>배송비</dt><dd>' + won(result.delivery) + '</dd><dt>포장비</dt><dd>' + won(result.packing) +
      '</dd><dt>검증용 포인트</dt><dd>−' + won(result.points) + '</dd><dt>최종 금액</dt><dd>' +
      won(result.payable) + '</dd></dl><a class="store-btn" href="' + root + '">컬렉션으로</a>');
    main.focus({preventScroll: true});
    window.scrollTo({top: 0, behavior: 'smooth'});
  }
  async function view(mode) {
    if (!['cart', 'interest', 'bookmark', 'checkout'].includes(mode)) return false;
    search(false);
    document.querySelectorAll('.hb-header-end a').forEach(link => {
      if (new URL(link.href).searchParams.get('view') === mode) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });
    if (mode === 'checkout') await checkoutPage();
    else await listPage(mode);
    return true;
  }
  window.StorePreview = {actions, bookmark, catalogQuery, copySearch, search, takeRestore, view};
})();
