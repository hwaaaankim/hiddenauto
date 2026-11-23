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
  const stored = (name, fallback, storage) => {
    try {
      const value = JSON.parse((storage || window.localStorage).getItem(prefix + name)) ?? fallback;
      if (Array.isArray(fallback) && !Array.isArray(value)) throw new Error();
      return value;
    } catch (_) {
      if (name === 'recent') return fallback;
      throw new Error('이 브라우저에 저장한 구성을 읽을 수 없습니다. 브라우저의 사이트 저장 권한을 확인한 뒤 다시 시도해 주세요.');
    }
  };
  const write = (name, value, storage) => {
    try { (storage || window.localStorage).setItem(prefix + name, JSON.stringify(value)); }
    catch (_) {
      if (name === 'recent') return;
      throw new Error('이 브라우저에 구성을 저장하지 못했습니다. 사이트 저장 권한과 남은 공간을 확인해 주세요.');
    }
    if (name === 'cart') window.dispatchEvent(new Event('store:cart-changed'));
  };
  function cart() {
    const rows = stored('cart', []);
    const seen = new Set();
    let changed = false;
    const normalized = rows.map(row => {
      if (typeof row?.key === 'string' && row.key.trim() && !seen.has(row.key)) {
        seen.add(row.key);
        return row;
      }
      changed = true;
      const key = crypto.randomUUID();
      seen.add(key);
      if (!row || typeof row !== 'object' || Array.isArray(row)) return {key, item: null, legacyValue: row};
      if (row.selection && !row.item) return {key, item: row, quantity: 1, fromCart: true};
      return {...row, key};
    });
    if (changed) write('cart', normalized);
    return normalized;
  }
  function deductCart(current, selectedRows, quantities) {
    let count = 0;
    const remaining = current.flatMap(row => {
      const selected = selectedRows.get(row.key);
      if (!selected || !row.item?.stamp || row.item.stamp !== (selected.sourceStamp || selected.item.stamp)) return [row];
      const available = Number(row.quantity || 1);
      const used = Math.min(available, quantities.get(row.key));
      count += used;
      // 새 키를 부여해 다른 탭의 오래된 초안이 남은 수량을 재차 차감하지 않게 한다.
      return available > used ? [{...row, key:crypto.randomUUID(), quantity:available - used}] : [];
    });
    return {remaining, count};
  }
  function changeCart(change) {
    const mutate = () => change(cart());
    return navigator.locks ? navigator.locks.request(prefix + 'cart', mutate) : mutate();
  }
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
  let postcodeRequest;
  function loadPostcode() {
    const constructor = () => window.kakao?.Postcode || window.daum?.Postcode;
    if (constructor()) return Promise.resolve(constructor());
    if (postcodeRequest) return postcodeRequest;
    const script = document.createElement('script');
    script.src = 'https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
    script.async = true;
    postcodeRequest = new Promise((resolve, reject) => {
      const finish = () => {
        clearTimeout(timer);
        script.onload = script.onerror = null;
        if (constructor()) resolve(constructor());
        else {
          script.remove();
          reject(new Error('주소 검색 서비스에 연결하지 못했습니다. 다시 시도하거나 업체 주소록을 이용해 주세요.'));
        }
      };
      const timer = setTimeout(finish, 12000);
      script.onload = script.onerror = finish;
      document.head.append(script);
    }).finally(() => { postcodeRequest = null; });
    return postcodeRequest;
  }
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
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    if (method !== 'GET' && csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;
    const options = {method, cache: 'no-store', credentials: 'same-origin', headers,
      signal: signal || window.AbortSignal?.timeout?.(40000)};
    if (body instanceof FormData) options.body = body;
    else if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body);
    }
    let response;
    try { response = await fetch(path, options); }
    catch (error) {
      if (error.name === 'AbortError') throw error;
      if (error.name === 'TimeoutError') throw new Error('서버 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.');
      throw new Error('서버에 연결할 수 없습니다. 네트워크 연결을 확인한 뒤 다시 시도해 주세요.');
    }
    const content = await response.json().catch(() => null);
    if (!response.ok || !content || content.success === false || response.redirected) {
      const loginRequired = response.redirected || response.status === 401 || response.status === 403;
      const error = new Error(loginRequired ? '로그인 상태를 확인해 주세요. 이 페이지를 새로고침한 뒤 다시 시도할 수 있습니다.' :
        content?.message || '서버 응답을 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.');
      error.status = response.status;
      error.invalid = response.status === 409 && content?.invalid === true;
      if (typeof content?.lineKey === 'string') error.lineKey = content.lineKey;
      throw error;
    }
    return replaceAssetUrls(content.data ?? content);
  };
  const call = async (path, method, body) => {
    const data = await S.api(S.base + path, method, body);
    const savedMutation = method === 'DELETE' && path.startsWith('/saved/') &&
      (!path.endsWith('/invalid') || data.removed || data.status === 'missing');
    if ((method === 'POST' && path === '/saved') || savedMutation) {
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
        delete details.dataset.disclosureTarget;
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
    details.dataset.disclosureTarget = open ? 'open' : 'closed';
    const animation = details.animate(
      [{height: start + 'px', boxSizing: 'border-box', overflow: 'clip'},
       {height: end + 'px', boxSizing: 'border-box', overflow: 'clip'}],
      {duration: 260, easing: 'cubic-bezier(.22,.61,.36,1)', fill: 'both'}
    );
    detailsAnimations.set(details, {animation, open});
    animation.onfinish = () => {
      if (detailsAnimations.get(details)?.animation !== animation) return;
      details.open = open;
      animation.cancel();
      detailsAnimations.delete(details);
      delete details.dataset.disclosureTarget;
    };
  });

  function feedback(text, error = false, showCart = false) {
    const dialog = document.activeElement?.closest('dialog[open]') ||
      [...document.querySelectorAll('dialog[open]')].at(-1);
    const host = dialog || document.body;
    let node = document.querySelector('#store-feedback');
    if (!node) {
      node = document.createElement('p');
      node.id = 'store-feedback';
      node.setAttribute('role', 'status');
    }
    if (node.parentElement !== host) host.append(node);
    const previousFocus = document.activeElement;
    node.textContent = text;
    if (showCart) {
      const shortcut = document.createElement('a');
      shortcut.href = root + '?view=cart';
      shortcut.textContent = '장바구니 보기';
      node.append(' ', shortcut);
    }
    if (error) {
      const dismiss = document.createElement('button');
      dismiss.type = 'button';
      dismiss.className = 'store-feedback-close';
      dismiss.setAttribute('aria-label', '안내 닫기');
      dismiss.innerHTML = icon('close');
      dismiss.onclick = () => {
        node.hidden = true;
        if (previousFocus?.isConnected && !node.contains(previousFocus)) previousFocus.focus({preventScroll:true});
      };
      node.append(dismiss);
    }
    node.classList.toggle('is-error', error);
    node.hidden = false;
    clearTimeout(feedback.timer);
    if (!error) {
      const hide = () => {
        if (node.matches(':focus-within, :hover')) { feedback.timer = setTimeout(hide, 1500); return; }
        node.hidden = true;
      };
      feedback.timer = setTimeout(hide, showCart ? 8500 : 5000);
    }
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

  // A stale quote can be older than the saved entry now shown in another tab.
  // Remove only the same entry and the same revision that failed validation.
  async function purgeInvalidRow(row, source) {
    const stamp = row?.sourceStamp || row?.item?.stamp;
    if (!stamp) throw new Error('저장한 구성을 식별할 수 없습니다. 목록에서 다시 확인해 주세요.');
    if (source === 'cart') {
      if (!row.key) throw new Error('장바구니 항목을 식별할 수 없습니다.');
      return changeCart(rows => {
        const index = rows.findIndex(candidate => candidate.key === row.key);
        if (index < 0) return true;
        if (rows[index].item?.stamp !== stamp) return false;
        rows.splice(index, 1);
        write('cart', rows);
        return true;
      });
    }
    if (source !== 'interest' && source !== 'bookmark') throw new Error('저장 목록을 확인해 주세요.');
    const id = row.savedId ?? row.id;
    if (!/^[1-9]\d*$/.test(String(id))) throw new Error('저장 항목을 식별할 수 없습니다.');
    const result = await call('/saved/' + id + '/invalid', 'DELETE', {stamp, kind: source});
    if (result.status === 'removed' || result.status === 'missing') return true;
    if (result.status === 'valid' || result.status === 'replaced') return false;
    throw new Error('현재 구성의 변경 여부를 확인하지 못했습니다. 항목을 유지했습니다. 다시 시도해 주세요.');
  }
  function selectionKey(selection) {
    if (!selection?.productId) return null;
    // The server controls choice order. Object key ordering can differ between saved JSON formats.
    return JSON.stringify(selection, (_, value) => value && typeof value === 'object' && !Array.isArray(value)
      ? Object.fromEntries(Object.keys(value).sort().map(key => [key, value[key]])) : value);
  }
  async function cartAdd(items) {
    const incoming = new Map(items.map(item => [selectionKey(item.selection), item]));
    const duplicates = cart().filter(row => {
      const key = selectionKey(row.item?.selection);
      const next = key && incoming.get(key);
      return next && row.item.stamp !== next.stamp;
    });
    const outdated = [];
    for (const row of duplicates) {
      try { await check(row.item); }
      catch (error) {
        if (!error.invalid) throw error;
        if (await purgeInvalidRow(row, 'cart')) outdated.push(row.item.name + ' · ' + error.message);
      }
    }
    if (outdated.length) {
      feedback('기존 장바구니 구성을 삭제했습니다. ' + outdated.join(' / ') +
        ' 새 구성과 금액을 확인한 뒤 다시 담아 주세요.', true);
      return;
    }
    const added = await changeCart(existing => {
      const originalCount = existing.length;
      const selections = new Set(existing.map(row => selectionKey(row.item?.selection)).filter(Boolean));
      for (const item of items) {
        const key = selectionKey(item.selection);
        if ((key && selections.has(key)) || existing.some(record => record?.item?.stamp === item.stamp)) continue;
        if (existing.length >= 100) throw new Error('장바구니에는 최대 100개 구성을 담을 수 있습니다.');
        existing.push({key: crypto.randomUUID(), item, quantity: 1, fromCart: true});
        if (key) selections.add(key);
      }
      if (existing.length !== originalCount) write('cart', existing);
      return existing.length - originalCount;
    });
    if (!added) {
      feedback('이미 장바구니에 담긴 구성입니다.');
      return;
    }
    const skipped = items.length - added;
    feedback(skipped ? added + '개 담았습니다. 이미 담긴 구성 ' + skipped + '개는 제외했습니다.' : '장바구니에 담았습니다.', false, true);
  }
  function startCheckout(lines) {
    write('checkout', lines, sessionStorage);
    sessionStorage.removeItem(prefix + 'checkoutChanges');
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
      if (action === 'cart') await cartAdd([item]);
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
    const panel = wrapper.querySelector('form');
    const submit = panel.querySelector('[type=submit]');
    panel.id = noteId + '-form';
    trigger.setAttribute('aria-controls', panel.id);
    trigger.setAttribute('aria-expanded', 'false');
    trigger.disabled = !Object.keys(selection.answers).length;
    let formAnimation;
    const showForm = open => {
      const from = panel.hidden ? 0 : panel.getBoundingClientRect().height;
      formAnimation?.cancel();
      panel.hidden = false;
      panel.inert = !open;
      trigger.setAttribute('aria-expanded', String(open));
      (open ? panel.elements.note : trigger).focus({preventScroll: true});
      const to = open ? panel.getBoundingClientRect().height : 0;
      if (reduceMotion() || !panel.animate || from === to) {
        panel.hidden = !open;
        return;
      }
      const animation = panel.animate([
        {height: from + 'px', overflow: 'clip'},
        {height: to + 'px', overflow: 'clip'}
      ], {duration: 230, easing: 'cubic-bezier(.22,.61,.36,1)', fill: 'both'});
      formAnimation = animation;
      animation.onfinish = () => {
        if (formAnimation !== animation) return;
        panel.hidden = !open;
        animation.cancel();
        formAnimation = null;
      };
    };
    trigger.onclick = () => showForm(trigger.getAttribute('aria-expanded') !== 'true');
    panel.onkeydown = event => {
      if (event.key === 'Escape' && !submit.disabled) {
        event.preventDefault();
        showForm(false);
      }
    };
    panel.onsubmit = async event => {
      event.preventDefault();
      if (submit.disabled) return;
      submit.disabled = true;
      trigger.disabled = true;
      panel.elements.note.readOnly = true;
      panel.setAttribute('aria-busy', 'true');
      let saved = false;
      try {
        await save(await quote(selection, true), 'bookmark', panel.elements.note.value);
        saved = true;
        feedback('회사 즐겨찾기에 저장했습니다.');
      } catch (error) { feedback(error.message, true); }
      finally {
        submit.disabled = false;
        trigger.disabled = false;
        panel.elements.note.readOnly = false;
        panel.removeAttribute('aria-busy');
        if (saved && wrapper.isConnected) showForm(false);
      }
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
  async function takeRestore(id) {
    const matchesRoute = item => {
      if (!item?.selection) return false;
      const routeId = item.custom ? item.selection.productId : item.selection.seriesId;
      const path = root + (item.custom ? '/products/' : '/series/') + routeId;
      return String(routeId) === String(id) && location.pathname.replace(/\/$/, '') === path;
    };
    const token = params().get('resume');
    const snapshot = history.state?.storeRestore;
    let reference;
    if (snapshot?.owner === prefix && matchesRoute(snapshot.item)) reference = snapshot;
    else {
      const pending = stored('restore', null, sessionStorage);
      if (pending?.token === token && token && matchesRoute(pending.item)) {
        reference = {...pending, owner: prefix};
        delete reference.token;
        history.replaceState({...history.state, storeRestore: reference}, '');
        sessionStorage.removeItem(prefix + 'restore');
      } else if (!token && !(pending && !pending.token && matchesRoute(pending))) return null;
    }
    if (!reference?.stamp || !(reference.source === 'cart' ? reference.key : reference.id)) {
      throw new Error('저장한 항목을 식별할 수 없습니다. 원래 목록에서 제품을 다시 열어 주세요.');
    }
    const {source, stamp} = reference;
    const row = source === 'cart' ? cart().find(record => record.key === reference.key) :
      await call('/saved/' + encodeURIComponent(reference.id));
    if (!row || (source === 'interest' && row.kind !== 'interest')) {
      throw new Error('목록에서 이 항목을 찾을 수 없습니다. 저장 목록을 확인해 주세요.');
    }
    if (!matchesRoute(row.item) || row.item.stamp !== stamp) {
      throw new Error('목록의 항목이 변경되었습니다. 현재 항목을 목록에서 다시 열어 주세요.');
    }
    return {source, row};
  }
  function removeCart(keys) {
    const set = new Set(keys);
    return changeCart(rows => write('cart', rows.filter(row => !set.has(row.key))));
  }
  function img(item) {
    const src = typeof item.image === 'string' ? item.image.trim() : '';
    const sample = src.startsWith('/administration/assets/images/customer-order-example/');
    return '<img data-store-photo ' + (src ? 'src="' + esc(src) + '"' : 'class="store-no-photo"') +
      (sample ? ' title="제품 예시 이미지"' : '') + ' alt="" loading="lazy" decoding="async">';
  }
  main.addEventListener('error', event => {
    const photo = event.target;
    if (!photo.matches?.('img[data-store-photo]')) return;
    photo.classList.add('store-no-photo');
    photo.removeAttribute('src');
    photo.title = '이미지를 불러올 수 없습니다';
  }, true);
  main.addEventListener('load', event => {
    const photo = event.target;
    if (!photo.matches?.('img[data-store-photo]')) return;
    photo.classList.remove('store-no-photo');
    photo.title = photo.getAttribute('src')?.includes('/customer-order-example/') ? '제품 예시 이미지' : '';
  }, true);
  function shell(title, body) {
    document.title = title + ' · 히든바스';
    main.innerHTML = '<section class="store-page"><header><h1>' + esc(title) + '</h1></header>' + body + '</section>';
  }
  function restore(row) {
    const token = crypto.randomUUID();
    const {custom, selection} = row.item;
    const source = row.kind === 'interest' ? 'interest' : 'cart';
    const stamp = row.sourceStamp || row.item.stamp;
    write('restore', {token, source, id: row.id, key: row.key, stamp,
      item: {custom, selection: {productId: selection.productId, seriesId: selection.seriesId}}}, sessionStorage);
    const url = new URL(root + (custom ? '/products/' + selection.productId : '/series/' + selection.seriesId), location.origin);
    if (custom) url.searchParams.set('kind', 'custom');
    url.searchParams.set('resume', token);
    url.searchParams.set('from', source);
    location.href = url.href;
  }
  function emptyList(title, description, destination = root, label = '쇼핑몰 둘러보기', symbol = 'cart') {
    return '<section class="store-empty">' + icon(symbol) + '<h2>' + esc(title) + '</h2><p>' +
      esc(description) + '</p><a class="store-btn" href="' + esc(destination) + '">' + esc(label) + '</a></section>';
  }
  function listDate(value) {
    if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T.*(?:Z|[+-]\d{2}:\d{2})$/.test(value)) {
      const instant = new Date(value);
      if (!Number.isNaN(instant.getTime())) {
        value = new Intl.DateTimeFormat('sv-SE', {
          year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'Asia/Seoul'
        }).format(instant);
      }
    }
    const parts = Array.isArray(value) ? value : /^(\d{4})[-.,]\s*(\d{1,2})[-.,]\s*(\d{1,2})(?:$|T)/.exec(String(value))?.slice(1);
    if (!parts || parts.length !== 3) return '확인 필요';
    const [year, month, day] = parts.map(Number);
    const date = new Date(Date.UTC(year, month - 1, day));
    if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return '확인 필요';
    return year + '.' + String(month).padStart(2, '0') + '.' + String(day).padStart(2, '0') + ' (' + '일월화수목금토'[date.getUTCDay()] + ')';
  }
  let listObserver;
  let listVersion = 0;
  let refreshVisibleList;
  let rememberVisibleList;
  window.addEventListener('pagehide', () => rememberVisibleList?.());
  window.addEventListener('store:external-list-change', event => refreshVisibleList?.(event.detail?.kind));
  window.addEventListener('pageshow', event => { if (event.persisted) refreshVisibleList?.('all'); });
  async function listPage(mode, limit = 10, selectedKeys, focusIndex, expandedKey, focusedKey, report) {
    const version = ++listVersion;
    const route = location.href;
    listObserver?.disconnect();
    if (!selectedKeys) {
      const snapshot = history.state?.storeList;
      const previous = snapshot?.owner === prefix && snapshot.mode === mode ? snapshot : null;
      selectedKeys = new Set(Array.isArray(previous?.selected) ? previous.selected : []);
      if (Number.isSafeInteger(previous?.displayed) && previous.displayed > 0) limit = previous.displayed;
      expandedKey = previous?.expanded;
    }
    const names = {cart: '장바구니', interest: '관심상품', bookmark: '즐겨찾기'};
    const rows = mode === 'cart' ? cart() : (await call('/saved?kind=' + mode)).filter(row => row.kind === mode);
    if (version !== listVersion || location.href !== route) return;
    let displayed = Math.min(limit, rows.length);
    const keyOf = row => mode === 'cart' ? row.key : row.id;
    const rowKeys = new Set(rows.map(keyOf));
    for (const key of selectedKeys) if (!rowKeys.has(key)) selectedKeys.delete(key);
    rows.forEach((row, index) => {
      const key = keyOf(row);
      if (index >= displayed && (selectedKeys.has(key) || key === expandedKey || key === focusedKey)) {
        displayed = Math.min(rows.length, Math.ceil((index + 1) / 10) * 10);
      }
    });
    const hints = {
      cart: '제품 옵션을 구성한 뒤 장바구니에 담아 주세요.',
      interest: '관심 있는 구성을 제품 상세에서 저장해 주세요.',
      bookmark: '비규격 제품을 구성하다가 즐겨찾기에 저장해 주세요.'
    };
    const hasPrice = item => item.price != null && String(item.price).trim() !== '' &&
      Number.isFinite(Number(item.price)) && Number(item.price) >= 0;
    const renderRow = (row, i) => {
      if (!Number.isSafeInteger(Number(row.item?.selection?.productId)) || Number(row.item.selection.productId) <= 0) {
        return '<article class="store-row store-saved-row"><div><strong>저장 구성 확인 필요</strong><p>이 항목의 옵션을 복원할 수 없습니다.</p>' +
          (row.note ? '<p>' + esc(row.note) + '</p>' : '') + '</div><button type="button" class="store-btn" data-remove="' + i + '">삭제</button></article>';
      }
      const item = row.item;
      const resumeUrl = mode === 'bookmark' ? root + '/products/' + encodeURIComponent(item.selection.productId) +
        '?kind=custom&bookmark=' + encodeURIComponent(row.id) : '';
      const title = resumeUrl ? '<a class="store-link" href="' + esc(resumeUrl) + '">' + esc(item.name) + '</a>' :
        '<button type="button" class="store-link" data-open="' + i + '">' + esc(item.name) + '</button>';
      const fields = [['구분', item.custom ? '비규격' : '규격'], ['시리즈', '조회 중…', 'series'],
        ['대분류', '조회 중…', 'category'], ['생산기간', '조회 중…', 'productionDays'],
        ['가장 빠른 발주 가능일', '조회 중…', 'earliest']];
      const extra = [];
      if (mode === 'cart') {
        extra.push(['수량', (row.quantity || 1) + '개']);
        if (row.quantity > 1 && hasPrice(item)) extra.push(['품목 상품금액',
          won(item.price) + ' × ' + row.quantity + '개 = ' + won(Number(item.price) * row.quantity)]);
      }
      if (row.createdAt) extra.push(['저장일', listDate(row.createdAt)]);
      if (row.note) extra.push(['남긴 말', row.note]);
      for (const answer of Array.isArray(item.summary) ? item.summary : []) {
        const text = String(answer), split = text.indexOf(' · ');
        extra.push(split < 0 ? ['옵션', text] : [text.slice(0, split), text.slice(split + 3)]);
      }
      const pairs = entries => entries.map(([label,value,fact]) => '<div><dt>' + esc(label) + '</dt><dd' +
        (fact ? ' data-fact="' + fact + '"' : '') + '>' + esc(value) + '</dd></div>').join('');
      const panelId = 'saved-options-' + mode + '-' + encodeURIComponent(keyOf(row));
      return '<article class="store-row store-saved-row saved-product"><div class="saved-product-head">' +
        (mode === 'bookmark' ? '' : '<label class="store-select-item"><input type="checkbox" data-index="' + i + '" aria-label="' + esc(item.name) + ' 선택"></label>') +
        img(item) + '<div class="saved-product-title">' + title +
        '<div class="store-row-price">' + (hasPrice(item) ? (mode === 'cart' && row.quantity > 1 ? '단가 ' : '') + won(item.price) : '가격 미확정') + '</div></div>' +
        (resumeUrl ? '<a class="store-btn store-resume" aria-label="' + esc(item.name) + ' 저장한 단계에서 이어하기" href="' + esc(resumeUrl) + '">이어하기 <span aria-hidden="true">→</span></a>' : '') +
        '<button class="store-icon" type="button" data-remove="' + i + '" aria-label="' + esc(item.name) + ' 삭제">' + icon('close') + '</button></div>' +
        '<dl class="store-product-facts" aria-busy="true">' + pairs(fields) + '</dl>' +
        (extra.length ? '<button type="button" class="saved-options-toggle" aria-label="' + esc(item.name) + ' 상세 정보" aria-expanded="false" aria-controls="' + esc(panelId) + '">상세 정보</button>' +
          '<div class="saved-options-panel" id="' + esc(panelId) + '" inert><div><dl class="store-answer-detail">' + pairs(extra) + '</dl></div></div>' : '') +
        '</article>';
    };
    shell(names[mode], rows.length
      ? '<div class="store-list"></div><div class="store-more"><p role="status"></p>' +
        '<button type="button" class="store-btn" hidden>더보기</button></div>'
      : emptyList('저장된 ' + names[mode] + ' 항목이 없습니다', hints[mode], root, '쇼핑몰 둘러보기', mode));
    const host = main.querySelector('.store-page');
    host.classList.add('store-saved-page');
    if (report) host.querySelector('header').after(report);
    let busy = false;
    let refreshQueued = false;
    let focusAfterRemoval;
    rememberVisibleList = () => {
      if (!host.isConnected || location.href !== route) return;
      const cards = Array.from(host.querySelectorAll('.store-list > article'));
      const open = rows[cards.indexOf(host.querySelector('.saved-options-toggle[aria-expanded=true]')?.closest('article'))];
      try {
        history.replaceState({...history.state, storeList: {owner: prefix, mode, displayed,
          selected: [...selectedKeys], expanded: open && keyOf(open)}}, '');
      } catch (_) { /* Browsers may restrict history updates during pagehide. */ }
    };
    host.addEventListener('change', () => rememberVisibleList());
    refreshVisibleList = kind => {
      if (!host.isConnected || (kind !== 'all' && kind !== (mode === 'cart' ? 'cart' : 'saved'))) return;
      if (busy) { refreshQueued = true; return; }
      const cards = [...host.querySelectorAll('.store-list > article')];
      const open = rows[cards.indexOf(host.querySelector('.saved-options-toggle[aria-expanded=true]')?.closest('article'))];
      const index = cards.indexOf(document.activeElement?.closest('article'));
      void listPage(mode, Math.max(displayed, 10), selectedKeys, index < 0 ? undefined : index,
        open && keyOf(open), index < 0 ? undefined : keyOf(rows[index]), host.querySelector('[data-check-report]')?.cloneNode(true))
        .catch(error => feedback(error.message, true));
    };
    if (!rows.length) {
      if (focusIndex != null) main.querySelector('.store-empty a')?.focus({preventScroll: true});
      return;
    }

    const list = host.querySelector('.store-list');
    const footer = host.querySelector('.store-more');
    const more = footer.querySelector('button');
    const status = footer.querySelector('p');
    const selection = document.createElement('div');
    selection.className = 'store-selection-head';
    host.querySelector('header').after(selection);
    selection.append(status);
    const changed = new Map();
    let updateAll = () => {};
    let bindCheck = () => {};
    function syncControls(pending) {
      busy = pending;
      host.setAttribute('aria-busy', String(pending));
      if (list.isConnected) {
        list.querySelectorAll('[data-remove], [data-open], [data-cleanup]').forEach(control => { control.disabled = pending; });
      }
      more.disabled = pending;
      updateAll();
      if (!pending && focusAfterRemoval != null) {
        if (document.activeElement === document.body) focusRow(focusAfterRemoval, true);
        focusAfterRemoval = undefined;
      }
      if (!pending && refreshQueued) {
        refreshQueued = false;
        refreshVisibleList('all');
      }
      if (!pending) rememberVisibleList();
    }
    function focusRow(index, preventScroll = false) {
      list.children[index]?.querySelector('a.store-link[href], button.store-link:not(:disabled), [data-remove]:not(:disabled)')?.focus({preventScroll});
    }
    function showCleanupRetry(card, reason) {
      card.querySelector('[data-config-changed]')?.remove();
      card.insertAdjacentHTML('beforeend', '<p class="store-row-notice" data-config-changed>' +
        esc(reason) + ' <button type="button" class="store-link" data-cleanup>다시 확인하고 정리</button></p>');
    }
    function removeDisplayedRows(removed) {
      if (!removed.size) return;
      const index = rows.findIndex(row => removed.has(keyOf(row)));
      if (index < 0) return;
      for (let i = rows.length - 1; i >= 0; i--) {
        const key = keyOf(rows[i]);
        if (!removed.has(key)) continue;
        selectedKeys.delete(key);
        changed.delete(key);
        list.children[i]?.remove();
        rows.splice(i, 1);
      }
      if (!rows.length) {
        listObserver?.disconnect();
        host.replaceChildren(host.querySelector('header'));
        host.insertAdjacentHTML('beforeend', emptyList('저장된 ' + names[mode] + ' 항목이 없습니다', hints[mode], root, '쇼핑몰 둘러보기', mode));
        host.querySelector('.store-empty a')?.focus({preventScroll: true});
        return;
      }
      displayed = Math.min(displayed, rows.length);
      Array.from(list.children).forEach((card, currentIndex) => {
        for (const attr of ['data-index', 'data-remove', 'data-open']) {
          card.querySelector('[' + attr + ']')?.setAttribute(attr, String(currentIndex));
        }
      });
      appendRows(list.children.length, displayed);
      focusAfterRemoval = Math.min(index, displayed - 1);
    }
    async function verifyRow(row) {
      try {
        const item = await check(row.item, mode === 'bookmark');
        return {valid: {...row, item, sourceStamp: row.sourceStamp || row.item.stamp}};
      }
      catch (error) {
        if (error.status !== 409 || error.invalid !== true) return {row, error: error.message || '현재 구성을 확인하지 못했습니다. 다시 시도해 주세요.'};
        try {
          const removed = await purgeInvalidRow(row, mode);
          return {row, reason: error.message, removed};
        } catch (cleanupError) {
          return {row, reason: error.message, cleanupError: cleanupError.message || '목록에 다시 연결해 주세요.'};
        }
      }
    }
    async function verifySelected(selected) {
      const results = [];
      const progress = host.querySelector('[data-amount-label]');
      for (let offset = 0; offset < selected.length; offset += 4) {
        if (progress && selected.length > 1) progress.textContent = '구성 확인 중 ' + offset + '/' + selected.length;
        results.push(...await Promise.all(selected.slice(offset, offset + 4).map(verifyRow)));
        if (refreshQueued || !host.isConnected || location.href !== route) break;
      }
      if (!host.isConnected || location.href !== route) return null;
      const removed = new Set(), messages = [];
      for (const result of results) {
        if (result.valid) {
          const row = result.valid, key = keyOf(row);
          if (changed.delete(key)) list.children[rows.findIndex(current => keyOf(current) === key)]?.querySelector('[data-config-changed]')?.remove();
          continue;
        }
        const {row, reason, cleanupError, error} = result;
        const key = keyOf(row);
        const detail = row.note || (Array.isArray(row.item?.summary) ? row.item.summary.slice(0, 2).join(' / ') : '');
        const name = (row.item?.name || '저장한 품목') + (detail ? ' (' + detail + ')' : '');
        if (error) messages.push(name + ' · ' + error);
        else if (cleanupError) {
          const message = reason + ' 목록 정리에 실패했습니다. ' + cleanupError;
          changed.set(key, message);
          selectedKeys.delete(key);
          const card = list.children[rows.indexOf(row)];
          const checkbox = card?.querySelector('[data-index]');
          if (checkbox) checkbox.checked = false;
          if (card) showCleanupRetry(card, message);
          messages.push(name + ' · ' + message);
        } else if (result.removed === true) {
          removed.add(key);
          messages.push(name + ' · ' + reason + ' ' + names[mode] + '에서 해당 구성을 삭제했습니다.');
        } else {
          refreshQueued = true;
          messages.push(name + ' · 저장 항목을 유지했습니다. 최신 구성을 다시 확인해 주세요.');
        }
      }
      removeDisplayedRows(removed);
      if (messages.length) {
        host.querySelector('[data-check-report]')?.remove();
        const report = document.createElement('section');
        report.dataset.checkReport = '';
        report.tabIndex = -1;
        report.setAttribute('aria-label', '저장 구성 확인 결과');
        report.innerHTML = messages.map(message => '<p class="store-row-notice">' + esc(message) + '</p>').join('');
        host.querySelector('header').after(report);
        report.focus();
        return null;
      }
      if (refreshQueued || results.length !== selected.length) {
        feedback('목록이 변경되었습니다. 선택한 품목을 다시 확인해 주세요.', true);
        return null;
      }
      return results.map(result => result.valid);
    }
    if (mode !== 'bookmark' && rows.length) {
      const bar = document.createElement('div');
      selection.insertAdjacentHTML('afterbegin', '<label><input type="checkbox" id="store-select-all"> 표시된 품목 전체 선택</label><span data-count></span>');
      bar.className = 'store-order-footer';
      bar.innerHTML = '<span class="store-order-amount" role="status" aria-atomic="true"><span data-amount-label></span><strong data-total></strong></span><div class="store-order-buttons">' +
        (mode === 'interest' ? button('cart', '장바구니 담기') : '') + button('order', '발주하기', true) + '</div>';
      footer.after(bar);
      const all = selection.querySelector('#store-select-all');
      const actions = [...bar.querySelectorAll('[data-action]')];
      const checks = () => [...list.querySelectorAll('[data-index]')];
      updateAll = () => {
        if (!list.isConnected) return;
        const visibleChecks = checks().filter(el => {
          const key = keyOf(rows[Number(el.dataset.index)]);
          const unavailable = changed.has(key);
          el.disabled = busy || unavailable;
          return !unavailable;
        });
        all.disabled = busy || !visibleChecks.length;
        all.checked = visibleChecks.length > 0 && visibleChecks.every(el => el.checked);
        const selected = visibleChecks.filter(el => el.checked);
        const count = selected.length;
        all.indeterminate = !all.checked && count > 0;
        const picked = selected.map(el => rows[Number(el.dataset.index)]);
        const subtotal = picked.every(row => hasPrice(row.item))
          ? won(picked.reduce((total, row) => total + Number(row.item.price) * (row.quantity || 1), 0))
          : '가격 확인 필요';
        const countLabel = count + '개 선택';
        selection.querySelector('[data-count]').textContent = countLabel;
        const label = bar.querySelector('[data-amount-label]'), amount = bar.querySelector('[data-total]');
        if (label.textContent !== countLabel + ' · 상품금액') label.textContent = countLabel + ' · 상품금액';
        if (amount.textContent !== subtotal) amount.textContent = subtotal;
        actions.forEach(action => { action.disabled = busy || !count; });
      };
      const updateSelection = el => {
        const key = keyOf(rows[Number(el.dataset.index)]);
        if (el.checked) selectedKeys.add(key);
        else selectedKeys.delete(key);
      };
      all.onchange = () => {
        checks().filter(el => !el.disabled).forEach(el => { el.checked = all.checked; updateSelection(el); });
        updateAll();
      };
      bindCheck = el => {
        if (!el) return;
        el.checked = selectedKeys.has(keyOf(rows[Number(el.dataset.index)]));
        el.onchange = () => { updateSelection(el); updateAll(); };
      };
      bindButtons(bar, async action => {
        if (busy) return;
        const selected = rows.slice(0, displayed).filter(row => selectedKeys.has(keyOf(row)));
        if (!selected.length) throw new Error('품목을 선택해 주세요.');
        if (action === 'order' && selected.length > 100) throw new Error('한 번에 발주할 수 있는 품목은 최대 100개입니다.');
        syncControls(true);
        try {
          const valid = await verifySelected(selected);
          if (!valid) return;
          if (action === 'cart') await cartAdd(valid.map(row => row.item));
          else startCheckout(valid.map(row => ({key: row.key || crypto.randomUUID(), item: row.item, sourceStamp: row.sourceStamp,
            quantity: row.quantity || 1, fromCart: mode === 'cart', savedId: mode === 'interest' ? row.id : undefined})));
        } finally {
          syncControls(false);
        }
      }, updateAll);
    }
    const metadata = new Map();
    function fillProductFacts(entries) {
      const ids = [...new Set(entries.map(entry => entry.item.selection.productId))].filter(id => !metadata.has(id));
      for (let start = 0; start < ids.length; start += 10) {
        const batch = ids.slice(start, start + 10);
        const request = call('/items/metadata', 'POST', {productIds: batch});
        batch.forEach(id => metadata.set(id, request.then(data => data[id])));
      }
      for (const {card, item} of entries) {
        const id = item.selection.productId;
        const request = metadata.get(id);
        request.then(data => {
          if (!card.isConnected) return;
          const facts = data && {
            category: data.category || '분류 확인 필요', series: data.series || '미등록',
            productionDays: data.productionDays == null || data.productionDays < 0
              ? '기본 2영업일' : data.productionDays + '일',
            earliest: data.earliest == null ? '공휴일 일정 확인 필요' : listDate(data.earliest)
          };
          card.querySelectorAll('[data-fact]').forEach(field => { field.textContent = facts?.[field.dataset.fact] || '확인 필요'; });
          card.querySelector('.store-product-facts')?.setAttribute('aria-busy', 'false');
          const image = card.querySelector('img');
          if (image && data?.image && image.getAttribute('src') !== data.image) image.src = data.image;
          card.querySelector('[data-meta-notice]')?.remove();
          if (!data) {
            card.insertAdjacentHTML('beforeend', '<p class="store-row-notice" data-meta-notice>현재 판매 목록에서 찾을 수 없습니다. 저장한 구성을 열거나 사용할 때 변경 여부를 확인합니다.</p>');
          }
        }).catch(() => {
          if (metadata.get(id) === request) metadata.delete(id);
          if (!card.isConnected) return;
          card.querySelectorAll('[data-fact]').forEach(field => { field.textContent = '조회 실패'; });
          card.querySelector('.store-product-facts')?.setAttribute('aria-busy', 'false');
          if (!card.querySelector('[data-meta-notice]')) card.insertAdjacentHTML('beforeend',
            '<p class="store-row-notice" data-meta-notice>현재 제품 정보를 불러오지 못했습니다. <button type="button" class="store-link" data-meta-retry>다시 조회</button></p>');
          const retry = card.querySelector('[data-meta-retry]');
          if (retry) retry.disabled = false;
        });
      }
    }
    function appendRows(from, to) {
      const template = document.createElement('template');
      template.innerHTML = rows.slice(from, to).map((row, i) => renderRow(row, from + i)).join('');
      const entries = [];
      Array.from(template.content.children).forEach((card, index) => {
        const item = rows[from + index].item;
        if (card.classList.contains('saved-product')) entries.push({card, item});
        const reason = changed.get(keyOf(rows[from + index]));
        if (reason) showCleanupRetry(card, reason);
      });
      template.content.querySelectorAll('[data-index]').forEach(bindCheck);
      if (from > 0) template.content.querySelectorAll('.store-saved-row').forEach((row, index) => {
        row.classList.add('store-row-enter');
        row.style.setProperty('--store-enter-delay', Math.min(index, 4) * 18 + 'ms');
      });
      list.append(template.content);
      fillProductFacts(entries);
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
      const retry = event.target.closest('[data-meta-retry]');
      if (retry) {
        retry.disabled = true;
        const card = retry.closest('article');
        card.querySelector('.store-product-facts')?.setAttribute('aria-busy', 'true');
        fillProductFacts([{card, item: rows[Array.from(list.children).indexOf(card)].item}]);
        return;
      }
      const toggle = event.target.closest('.saved-options-toggle');
      if (toggle) {
        const expand = toggle.getAttribute('aria-expanded') !== 'true';
        for (const other of list.querySelectorAll('.saved-options-toggle[aria-expanded="true"]')) {
          if (other !== toggle) expandOptions(other, false);
        }
        expandOptions(toggle, expand);
        rememberVisibleList();
        return;
      }
      const action = event.target.closest('[data-remove],[data-open],[data-cleanup]');
      if (!action || busy || action.disabled) return;
      action.dataset.busy = 'true';
      action.setAttribute('aria-busy', 'true');
      syncControls(true);
      try {
        if (action.hasAttribute('data-remove')) {
          const index = Number(action.dataset.remove);
          const row = rows[index];
          if (mode === 'cart') await removeCart([row.key]);
          else {
            if (!window.confirm('회사 ' + names[mode] + '에서 ' + (row.item?.name || '이 항목') + ' 구성을 삭제할까요?')) return;
            await call('/saved/' + row.id, 'DELETE');
          }
          removeDisplayedRows(new Set([keyOf(row)]));
          feedback((row.item?.name || '저장한 항목') + ' 삭제를 완료했습니다.');
        } else {
          const index = Array.from(list.children).indexOf(action.closest('article'));
          const verified = await verifySelected([rows[index]]);
          if (!verified) return;
          if (action.hasAttribute('data-cleanup')) {
            host.querySelector('[data-check-report]')?.remove();
            feedback('현재 저장 구성을 다시 사용할 수 있습니다.');
          }
          else restore(verified[0]);
        }
      } catch (error) { feedback(error.message, true); }
      finally {
        delete action.dataset.busy;
        action.removeAttribute('aria-busy');
        syncControls(false);
        if (action.isConnected && document.activeElement === document.body) action.focus({preventScroll: true});
      }
    });
    const loadMore = manual => {
      if (busy || !host.isConnected || displayed >= rows.length) return;
      const start = displayed;
      displayed = Math.min(start + 10, rows.length);
      appendRows(start, displayed);
      rememberVisibleList();
      if (manual) focusRow(start);
      if (displayed >= rows.length) listObserver?.disconnect();
    };
    appendRows(0, displayed);
    if (expandedKey != null) {
      const toggle = list.children[rows.findIndex(row => keyOf(row) === expandedKey)]?.querySelector('.saved-options-toggle');
      if (toggle) expandOptions(toggle, true);
    }
    if (focusIndex != null) {
      const retained = focusedKey == null ? -1 : rows.findIndex(row => keyOf(row) === focusedKey);
      const index = retained >= 0 && retained < displayed ? retained : Math.min(focusIndex, displayed - 1);
      focusRow(index, true);
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
    const summary = compact.join(' | ');
    return (summary ? '<p class="store-option-preview">' + esc(summary) + '</p>' : '') +
      '<details class="store-config-detail"><summary>선택 옵션 전체 보기 (' + answers.length + ')</summary>' +
      '<dl class="store-answer-detail">' + answers.map(answer => {
        const text = String(answer);
        const split = text.indexOf(' · ');
        return '<div><dt>' + esc(split < 0 ? '옵션' : text.slice(0, split)) +
          '</dt><dd>' + esc(split < 0 ? text : text.slice(split + 3)) + '</dd></div>';
      }).join('') + '</dl></details>';
  }

  function deliveryAddressKey(address) {
    const clean = value => {
      const text = String(value || '').trim().replace(/\s+/g, ' ');
      return /^[-–—]$/.test(text) ? '' : text;
    };
    const base = clean(address?.road) || clean(address?.jibun);
    const [first, ...rest] = base.split(' ');
    const province = first && window.HiddenAutoAddressRegion?.normalize?.(first, '', '', '')?.doName;
    return JSON.stringify([clean(address?.zip), [province || first, ...rest].join(' '), clean(address?.detail)]);
  }

  function deliveryGroups(lines) {
    const grouped = new Map();
    for (const line of lines) {
      // 새 서버는 배송비 계산에 실제 사용한 묶음의 첫 품목 키를 반환합니다.
      const key = line.shippingGroup || JSON.stringify([line.date, line.method, deliveryAddressKey(line.address)]);
      if (!grouped.has(key)) grouped.set(key, {lines:[], number:grouped.size + 1});
      grouped.get(key).lines.push(line);
    }
    return [...grouped.values()];
  }

  function shippingGroupTitle(group, count) {
    if (count === 1) return group.lines.length > 1 ? '전체 품목 공통 배송' : '개별 배송';
    return (group.lines.length > 1 ? '묶음 배송 ' : '개별 배송 ') + group.number;
  }

  function confirmedFee(result, field) {
    if (result.lines.every(line => line[field] == null)) return '미확정 · 관리자 안내 예정';
    return won(result[field]) + (result.lines.some(line => line[field] == null) ? ' (미확정분 별도)' : '');
  }

  function shippingBreakdown(groups, section) {
    const titleTag = section === 'result' ? 'h2' : 'h3';
    const groupTag = section === 'result' ? 'h3' : 'h4';
    const cards = groups.map(group => {
      const first = group.lines[0], address = first.address;
      const fee = key => group.lines.some(line => line[key] == null) ? null
        : group.lines.reduce((sum, line) => sum + Number(line[key]), 0);
      const delivery = fee('delivery'), packing = fee('packing');
      const cost = value => value == null ? '미확정' : won(value);
      const subtotal = delivery == null || packing == null ? '관리자 확인 예정' : won(delivery + packing);
      const names = group.lines.map(line => '<li><strong>' + esc(line.item.name) + '</strong><span>수량 ' +
        esc(line.quantity) + '개</span></li>').join('');
      const place = [address?.road || address?.jibun, address?.detail, address?.zip && '(' + address.zip + ')'].filter(Boolean).join(' ');
      return '<article class="store-delivery-group" id="' + section + '-delivery-' + group.number + '" tabindex="-1"><header><' + groupTag + '>' +
        esc(shippingGroupTitle(group, groups.length)) + '</' + groupTag + '><strong>' + esc(subtotal) + '</strong></header><p class="store-group-destination">' +
        esc([first.method, listDate(first.date), place].filter(Boolean).join(' · ')) +
        '</p><p class="store-group-fees">배송비 ' + cost(delivery) + ' · 포장비 ' + cost(packing) +
        '</p><details><summary>배송 품목 ' + group.lines.length + '개 · 계산 근거</summary><div>' +
        '<ul class="store-fee-lines">' + names + '</ul><p>' + esc(first.explanation) + '</p></div></details></article>';
    }).join('');
    return '<section class="store-delivery-groups" aria-labelledby="' + section + '-fees-title"><' + titleTag +
      ' id="' + section + '-fees-title">배송비 계산 내역</' + titleTag + '>' + cards + '</section>';
  }

  function checkoutDays(value) {
    if (value == null || String(value).trim() === '') return null;
    const number = Number(value);
    return Number.isFinite(number) && number >= 0 ? Math.ceil(number) : null;
  }

  function checkoutEarliest(item, today, holidays, through) {
    if (!today) return item.earliest;
    if (through && today > through) return null;
    const date = new Date(today + 'T00:00:00Z');
    let remaining = checkoutDays(item.productionDays) ?? 2;
    const iso = () => date.toISOString().slice(0, 10);
    const working = () => date.getUTCDay() !== 0 && date.getUTCDay() !== 6 && !holidays.has(iso());
    while (remaining > 0 || !working()) {
      date.setUTCDate(date.getUTCDate() + 1);
      if (through && iso() > through) return null;
      if (remaining > 0 && working()) remaining--;
    }
    return iso();
  }

  let leaveCheckout;
  async function checkoutPage(cachedOptions, recovered) {
    leaveCheckout?.();
    const rows = recovered?.rows ?? stored('checkout', [], sessionStorage);
    const history = recovered?.notices ?? stored('checkoutChanges', [], sessionStorage);
    const notices = Array.isArray(history) ? history.filter(entry => typeof entry?.text === 'string') : [];
    const noticeHtml = notices.length ? '<div role="status" class="store-check-changes">' + notices.map(entry =>
      entry.reason ? '<details class="store-validation"><summary>' + esc(entry.text) + '</summary><p>' + esc(entry.reason) +
        '</p>' + (entry.selection ? '<p>저장한 옵션 · ' + esc(entry.selection) + '</p>' : '') + '</details>'
        : '<p class="store-row-notice">' + esc(entry.text) + '</p>').join('') + '</div>' : '';

    if (!Array.isArray(rows) || rows.some(row => !row?.item || !row.key)) {
      shell('발주하기', emptyList('발주 품목을 다시 선택해 주세요', '현재 탭의 발주 품목 구성을 읽을 수 없습니다.', root + '?view=cart', '장바구니로 이동'));
      return;
    }
    if (!rows.length) { shell('발주하기', noticeHtml + emptyList('선택한 발주 품목이 없습니다', '장바구니에서 발주할 품목을 선택해 주세요.', root + '?view=cart', '장바구니로 이동')); return; }
    const options = cachedOptions || await call('/checkout');
    if (!options.methods.length) {
      shell('발주하기', emptyList('등록된 배송수단이 없습니다',
        '관리자가 배송수단을 등록하면 발주를 진행할 수 있습니다. 선택한 품목은 유지됩니다.',
        root + '?view=cart', '장바구니로 이동'));
      return;
    }
    const holidays = new Set(options.holidays);
    const dates = rows.map(row => checkoutEarliest(row.item, options.today, holidays, options.calendarThrough));
    const methods = '<option value="">배송수단 선택</option>' + options.methods.map(m =>
      '<option value="' + esc(m.id) + '">' + esc(m.name) + '</option>').join('');
    const shipping = '<label>배송수단<select name="method" required>' + methods + '</select></label>';
    const contactFields = [['orderer','주문자 이름'],['phone','주문자 연락처'],['manager','현장 담당자 이름'],['managerPhone','현장 담당자 연락처']];
    const contacts = '<details class="store-contacts"><summary>주문자 · 현장 담당자 정보</summary><div class="store-fields">' +
      contactFields.map(([key,label]) => '<label>' + label + '<input name="' + key + '" maxlength="60" type="' + (key.toLowerCase().includes('phone') ? 'tel' : 'text') + '"></label>').join('') + '</div></details>';
    const address = '<section class="store-delivery-address"><h3>배송지</h3><input name="address" type="hidden">' +
      '<label>도로명주소<input name="road" readonly></label><label>지번주소<input name="jibun" readonly></label>' +
      '<label>상세주소<input name="addressDetail" maxlength="180" placeholder="건물명, 동·호수"></label>' +
      '<div class="store-address-tools"><label>우편번호<input name="postal" readonly></label>' +
      '<button type="button" class="store-btn" data-address-search>주소 검색</button><button type="button" class="store-btn" data-address-book>업체 주소록</button></div>' +
      '<small data-region></small></section>';
    const addressLabel = a => a ? [a.zip, a.road || a.jibun].filter(Boolean).join(' ') : '';
    const addressBook = '<dialog class="store-address-book" aria-labelledby="store-book-title"><header class="store-dialog-head"><h2 id="store-book-title">업체 주소록</h2></header><div></div><button type="button" class="store-btn" data-close>닫기</button></dialog>';
    const date = '<div class="store-date-field"><span>배송 희망일</span><input aria-label="배송 희망일" type="text" name="date" placeholder="달력에서 선택" required readonly></div>';
    const latest = dates.every(Boolean) ? [...dates].sort().at(-1) : null;
    shell('발주하기', noticeHtml + '<div class="store-bulk-launch"><div><strong>전체 ' + rows.length +
      '개 품목</strong><p>같은 배송 정보를 한 번에 입력하거나, 품목별로 다르게 지정할 수 있습니다.</p></div>' +
      '<button type="button" class="store-btn" id="store-bulk-open">배송 · 연락처 일괄 적용</button></div><details class="store-validation"><summary>배송일과 비용 산정 기준</summary>' +
      '<p>오늘을 제외한 영업일로 계산합니다. 예를 들어 1.5일은 2영업일로 올립니다. 주말과 등록된 공휴일은 선택할 수 없습니다. 일괄 배송 희망일은 품목별 가능일 중 가장 늦은 날짜부터 선택할 수 있습니다.</p>' +
      '<p>배송 희망일, 배송수단, 배송 주소가 같으면 배송비와 포장비를 각각 한 번 부과합니다. 포인트는 상품금액에만 적용됩니다. 발주 결과는 저장되지 않습니다.</p></details>' +
      '<dialog class="store-bulk store-bulk-dialog" aria-labelledby="store-bulk-title"><header class="store-dialog-head"><h2 id="store-bulk-title">배송 · 연락처 일괄 적용</h2></header>' +
      '<p>이번에 입력한 항목만 모든 품목에 적용합니다. 직배송 품목은 업체 주소를 사용합니다.</p><div class="store-fields"><label>품목당 수량<input name="quantity" type="number" min="1" max="99999" step="1" placeholder="기존 수량 유지"></label>' + shipping + date + address + contacts +
      '</div><p class="store-bulk-warning" role="status"></p><div class="store-actions"><button type="button" class="store-btn" id="store-bulk-close">닫기</button>' +
      '<button type="button" class="store-btn is-primary" id="store-apply">적용</button></div></dialog>' +
      '<form id="store-checkout"><div class="store-list">' + rows.map((row, index) => '<article class="store-check-line"><div class="store-row">' +
      img(row.item) + '<div><strong>' + esc(row.item.name) + '</strong><div class="checkout-price" data-item-total></div><small>' +
      '가장 빠른 배송 희망일 ' + esc(dates[index] ? listDate(dates[index]) : '공휴일 일정 확인 필요') + (checkoutDays(row.item.productionDays) == null ? ' · 기본 생산기간 2영업일 적용' : '') + '</small></div><span class="store-line-status"></span></div>' +
      checkoutOptions(row.item) + '<section class="store-check-details"><h3>품목별 배송 설정</h3><div class="store-fields">' +
      '<label>수량<input type="number" name="quantity" min="1" max="99999" required value="' + esc(row.quantity || 1) + '"></label>' +
      shipping + date + address + contacts + '</div><small class="store-method-note" aria-live="polite"></small></section><p class="store-line-overview" hidden></p></article>').join('') +
      '</div><section class="store-shipping-review"><p data-shared></p><h2>배송비 및 포장비</h2><button type="submit" class="store-btn" name="quote" formnovalidate>배송비 계산</button><div data-breakdown aria-live="polite"></div></section>' +
      '<section class="store-payment" hidden><h2>발주 금액 및 포인트</h2><p data-payable></p><label>사용 포인트<input type="number" name="points" value="0" min="0" step="1" disabled max="' +
      esc(Math.max(0, options.points)) + '"></label><small data-point-limit></small><button type="submit" name="confirm" class="store-btn is-primary">최종 미리보기 확인</button></section></form>'  + addressBook);
    const form = main.querySelector('#store-checkout');
    const lines = [...form.querySelectorAll('.store-check-line')];
    const bulk = main.querySelector('.store-bulk');
    const payment = form.querySelector('.store-payment');
    const breakdown = form.querySelector('[data-breakdown]');
    const points = form.elements.points;
    points.value = rows[0].draftPoints ?? 0;
    const quoteButton = form.querySelector('[name=quote]');
    let quoteVersion = 0, currentQuote = null, activeRequest = null, checkingChanges = false;
    function supersedeRequest() {
      quoteVersion++;
      if (!activeRequest) return;
      activeRequest.controller.abort();
      activeRequest.submit.disabled = false;
      activeRequest.submit.removeAttribute('aria-busy');
      activeRequest = null;
    }
    function resetQuote() {
      supersedeRequest();
      currentQuote = null;
      payment.hidden = true;
      points.disabled = true;
      breakdown.replaceChildren();
    }
    function showPayable() {
      if (!currentQuote) return;
      const amount = Number(points.value || 0);
      form.querySelector('[data-payable]').textContent = points.validity.valid
        ? (currentQuote.pending ? '현재 확정 금액 ' : '결제 예정 금액 ') + won(currentQuote.payable - amount)
        : '사용 포인트를 0부터 ' + Number(points.max).toLocaleString('ko-KR') + ' 사이의 정수로 입력해 주세요.';
    }
    function fieldChanged(event) {
      if (event.target === points || contactFields.some(([key]) => key === event.target.name)) {
        if (activeRequest?.confirming) supersedeRequest();
        if (event.target === points) showPayable();
      } else resetQuote();
    }
    form.addEventListener('input', fieldChanged);
    form.addEventListener('change', fieldChanged);
    const selectedAddress = new WeakMap();
    const alternateAddress = new WeakMap();
    const validAddress = address => /^\d{5}$/.test(address?.zip || '') && !!address?.province?.trim()
      && !!(address.road?.trim() || address.jibun?.trim())
      && [address.road, address.jibun, address.detail].every(value => !value || value.length <= 255);
    function setAddress(node, value) {
      if (node !== bulk && !applying) resetQuote();
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
      if (method.value === '1' && validAddress(address) && addressLabel(address) === addressLabel(options.companyAddress)
          && (address.detail || '') === (options.companyAddress?.detail || '')) {
        setAddress(node, options.companyAddress);
        return;
      }
      if (method.value === '1') {
        method.value = '';
        method.dispatchEvent(new Event('change'));
        feedback('배송지를 변경했습니다. 이 주소로 받을 배송수단을 선택해 주세요.');
      }
      setAddress(node, address);
      alternateAddress.set(node, address);
    }
    const book = main.querySelector('.store-address-book');
    book.querySelector('[data-close]').onclick = () => book.close();
    for (const container of [bulk, ...lines]) {
      container.querySelector('[data-address-book]').onclick = () => {
        const index = lines.indexOf(container);
        book.querySelector('h2').textContent = index < 0 ? '전체 품목에 적용할 배송지' : (index + 1) + '. ' + rows[index].item.name + ' 배송지';
        const list = book.querySelector('div');
        list.replaceChildren();
        const unique = new Map();
        for (const address of [options.companyAddress, ...options.addresses]) {
          if (!address?.road && !address?.jibun) continue;
          const key = deliveryAddressKey(address);
          if (!unique.has(key) || !validAddress(unique.get(key))) unique.set(key, address);
        }
        const entries = [...unique.values()];
        if (!entries.length) list.textContent = '등록된 업체 주소가 없습니다.';
        for (const a of entries) {
          const button = document.createElement('button');
          button.type = 'button'; button.className = 'store-btn';
          const title = document.createElement('strong');
          title.textContent = a === options.companyAddress ? '업체 기본 주소' : '저장 배송지';
          button.append(title);
          for (const [label, value] of [['도로명주소',a.road],['지번주소',a.jibun],['상세주소',a.detail],['우편번호',a.zip]]) {
            const field = document.createElement('span');
            field.textContent = label + ' · ' + (value || '미등록');
            button.append(field);
          }
          if (!validAddress(a)) {
            button.disabled = true;
            button.append('우편번호·행정구역 확인 필요');
          }
          button.onclick = () => { chooseAddress(container, a); book.close(); if (container !== bulk) updateLineStatus(container); };
          list.append(button);
        }
        book.showModal();
      };
      container.querySelector('[data-address-search]').onclick = async () => {
        const dialog = document.createElement('dialog');
        dialog.className = 'store-postcode-dialog';
        dialog.setAttribute('aria-label', '배송지 검색');
        dialog.innerHTML = '<header class="store-dialog-head"><h2>배송지 검색</h2><button type="button" class="store-btn">닫기</button></header><div class="store-postcode-content" role="status">주소 검색을 불러오는 중입니다.</div>';
        main.append(dialog);
        dialog.querySelector('button').onclick = () => dialog.close();
        dialog.addEventListener('close', () => {
          const transitions = dialog.getAnimations?.() || [];
          if (transitions.length) Promise.allSettled(transitions.map(animation => animation.finished)).then(() => dialog.remove());
          else dialog.remove();
        }, {once:true});
        dialog.showModal();
        const content = dialog.querySelector('.store-postcode-content');
        try {
          const Postcode = await loadPostcode();
          if (!dialog.open) return;
          content.replaceChildren();
          content.removeAttribute('role');
          new Postcode({width:'100%', height:'100%', oncomplete(data) {
            if (!dialog.open) return;
            dialog.close();
            const region = window.HiddenAutoAddressRegion.fromDaum(data);
            chooseAddress(container, {zip:data.zonecode, province:region.doName, city:region.siName,
              district:region.guName, road:data.roadAddress || data.autoRoadAddress,
              jibun:data.jibunAddress || data.autoJibunAddress, detail:''});
            container.querySelector('[name=addressDetail]').focus();
            if (container !== bulk) updateLineStatus(container);
          }}).embed(content);
        } catch (error) {
          if (!dialog.open) return;
          dialog.close();
          feedback('주소 검색을 열 수 없습니다. 주소 검색을 다시 누르거나 업체 주소록을 이용해 주세요.', true);
        }
      };
    }
    function applyMethod(node) {
      const direct = node.querySelector('[name=method]').value === '1';
      if (direct) {
        if (node.dataset.direct !== 'true') {
          const address = selectedAddress.get(node);
          if (address) alternateAddress.set(node, {...address, detail:node.querySelector('[name=addressDetail]').value});
        }
        setAddress(node, options.companyAddress);
      } else if (node.dataset.direct === 'true') setAddress(node, alternateAddress.get(node));
      if (direct && !validAddress(options.companyAddress)) {
        feedback('직배송에 사용할 업체 주소의 우편번호와 기본주소를 확인해 주세요.', true);
      }
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
      alternateAddress.delete(bulk);
      setAddress(bulk, null);
      assignDate(bulk.querySelector('[name=date]'), '');
      contactFields.forEach(([key]) => bulk.querySelector('[name=' + key + ']').value = '');
      bulk.querySelector('.store-bulk-warning').textContent = '';
      bulk.showModal();
    };
    bulk.addEventListener('close', () => openBulk.focus());
    function allowedDate(value, earliest) {
      if (!earliest || !/^\d{4}-\d{2}-\d{2}$/.test(value) || value < earliest ||
          (options.calendarThrough && value > options.calendarThrough) || holidays.has(value)) return false;
      const date = new Date(value + 'T00:00:00Z');
      return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value
        && date.getUTCDay() !== 0 && date.getUTCDay() !== 6;
    }
    function assignDate(input, value) {
      const next = allowedDate(value, input.dataset.earliest) ? value : '';
      if (input._flatpickr) input._flatpickr.setDate(next, false);
      else input.value = next;
    }
    function pick(node, earliest) {
      const input = node.querySelector('[name=date]');
      input.dataset.earliest = earliest || '';
      if (!earliest) {
        input.placeholder = '공휴일 일정 확인 필요';
        input.disabled = true;
        return;
      }
      // 열지 않은 품목까지 월간 달력 DOM을 생성하지 않습니다.
      const open = () => {
        input.removeEventListener('focus', open);
        input.removeEventListener('click', open);
        const picker = window.flatpickr(input, {
          dateFormat:'Y-m-d', minDate:earliest, maxDate:options.calendarThrough || undefined,
          defaultDate: input.value || undefined, disableMobile: true, static: true,
          ariaDateFormat: 'Y년 n월 j일',
          locale: {...window.flatpickr.l10ns?.ko, yearAriaLabel: '연도', monthAriaLabel: '월'},
          onReady: (_, __, instance) => {
            const year = instance.currentYearElement.closest('.numInputWrapper');
            const month = instance.monthsDropdownContainer;
            if (year && month?.parentElement === year.parentElement) {
              const suffix = document.createElement('span');
              suffix.setAttribute('aria-hidden', 'true');
              suffix.className = 'store-year-unit';
              suffix.textContent = '년';
              month.before(year, suffix);
            }
          },
          onOpen: (_, __, instance) => {
            const rect = input.getBoundingClientRect();
            const dialog = input.closest('dialog');
            const bottom = dialog ? Math.min(innerHeight, dialog.getBoundingClientRect().bottom) : innerHeight;
            const height = instance.calendarContainer.offsetHeight;
            instance.calendarContainer.classList.toggle('store-calendar-above',
              bottom - rect.bottom < height && rect.top - (dialog?.getBoundingClientRect().top || 0) > height);
          },
          disable:[day => {
            const iso = [day.getFullYear(), String(day.getMonth()+1).padStart(2,'0'), String(day.getDate()).padStart(2,'0')].join('-');
            return !allowedDate(iso, earliest);
          }]
        });
        picker.open();
      };
      input.addEventListener('focus', open);
      input.addEventListener('click', open);
    }
    pick(bulk, latest);
    const shippingState = new Map();
    let applying = false;
    function updateShared() {
      const counts = new Map();
      for (const state of shippingState.values()) {
        if (!state.complete) continue;
        const group = counts.get(state.key) || {total:0, state};
        group.total++;
        counts.set(state.key, group);
      }
      const common = [...counts.values()].find(g => g.total > 1 && g.total > lines.length / 2);
      for (const line of lines) {
        const state = shippingState.get(line), overview = line.querySelector('.store-line-overview');
        const shared = common && state?.complete && state.key === common.state.key;
        overview.hidden = !!shared || !state?.text;
        if (!overview.hidden) overview.textContent = (common && state.complete ? '개별 배송 · ' : '') + state.text;
      }
      form.querySelector('[data-shared]').textContent = common
        ? (common.total === lines.length ? '전체 품목 공통 배송' : '전체 ' + lines.length + '개 중 ' + common.total + '개 품목 공통 배송') +
          ' · ' + common.state.text : '';
    }
    function updateLineStatus(line, shared = true) {
      const field = key => line.querySelector('[name=' + key + ']');
      const address = selectedAddress.get(line);
      const method = field('method'), quantity = field('quantity'), date = field('date').value;
      const complete = Boolean(validAddress({...address, detail:field('addressDetail').value}) && method.value && allowedDate(date, field('date').dataset.earliest) && quantity.validity.valid);
      const detail = field('addressDetail').value.trim();
      const addressText = [address?.road || address?.jibun, detail, address?.zip].filter(Boolean).join(' ');
      const text = [method.value && method.selectedOptions[0]?.text, date && listDate(date), addressText].filter(Boolean).join(' · ');
      const key = JSON.stringify([method.value, date, deliveryAddressKey({...address, detail})]);
      shippingState.set(line, {complete, key, text});
      line.querySelector('.store-line-overview').textContent = text;
      const item = rows[lines.indexOf(line)].item;
      line.querySelector('[data-item-total]').textContent = item.price == null ? '가격 미확정'
        : quantity.validity.valid && Number(quantity.value) > 1
          ? won(item.price * Number(quantity.value)) + ' (' + won(item.price) + ' × ' + quantity.value + '개)'
          : won(item.price);
      const status = line.querySelector('.store-line-status');
      status.textContent = complete ? '입력 완료' : '배송 정보 입력 필요';
      status.classList.toggle('is-complete', complete);
      if (shared && !applying) updateShared();
    }
    for (const eventType of ['input', 'change']) form.addEventListener(eventType, event => {
      const line = event.target.closest('.store-check-line');
      if (line && !applying) updateLineStatus(line);
    });
    lines.forEach((line, i) => {
      pick(line, dates[i]);
      const method = line.querySelector('[name=method]');
      method.onchange = () => {
        const chosen = options.methods.find(value => String(value.id) === method.value);
        line.querySelector('.store-method-note').textContent = chosen ?
          chosen.description : '';
        applyMethod(line);
        if (!applying) updateLineStatus(line);
      };
      const draft = rows[i].draft;
      if (draft) {
        setAddress(line, draft.address);
        contactFields.forEach(([key]) => line.querySelector('[name='+key+']').value = draft.contacts?.[key] || '');
        line.querySelector('[name=addressDetail]').value = draft.addressDetail || '';
        line.querySelector('[name=quantity]').value = draft.quantity;
        assignDate(line.querySelector('[name=date]'), draft.date);
        method.value = String(draft.methodId);
        method.onchange();
      }
    });
    lines.forEach(line => updateLineStatus(line, false));
    updateShared();
    main.querySelector('#store-apply').onclick = () => {
      const quantity = bulk.querySelector('[name=quantity]');
      if (!quantity.validity.valid) { quantity.reportValidity(); return; }
      if (bulk.querySelector('[name=addressDetail]').value.trim() && !bulk.querySelector('[name=address]').value) {
        bulk.querySelector('[data-address-search]').focus();
        bulk.querySelector('.store-bulk-warning').textContent = '상세주소를 적용할 기본 배송지를 먼저 선택해 주세요.';
        return;
      }
      const updates = ['quantity', 'method', 'address', 'date', ...contactFields.map(([key]) => key)]
        .map(name => [name, bulk.querySelector('[name=' + name + ']').value.trim()])
        .filter(([, value]) => value);
      if (!updates.length) {
        bulk.querySelector('[name=quantity]').focus();
        bulk.querySelector('.store-bulk-warning').textContent = '적용할 배송 정보를 한 가지 이상 입력해 주세요.';
        return;
      }
      bulk.querySelector('.store-bulk-warning').textContent = '';

      applying = true;
      try {
        for (const line of lines) {
          for (const [name, value] of updates) {
            const target = line.querySelector('[name=' + name + ']');
            if (name === 'address') {
              // 주소만 일괄 변경할 때도 직배송 품목은 회사 주소를 유지합니다.
              if (line.querySelector('[name=method]').value !== '1') {
                setAddress(line, {...selectedAddress.get(bulk), detail:bulk.querySelector('[name=addressDetail]').value.trim()});
              }
            } else if (name === 'date') assignDate(target, value);
            else {
              target.value = value;
              if (name === 'method') target.onchange();
            }
          }
          updateLineStatus(line, false);
        }
      } finally { applying = false; }
      if (updates.some(([name]) => ['quantity','method','address','date'].includes(name))) resetQuote();
      else if (activeRequest?.confirming) supersedeRequest();
      updateShared();
      bulk.close();
      feedback('입력한 항목을 전체 품목에 적용했습니다.');
    };
    const readDraft = (line, index) => {
      const value = name => line.querySelector('[name=' + name + ']').value;
      return {key:rows[index].key, item:rows[index].item, quantity:value('quantity'),
        addressDetail:value('addressDetail'), address:{...selectedAddress.get(line), detail:value('addressDetail').trim()},
        contacts:Object.fromEntries(contactFields.map(([key]) => [key,value(key).trim()])),
        date:value('date'), methodId:value('method')};
    };
    points.addEventListener('keydown', event => {
      if (event.key !== 'Enter') return;
      event.preventDefault();
      if (currentQuote) form.requestSubmit(form.querySelector('[name=confirm]'));
    });
    function draftRows() {
      return rows.map((row, i) => {
        const {item, key, ...draft} = readDraft(lines[i], i);
        return {...row, draft, ...(i === 0 ? {draftPoints:points.value} : {})};
      });
    }
    function saveDraft() {
      try { write('checkout', draftRows(), sessionStorage); }
      catch (_) { /* The active form remains usable when tab storage is unavailable. */ }
    }
    window.addEventListener('pagehide', saveDraft);
    leaveCheckout = () => {
      supersedeRequest();
      window.removeEventListener('pagehide', saveDraft);
      for (const node of [bulk, ...lines]) node.querySelector('[name=date]')._flatpickr?.destroy();
      leaveCheckout = null;
    };
    async function reconcileChanges(first, cause) {
      checkingChanges = true;
      resetQuote();
      quoteButton.disabled = true;
      breakdown.textContent = '상품 구성이 변경되어 발주 품목을 다시 확인하고 있습니다.';
      const changed = new Map([[first.key,cause]]);
      const others = rows.filter(row => row.key !== first.key);
      let incomplete = false;
      try {
        // 배송·포인트 검증 오류와 구분되는 서버의 확정 신호만 정리 대상으로 사용합니다.
        for (let offset = 0; offset < others.length && !incomplete; offset += 4) {
          breakdown.textContent = '발주 품목의 변경 여부를 확인하고 있습니다. ' + (offset + 1) + ' / ' + rows.length + '개 확인';
          await Promise.all(others.slice(offset, offset + 4).map(async row => {
            try { await check(row.item); }
            catch (error) {
              if (error.status === 409 && error.invalid === true) changed.set(row.key, error);
              else incomplete = true;
            }
          }));
        }
        const removed = new Set();
        const updated = notices.filter(entry => entry.removed);
        for (const [index,row] of rows.entries()) {
          const error = changed.get(row.key);
          if (!error) continue;
          const source = row.fromCart ? 'cart' : row.savedId ? 'interest' : null;
          const name = (index + 1) + '번 품목 ' + row.item.name;
          let reason;
          try {
            if (!source || await purgeInvalidRow(row, source)) {
              removed.add(row.key);
              const origin = source === 'cart' ? '장바구니와 이번 발주' : source === 'interest' ? '관심상품과 이번 발주' : '이번 발주';
              reason = name + ' · 구성이 변경되어 ' + origin + '에서 제외했습니다.';
            } else reason = name + ' · 저장된 구성이 달라 정리하지 않았습니다. 해당 품목을 다시 선택해 주세요.';
          } catch (failed) {
            reason = name + ' · 변경 구성을 정리하지 못해 품목을 유지합니다. ' + failed.message;
          }
          updated.push({key:row.key, removed:removed.has(row.key), text:reason, reason:error.message,
            selection:Array.isArray(row.item.summary) ? row.item.summary.join(' | ') : ''});
        }
        if (incomplete) updated.push({text:'일부 품목의 변경 여부를 확인하지 못했습니다. 배송비 계산을 다시 누르면 재검증합니다.'});
        // 조회 중 사용자가 바꾼 나머지 품목의 입력까지 현재 시점의 폼에서 보존합니다.
        const remaining = draftRows().filter(row => !removed.has(row.key));
        if (remaining.length) {
          remaining[0].draftPoints = points.value;
          updated.push({text:'남은 품목의 배송 정보를 확인하고 배송비를 다시 계산해 주세요.'});
        }
        try { write('checkout', remaining, sessionStorage); }
        catch (error) { updated.push({text:'현재 화면에 변경 내용을 적용했습니다. 새로고침 후에는 다시 확인이 필요합니다. ' + error.message}); }
        try { write('checkoutChanges', updated, sessionStorage); }
        catch (_) { /* Render the notice in the active tab even when storage is full. */ }
        await checkoutPage(options, {rows:remaining, notices:updated});
      } finally {
        checkingChanges = false;
        quoteButton.disabled = false;
      }
    }
    form.onsubmit = async event => {
      event.preventDefault();
      if (checkingChanges) return;
      const confirming = event.submitter?.name === 'confirm';
      if (confirming && !currentQuote) return;
      for (const [index, line] of lines.entries()) {
        const field = key => line.querySelector('[name=' + key + ']');
        for (const key of ['quantity', 'method']) {
          if (!field(key).validity.valid) { field(key).reportValidity(); return; }
        }
        if (!dates[index]) {
          field('date').focus();
          return feedback(rows[index].item.name + '의 공휴일 일정 확인이 필요합니다. 배송 희망일을 관리자에게 문의해 주세요.', true);
        }
        if (!allowedDate(field('date').value, dates[index])) {
          field('date').focus();
          field('date')._flatpickr?.open();
          return feedback(rows[index].item.name + '의 배송 희망일을 선택해 주세요.', true);
        }
        if (!validAddress({...selectedAddress.get(line), detail:field('addressDetail').value})) {
          const direct = field('method').value === '1';
          line.querySelector(direct ? '[name=method]' : '[data-address-search]').focus();
          return feedback(rows[index].item.name + (direct ? '의 직배송용 업체 주소를 확인해 주세요.' : '의 배송 주소를 검색하거나 업체 주소록에서 선택해 주세요.'), true);
        }
      }
      if (confirming && !points.validity.valid) { points.reportValidity(); return; }
      if (activeRequest?.version === quoteVersion && activeRequest.confirming === confirming) return;
      if (!confirming) resetQuote();
      const version = quoteVersion, submit = event.submitter || quoteButton;
      const request = {version, confirming, submit, controller:new AbortController()};
      activeRequest = request;
      submit.disabled = true;
      submit.setAttribute('aria-busy', 'true');
      const payload = lines.map(readDraft);
      try {
        const result = await S.api(S.base + '/checkout', 'POST', {lines: payload.map(({key, item, quantity, address, contacts, date, methodId}) =>
          ({key, item, quantity:Number(quantity), address, contacts, date, methodId:Number(methodId)})), points: confirming ? Number(points.value || 0) : 0}, request.controller.signal);
        if (version !== quoteVersion) return;
        if (!Array.isArray(result?.lines) || result.lines.length !== payload.length ||
            result.lines.some((line, index) => !line?.item || line.key !== payload[index].key)) {
          throw new Error('서버가 요청한 품목의 발주 검증 결과를 반환하지 않았습니다. 입력 내용을 유지합니다.');
        }
        if (confirming) {
          const cartLines = new Map(rows.filter(row => row.fromCart).map(row => [row.key, row]));
          const deducted = cartLines.size ? await changeCart(current => {
            if (version !== quoteVersion) return 0;
            const quantities = new Map(payload.map(row => [row.key, Number(row.quantity)]));
            const {remaining, count} = deductCart(current, cartLines, quantities);
            if (count) write('cart', remaining);
            return count;
          }) : 0;
          if (version !== quoteVersion) return;
          rows.forEach(row => { row.fromCart = false; });
          saveDraft();
          const editing = main.querySelector('.store-page');
          editing.remove();
          resultPage(result, deducted);
          main.querySelector('[data-edit-review]').onclick = event => {
            if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            event.preventDefault();
            main.replaceChildren(editing);
            lines[0].querySelector('[name=quantity]').focus();
          };
          return;
        }
        currentQuote = result;
        breakdown.innerHTML = '<dl class="store-totals"><dt>상품금액</dt><dd>' + won(result.products) +
          '</dd><dt>확정 배송비</dt><dd>' + confirmedFee(result, 'delivery') + '</dd><dt>확정 포장비</dt><dd>' + confirmedFee(result, 'packing') + '</dd></dl>' +
          shippingBreakdown(deliveryGroups(result.lines), 'quote') +
          (result.pending ? '<p>미확정 비용은 관리자 안내 후 반영됩니다.</p>' : '');
        points.max = Math.max(0, Math.floor(Math.min(options.points, result.products)));
        const before = Number(points.value || 0);
        points.value = Number.isFinite(before) ? Math.max(0, Math.min(Math.floor(before), Number(points.max))) : 0;
        form.querySelector('[data-point-limit]').textContent = '보유 ' + Number(options.points).toLocaleString('ko-KR') +
          'P · 이번 발주 최대 ' + Number(points.max).toLocaleString('ko-KR') + 'P';
        payment.hidden = false;
        points.readOnly = Number(points.max) === 0;
        points.disabled = false;
        showPayable();
        // 검증 단계에서는 장바구니와 발주 초안을 유지합니다.
      } catch (error) {
        if (version !== quoteVersion) return;
        const first = error.status === 409 && error.invalid === true && typeof error.lineKey === 'string' && error.lineKey.trim()
          ? rows.find(row => row.key === error.lineKey) : null;
        if (first) {
          try { await reconcileChanges(first, error); }
          catch (failure) { feedback(failure.message, true); }
          return;
        }
        feedback(error.message, true);
      } finally {
        if (activeRequest === request) {
          submit.disabled = false;
          submit.removeAttribute('aria-busy');
          activeRequest = null;
        }
      }
    };
  }
  function resultPage(result, deducted) {
    const groups = deliveryGroups(result.lines);
    const common = groups.find(group => group.lines.length > 1 && group.lines.length > result.lines.length / 2);
    const groupByKey = new Map();
    for (const group of groups) for (const line of group.lines) groupByKey.set(line.key, group);
    const facts = entries => '<dl class="store-answer-detail">' + entries.map(([label, value]) =>
      '<div><dt>' + esc(label) + '</dt><dd>' + esc(value || '미입력') + '</dd></div>').join('') + '</dl>';
    const cards = result.lines.map(line => {
      const address = line.address, contacts = line.contacts || {};
      const destination = [
        ['도로명주소',address.road], ['지번주소',address.jibun], ['상세주소',address.detail], ['우편번호',address.zip],
        ['행정구역',[address.province,address.city,address.district].filter(Boolean).join(' ')],
        ['주문자',[contacts.orderer,contacts.phone].filter(Boolean).join(' ')],
        ['현장 담당자',[contacts.manager,contacts.managerPhone].filter(Boolean).join(' ')]
      ];
      const group = groupByKey.get(line.key);
      const fees = [['대분류',line.item.category], ['상품 단가',won(line.item.price)]];
      const groupName = shippingGroupTitle(group, groups.length);
      const dispatch = [line.method,listDate(line.date)];
      if (group !== common) dispatch.push(address.road || address.jibun, address.detail);
      if (line.item.width != null) fees.splice(1, 0, ['가로 규격',line.item.width + ' mm']);
      return '<article class="store-row store-result">' + img(line.item) + '<div><strong>' + esc(line.item.name) +
        '</strong><p><b>' + won(line.item.price * line.quantity) + '</b> · ' + esc(line.quantity) + '개</p><p>' +
        '<a class="store-link" href="#result-delivery-' + group.number + '">' + esc(groupName) + '</a> · ' +
        esc(dispatch.filter(Boolean).join(' · ')) +
        '</p><details><summary>품목 옵션 · 배송지 · 연락처</summary>' + facts(fees) + facts(destination) +
        checkoutOptions(line.item) + '</details></div></article>';
    }).join('');
    shell('발주 검증 결과', '<p class="store-validation">발주 전 검증 결과입니다. 품목별 입력 내용과 배송 묶음별 금액을 확인해 주세요. 실제 발주는 접수되지 않았습니다. 이번 확인에서 장바구니 수량 ' + esc(deducted) + '개를 차감했습니다.</p>' +
      '<div class="store-list">' + cards + '</div>' + shippingBreakdown(groups, 'result') + '<dl class="store-totals"><dt>상품금액</dt><dd>' + won(result.products) +
      '</dd><dt>확정 배송비</dt><dd>' + confirmedFee(result, 'delivery') + '</dd><dt>확정 포장비</dt><dd>' + confirmedFee(result, 'packing') +
      '</dd><dt>사용 예정 포인트</dt><dd>−' + won(result.points) + '</dd><dt>' + (result.pending ? '현재 확정 금액 · 미확정 비용 별도' : '합계') + '</dt><dd>' +
      won(result.payable) + '</dd></dl><div class="store-actions"><a class="store-btn" data-edit-review href="' + root +
      '?view=checkout">입력 내용 수정</a><a class="store-btn" href="' + root + '">쇼핑몰로 이동</a></div>');
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
  window.StorePreview = {actions, bookmark, catalogQuery, copySearch, search, takeRestore, purgeInvalidRow, view};
})();
