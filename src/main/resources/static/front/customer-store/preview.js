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
    if (name === 'cart') window.dispatchEvent(new Event('store:cart-changed'));
  };
  const cart = () => stored('cart', []);
  const params = () => new URLSearchParams(location.search);
  const icons = {
    cart: '<path d="M3 3h2l2 12h12l2-8H6m3 13h.01m8 0h.01"/>',
    interest: '<path d="M20.8 8.5c0 4.5-8.8 9.5-8.8 9.5S3.2 13 3.2 8.5C3.2 4.4 8.8 3.2 12 7c3.2-3.8 8.8-2.6 8.8 1.5Z"/>',
    bookmark: '<path d="M6 4h12v16l-6-4-6 4V4Z"/>',
    order: '<path d="M4 12h15m-6-6 6 6-6 6"/>',
    reset: '<path d="M3 10a9 9 0 1 1 1.8 7.4M3 4v6h6"/>',
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
    const content = await response.json().catch(() => null);
    if (!response.ok || !content || content.success === false || response.redirected) {
      const error = new Error(content?.message || (response.redirected || response.status === 401 || response.status === 403
        ? '로그인 상태가 변경되었습니다. 고객 계정으로 다시 로그인해 주세요.'
        : '서버 응답을 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.'));
      error.status = response.status;
      throw error;
    }
    return replaceAssetUrls(content.data ?? content);
  };
  const call = async (path, method, body) => {
    const data = await S.api(S.base + path, method, body);
    if ((method === 'POST' && path === '/saved') || (method === 'DELETE' && path.startsWith('/saved/'))) {
      window.dispatchEvent(new Event('store:saved-changed'));
    }
    return data;
  };

  const reduceMotion = () => document.body.dataset.motion === 'reduced' ||
    matchMedia('(prefers-reduced-motion: reduce)').matches;
  const detailsAnimations = new WeakMap();
  document.addEventListener('click', event => {
    const summary = event.target.closest('details > summary:first-child');
    if (!summary || event.target.closest('a, button, input, select, textarea') || event.defaultPrevented) return;
    const details = summary.parentElement;
    const previous = detailsAnimations.get(details);
    if (reduceMotion() || !details.animate) {
      if (previous) {
        previous.animation.cancel();
        detailsAnimations.delete(details);
        details.open = previous.open;
      }
      return;
    }
    event.preventDefault();
    const start = details.getBoundingClientRect().height;
    const open = !(previous?.open ?? details.open);
    previous?.animation.cancel();
    details.open = open;
    const end = details.getBoundingClientRect().height;
    details.open = true;
    const animation = details.animate(
      [{height: start + 'px', boxSizing: 'border-box', overflow: 'clip'},
       {height: end + 'px', boxSizing: 'border-box', overflow: 'clip'}],
      {duration: 260, easing: 'cubic-bezier(.22,.61,.36,1)', fill: 'both'}
    );
    detailsAnimations.set(details, {animation, open});
    animation.onfinish = () => {
      details.open = open;
      animation.cancel();
      detailsAnimations.delete(details);
    };
  });

  function feedback(text, error = false, showCart = false) {
    let node = document.querySelector('#store-feedback');
    if (!node) {
      node = document.createElement('p');
      node.id = 'store-feedback';
      node.setAttribute('role', 'status');
      document.body.append(node);
    }
    node.textContent = text;
    if (showCart) {
      const shortcut = document.createElement('a');
      shortcut.href = root + '?view=cart';
      shortcut.textContent = '장바구니 보기';
      node.append(' ', shortcut);
    }
    node.classList.toggle('is-error', error);
    node.hidden = false;
    clearTimeout(feedback.timer);
    feedback.timer = setTimeout(() => { if (!node.matches(':focus-within')) node.hidden = true; }, showCart ? 8500 : 5000);
  }

  function bindButtons(host, callback, onSettled) {
    host.querySelectorAll('button[data-action]').forEach(el => {
      el.onclick = async () => {
        el.dataset.busy = 'true';
        el.disabled = true;
        try { await callback(el.dataset.action); }
        catch (error) { feedback(error.message, true); }
        finally {
          delete el.dataset.busy;
          el.disabled = false;
          onSettled?.();
        }
      };
    });
  }
  const quote = (selection, bookmark = false) => call('/item', 'POST', {selection, bookmark});
  const check = (item, bookmark = false) => call('/item/check', 'POST', {item, bookmark});
  const save = (item, kind, note = '') => call('/saved', 'POST', {item, kind, note});

  function cartAdd(items) {
    const existing = cart();
    const originalCount = existing.length;
    for (const item of items) {
      if (existing.some(record => record.item.stamp === item.stamp)) continue;
      if (existing.length >= 100) throw new Error('장바구니에는 최대 100개 구성을 담을 수 있습니다.');
      existing.push({key: crypto.randomUUID(), item, quantity: 1, fromCart: true});
    }
    const added = existing.length - originalCount;
    if (!added) {
      feedback('이미 장바구니에 담긴 구성입니다.');
      return;
    }
    write('cart', existing);
    const skipped = items.length - added;
    feedback(skipped ? added + '개 담았습니다. 이미 담긴 구성 ' + skipped + '개는 제외했습니다.' : '장바구니에 담았습니다.', false, true);
  }
  function startCheckout(lines) {
    write('checkout', lines, sessionStorage);
    location.href = root + '?view=checkout';
  }
  function actions(host, selection, ready, confirmationMessage, reset) {
    const wrapper = document.createElement('div');
    wrapper.className = 'store-actions store-product-actions';
    wrapper.innerHTML = button('order', '바로발주', true) + button('cart', '장바구니') +
      button('interest', '관심상품');
    host.append(wrapper);
    if (!ready) {
      wrapper.querySelectorAll('button').forEach(el => { el.disabled = true; });
    } else bindButtons(wrapper, async action => {
      const message = confirmationMessage?.trim();
      if ((action === 'order' || action === 'cart') && message && !window.confirm(message)) return;
      const item = await quote(selection);
      if (action === 'cart') cartAdd([item]);
      if (action === 'interest') { await save(item, 'interest'); feedback('회사 관심상품에 저장했습니다.'); }
      if (action === 'order') startCheckout([{key: crypto.randomUUID(), item, quantity: 1}]);
    });
    if (reset) {
      wrapper.insertAdjacentHTML('beforeend', button('reset', '옵션 다시 구성하기'));
      wrapper.querySelector('[data-action="reset"]').onclick = reset;
    }
  }
  function bookmark(host, selection) {
    const wrapper = document.createElement('div');
    wrapper.className = 'store-actions store-bookmark';
    const noteId = 'store-bookmark-note-' + crypto.randomUUID();
    wrapper.innerHTML = button('bookmark', '즐겨찾기') +
      '<form hidden><label for="' + noteId + '">남김말</label><div class="store-bookmark-controls"><input id="' + noteId + '" name="note" maxlength="300" placeholder="다음에 이어서 볼 내용">' +
      '<button class="store-btn is-primary" type="submit">저장</button></div></form>';
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
      // 구성 변경 시에도 사용자가 삭제하기 전까지 저장한 항목을 보존합니다.
      feedback(error.message, true);
      return null;
    }
  }
  function emptyList(title, description, destination = root, label = '쇼핑몰 둘러보기', symbol = 'cart') {
    return '<section class="store-empty">' + icon(symbol) + '<h2>' + esc(title) + '</h2><p>' +
      esc(description) + '</p><a class="store-btn" href="' + esc(destination) + '">' + esc(label) + '</a></section>';
  }
  function listDate(value) {
    const parts = Array.isArray(value) ? value : /^(\d{4})[-.,]\s*(\d{1,2})[-.,]\s*(\d{1,2})(?:$|T)/.exec(String(value))?.slice(1);
    if (!parts || parts.length !== 3) return '확인 필요';
    const [year, month, day] = parts.map(Number);
    const date = new Date(Date.UTC(year, month - 1, day));
    if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return '확인 필요';
    return year + '.' + String(month).padStart(2, '0') + '.' + String(day).padStart(2, '0') + ' (' + '일월화수목금토'[date.getUTCDay()] + ')';
  }
  let listObserver;
  async function listPage(mode, limit = 10, selectedKeys = new Set(), focusIndex) {
    listObserver?.disconnect();
    const names = {cart: '장바구니', interest: '관심상품', bookmark: '즐겨찾기'};
    const rows = mode === 'cart' ? cart() : (await call('/saved')).filter(row => row.kind === mode);
    let displayed = Math.min(limit, rows.length);
    const keyOf = row => mode === 'cart' ? row.key : row.id;
    const rowKeys = new Set(rows.map(keyOf));
    for (const key of selectedKeys) if (!rowKeys.has(key)) selectedKeys.delete(key);
    const hints = {
      cart: '제품 옵션을 구성한 뒤 장바구니에 담아 주세요.',
      interest: '관심 있는 구성을 제품 상세에서 저장해 주세요.',
      bookmark: '비규격 제품을 구성하다가 즐겨찾기에 저장해 주세요.'
    };
    const renderRow = (row, i) => {
      if (!row.item?.selection) return '<article class="store-row store-saved-row"><div><strong>저장 구성 확인 필요</strong><p>이 항목의 옵션을 복원할 수 없습니다.</p><p>' + esc(row.note || '') + '</p></div><button type="button" class="store-btn" data-remove="' + i + '">삭제</button></article>';
      const item = row.item;
      const resumeUrl = mode === 'bookmark' ? root + '/products/' + encodeURIComponent(item.selection.productId) +
        '?kind=custom&bookmark=' + encodeURIComponent(row.id) : '';
      const title = resumeUrl ? '<a class="store-link" href="' + esc(resumeUrl) + '">' + esc(item.name) + '</a>' :
        '<button type="button" class="store-link" data-open="' + i + '">' + esc(item.name) + '</button>';
      const fields = [['구분', item.custom ? '비규격' : '규격'], ['시리즈', item.series || '미등록'],
        ['대분류', item.category?.trim() || '조회 중…'], ['생산기간', item.productionDays == null || item.productionDays < 0 ? '기본 2영업일' : item.productionDays + '일'],
        ['가장 빠른 발주 가능일', listDate(item.earliest)]];
      const extra = [];
      if (mode === 'cart') extra.push(['수량', (row.quantity || 1) + '개']);
      if (row.createdAt) extra.push(['저장일', listDate(row.createdAt)]);
      if (row.note) extra.push(['남긴 말', row.note]);
      for (const answer of item.summary || []) {
        const text = String(answer), split = text.indexOf(' · ');
        extra.push(split < 0 ? ['옵션', text] : [text.slice(0, split), text.slice(split + 3)]);
      }
      const pairs = entries => entries.map(([label,value]) => '<div><dt>' + esc(label) + '</dt><dd' + (label === '대분류' ? ' data-category' : '') + '>' + esc(value) + '</dd></div>').join('');
      return '<article class="store-row store-saved-row saved-product"><div class="saved-product-head">' +
        (mode === 'bookmark' ? '' : '<input type="checkbox" data-index="' + i + '" aria-label="' + esc(item.name) + ' 선택">') +
        img(item) + '<div class="saved-product-title">' + title +
        '<div class="store-row-price">' + (item.price == null ? '가격 미확정' : won(item.price)) + '</div></div>' +
        '<button class="store-icon" type="button" data-remove="' + i + '" aria-label="' + esc(item.name) + ' 삭제">' + icon('close') + '</button></div>' +
        '<dl class="store-product-facts">' + pairs(fields) + '</dl>' +
        (resumeUrl ? '<a class="store-btn store-resume" href="' + esc(resumeUrl) + '">저장한 단계에서 이어하기</a>' : '') +
        (extra.length ? '<button type="button" class="saved-options-toggle" aria-expanded="false" aria-controls="saved-options-' + mode + '-' + i + '">상세 정보</button>' +
          '<div class="saved-options-panel" id="saved-options-' + mode + '-' + i + '" inert><div><dl class="store-answer-detail">' + pairs(extra) + '</dl></div></div>' : '') +
        '</article>';
    };
    shell(names[mode], rows.length
      ? '<div class="store-list"></div><div class="store-more"><p role="status"></p>' +
        '<button type="button" class="store-btn" hidden>더보기</button></div>'
      : emptyList('저장된 ' + names[mode] + ' 항목이 없습니다', hints[mode], root, '쇼핑몰 둘러보기', mode));
    if (!rows.length) {
      if (focusIndex != null) main.querySelector('.store-empty a')?.focus({preventScroll: true});
      return;
    }

    const host = main.querySelector('.store-page');
    const list = host.querySelector('.store-list');
    const footer = host.querySelector('.store-more');
    const more = footer.querySelector('button');
    const status = footer.querySelector('p');
    const selection = document.createElement('div');
    selection.className = 'store-selection-head';
    host.querySelector('header').after(selection);
    selection.append(status);
    let updateAll = () => {};
    let bindCheck = () => {};
    if (mode !== 'bookmark' && rows.length) {
      const bar = document.createElement('div');
      selection.insertAdjacentHTML('afterbegin', '<label><input type="checkbox" id="store-select-all"> 표시된 품목 전체 선택</label><span data-count role="status"></span>');
      bar.className = 'store-order-footer';
      bar.innerHTML = '<span class="store-order-amount">선택 상품금액 <strong data-total aria-live="polite"></strong></span><div class="store-order-buttons">' +
        (mode === 'interest' ? button('cart', '장바구니 담기') : '') + button('order', '발주하기', true) + '</div>';
      footer.after(bar);
      const all = selection.querySelector('#store-select-all');
      const actions = [...bar.querySelectorAll('[data-action]')];
      const checks = () => [...list.querySelectorAll('[data-index]')];
      updateAll = () => {
        const visibleChecks = checks();
        all.checked = visibleChecks.length > 0 && visibleChecks.every(el => el.checked);
        const selected = visibleChecks.filter(el => el.checked);
        const count = selected.length;
        all.indeterminate = !all.checked && count > 0;
        const subtotal = selected.reduce((total, el) => {
          const row = rows[Number(el.dataset.index)];
          return total + Number(row.item.price || 0) * (row.quantity || 1);
        }, 0);
        selection.querySelector('[data-count]').textContent = count + '개 선택';
        bar.querySelector('[data-total]').textContent = won(subtotal);
        actions.forEach(action => { action.disabled = !count || action.dataset.busy === 'true'; });
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
      }, updateAll);
    }
    const categories = new Map();
    function fillCategory(card, item) {
      if (item.category?.trim()) return;
      const id = item.selection.productId;
      if (!categories.has(id)) {
        categories.set(id, call('/customer-series/' + encodeURIComponent(id)).then(data => {
          const name = data.series.category?.trim();
          if (!name) throw new Error();
          return name;
        }).catch(() => { categories.delete(id); return null; }));
      }
      const field = card.querySelector('[data-category]');
      categories.get(id).then(name => {
        field.textContent = name || '분류 조회 실패';
        if (name) item.category = name;
      });
    }
    function appendRows(from, to) {
      const template = document.createElement('template');
      template.innerHTML = rows.slice(from, to).map((row, i) => renderRow(row, from + i)).join('');
      Array.from(template.content.children).forEach((card, index) => {
        const item = rows[from + index].item;
        if (item?.selection) fillCategory(card, item);
      });
      template.content.querySelectorAll('[data-index]').forEach(bindCheck);
      if (from > 0) template.content.querySelectorAll('.store-saved-row').forEach((row, index) => {
        row.classList.add('store-row-enter');
        row.style.setProperty('--store-enter-delay', Math.min(index, 4) * 18 + 'ms');
      });
      list.append(template.content);
      status.textContent = to >= rows.length ? '전체 ' + rows.length + '개' : '전체 ' + rows.length + '개 중 ' + to + '개 표시';
      footer.hidden = to >= rows.length;
      more.hidden = to >= rows.length;
      updateAll();
    }
    function expandOptions(toggle, expanded) {
      toggle.setAttribute('aria-expanded', String(expanded));
      const panel = toggle.nextElementSibling;
      panel.inert = !expanded;
    }
    list.addEventListener('click', async event => {
      const toggle = event.target.closest('.saved-options-toggle');
      if (toggle) {
        const expand = toggle.getAttribute('aria-expanded') !== 'true';
        for (const other of list.querySelectorAll('.saved-options-toggle[aria-expanded="true"]')) {
          if (other !== toggle) expandOptions(other, false);
        }
        expandOptions(toggle, expand);
        return;
      }
      const action = event.target.closest('[data-remove],[data-open]');
      if (!action || action.disabled) return;
      action.disabled = true;
      try {
        if (action.hasAttribute('data-remove')) {
          const index = Number(action.dataset.remove);
          const row = rows[index];
          if (mode === 'cart') removeCart([row.key]);
          else await call('/saved/' + row.id, 'DELETE');
          selectedKeys.delete(keyOf(row));
          await listPage(mode, displayed, selectedKeys, index);
          feedback('목록에서 삭제했습니다.');
        } else {
          const index = Number(action.dataset.open);
          const row = await verifyRow(rows[index], mode);
          if (row) restore(row);
          else await listPage(mode, displayed, selectedKeys, index);
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
    if (focusIndex != null) {
      const index = Math.min(focusIndex, displayed - 1);
      list.querySelector('[data-open="' + index + '"]')?.focus({preventScroll: true});
    }
    more.onclick = () => loadMore(true);
    if (!more.hidden && 'IntersectionObserver' in window) {
      listObserver = new IntersectionObserver(entries => {
        if (entries.some(entry => entry.isIntersecting)) loadMore(false);
      });
      listObserver.observe(more);
    }
  }

  function checkoutOptions(item) {
    const answers = Array.isArray(item.summary) ? item.summary : [];
    if (!answers.length) return '<p class="store-no-options">선택 옵션 없음</p>';
    const compact = item.compactSummary ?? answers;
    const summary = compact.slice(0, 2).join(' | ');
    return (summary ? '<p class="store-option-preview">' + esc(summary) + '</p>' : '') +
      '<details class="store-config-detail"><summary>선택 옵션 전체 보기 (' + answers.length + ')</summary>' +
      '<dl class="store-answer-detail">' + answers.map(answer => {
        const text = String(answer);
        const split = text.indexOf(' · ');
        return '<div><dt>' + esc(split < 0 ? '옵션' : text.slice(0, split)) +
          '</dt><dd>' + esc(split < 0 ? text : text.slice(split + 3)) + '</dd></div>';
      }).join('') + '</dl></details>';
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
    const contactFields = [['orderer','주문자 이름'],['phone','주문자 연락처'],['manager','현장 담당자 이름'],['managerPhone','현장 담당자 연락처']];
    const contacts = '<details class="store-contacts"><summary>주문자 · 현장 담당자 정보</summary><div class="store-fields">' +
      contactFields.map(([key,label]) => '<label>' + label + '<input name="' + key + '" maxlength="60" type="' + (key.toLowerCase().includes('phone') ? 'tel' : 'text') + '"></label>').join('') + '</div></details>';
    const address = '<section class="store-delivery-address"><h3>배송지</h3><input name="address" type="hidden">' +
      '<div class="store-address-tools"><label>우편번호<input name="postal" readonly></label>' +
      '<button type="button" class="store-btn" data-address-search>주소 검색</button><button type="button" class="store-btn" data-address-book>업체 주소록</button></div>' +
      '<label>도로명주소<input name="road" readonly></label><label>지번주소<input name="jibun" readonly></label>' +
      '<label>상세주소<input name="addressDetail" maxlength="180" placeholder="건물명, 동·호수"></label>' +
      '<small data-region></small></section>';
    const addressLabel = a => a ? [a.zip, a.road || a.jibun].filter(Boolean).join(' ') : '';
    const addressBook = '<dialog class="store-address-book"><h2>업체 주소록</h2><div></div><button type="button" class="store-btn" data-close>닫기</button></dialog>';
    const date = '<div class="store-date-field"><span>희망 출고일</span><input aria-label="희망 출고일" type="text" name="date" placeholder="달력에서 선택" required readonly></div>';
    const dates = rows.map(row => row.item.earliest);
    const latest = dates.every(Boolean) ? dates.sort().at(-1) : null;
    shell('발주하기', '<div class="store-bulk-launch"><div><strong>전체 ' + rows.length +
      '개 품목</strong><p>각 품목에서 배송 정보를 입력해 주세요. 같은 정보는 일괄 적용할 수 있습니다.</p></div>' +
      '<button type="button" class="store-btn" id="store-bulk-open">배송 · 연락처 일괄 적용</button></div><details class="store-validation"><summary>배송일과 비용 산정 기준</summary>' +
      '<p>오늘을 제외한 영업일로 계산합니다. 예를 들어 1.5일은 2영업일로 올립니다. 주말과 등록된 공휴일은 선택할 수 없습니다. 일괄 출고일은 품목별 가능일 중 가장 늦은 날짜부터 선택할 수 있습니다.</p>' +
      '<p>희망 출고일, 배송수단, 배송 주소가 같으면 배송비와 포장비를 각각 한 번 부과합니다. 포인트는 상품금액에만 적용됩니다. 발주 결과는 저장되지 않습니다.</p></details>' +
      '<dialog class="store-bulk store-bulk-dialog" aria-labelledby="store-bulk-title"><h2 id="store-bulk-title">배송 · 연락처 일괄 적용</h2>' +
      '<p>이번에 입력한 항목만 모든 품목에 적용합니다. 직배송 품목은 업체 주소를 사용합니다.</p><div class="store-fields"><label>품목당 수량<input name="quantity" type="number" min="1" max="99999" step="1" placeholder="기존 수량 유지"></label>' + shipping + date + address + contacts +
      '</div><p class="store-bulk-warning" role="status"></p><div class="store-actions"><button type="button" class="store-btn" id="store-bulk-close">닫기</button>' +
      '<button type="button" class="store-btn is-primary" id="store-apply">적용</button></div></dialog>' +
      '<form id="store-checkout"><div class="store-list">' + rows.map((row, index) => '<article class="store-check-line"><div class="store-row">' +
      img(row.item) + '<div><strong>' + esc(row.item.name) + '</strong><div class="checkout-price">' + won(row.item.price) + '</div><small>' +
      '최초 가능일 ' + esc(listDate(row.item.earliest) || '미등록') + (row.item.productionDays == null || row.item.productionDays < 0 ? ' · 기본 생산기간 2영업일 적용' : '') + '</small></div><span class="store-line-status"></span></div>' +
      checkoutOptions(row.item) + '<p class="store-line-overview"></p><section class="store-check-details"><h3>품목별 배송 설정</h3><div class="store-fields">' +
      '<label>수량<input type="number" name="quantity" min="1" max="99999" required value="' + esc(row.quantity || 1) + '"></label>' +
      shipping + date + address + contacts + '</div><small class="store-method-note" aria-live="polite"></small></section></article>').join('') +
      '</div><section class="store-payment"><h2>결제 정보</h2><p>계좌결제 · 미리보기</p><label>사용 포인트 <input type="number" name="points" value="0" min="0" max="' +
      esc(options.points) + '" required></label><small>보유 ' + won(options.points) + ' 상당</small>' +
      '<button type="submit" class="store-btn is-primary">배송비 계산</button></section></form>' + addressBook);
    const form = main.querySelector('#store-checkout');
    const lines = [...form.querySelectorAll('.store-check-line')];
    const bulk = main.querySelector('.store-bulk');
    const selectedAddress = new WeakMap();
    function setAddress(node, value) {
      selectedAddress.set(node, value || {});
      node.querySelector('[name=address]').value = addressLabel(value);
      node.querySelector('[name=postal]').value = value?.zip || '';
      node.querySelector('[name=road]').value = value?.road || '';
      node.querySelector('[name=jibun]').value = value?.jibun || '';
      node.querySelector('[data-region]').textContent = [value?.province,value?.city,value?.district].filter(Boolean).join(' · ');
      node.querySelector('[name=addressDetail]').value = value?.detail || '';
    }
    function chooseAddress(node, address) {
      const method = node.querySelector('[name=method]');
      if (method.value === '1') {
        method.value = '';
        method.dispatchEvent(new Event('change'));
        feedback('배송지를 변경했습니다. 이 주소로 받을 배송수단을 선택해 주세요.');
      }
      setAddress(node, address);
    }
    const book = main.querySelector('.store-address-book');
    book.querySelector('[data-close]').onclick = () => book.close();
    for (const container of [bulk, ...lines]) {
      container.querySelector('[data-address-book]').onclick = () => {
        const index = lines.indexOf(container);
        book.querySelector('h2').textContent = index < 0 ? '전체 품목에 적용할 배송지' : (index + 1) + '. ' + rows[index].item.name + ' 배송지';
        const list = book.querySelector('div');
        list.replaceChildren();
        const entries = [options.companyAddress, ...options.addresses].filter(a => a?.road || a?.jibun);
        if (!entries.length) list.textContent = '등록된 업체 주소가 없습니다.';
        for (const a of entries) {
          const button = document.createElement('button');
          button.type = 'button'; button.className = 'store-btn';
          const title = document.createElement('strong');
          title.textContent = a === options.companyAddress ? '업체 기본 주소' : '저장 배송지';
          button.append(title);
          for (const [label, value] of [['우편번호',a.zip],['도로명주소',a.road],['지번주소',a.jibun],['상세주소',a.detail]]) {
            const field = document.createElement('span');
            field.textContent = label + ' · ' + (value || '미등록');
            button.append(field);
          }
          button.onclick = () => { chooseAddress(container, a); book.close(); if (container !== bulk) updateLineStatus(container); };
          list.append(button);
        }
        book.showModal();
      };
      container.querySelector('[data-address-search]').onclick = () => {
        const Postcode = window.kakao?.Postcode || window.daum?.Postcode;
        if (!Postcode) return feedback('주소 검색 서비스를 불러오지 못했습니다. 업체 주소록을 이용하거나 다시 시도해 주세요.', true);
        const dialog = document.createElement('dialog');
        dialog.className = 'store-postcode-dialog';
        dialog.innerHTML = '<header><h2>배송지 검색</h2><button type="button" class="store-btn">닫기</button></header><div class="store-postcode-content"></div>';
        main.append(dialog);
        dialog.querySelector('button').onclick = () => dialog.close();
        dialog.addEventListener('close', () => dialog.remove(), {once:true});
        dialog.showModal();
        new Postcode({width:'100%', height:'100%', oncomplete(data) {
          dialog.close();
          const region = window.HiddenAutoAddressRegion.fromDaum(data);
          chooseAddress(container, {zip:data.zonecode, province:region.doName, city:region.siName,
            district:region.guName, road:data.roadAddress || data.autoRoadAddress,
            jibun:data.jibunAddress || data.autoJibunAddress, detail:''});
          container.querySelector('[name=addressDetail]').focus();
          if (container !== bulk) updateLineStatus(container);
        }}).embed(dialog.querySelector('.store-postcode-content'));
      };
    }
    function applyMethod(node) {
      const direct = node.querySelector('[name=method]').value === '1';
      if (direct) setAddress(node, options.companyAddress);
      else if (node.dataset.direct === 'true') setAddress(node, null);
      node.dataset.direct = String(direct);
      node.querySelector('[name=addressDetail]').readOnly = direct;

    }
    bulk.querySelector('[name=method]').onchange = () => applyMethod(bulk);
    const openBulk = main.querySelector('#store-bulk-open');
    main.querySelector('#store-bulk-close').onclick = () => bulk.close();
    openBulk.onclick = () => {
      bulk.querySelector('[name=quantity]').value = '';
      bulk.querySelector('[name=method]').value = '';
      applyMethod(bulk);
      setAddress(bulk, null);
      bulk.querySelector('[name=date]')._flatpickr.clear();
      contactFields.forEach(([key]) => bulk.querySelector('[name=' + key + ']').value = '');
      bulk.querySelector('.store-bulk-warning').textContent = '';
      bulk.showModal();
    };
    bulk.addEventListener('close', () => openBulk.focus());
    const holidays = new Set(options.holidays);
    const pick = (node, earliest) => {
      const input = node.querySelector('[name=date]');
      window.flatpickr(input, {dateFormat: 'Y-m-d', minDate: earliest || undefined, disableMobile: true, static: true,
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
      const overview = ['수량 ' + quantityInput.value + '개', methodInput.selectedOptions[0]?.value ? methodInput.selectedOptions[0].text : '',
        dateInput.value, [addressInput.value, line.querySelector('[name=addressDetail]').value].filter(Boolean).join(' ')];
      line.querySelector('.store-line-overview').textContent = overview.filter(Boolean).join(' · ');
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
          chosen.description : '';
        applyMethod(line); updateLineStatus(line);
      };
      const draft = previous.get(rows[i].key);
      if (draft) {
        setAddress(line, draft.address);
        contactFields.forEach(([key]) => line.querySelector('[name='+key+']').value = draft.contacts?.[key] || '');
        line.querySelector('[name=addressDetail]').value = draft.addressDetail || '';
        line.querySelector('[name=quantity]').value = draft.quantity;
        line.querySelector('[name=date]')._flatpickr.setDate(draft.date, true);
        method.value = String(draft.methodId);
        method.onchange();
      }
      updateLineStatus(line);
    });
    main.querySelector('#store-apply').onclick = () => {
      const quantity = bulk.querySelector('[name=quantity]');
      if (!quantity.validity.valid) { quantity.reportValidity(); return; }
      const updates = ['quantity', 'method', 'address', 'date', ...contactFields.map(([key]) => key)]
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
          if (name === 'address') {
            if (bulk.querySelector('[name=method]').value !== '1') chooseAddress(line, {...selectedAddress.get(bulk), detail:bulk.querySelector('[name=addressDetail]').value});
          } else if (name === 'date') target._flatpickr.setDate(value, true);
          else { target.value = value; target.dispatchEvent(new Event('change')); }
        }
      }
      lines.forEach(updateLineStatus);
      bulk.close();
      feedback('입력한 항목을 전체 품목에 적용했습니다.');
    };
    form.onsubmit = async event => {
      event.preventDefault();
      const missing = lines.find(line => !line.querySelector('[name=address]').value.trim());
      if (missing) { missing.querySelector('[data-address-search]').focus(); return feedback('배송 주소를 검색해 주세요.', true); }
      const submit = form.querySelector('button[type=submit]');
      submit.disabled = true;
      const payload = lines.map((line, i) => {
        const get = field => line.querySelector('[name=' + field + ']').value;
        return {...rows[i], quantity: Number(get('quantity')), baseAddress: get('address'), addressDetail: get('addressDetail'), address: {...selectedAddress.get(line), detail:get('addressDetail').trim()}, contacts:Object.fromEntries(contactFields.map(([key]) => [key,get(key).trim()])),
          date: get('date'), methodId: Number(get('method'))};
      });
      try {
        const missingDate = payload.findIndex(row => !row.date);
        if (missingDate >= 0) {
          const line = lines[missingDate];
          line.querySelector('[name=date]').focus();
          throw new Error('품목별 희망 출고일을 모두 선택해 주세요.');
        }
        const result = await call('/checkout', 'POST', {lines: payload.map(({key, item, quantity, address, contacts, date, methodId}) =>
          ({key, item, quantity, address, contacts, date, methodId})), points: Number(form.elements.points.value)});
        if (!Array.isArray(result?.lines) || result.lines.length !== payload.length ||
            result.lines.some((line, index) => !line?.item || line.key !== payload[index].key)) {
          throw new Error('서버가 요청한 품목의 발주 검증 결과를 반환하지 않았습니다. 입력 내용을 유지합니다.');
        }
        const dialog = document.createElement('dialog');
        dialog.className = 'store-address-book';
        dialog.innerHTML = '<h2>배송비 계산 결과</h2><p>상품 ' + won(result.products) + ' · 확정 운임 ' + won(result.delivery) + ' · 확정 포장비 ' + won(result.packing) + '</p>' +
          (result.pending ? '<p>관리자 확인이 필요한 묶음이 있습니다.</p>' : '') +
          '<button type="button" class="store-btn">입력 수정</button> <button type="button" class="store-btn is-primary">발주 확정 · 상세 검증</button>';
        main.append(dialog);
        const [edit, confirm] = dialog.querySelectorAll('button');
        edit.onclick = () => dialog.close();
        confirm.onclick = () => { dialog.close(); resultPage(result); };
        dialog.addEventListener('close', () => dialog.remove(), {once:true});
        dialog.showModal();
        // 검증 단계에서는 장바구니와 발주 초안을 유지합니다.
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
    shell('발주 검증 결과', '<p class="store-validation">발주 전 검증 결과입니다. 묶음 배송비와 포장비는 각 조합의 첫 품목에 표시합니다.</p>' +
      '<div class="store-list">' + result.lines.map(line => '<article class="store-row store-result">' + img(line.item) +
      '<div><strong>' + esc(line.item.name) + '</strong><p>' + esc([line.address.zip, line.address.road || line.address.jibun, line.address.detail].filter(Boolean).join(' ')) + '</p><p>' +
      esc(listDate(line.date)) + ' · ' + esc(line.method) + '</p><p>상품 ' + won(line.item.price) + ' × ' +
      esc(line.quantity) + '개 · 배송비 ' + (line.delivery == null ? '미확정' : won(line.delivery)) + ' · 포장비 ' + (line.packing == null ? '미확정' : won(line.packing)) + '</p><details><summary>계산 근거와 품목 상세</summary><p>' + esc(line.explanation) + '</p><p>대분류 ' + esc(line.item.category || '미지정') + (line.item.width == null ? '' : ' · 가로 ' + esc(line.item.width) + 'mm') + '</p><p>' + esc([line.address.province,line.address.city,line.address.district].filter(Boolean).join(' ')) + '</p><p>도로명 ' + esc(line.address.road || '미등록') + ' · 지번 ' + esc(line.address.jibun || '미등록') + '</p>' + checkoutOptions(line.item) + '<p>주문자 ' + esc([line.contacts?.orderer,line.contacts?.phone].filter(Boolean).join(' ') || '미입력') + ' · 현장 담당자 ' + esc([line.contacts?.manager,line.contacts?.managerPhone].filter(Boolean).join(' ') || '미입력') + '</p></details></div></article>').join('') +
      '</div><dl class="store-totals"><dt>상품금액</dt><dd>' + won(result.products) +
      '</dd><dt>배송비</dt><dd>' + won(result.delivery) + '</dd><dt>포장비</dt><dd>' + won(result.packing) +
      '</dd><dt>검증용 포인트</dt><dd>−' + won(result.points) + '</dd><dt>' + (result.pending ? '현재 확정 금액 · 미확정 비용 별도' : '합계') + '</dt><dd>' +
      won(result.payable) + '</dd></dl><a class="store-btn" href="' + root + '">컬렉션으로</a>');
    main.focus({preventScroll: true});
    window.scrollTo({top: 0, behavior: reduceMotion() ? 'instant' : 'smooth'});
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
