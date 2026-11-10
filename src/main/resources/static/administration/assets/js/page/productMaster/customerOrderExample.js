(function (S) {
  'use strict';
  const main = document.getElementById('hb-main'), overlay = document.getElementById('hb-loading');
  const errorBox = document.getElementById('hb-error');
  const ROOT = '/admin/customer-order-example';
  const originalApi = S.api;
  S.api = async (path, method = 'GET', body, signal, options) => {
    const controller = new AbortController();
    const cancel = () => controller.abort();
    if (signal?.aborted) cancel(); else signal?.addEventListener('abort', cancel, { once: true });
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; cancel(); }, body instanceof FormData ? 120000 : 40000);
    try { return await originalApi(path, method, body, controller.signal, { ...options, quiet: true }); }
    catch (err) { if (timedOut) throw Error('서버 응답 시간이 초과되었습니다. 연결 상태를 확인하고 다시 시도해 주세요.'); throw err; }
    finally { clearTimeout(timer); signal?.removeEventListener('abort', cancel); }
  };
  const imageRoot = '/administration/assets/images/customer-order-example/';
  const e = S.e, list = x => x || [], money = value => {
    if (value == null) return '가격 미등록';
    const parts = String(value).match(/^([+-]?)(\d+)(\.\d+)?$/);
    return parts ? '₩' + parts[1] + parts[2].replace(/\B(?=(\d{3})+(?!\d))/g, ',') + ((parts[3] || '').replace(/0+$/, '').replace(/\.$/, '')) : '₩' + String(value);
  };
  const sample = i => imageRoot + String((Math.abs(Number(i) || 0) % 22) + 1).padStart(2, '0') + '.webp';
  let collections = [], kind = false, busy = 0, running = false;
  let seriesContext = null, standardState = null, standardSelections = {}, standardProduct = null;
  let product = null, answers = {}, evaluation = null, completed = [], assets = new Map(), currentQuestion = null;
  let uploadCount = 0, slide = 0, album = [], lastAction = null, pendingRoute = false;
  let loadingProgress = 0, loadingHide = null, galleryAnimation = null, gallerySequence = 0, galleryRequested = 0;
  const railMotions = new WeakMap();
  let motion = 'smooth';
  try { motion = localStorage.getItem('hb-store-motion-v2') || 'smooth'; } catch (_) { /* Storage can be disabled. */ }
  document.body.dataset.motion = motion === 'reduced' ? 'reduced' : 'smooth';
  const duration = normal => document.body.dataset.motion === 'reduced' ? 0 : normal;
  function progress(value) {
    if (!busy) return;
    loadingProgress = Math.max(loadingProgress, Math.min(100, value));
    overlay.querySelector('.hb-loader-track i').style.width = loadingProgress + '%';
  }
  function setBusy(on, label) {
    clearTimeout(loadingHide);
    busy = Math.max(0, busy + (on ? 1 : -1));
    if (label) document.getElementById('hb-loading-label').textContent = label;
    if (on && busy === 1) {
      loadingProgress = 0;
      const bar = overlay.querySelector('.hb-loader-track i');
      bar.style.transition = 'none'; bar.style.width = '0%';
      overlay.hidden = false;
      void bar.offsetWidth; bar.style.transition = '';
      requestAnimationFrame(() => progress(18));
    }
    main.inert = busy > 0;
    main.setAttribute('aria-busy', String(busy > 0));
    if (!busy) {
      overlay.querySelector('.hb-loader-track i').style.width = '100%';
      loadingHide = setTimeout(() => { if (!busy) overlay.hidden = true; }, duration(260));
    }
  }
  function error(message, inline = false) {
    if (inline) {
      const host = document.getElementById('hb-question-errors') || document.getElementById('hb-flow-error');
      if (host) { host.textContent = message || ''; host.hidden = !message;
        const retry = main.querySelector('.hb-question.is-current .hb-next');
        if (message && retry && main.querySelector('.hb-question.is-current input[type=radio]:checked')) { retry.hidden = false; retry.textContent = '선택한 옵션으로 다시 시도'; }
        return; }
    }
    errorBox.hidden = !message;
    errorBox.textContent = message || '';
    if (message) {
      const retry = document.createElement('button'); retry.textContent = '다시 시도';
      retry.onclick = () => run(lastAction); errorBox.append(retry);
      errorBox.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }
  }
  let stepLoading = null, focusMotion = null;
  const stepLoaderHtml = '<span class="hb-step-loader" aria-hidden="true"><i></i></span>';
  function loaderIn(node) {
    if (!node) return null;
    let loader = node.querySelector(':scope > .hb-step-loader');
    if (!loader) { node.insertAdjacentHTML('beforeend', stepLoaderHtml); loader = node.lastElementChild; }
    return loader;
  }
  function localBusy(on, label, lockCurrent = true) {
    const config = document.getElementById('hb-config');
    if (!config) return;
    config.setAttribute('aria-busy', String(on));
    const current = config.querySelector('.hb-question.is-current');
    if (on) {
      if (!busy) { clearTimeout(loadingHide); overlay.hidden = true; }
      cancelFocusMotion();
      if (current) {
        current.inert = lockCurrent; current.classList.add('is-loading');
        if (!stepLoading || stepLoading.node !== current) {
        const anchor = current.querySelector('input[type=radio]:checked')?.closest('.hb-standard-option,.pm-answer-choice') || current;
        const loader = loaderIn(anchor), bar = loader.querySelector('i');
        if (anchor !== current) anchor.classList.add('hb-choice-loading');
        bar.style.transition = 'none'; bar.style.width = '6%';
        void bar.offsetWidth; bar.style.transition = '';
        stepLoading = { node:current, anchor, bar, started:performance.now(), settled:false };
        requestAnimationFrame(() => { if (bar.isConnected && stepLoading?.bar === bar && !stepLoading.settled) bar.style.width = '82%'; });
        }
      }
    } else {
      if (stepLoading) { stepLoading.node.inert = false; stepLoading.node.classList.remove('is-loading'); stepLoading.anchor.classList.remove('hb-choice-loading'); }
      stepLoading = null;
      if (current) { current.inert = false; current.classList.remove('is-loading'); }
    }
    config.querySelectorAll('[data-edit],.hb-question.is-current .hb-next').forEach(button => { button.disabled = on; });
    const status = document.getElementById('hb-inline-status');
    if (status) { status.textContent = on ? label : ''; status.hidden = !on; }
  }
  async function settleStepLoading() {
    const state = stepLoading;
    if (!state || state.settled) return;
    state.settled = true;
    state.bar.style.transitionDuration = duration(110) + 'ms'; state.bar.style.width = '100%';
    const wait = Math.max(duration(90), duration(180) - (performance.now() - state.started));
    if (wait > 0) await new Promise(resolve => setTimeout(resolve, wait));
  }
  function resultLoading(target) {
    target.innerHTML = `<div class="hb-result-loading" role="status" aria-label="선택한 구성을 확인하고 있습니다."><span class="hb-result-line"></span><span class="hb-result-line is-short"></span>${stepLoaderHtml}</div>`;
    const placeholder = target.firstElementChild;
    placeholder._hbHeight = placeholder.getBoundingClientRect().height;
    requestAnimationFrame(() => { if (placeholder.isConnected) placeholder.querySelector('i').style.width = '84%'; });
    return placeholder;
  }
  function cancelFocusMotion() {
    if (focusMotion?.frame) cancelAnimationFrame(focusMotion.frame);
    focusMotion = null;
  }
  function moveToQuestion(card, focusInput = false) {
    cancelFocusMotion();
    if (!card?.isConnected) return;
    const header = main.ownerDocument.querySelector('.hb-header')?.getBoundingClientRect().height || 20;
    const rect = card.getBoundingClientRect(), from = scrollY;
    const needsMove = rect.top < header + 16 || rect.bottom > innerHeight - 28;
    const destination = () => Math.max(0, Math.min(document.documentElement.scrollHeight - innerHeight,
      scrollY + card.getBoundingClientRect().top - header - 24));
    const target = needsMove ? destination() : from;
    if (focusInput && S.desktopQuestionInput()) {
      card.querySelector('input[data-answer-field]:not(:disabled):not([type=file]),input[data-answer-choice][tabindex="0"],input[data-answer-choice]:not(:disabled),h2')?.focus({preventScroll:true});
    }
    if (Math.abs(target - from) < 2) return;
    const ms = duration(Math.min(640, 340 + Math.abs(target-from) * .16));
    if (!ms) { window.scrollTo({top:target,behavior:'instant'}); return; }
    const state = { frame:null }, started = performance.now(); focusMotion = state;
    const frame = now => {
      if (focusMotion !== state || !card.isConnected) return;
      const t = Math.min(1, (now-started)/ms), ease = 1-Math.pow(1-t,4);
      window.scrollTo({top:from+(destination()-from)*ease,behavior:'instant'});
      if (t < 1) state.frame = requestAnimationFrame(frame); else focusMotion = null;
    };
    state.frame = requestAnimationFrame(frame);
  }
  function focusQuestion() {
    const card = main.querySelector('.hb-question.is-current') || main.querySelector('#hb-product-result .hb-quote') || main.querySelector('#hb-price .hb-quote');
    moveToQuestion(card, true);
  }
  for (const event of ['wheel','touchstart','pointerdown']) window.addEventListener(event, cancelFocusMotion, {passive:true});
  window.addEventListener('keydown', event => {
    if (['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','PageUp','PageDown','Home','End','Escape'].includes(event.key)) cancelFocusMotion();
  }, {capture:true});
  function bindStoreQuestionNavigation(box, control, submit, nextButton, autoAdvance = control === 'RADIO') {
    const radio = control === 'RADIO', choice = radio || control === 'CHECKBOX';
    let cursor = 0, advancing = false;
    const inputs = () => [...box.querySelectorAll('input[data-answer-choice]:not(:disabled)')];
    const mark = (index, focus = false) => {
      const values = inputs(); if (!values.length) return;
      cursor = (index + values.length) % values.length;
      values.forEach((input,i) => {
        input.tabIndex = i === cursor ? 0 : -1;
        input.closest('.hb-standard-option,.pm-answer-choice')?.classList.toggle('hb-option-cursor', i === cursor);
      });
      if (focus) {
        const input = values[cursor]; input.focus({preventScroll:true});
        const rect = input.getBoundingClientRect(), header = main.ownerDocument.querySelector('.hb-header')?.offsetHeight || 20;
        if (rect.top < header+16 || rect.bottom > innerHeight-28) moveToQuestion(input.closest('.hb-standard-option,.pm-answer-choice'));
      }
    };
    const advance = () => {
      if (advancing || running || uploadCount || !box.isConnected) return;
      advancing = true; setDetailFocus('options');
      Promise.resolve(submit()).finally(() => { advancing = false; });
    };
    if (control === 'CHECKBOX') {
      const hint=document.createElement('p');hint.className='hb-choice-hint';hint.textContent='항목을 모두 선택한 뒤 다음 버튼으로 확정해 주세요.';box.before(hint);
    }
    if (control === 'TEXTAREA') {
      const hint=document.createElement('p');hint.className='hb-choice-hint';hint.textContent='Enter로 다음 단계 · Shift+Enter로 줄바꿈';box.before(hint);
    }
    if (choice) {
      mark(Math.max(0,inputs().findIndex(input => input.checked)));
      box.addEventListener('focusin', event => {
        if (event.target.matches('input[data-answer-choice]')) mark(inputs().indexOf(event.target));
      });
    }
    if (radio) {
      nextButton.hidden = autoAdvance && !box.querySelector('input:checked');
      const hint = document.createElement('p'); hint.className = 'hb-choice-hint';
      hint.innerHTML = autoAdvance ? '옵션을 선택하면 다음 단계로 이어집니다.<span>화살표로 이동 · Enter로 선택</span>' : '등록된 값을 선택한 뒤 다음 버튼 또는 Enter로 확정해 주세요.<span>화살표로 이동 · Enter로 확정</span>';
      box.before(hint);
      if (autoAdvance) {
        box.addEventListener('change', event => { if (event.target.matches('input[data-answer-choice]')) advance(); });
        // A checked option can be selected again after a failed request.
        box.addEventListener('click', event => { if (event.target.matches('input[data-answer-choice]') && event.target.checked) advance(); });
      }
    }
    box.addEventListener('keydown', event => {
      if (event.isComposing || event.keyCode === 229 || event.ctrlKey || event.metaKey || event.altKey || event.target.closest('button,a,select,[data-pm-help]')) return;
      const target = event.target;
      if (event.repeat && event.key === ' ') { event.preventDefault(); return; }
      if (choice && target.matches('input[data-answer-choice]')) {
        if (['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(event.key)) {
          event.preventDefault();
          mark(inputs().indexOf(target) + (['ArrowLeft','ArrowUp'].includes(event.key) ? -1 : 1), true);
        } else if (event.key === 'Enter') {
          event.preventDefault(); if (event.repeat) return;
          if (!radio) { nextButton.focus({preventScroll:true}); return; }
          target.checked = true; target.dispatchEvent(new Event('input',{bubbles:true}));
          advance();
        }
        return;
      }
      if (event.key === 'Enter' && target.matches('input[data-answer-field]:not([type=file]),textarea[data-answer-field]')) {
        if (target.matches('textarea') && event.shiftKey) return;
        event.preventDefault(); if (event.repeat || !target.reportValidity()) return;
        const fields = [...box.querySelectorAll('input[data-answer-field]:not(:disabled):not([type=file]),textarea[data-answer-field]:not(:disabled)')];
        const next = fields[fields.indexOf(target)+1];
        if (next) { next.focus({preventScroll:true}); if (next.getBoundingClientRect().bottom > innerHeight-32) moveToQuestion(next.closest('.pm-answer-field')); }
        else advance();
      }
    });
  }
  async function run(action, label = '화면을 준비하고 있습니다.', scope = 'page') {
    if (running || uploadCount || !action) return;
    running = true; lastAction = action; error('');
    const local = scope === 'question';
    if (local) { error('', true); localBusy(true, label); } else { cancelFocusMotion(); setBusy(true, label); }
    try { await action(); }
    catch (err) { error(err.message || '처리 중 오류가 발생했습니다.', local); }
    finally {
      if (local) localBusy(false, label); else setBusy(false);
      running = false;
      if (pendingRoute && !uploadCount) { pendingRoute = false; run(route); }
    }
  }
  // Every request has a bounded lifetime so a disconnected server cannot leave the overlay open.
  async function api(path, method = 'GET', body, options) {
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 40000);
    try { const result = await S.api(path, method, body, controller.signal, options); progress(Math.min(86, loadingProgress + 24)); return result; }
    catch (err) { if (err.name === 'AbortError') throw Error('서버 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.'); throw err; }
    finally { clearTimeout(timer); }
  }
  const request = (path, method = 'GET', body, options) => api(S.base + path, method, body, options);
  function safeUrl(url) {
    if (typeof url !== 'string' || !url.trim()) return '';
    try { const u = new URL(url, location.origin); return ['http:', 'https:'].includes(u.protocol) ? u.href : ''; } catch { return ''; }
  }
  function photo(card) { return list(card.assets).find(a => a.image && safeUrl(a.url)); }
  function cardImage(card, index = 0) { return safeUrl(photo(card)?.url) || sample(card.id ?? index); }
  function routeUrl(id, type = 'products') {
    const url = new URL(id ? `${ROOT}/${type}/${id}` : ROOT, location.origin);
    if (kind) url.searchParams.set('kind', 'custom');
    return url.pathname + url.search;
  }
  function saveRoute(id, replace = false, type = 'products') {
    history[replace ? 'replaceState' : 'pushState']({}, '', routeUrl(id, type));
  }
  async function catalog(_fetchData = true, replace = false) {
    const result = await request('/customer-collections?nonStandard=' + kind);
    if (pendingRoute) return;
    collections = list(result); removeFaq();
    product = null; seriesContext = null; standardState = null; standardProduct = null;
    answers = {}; completed = []; evaluation = null; currentQuestion = null;
    document.body.classList.remove('hb-detail-page');
    saveRoute(null, replace); paintCatalog();
  }
  function seriesCardHtml(card, i) {
    const url = routeUrl(card.productId, 'series');
    return `<article class="hb-card"><a href="${e(url)}" data-series-product="${e(card.productId)}"><div class="hb-card-copy"><small>${kind ? 'MADE TO ORDER' : 'STANDARD COLLECTION'}</small><h3>${e(card.series)}</h3><div class="hb-card-price">${card.total}개 제품 · ${kind ? '제품 선택 후 맞춤 구성' : '옵션을 선택하여 제품 완성'}</div></div><div class="hb-card-image is-loading"><img src="${e(cardImage({assets:card.assets,id:card.productId},i))}" alt="${e(card.series)}${photo(card) ? '' : ' · 예시 이미지'}" loading="lazy" data-fallback="${e(sample(card.productId))}"></div><div class="hb-card-foot"><span>${photo(card) ? '등록 이미지' : '<span class="hb-sample-badge">예시 이미지</span>'}</span><span>시리즈 살펴보기 ↗</span></div></a></article>`;
  }
  function sectionHtml(collection) {
    return `<section class="hb-section" data-section="${e(collection.key)}"><div class="hb-section-head"><div><span class="hb-eyebrow">${kind ? 'MADE TO ORDER' : 'STANDARD COLLECTION'}</span><h2>${e(collection.category)}</h2><p>${collection.series.length}개 시리즈 · 공간에 어울리는 시리즈를 선택해 주세요.</p></div><div class="hb-arrows"><button data-scroll="-1" aria-label="${e(collection.category)} 이전 시리즈">‹</button><button data-scroll="1" aria-label="${e(collection.category)} 다음 시리즈">›</button></div></div><div class="hb-rail" tabindex="0" aria-label="${e(collection.category)} 시리즈 슬라이드">${collection.series.map(seriesCardHtml).join('')}</div></section>`;
  }
  function railItems(rail) { return [...rail.querySelectorAll(':scope > .hb-card, :scope > button')]; }
  function scrollRail(rail, direction) {
    const items = railItems(rail);
    if (!items.length) return;
    const first = items[0].offsetLeft, max = Math.max(0, rail.scrollWidth - rail.clientWidth);
    const positions = [...new Set(items.map(item => Math.min(max, Math.max(0, item.offsetLeft - first))))];
    const old = railMotions.get(rail), from = rail.scrollLeft;
    let index = old?.moving ? old.index : positions.reduce((best, x, i) => Math.abs(x - from) < Math.abs(positions[best] - from) ? i : best, 0);
    index = Math.max(0, Math.min(positions.length - 1, index + direction));
    if (old?.frame) cancelAnimationFrame(old.frame);
    const target = positions[index], state = { index, moving: true, frame: null };
    railMotions.set(rail, state); rail.dataset.moving = 'true';
    const started = performance.now(), ms = duration(480);
    const frame = now => {
      if (!rail.isConnected || railMotions.get(rail) !== state) return;
      const t = ms ? Math.min(1, (now - started) / ms) : 1;
      const ease = 1 - Math.pow(1 - t, 4);
      rail.scrollLeft = from + (target - from) * ease;
      if (t < 1) state.frame = requestAnimationFrame(frame);
      else { rail.scrollLeft = target; state.moving = false; delete rail.dataset.moving; rail.dispatchEvent(new Event('scroll')); }
    };
    state.frame = requestAnimationFrame(frame);
  }
  function bindRail(rail, prev, next) {
    if (!rail || !prev || !next || rail._hbBound) return;
    rail._hbBound = true;
    const refresh = () => {
      prev.disabled = rail.scrollLeft <= 1;
      next.disabled = rail.scrollLeft + rail.clientWidth >= rail.scrollWidth - 1;
    };
    prev.onclick = () => scrollRail(rail, -1); next.onclick = () => scrollRail(rail, 1);
    const cancel = () => {
      const state = railMotions.get(rail);
      if (state?.frame) cancelAnimationFrame(state.frame);
      railMotions.delete(rail); delete rail.dataset.moving;
    };
    rail.addEventListener('pointerdown', cancel, { passive: true });
    rail.addEventListener('wheel', cancel, { passive: true });
    rail.addEventListener('scroll', refresh, { passive: true });
    rail.addEventListener('hb-resize', () => { cancel(); refresh(); });
    rail.addEventListener('keydown', event => {
      if (event.target === rail && ['ArrowLeft', 'ArrowRight'].includes(event.key)) {
        event.preventDefault(); scrollRail(rail, event.key === 'ArrowLeft' ? -1 : 1);
      }
    });
    requestAnimationFrame(refresh);
  }
  function paintCatalog() {
    const markup = `<section class="hb-intro"><div><span class="hb-eyebrow">HIDDENBATH COLLECTION</span><h1>당신의 공간을 위한 선택.</h1><p>시리즈를 만나고, 나에게 맞는 구성으로 완성해 보세요.</p></div><div class="hb-kind" role="group" aria-label="제품 구분"><button data-kind="standard" aria-pressed="${!kind}">규격제품</button><button data-kind="custom" aria-pressed="${kind}">비규격제품</button></div></section>
      <div id="hb-collections">${collections.length ? collections.map(sectionHtml).join('') : `<div class="hb-empty"><span class="hb-eyebrow">COLLECTION</span><h2>표시할 ${kind ? '비규격' : '규격'} 시리즈가 없습니다.</h2><p>제품 관리에서 분류와 활성 옵션을 확인해 주세요.</p><a href="/admin/product-master/${kind ? 'non-standard' : 'products'}">제품 관리로 이동 ↗</a></div>`}</div>`;
    const previousIntro = main.querySelector(':scope > .hb-intro');
    if (previousIntro) {
      const template = document.createElement('template'); template.innerHTML = markup;
      template.content.querySelector('.hb-intro').remove();
      [...main.children].filter(node => node !== previousIntro).forEach(node => node.remove());
      previousIntro.querySelectorAll('[data-kind]').forEach(button => button.setAttribute('aria-pressed', String((button.dataset.kind === 'custom') === kind)));
      main.append(template.content);
    } else main.innerHTML = markup;
    main.querySelectorAll('[data-kind]').forEach(b => b.onclick = () => run(async () => {
      kind = b.dataset.kind === 'custom'; await catalog();
    }, '컬렉션을 전환하고 있습니다.'));
    main.querySelectorAll('.hb-section').forEach(section => bindRail(section.querySelector('.hb-rail'), section.querySelector('[data-scroll="-1"]'), section.querySelector('[data-scroll="1"]')));
    main.querySelectorAll('[data-series-product]').forEach(link => link.onclick = event => {
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      event.preventDefault(); run(() => openSeries(Number(link.dataset.seriesProduct)), '시리즈 구성을 불러오고 있습니다.');
    });
    main.querySelectorAll('.hb-card-image img').forEach(img => { if (img.complete) img.closest('.hb-card-image').classList.remove('is-loading'); });
  }
  function setupProduct(next) {
    gallerySequence++; galleryAnimation?.cancel(); removeFaq();
    product = next; product._isAdminTest = true; kind = next.nonStandard;
    answers = {}; completed = []; evaluation = null; currentQuestion = null;
    assets = new Map([...list(product.processAssets), ...list(product.assets), ...list(product.groups).flatMap(g => [...list(g.assets), ...list(g.values).flatMap(v => list(v.assets))])].map(a => [a.id, a]));
    const registered = list(product.assets).filter(a => a.image && safeUrl(a.url));
    album = registered.length ? [...registered] : [0,7,14].map(offset => ({ url:sample(product.id + offset), name:'예시 이미지', sample:true }));
    slide = 0; galleryRequested = 0;
  }
  async function openSeries(id, replace = false) {
    const context = await request(`/customer-series/${id}`);
    if (pendingRoute) return;
    if (context.series.nonStandard && context.products.length === 1) return detail(context.products[0].id, replace, context);
    const state = context.series.nonStandard ? null : await request(`/customer-series/${id}/configure`, 'POST', {});
    if (pendingRoute) return;
    seriesContext = context; standardState = state; standardSelections = {}; standardProduct = null;
    setupProduct(context.representative);
    document.body.classList.add('hb-detail-page');
    saveRoute(id, replace, 'series'); paintDetail(); window.scrollTo({top:0,behavior:'instant'});
    if (kind) paintCustomModels(); else await paintStandard();
    await waitImage(main.querySelector('.hb-gallery-slide img'));
  }
  async function detail(id, replace = false, context = null) {
    const next = await request(`/products/${id}`);
    if (pendingRoute) return;
    if (next.status === 'DISCONTINUED') throw Error('단종된 제품은 이 화면에서 선택할 수 없습니다.');
    if (!next.nonStandard) return openSeries(id, replace);
    seriesContext = context; standardState = null; standardProduct = null; standardSelections = {};
    setupProduct(next);
    evaluation = await request(`/products/${id}/customer-evaluate`, 'POST', answers);
    if (pendingRoute) return;
    document.body.classList.add('hb-detail-page');
    saveRoute(id, replace); paintDetail(); window.scrollTo({ top: 0, behavior: 'instant' });
    await paintProcess(); void installFaq();
    await waitImage(main.querySelector('.hb-gallery-slide img'));
  }
  function waitImage(img) {
    if (!img || img.complete) return Promise.resolve();
    return new Promise(resolve => {
      const done = () => { clearTimeout(timer); img.removeEventListener('load', done); img.removeEventListener('error', done); resolve(); };
      const timer = setTimeout(done, 8000); img.addEventListener('load', done); img.addEventListener('error', done);
    });
  }
  function paintDetail() {
    const choosingSeries = !!standardState || (kind && !evaluation && !!seriesContext);
    const heading = choosingSeries ? seriesContext.series.series : product.productName;
    main.innerHTML = `<div class="hb-breadcrumb"><a href="${e(routeUrl())}" id="hb-back">컬렉션</a><span>/</span><span>${kind ? '비규격제품' : '규격제품'}</span><span>/</span><span>${e(choosingSeries ? heading : product.catalogCode)}</span>${kind && evaluation && seriesContext?.products.length > 1 ? '<button id="hb-back-models" type="button">시리즈 제품 다시 선택</button>' : ''}</div>
      <div class="hb-detail-toolbar" role="group" aria-label="상세 화면 보기"><span>이미지와 옵션을 누르면 보기 편한 너비로 전환됩니다.</span><button type="button" data-detail-focus="gallery" aria-pressed="false">이미지 넓게 보기</button><button type="button" data-detail-focus="options" aria-pressed="false">옵션 넓게 보기</button></div>
      <section class="hb-detail" data-focus="balanced"><div class="hb-gallery"><div class="hb-gallery-stage" role="region" aria-label="제품 이미지 슬라이드" tabindex="0"><div class="hb-gallery-track">${album.map((a, i) => `<div class="hb-gallery-slide" aria-hidden="${i !== 0}"><img src="${e(safeUrl(a.url))}" alt="${e(heading)} · ${e(a.name)}" data-fallback="${e(sample(product.id))}"></div>`).join('')}</div><div class="hb-image-loading" role="status" hidden>이미지를 불러오는 중입니다.</div><div class="hb-gallery-tools"><button id="hb-prev" aria-label="이전 이미지">‹</button><span class="hb-gallery-count" aria-live="polite"></span><button id="hb-next" aria-label="다음 이미지">›</button></div></div><div class="hb-gallery-thumbs">${album.map((a, i) => `<button data-slide="${i}" aria-label="${i + 1}번 이미지" aria-pressed="${i === 0}"><img src="${e(safeUrl(a.url))}" alt="" data-fallback="${e(sample(product.id))}"></button>`).join('')}</div><p class="hb-gallery-caption"></p></div>
      <div class="hb-info pms"><span class="hb-eyebrow">${kind ? 'MADE TO ORDER' : 'STANDARD COLLECTION'}</span><h1>${e(heading)}</h1><p class="hb-info-description">${choosingSeries ? (kind ? '제품을 선택한 뒤 질문을 따라 나만의 구성을 완성해 주세요.' : '등록된 옵션을 순서대로 선택해 주세요. 모든 선택이 끝나면 실제 제품과 가격을 확인하실 수 있습니다.') : e(product.description || '질문을 따라 나에게 맞는 구성을 완성해 주세요.')}</p>${choosingSeries ? `<dl class="hb-meta"><dt>대분류</dt><dd>${e(seriesContext.series.category)}</dd><dt>시리즈</dt><dd>${e(heading)}</dd><dt>등록 제품</dt><dd>${seriesContext.series.total}개</dd></dl>` : `<dl class="hb-meta"><dt>제품코드</dt><dd>${e(product.catalogCode || '미등록')}</dd><dt>제품 구분</dt><dd>비규격 · 맞춤 구성</dd><dt>생산기간</dt><dd>${product.productionHours == null ? '미등록' : e(S.productionTime(product.productionHours))}</dd><dt>등록 상태</dt><dd>${e(S.status[product.status] || product.status)}</dd></dl>${S.media(list(product.assets).filter(a=>!a.image),'제품 공통 첨부파일')}`}<div id="hb-config"></div><div id="hb-price" aria-live="polite"></div></div></section>`;
    const backModels = document.getElementById('hb-back-models');
    if (backModels) backModels.onclick = () => run(() => openSeries(seriesContext.series.productId), '시리즈 제품을 불러오고 있습니다.');
    document.getElementById('hb-back').onclick = ev => { ev.preventDefault(); run(() => catalog(true), '컬렉션으로 돌아갑니다.'); };
    S.bindMedia(main); updateSlide();
    const changeSlide = async i => {
      if (running || uploadCount) return;
      const serial = ++gallerySequence, track = main.querySelector('.hb-gallery-track');
      const stage = main.querySelector('.hb-gallery-stage');
      const targetIndex = Math.max(0, Math.min(album.length - 1, i));
      if (!track) return;
      galleryRequested = targetIndex;
      const hint = stage.querySelector('.hb-image-loading');
      if (targetIndex === slide) { hint.hidden = true; return; }
      const img = track.querySelectorAll('img')[targetIndex];
      if (!img.complete) { hint.hidden = false; await waitImage(img); }
      if (serial !== gallerySequence || !track.isConnected) return;
      hint.hidden = true;
      const from = getComputedStyle(track).transform;
      galleryAnimation?.cancel(); slide = targetIndex;
      updateSlide();
      const to = `translateX(-${slide * 100}%)`;
      galleryAnimation = track.animate([{ transform: from === 'none' ? 'translateX(0)' : from }, { transform: to }], {
        duration: duration(560), easing: 'cubic-bezier(.22,.61,.36,1)'
      });
      try { await galleryAnimation.finished; } catch (_) { /* A later arrow press can replace this movement. */ }
    };
    document.getElementById('hb-prev').onclick = () => changeSlide(galleryRequested - 1);
    document.getElementById('hb-next').onclick = () => changeSlide(galleryRequested + 1);
    main.querySelectorAll('[data-slide]').forEach(b => b.onclick = () => changeSlide(Number(b.dataset.slide)));
    const stage = main.querySelector('.hb-gallery-stage');
    stage.onkeydown = ev => { if (['ArrowLeft', 'ArrowRight'].includes(ev.key)) { ev.preventDefault(); changeSlide(galleryRequested + (ev.key === 'ArrowLeft' ? -1 : 1)); } };
    let touchStart = null;
    stage.addEventListener('touchstart', ev => { touchStart = ev.touches[0].clientX; }, { passive: true });
    stage.addEventListener('touchend', ev => { const dx = ev.changedTouches[0].clientX - touchStart; if (touchStart != null && Math.abs(dx) > 45) changeSlide(galleryRequested + (dx < 0 ? 1 : -1)); touchStart = null; }, { passive: true });
    bindDetailFocus();
  }
  function teamComparison(rows) {
    const cell = (row, role) => `<td><strong>${e(row.group?.[role] || row.group?.customer)}</strong><p>${e(row.answer?.[role] || row.answer?.customer || '아직 선택하지 않음')}</p></td>`;
    return `<section class="hb-team-preview"><h3>팀별 표시 확인</h3><p>고객에게 보이는 질문과 각 화면의 그룹명·선택값·입력값을 비교합니다.</p><small class="hb-team-hint">표를 좌우로 밀어 각 화면의 표시 내용을 확인해 주세요.</small><div class="hb-team-scroll"><table><thead><tr><th>고객 질문</th><th>고객 화면</th><th>생산팀 화면</th><th>관리팀 화면</th></tr></thead><tbody>${rows.map(row => `<tr><th>${e(row.question || row.group?.customer)}</th>${cell(row,'customer')}${cell(row,'production')}${cell(row,'management')}</tr>`).join('') || '<tr><td colspan="4">선택 가능한 질문이 없습니다.</td></tr>'}</tbody></table></div></section>`;
  }
  function updateTeamPreview(result = evaluation, standardDisplay = null) {
    const config = document.getElementById('hb-config');
    if (!config) return;
    let host = document.getElementById('hb-team-preview');
    if (!host) { host = document.createElement('div'); host.id = 'hb-team-preview'; config.append(host); }
    if (standardState) { host.innerHTML = teamComparison(list(standardDisplay || standardState.display)); return; }
    if (!result) return;
    const raw = product._teamProcess || product.process;
    const rows = list(result.questions).filter(state => (state.visible || state.question.fixed) && product.groups?.find(g => g.id === state.question.groupId)?.role !== 'SUBCATEGORY').map(state => {
      const q = state.question, original = raw?.questions.find(item => item.key === q.key) || q;
      const question = { ...q, choices:list(q.choices).map(c => ({...c,labels:original.choices?.find(x=>x.key===c.key)?.labels || c.labels})), fields:list(q.fields).map(f => ({...f,labels:original.fields?.find(x=>x.key===f.key)?.labels || f.labels})) };
      const group = original.labels || q.labels;
      const answer = Object.fromEntries(['customer','production','management'].map(role => [role,S.answerText(question,state.answer || {},assets,role)]));
      return {group,question:q.question || q.labels?.customer,answer};
    });
    host.innerHTML = teamComparison(rows);
  }
  function setDetailFocus(area) {
    const detail = main.querySelector('.hb-detail');
    if (!detail || !['gallery','options'].includes(area)) return;
    detail.dataset.focus = area;
    main.querySelectorAll('[data-detail-focus]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.detailFocus === area)));
  }
  function bindDetailFocus() {
    main.querySelectorAll('[data-detail-focus]').forEach(button => button.onclick = () => setDetailFocus(button.dataset.detailFocus));
    const gallery = main.querySelector('.hb-gallery'), info = main.querySelector('.hb-info');
    gallery.addEventListener('click', () => setDetailFocus('gallery'));
    gallery.addEventListener('focusin', event => { if (event.target.matches(':focus-visible')) setDetailFocus('gallery'); });
    // Expand after pointer activation, so a moving label cannot escape the initial click.
    info.addEventListener('click', event => { if (event.target.closest('input,textarea,select,label,button')) setDetailFocus('options'); });
    info.addEventListener('input', () => setDetailFocus('options'));
    info.addEventListener('change', () => setDetailFocus('options'));
    info.addEventListener('focusin', event => { if (event.target.matches(':focus-visible')) setDetailFocus('options'); });
  }
  function paintCustomModels() {
    const config = document.getElementById('hb-config');
    config.innerHTML = `<article class="hb-question is-current"><header class="hb-question-heading"><span class="hb-eyebrow">STEP 01 · 제품 선택</span></header><h2 tabindex="-1">어떤 제품을 구성하시겠어요?</h2><div class="hb-model-choices">${seriesContext.products.map(p => `<button type="button" data-custom-model="${p.id}"><strong>${e(p.productName)}</strong><span>${e(p.catalogCode)} · ${e(S.status[p.status] || p.status)}</span><small>이 제품으로 맞춤 구성 ↗</small></button>`).join('')}</div><div id="hb-flow-error" class="hb-question-errors" role="alert" hidden></div></article>`;
    config.querySelectorAll('[data-custom-model]').forEach(button => button.onclick = () => run(() => detail(Number(button.dataset.customModel), false, seriesContext), '선택한 제품의 질문을 불러오고 있습니다.'));
  }
  async function resetStandard(groupId) {
    return run(async () => {
      const index = standardState.steps.findIndex(q => q.groupId === groupId);
      if (index < 0) return;
      const selections = Object.fromEntries(standardState.steps.slice(0,index).map(q => [q.groupId,standardSelections[q.groupId]]));
      const next = await request(`/customer-series/${seriesContext.series.productId}/configure`, 'POST', selections);
      if (pendingRoute) return;
      standardSelections = selections; standardState = next; standardProduct = null; removeFaq();
      document.getElementById('hb-price').replaceChildren();
      await paintStandard(); setDetailFocus('options'); focusQuestion();
    }, '이 질문부터 다시 선택할 수 있도록 준비하고 있습니다.', 'question');
  }
  function decimalKey(value) {
    const match = String(value ?? '').trim().match(/^([+-]?)(\d*\.?\d+)(?:[eE]([+-]?\d+))?$/);
    if (!match) return null;
    const parts = match[2].split('.'), exponent = Number(match[3] || 0);
    if (Math.abs(exponent) > 1000) return null;
    let digits = parts.join(''), point = parts[0].length + exponent;
    if (point < 0) { digits = '0'.repeat(-point) + digits; point = 0; }
    if (point > digits.length) digits += '0'.repeat(point-digits.length);
    const integer = (digits.slice(0,point) || '0').replace(/^0+(?=\d)/,'');
    const fraction = digits.slice(point).replace(/0+$/,'');
    return (match[1] === '-' && (integer !== '0' || fraction) ? '-' : '') + integer + (fraction ? '.'+fraction : '');
  }
  function standardAnswer(q, node) {
    if (node.querySelector('[data-standard-absent]')?.checked) return { absent:true, choices:[],fields:{} };
    if (q.control === 'FILE') return { fileKey:node.querySelector('[data-standard-file]:checked')?.value,choices:[],fields:{} };
    const box = node.querySelector('.hb-standard-answer');
    if (S.isChoice(q.control)) return S.readAnswer(box,q);
    const fields = {};
    box.querySelectorAll('[data-answer-field]:not(:disabled)').forEach(input => { if (input.value !== '' && (q.control === 'NUMBER' || input.value.trim())) fields[input.dataset.answerField] = input.value; });
    return { choices:[],fields };
  }
  function standardMatch(q, answer) {
    if (answer.absent) return q.options.find(option => option.absent);
    if (q.control === 'FILE') return q.options.find(option => !option.absent && option.key === answer.fileKey);
    const choiceKey = values => JSON.stringify([...values].sort());
    return q.options.find(option => !option.absent && choiceKey(option.choices) === choiceKey(answer.choices) &&
      Object.keys(option.inputs).length === Object.keys(answer.fields).length && Object.entries(option.inputs).every(([key,value]) =>
        q.control === 'NUMBER' ? decimalKey(value) !== null && decimalKey(value) === decimalKey(answer.fields[key])
          : String(value).replace(/\r\n?/g,'\n') === String(answer.fields[key] ?? '').replace(/\r\n?/g,'\n')));
  }
  function standardLivePreview(q, answer) {
    const option = standardMatch(q,answer), actual = option ? {choices:option.choices,fields:option.inputs} : answer;
    const label = answer.absent ? '해당 없음' : q.control === 'FILE' ? option?.label : null;
    const display = list(standardState.display).map(row => row === standardState.display.at(-1) ? {
      ...row,answer:Object.fromEntries(['customer','production','management'].map(role => [role,label || S.answerText(q,actual,assets,role)]))
    } : row);
    updateTeamPreview(null,display);
  }
  function standardFileOptions(q) {
    return `<div class="hb-standard-options" role="radiogroup" aria-label="${e(q.label)}">${q.options.filter(option=>!option.absent).map(option => {
      const files = Object.values(option.inputs).flat().map(id=>assets.get(id)).filter(Boolean);
      return `<article class="hb-standard-option"><label class="hb-standard-choice"><input type="radio" name="standard-file-${q.groupId}" value="${e(option.key)}" data-standard-file data-answer-choice="${e(option.key)}"><span>${e(option.label)}</span></label>${S.media(files,'등록된 사양 파일')}${S.help(option,option.assets,'선택 사양 안내')}</article>`;
    }).join('')}</div>`;
  }
  function mountStandardQuestion(node,q) {
    list(q.assets).forEach(asset=>assets.set(asset.id,asset));
    const input = !S.isChoice(q.control) && q.control !== 'FILE';
    const absence = q.options.some(option=>option.absent) ? '<label class="hb-standard-absence"><input type="checkbox" data-standard-absent><span>이 항목 해당 없음</span></label>' : '';
    const presets = input ? `<details class="hb-standard-presets"><summary>입력 가능한 등록 규격 ${q.options.filter(option=>!option.absent).length}가지 보기</summary><div>${q.options.filter(option=>!option.absent).map(option=>`<button type="button" data-standard-preset="${e(option.key)}">${e(option.label)}</button>`).join('')}</div><small>등록 규격을 누르면 입력칸에 채워집니다. 다음 버튼 또는 Enter로 확정해 주세요.</small></details>` : '';
    node.querySelector('.hb-question-body').innerHTML = `${S.help(q,list(q.assetIds).map(id=>assets.get(id)).filter(Boolean),'질문 안내')}${input ? '<p class="hb-standard-input-note">등록된 규격과 일치하는 값을 입력한 뒤 확정해 주세요.</p>' : ''}${absence}<div class="hb-standard-answer" data-control="${e(q.control)}">${q.control==='FILE' ? standardFileOptions(q) : S.inputAnswer(q,{choices:[],fields:{}},false,assets)}</div>${presets}<div id="hb-question-errors" class="hb-question-errors" role="alert" hidden></div><button type="button" class="hb-next" id="hb-standard-next">입력·선택 완료 · 다음으로</button>`;
    const box=node.querySelector('.hb-standard-answer'), next=node.querySelector('#hb-standard-next');
    box.querySelectorAll('[aria-required=true]').forEach(field=>field.required=true);
    box.querySelectorAll('input[data-answer-field]').forEach(field => {
      if(q.control==='NUMBER') {
        const id=`hb-standard-values-${q.groupId}-${field.dataset.answerField}`;
        const values=[...new Set(q.options.filter(option=>!option.absent).map(option=>option.inputs[field.dataset.answerField]).filter(value=>value!=null))];
        field.setAttribute('list',id);field.insertAdjacentHTML('afterend',`<datalist id="${e(id)}">${values.map(value=>`<option value="${e(value)}"></option>`).join('')}</datalist>`);
      }
    });
    box.querySelectorAll('.pm-answer-fields').forEach(fields=>{fields.style.setProperty('--hb-field-columns',Math.min(3,Math.max(1,fields.querySelectorAll('.pm-answer-field').length)));});
    const update = () => {
      const absent=!!node.querySelector('[data-standard-absent]')?.checked;
      box.querySelectorAll('input,textarea,button').forEach(field=>{field.disabled=absent;});
      const answer=standardAnswer(q,node);
      standardLivePreview(q,answer);
    };
    box.addEventListener('input',update);box.addEventListener('change',update);
    const submit=()=>{
      if(!node.querySelector('[data-standard-absent]')?.checked) {
        for(const field of box.querySelectorAll('input[data-answer-field]:not(:disabled),textarea[data-answer-field]:not(:disabled)')) if(!field.checkValidity()) {
          const meta=q.fields.find(item=>item.key===field.dataset.answerField),name=meta?.labels?.customer||'입력값';
          const reason=field.validity.valueMissing?'값을 입력해 주세요.':field.validity.rangeUnderflow?`최소 ${field.min} 이상 입력해 주세요.`:field.validity.rangeOverflow?`최대 ${field.max} 이하로 입력해 주세요.`:field.validity.stepMismatch?`${field.step} 간격으로 입력해 주세요.`:'올바른 값을 입력해 주세요.';
          error(name+': '+reason,true);field.reportValidity();return;
        }
      }
      const selected=standardMatch(q,standardAnswer(q,node));
      if(!selected) { error(S.isChoice(q.control)?'선택한 조합과 일치하는 등록 제품이 없습니다. 선택 가능한 항목과 조합을 확인해 주세요.':'입력한 값과 일치하는 등록 규격이 없습니다. 입력 가능한 등록 규격을 확인해 주세요.',true);const presets=node.querySelector('.hb-standard-presets');if(presets)presets.open=true;return; }
      return run(async()=>{
      const selections={...standardSelections,[q.groupId]:selected.key};
      const next=await request(`/customer-series/${seriesContext.series.productId}/configure`,'POST',selections);
      if(pendingRoute)return;
      standardSelections=selections;standardState=next;standardProduct=null;removeFaq();
      document.getElementById('hb-price').replaceChildren();await paintStandard();focusQuestion();
      },'다음 구성을 준비하고 있습니다.','question');
    };
    next.onclick=submit;
    bindStoreQuestionNavigation(box,q.control==='FILE'?'RADIO':q.control,submit,next,q.control==='RADIO');
    node.querySelector('[data-standard-absent]')?.addEventListener('change',()=>{update();if(q.control==='RADIO'&&node.querySelector('[data-standard-absent]').checked)submit();});
    node.querySelectorAll('[data-standard-preset]').forEach(button=>button.onclick=()=>{
      const option=q.options.find(option=>option.key===button.dataset.standardPreset);
      const absent=node.querySelector('[data-standard-absent]');if(absent)absent.checked=false;
      box.querySelectorAll('[data-answer-field]').forEach(field=>{field.value=option.inputs[field.dataset.answerField]??'';});update();
    });
    update();
  }
  async function paintStandard() {
    await settleStepLoading();
    const config=document.getElementById('hb-config'),state=standardState;
    let thread=config.querySelector('#hb-standard-thread');
    if(!thread){config.innerHTML=`<div class="hb-process-header"><span>나에게 맞는 규격 구성</span><span id="hb-step-count"></span></div><div class="hb-progress"><i></i></div><div id="hb-standard-thread" aria-label="규격 옵션 질문과 답변"></div><div id="hb-flow-error" class="hb-question-errors" role="alert" hidden></div><div id="hb-inline-status" class="hb-inline-status" role="status" aria-live="polite" hidden></div>`;thread=config.querySelector('#hb-standard-thread');}
    document.getElementById('hb-step-count').textContent=`${state.steps.length} / ${state.totalGroups} 단계`;
    config.querySelector('.hb-progress i').style.width=`${state.totalGroups?state.steps.length/state.totalGroups*100:100}%`;
    const wanted=[...state.steps,...(state.next?[state.next]:[])],keys=new Set(wanted.map(q=>String(q.groupId)));
    await Promise.all([...thread.children].filter(node=>!keys.has(node.dataset.groupId)).map(removeMessage));
    const motions=[];
    for(let index=0;index<wanted.length;index++){
      const q=wanted[index],selected=q.options.find(option=>option.key===standardSelections[q.groupId]),complete=index<state.steps.length;
      list(q.assets).forEach(asset=>assets.set(asset.id,asset));
      let node=[...thread.children].find(node=>node.dataset.groupId===String(q.groupId));const added=!node;
      if(added){node=document.createElement('article');node.dataset.groupId=q.groupId;thread.append(node);}
      const signature=JSON.stringify([complete,q,selected?.key]);if(node._hbSignature===signature)continue;
      const oldHeight=node.getBoundingClientRect().height;node._hbSignature=signature;node.inert=!complete&&!!stepLoading;node.className='hb-question '+(complete?'is-complete':'is-current');
      node.innerHTML=`<header class="hb-question-heading"><span class="hb-eyebrow">STEP ${String(index+1).padStart(2,'0')} · ${e(q.label)}</span><span class="hb-question-state">${complete?'<span>선택 완료</span><button type="button" data-edit>이 질문부터 초기화</button>':'선택 중'}</span></header><h2 tabindex="-1">${e(q.question||q.label)}</h2><div class="hb-question-body"></div>`;
      if(complete){const help=selected&&!selected.absent?S.answerMedia(q,{choices:selected.choices,fields:selected.inputs},assets):'';node.querySelector('.hb-question-body').innerHTML=`<div class="hb-response"><span class="hb-response-label">확정하신 내용</span><p>${e(selected?.label)}</p>${S.help(q,list(q.assetIds).map(id=>assets.get(id)).filter(Boolean),'질문 안내')}${help}</div>`;node.querySelector('[data-edit]').onclick=()=>resetStandard(q.groupId);}
      else mountStandardQuestion(node,q);
      motions.push(reveal(node,added?0:oldHeight));
    }
    await Promise.all(motions);updateTeamPreview();
    if(state.complete){
      if(state.products.length===1)await showStandardProduct(state.products[0].id);
      else{document.getElementById('hb-price').innerHTML=`<section class="hb-quote"><h2>이 구성에 맞는 제품을 선택해 주세요.</h2><p>같은 옵션 구성으로 등록된 제품이 여러 개 있습니다.</p><div class="hb-model-choices">${state.products.map(p=>`<button type="button" data-standard-product="${p.id}"><strong>${e(p.productName)}</strong><span>${e(p.catalogCode)}</span><small>${e(money(p.unitPrice))}</small></button>`).join('')}</div><div id="hb-product-result"></div></section>`;main.querySelectorAll('[data-standard-product]').forEach(button=>button.onclick=()=>run(async()=>{await showStandardProduct(Number(button.dataset.standardProduct));focusQuestion();},'제품을 확인하고 있습니다.','question'));await reveal(document.getElementById('hb-price').firstElementChild);}
    }
  }
  function standardGuidance(selected) {
    const media = new Map([...list(selected.assets),...list(selected.processAssets),...list(selected.groups).flatMap(g=>[...list(g.assets),...list(g.values).flatMap(v=>list(v.assets))])].map(a=>[a.id,a]));
    const content = list(selected.process?.questions).map(q => {
      const group = list(selected.groups).find(g=>g.id===q.groupId);
      if (group?.role==='SUBCATEGORY') return '';
      const variant = list(selected.variants).find(v=>v.groupId===q.groupId);
      const answer = {choices:list(group?.values).filter(v=>list(variant?.valueIds).includes(v.id)).map(v=>v.key),fields:variant?.inputs || {}};
      const help = S.help(q,list(q.assetIds).map(id=>media.get(id)).filter(Boolean),'질문 안내') + S.answerMedia(q,answer,media);
      return help ? `<article class="hb-answer"><strong>${e(q.question || q.labels?.customer)}</strong>${help}</article>` : '';
    }).join('');
    return content ? `<details class="hb-final-guidance" open><summary>완성된 제품의 상세 안내·이미지</summary>${content}</details>` : '';
  }
  async function showStandardProduct(id) {
    const target = document.getElementById('hb-product-result') || document.getElementById('hb-price');
    const placeholder = resultLoading(target);
    try {
    const fresh = await request(`/customer-series/${seriesContext.series.productId}/configure`, 'POST', standardSelections);
    if (pendingRoute) return;
    if (!fresh.complete || !fresh.products.some(p => p.id === id)) throw Error('제품 구성이 변경되었습니다. 옵션을 다시 확인해 주세요.');
    const selected = await request(`/products/${id}`);
    if (pendingRoute) return;
    if (selected.nonStandard || selected.status === 'DISCONTINUED' || !standardState.products.some(p => p.id === selected.id)) throw Error('현재 구성에 맞는 규격제품을 다시 확인해 주세요.');
    standardProduct = selected;
    target.innerHTML = `<section class="hb-quote"><span class="hb-eyebrow">YOUR CONFIGURATION</span><h2>선택하신 제품이 완성되었습니다.</h2><strong class="hb-selected-name">${e(selected.productName)}</strong>${selected.description ? `<p class="hb-info-description">${e(selected.description)}</p>` : ''}<dl class="hb-meta"><dt>제품코드</dt><dd>${e(selected.catalogCode)}</dd><dt>생산기간</dt><dd>${selected.productionHours == null ? '미등록' : e(S.productionTime(selected.productionHours))}</dd><dt>등록 상태</dt><dd>${e(S.status[selected.status] || selected.status)}</dd></dl><div class="hb-total">${e(money(selected.unitPrice))}</div><small>선택한 옵션에 일치하는 실제 규격제품의 등록 단가</small>${S.media(list(selected.assets).filter(a=>!a.image),'제품 첨부파일')}${standardGuidance(selected)}<p class="hb-result-note">배송비·할인·결제는 연결 전이며 주문은 저장되지 않습니다.</p>${standardState.steps.length ? '<button type="button" id="hb-standard-restart">옵션 다시 구성하기</button>' : ''}</section>`;
    S.confirmationTest(target.querySelector('.hb-quote'), selected.process);
    const restart = document.getElementById('hb-standard-restart');
    if (restart) restart.onclick = () => resetStandard(standardState.steps[0].groupId);
    await reveal(target.querySelector('.hb-quote'), placeholder._hbHeight); void installFaq();
    } catch (err) {
      target.innerHTML = `<section class="hb-quote"><h2>선택한 제품을 다시 확인해 주세요.</h2><p class="hb-question-errors">${e(err.message)}</p><button type="button" id="hb-standard-retry">제품·가격 다시 확인</button></section>`;
      document.getElementById('hb-standard-retry').onclick = () => run(async () => { await showStandardProduct(id); focusQuestion(); }, '제품을 확인하고 있습니다.', 'question');
      await reveal(target.querySelector('.hb-quote'), placeholder._hbHeight);
    } finally { if (placeholder.isConnected) placeholder.remove(); }
  }
  let faqSequence=0;
  function removeFaq(){faqSequence++;document.getElementById('hb-faq-button')?.remove();}
  function faqContext(){
    const faqProduct = standardProduct || product;
    const current={...(faqProduct.fixedAnswers||{}),...S.copy(answers)};
    const state=evaluation?.questions.find(s=>s.question.key===currentQuestion),box=document.getElementById('hb-input');
    if(state&&box)current[currentQuestion]=S.readAnswer(box,{...state.question,fields:list(state.question.fields).filter(f=>state.allowed.includes(f.key))},current[currentQuestion]);
    if(!faqProduct.nonStandard)for(const q of faqProduct.process?.questions||[]){
      if(current[q.key])continue;
      const variant=faqProduct.variants?.find(v=>v.groupId===q.groupId),group=faqProduct.groups?.find(g=>g.id===q.groupId);
      if(variant)current[q.key]={choices:list(group?.values).filter(v=>variant.valueIds?.includes(v.id)).map(v=>v.key),fields:variant.inputs||{}};
    }
    return {product:{name:faqProduct.productName,code:faqProduct.catalogCode},answers:current};
  }
  async function installFaq() {
    removeFaq();
    const faqProduct = standardProduct || (kind && evaluation ? product : null);
    if(!faqProduct?.faqTopicId)return;
    const id=faqProduct.id,topicId=faqProduct.faqTopicId,serial=faqSequence;
    const button=document.createElement('button');button.id='hb-faq-button';button.className='pm-faq-float pm-help-icon';button.type='button';button.textContent='FAQ 불러오는 중…';button.disabled=true;document.body.append(button);
    const current=()=>(standardProduct || product)?.id===id&&serial===faqSequence&&button.isConnected;
    try{
      const topics=await request('/faq','GET',undefined,{quiet:true});
      if(!current())return;
      const topic=list(topics).find(f=>f.id===topicId);
      if(!topic)throw Error('연결된 FAQ를 찾을 수 없습니다. 제품의 FAQ 연결 설정을 확인해 주세요.');
      button.disabled=false;button.textContent='ⓘ FAQ · 문의';
      button.onclick=()=>{if(current())S.showProductFaq(topic,faqContext);};
    }catch(err){
      if(!current())return;
      button.disabled=false;button.textContent='ⓘ FAQ 다시 불러오기';button.title=err.message;
      button.onclick=()=>installFaq();
    }
  }
  function updateSlide() {
    main.querySelector('.hb-gallery-track').style.transform = `translateX(-${slide * 100}%)`;
    main.querySelector('.hb-gallery-count').textContent = `${String(slide + 1).padStart(2, '0')} / ${String(album.length).padStart(2, '0')}`;
    main.querySelector('#hb-prev').disabled = slide === 0;
    main.querySelector('#hb-next').disabled = slide === album.length - 1;
    main.querySelectorAll('[data-slide]').forEach(b => b.setAttribute('aria-pressed', String(Number(b.dataset.slide) === slide)));
    main.querySelectorAll('.hb-gallery-slide').forEach((el, i) => el.setAttribute('aria-hidden', String(i !== slide)));
    const a = album[slide];
    main.querySelector('.hb-gallery-caption').textContent = a.sample ? '예시 이미지 · 실제 제품과 다를 수 있습니다.' : '등록된 제품 이미지';
  }
  function prunedFrom(key) {
    const order = list(product.process?.questions), index = order.findIndex(q => q.key === key);
    const next = S.copy(answers);
    if (index < 0) return next;
    for (const q of order.slice(index)) if (!q.fixed) delete next[q.key];
    return next;
  }
  function beforeQuestion(key) {
    const order = list(product.process?.questions), index = order.findIndex(q => q.key === key);
    return completed.filter(k => order.findIndex(q => q.key === k) < index);
  }
  function mergeAnswers() {
    const visible = list(evaluation.questions).filter(s => s.visible && !s.question.fixed);
    for (const state of evaluation.questions) {
      const q = state.question;
      if (!state.visible && !q.fixed) delete answers[q.key];
      else if (!state.errors.length) answers[q.key] = S.copy(state.answer);
      else if (answers[q.key]) {
        const fields = new Set(list(q.fields).map(f => f.key));
        answers[q.key] = S.isChoice(q.control)
          ? { choices: list(answers[q.key].choices).filter(k => state.allowed.includes(k)), fields: {} }
          : { choices: [], fields: Object.fromEntries(Object.entries(answers[q.key].fields || {}).filter(([k]) => fields.has(k))) };
      }
    }
    completed = completed.filter(k => visible.some(s => s.question.key === k && !s.errors.length));
    return visible;
  }
  // Keep registered ALWAYS/ICON rendering intact; compact only media already rendered here.
  function compactQuestionMedia(root) {
    if (!root) return;
    root.querySelectorAll('[data-pm-gallery]').forEach(gallery => {
      const previews = [...gallery.querySelectorAll('[data-pm-image]')];
      if (!previews.length) return;
      const question = gallery.closest('.hb-question');
      if (gallery.classList.contains('pm-media')) {
        gallery.classList.add('hb-media-compact');
        gallery.setAttribute('aria-label', gallery.dataset.pmGalleryTitle || '안내 이미지');
        const caption = gallery.querySelector('.pm-media-caption');
        if (question) caption?.remove();
        else caption?.querySelector('small')?.remove();
      }
      const cover = previews[0];
      if (!cover.querySelector('.hb-media-count')) {
        const count = document.createElement('span');
        count.className = 'hb-media-count'; count.textContent = String(previews.length);
        count.setAttribute('aria-hidden', 'true'); cover.append(count);
        cover.setAttribute('aria-label', `${cover.getAttribute('aria-label') || '이미지 확대'} · 이미지 ${previews.length}장`);
      }
    });
    root.querySelectorAll('.pm-upload-heading > div > small').forEach(summary => {
      // The editable attachment list keeps its own filenames, actions and upload status.
      summary.textContent = `${summary.closest('.pm-upload-area').querySelectorAll('.pms-file').length}개 첨부`;
    });
    const question = root.matches('.hb-question') ? root : root.closest('.hb-question');
    if (!question) return;
    const visibleImage = question.querySelector('[data-pm-image]');
    let hint = question.querySelector(':scope > .hb-image-hint');
    if (!visibleImage) { hint?.remove(); return; }
    if (!hint) {
      hint = document.createElement('p'); hint.className = 'hb-image-hint';
      hint.textContent = '이미지를 누르면 크게 볼 수 있습니다.';
      question.querySelector(':scope > h2').after(hint);
    }
  }
  async function reveal(node, oldHeight = 0) {
    if (!node?.isConnected) return;
    compactQuestionMedia(node);
    const height = node.getBoundingClientRect().height;
    if (!duration(1)) return;
    node.style.overflow = 'hidden';
    const animation = node.animate([
      { height: oldHeight + 'px', opacity: oldHeight ? 1 : 0, transform: oldHeight ? 'translateY(0)' : 'translateY(14px)' },
      { height: height + 'px', opacity: 1, transform: 'translateY(0)' }
    ], { duration: duration(320), easing: 'cubic-bezier(.22,.61,.36,1)' });
    try { await animation.finished; } catch (_) { /* Navigation may remove the thread. */ }
    if (node.isConnected) node.style.overflow = '';
  }
  async function removeMessage(node) {
    if (!duration(1)) { node.remove(); return; }
    node.inert = true; node.style.overflow = 'hidden';
    const animation = node.animate([
      { height: node.getBoundingClientRect().height + 'px', opacity: 1 },
      { height: '0px', opacity: 0 }
    ], { duration: duration(180), easing: 'ease-in-out' });
    try { await animation.finished; } catch (_) { /* Safe during route changes. */ }
    node.remove();
  }
  async function resetQuestion(key) {
    if (!key) return;
    return run(async () => {
      const next = prunedFrom(key);
      // Commit only after success: a network failure must not erase previous answers.
      const result = await request(`/products/${product.id}/customer-evaluate`, 'POST', next);
      if (pendingRoute) return;
      answers = next; completed = beforeQuestion(key); evaluation = result;
      document.getElementById('hb-price').replaceChildren();
      await paintProcess(); focusQuestion();
    }, '이 질문부터 이후 선택을 초기화하고 있습니다.', 'question');
  }
  function questionShell(state, index) {
    const node = document.createElement('article');
    node.className = 'hb-question'; node.dataset.questionKey = state.question.key;
    node.innerHTML = `<header class="hb-question-heading"><span class="hb-eyebrow">STEP ${String(index + 1).padStart(2, '0')} · ${state.required ? '필수' : '선택'}</span><span class="hb-question-state"></span></header><h2 tabindex="-1"></h2><div class="hb-question-body"></div>`;
    return node;
  }
  function completeQuestion(node, state) {
    const signature = JSON.stringify([state.question.question, state.answer]);
    if (node._hbComplete === signature) return null;
    const height = node.getBoundingClientRect().height;
    node._hbComplete = signature; node.classList.remove('is-current', 'hb-current', 'is-loading'); node.classList.add('is-complete');
    node.removeAttribute('id'); node.inert = false;
    const key = state.question.key;
    node.querySelector('.hb-question-state').innerHTML = `<span>선택 완료</span><button type="button" data-edit="${e(key)}" aria-label="${e(state.question.labels.customer)} 이 질문부터 초기화">이 질문부터 초기화</button>`;
    node.querySelector('h2').textContent = state.question.question || state.question.labels.customer;
    node.querySelector('.hb-question-body').innerHTML = `<div class="hb-response"><span class="hb-response-label">선택하신 내용</span><p>${e(S.answerText(state.question, state.answer, assets)) || '선택하지 않음'}</p>${S.help(state.question,list(state.question.assetIds).map(id=>assets.get(id)).filter(Boolean),'질문 참고자료')}${S.answerNotices(state.question, state.answer, state, product.process).map(t => S.guideHtml(state.question,t,product.process)).join('')}${S.answerMedia(state.question, state.answer, assets)}</div>`;
    node.querySelector('[data-edit]').onclick = () => resetQuestion(key);
    return reveal(node, height);
  }
  function mountCurrent(node, state, index, attemptedKey) {
    const q = { ...state.question, choices: list(state.question.choices).filter(c => state.allowed.includes(c.key)), fields: list(state.question.fields).filter(f => state.allowed.includes(f.key)) };
    node._hbComplete = null; node.inert = !!stepLoading; node.classList.remove('is-complete','is-loading'); node.classList.add('is-current', 'hb-current'); node.id = 'hb-current';
    node.querySelector('h2').textContent = q.question || q.labels.customer;
    node.querySelector('.hb-question-state').textContent = '선택 중';
    answers[q.key] = answers[q.key] || S.copy(state.answer);
    node.querySelector('.hb-question-body').innerHTML = `${S.help(q, list(q.assetIds).map(id => assets.get(id)).filter(Boolean), "질문 참고자료")}<div id="hb-input" data-control="${e(q.control)}">${S.inputAnswer(q, answers[q.key], false, assets)}</div><div id="hb-guides"></div><div id="hb-question-errors" class="hb-question-errors" role="alert">${attemptedKey === q.key ? state.errors.map(e).join('<br>') : ''}</div><button type="button" class="hb-next" id="hb-answer-next">선택 완료 · 다음으로</button>`;
    const box = node.querySelector('#hb-input');
    S.bindEqualInputs(box, q, product.process, answers);
    box.querySelectorAll('input[aria-required],textarea[aria-required]').forEach(input => { input.required = !!state.required && input.getAttribute('aria-required') === 'true'; });
    // Choice and dimension counts are based on the registered question, not label guesses.
    const grid = box.querySelector('.pm-answer-choices');
    if (grid) {
      grid.style.setProperty('--hb-option-columns', Math.min(4, Math.max(1, q.choices.length)));
      grid.dataset.optionCount = String(q.choices.length);
    }
    box.querySelectorAll('.pm-answer-fields').forEach(fields => {
      const count = fields.querySelectorAll(':scope > .pm-answer-field').length;
      fields.style.setProperty('--hb-field-columns', Math.min(3, Math.max(1, count)));
      fields.dataset.fieldCount = String(count);
    });
    const showGuides = () => {
      if (!box.isConnected) return;
      const value = S.readAnswer(box, q, answers[q.key]);
      updateTeamPreview({...evaluation,questions:evaluation.questions.map(item=>item.question.key===q.key?{...item,answer:value}:item)});
      node.querySelector('#hb-guides').innerHTML = S.answerNotices(q, value, state, product.process).map(t => S.guideHtml(q, t, product.process)).join('');
    };
    let guideSequence=0, guideTimer;
    const updateGuidance=()=>{
      if (!box.isConnected) return;
      showGuides(); clearTimeout(guideTimer);
      if (running || uploadCount) return;
      const seq=++guideSequence, id=product.id;
      guideTimer=setTimeout(async()=>{
        if (!box.isConnected || running || seq !== guideSequence) return;
        try {
          const values={...answers,[q.key]:S.readAnswer(box,q,answers[q.key])};
          const result=await request(`/products/${id}/customer-evaluate`,'POST',values,{quiet:true});
          if(!box.isConnected||seq!==guideSequence||product?.id!==id)return;
          const fresh=result.questions.find(s=>s.question.key===q.key);if(!fresh)return;
          S.updateAnswerLimits(box,fresh.question.fields);
          updateTeamPreview(result);
          const validation = node.querySelector('#hb-question-errors');
          if (validation?.textContent.trim()) { validation.textContent = list(fresh.errors).join('\n'); validation.hidden = !validation.textContent; }
          node.querySelector('#hb-guides').innerHTML=S.answerNotices(q,values[q.key],fresh,product.process).map(t=>S.guideHtml(q,t,product.process)).join('');
        }catch { /* The explicit Next action still validates, and displays any request error. */ }
      },160);
    };
    box.addEventListener('input', updateGuidance); box.addEventListener('change', updateGuidance); showGuides();
    S.answerFileEvents(box, q, answers[q.key], assets, {
      uploadPath: S.base + '/assets/stage', isCurrent: () => box.isConnected && currentQuestion === q.key,
      onChange: value => { S.dirty = false; answers[q.key] = value; showGuides(); queueMicrotask(() => compactQuestionMedia(node)); },
      onBusy: on => {
        uploadCount = Math.max(0, uploadCount + (on ? 1 : -1));
        localBusy(uploadCount > 0, '첨부파일을 업로드하고 있습니다.', false);
        if (on) queueMicrotask(() => {
          if (box.isConnected && uploadCount) box.querySelectorAll('.pm-upload-area[aria-disabled=true]').forEach(area => area.removeAttribute('aria-disabled'));
        });
        if (!on) queueMicrotask(() => compactQuestionMedia(node));
        if (!on && !uploadCount && pendingRoute && !running) { pendingRoute = false; run(route); }
      },
      onError: message => error(message, true)
    });
    node.querySelector('#hb-answer-next').onclick = () => run(async () => {
      clearTimeout(guideTimer); guideSequence++;
      const next = prunedFrom(q.key); next[q.key] = S.readAnswer(box, q, answers[q.key]);
      const result = await request(`/products/${product.id}/customer-evaluate`, 'POST', next);
      if (pendingRoute) return;
      const done = beforeQuestion(q.key), fresh = result.questions.find(s => s.question.key === q.key);
      if (fresh?.visible && !fresh.errors.length) done.push(q.key);
      answers = next; completed = done; evaluation = result;
      document.getElementById('hb-price').replaceChildren();
      await paintProcess(q.key); focusQuestion();
    }, '선택한 내용에 맞는 다음 질문을 확인하고 있습니다.', 'question');
    bindStoreQuestionNavigation(box, q.control, () => node.querySelector('#hb-answer-next').onclick(), node.querySelector('#hb-answer-next'));
  }
  async function paintProcess(attemptedKey) {
    await settleStepLoading();
    const visible = mergeAnswers(), active = visible.find(s => !completed.includes(s.question.key));
    currentQuestion = active?.question?.key || null;
    const config = document.getElementById('hb-config');
    let thread = config.querySelector('#hb-thread');
    if (!thread) {
      const fixed = list(evaluation.questions).filter(s => s.question.fixed && product.groups?.find(g=>g.id===s.question.groupId)?.role!=='SUBCATEGORY');
      config.innerHTML = `<div class="hb-process-header"><span>나만의 제품 구성</span><span id="hb-step-count"></span></div><div class="hb-progress"><i></i></div>${fixed.length ? `<details class="hb-fixed-specs"><summary>기본 사양 보기</summary>${fixed.map(s => `<div class="hb-answer"><strong>${e(s.question.labels.customer)}</strong><p>${e(S.answerText(s.question, s.answer, assets))}</p>${S.help(s.question,list(s.question.assetIds).map(id=>assets.get(id)).filter(Boolean),'기본 사양 안내')}${S.answerMedia(s.question, s.answer, assets)}</div>`).join('')}</details>` : ''}<div id="hb-thread" aria-label="제품 구성 질문과 답변"></div><div id="hb-flow-error" class="hb-question-errors" role="alert" hidden></div><div id="hb-inline-status" class="hb-inline-status" role="status" aria-live="polite" hidden></div>`;
      thread = config.querySelector('#hb-thread');
      compactQuestionMedia(config.querySelector('.hb-fixed-specs'));
    }
    document.getElementById('hb-step-count').textContent = `${completed.length} / ${visible.length} 단계`;
    config.querySelector('.hb-progress i').style.width = `${visible.length ? completed.length / visible.length * 100 : 100}%`;
    const wanted = visible.filter(s => completed.includes(s.question.key) || s === active);
    const keys = new Set(wanted.map(s => s.question.key));
    await Promise.all([...thread.children].filter(n => !keys.has(n.dataset.questionKey)).map(removeMessage));
    const motions = [];
    for (const state of wanted) {
      let node = [...thread.children].find(n => n.dataset.questionKey === state.question.key);
      const index = visible.indexOf(state), added = !node;
      if (added) { node = questionShell(state, index); thread.append(node); }
      if (completed.includes(state.question.key)) {
        const change = completeQuestion(node, state); if (change) motions.push(change);
      } else {
        const oldHeight = node.getBoundingClientRect().height;
        mountCurrent(node, state, index, attemptedKey);
        motions.push(reveal(node, added ? 0 : oldHeight));
      }
    }
    await Promise.all(motions);
    updateTeamPreview();
    if (!active) {
      if (evaluation.complete) await calculate();
      else {
        const notice = document.createElement('p'); notice.className = 'hb-question-errors';
        notice.textContent = '구성을 완료할 수 없습니다. 등록된 필수 질문과 기본 사양을 확인해 주세요. ' + evaluation.questions.flatMap(s => s.errors).join(' ');
        thread.append(notice);
      }
    }
  }
  async function calculate() {
    const target = document.getElementById('hb-price');
    const placeholder = resultLoading(target);
    try {
      const status = (await request('/pricing/status', 'POST', { productIds: [product.id] }))[0];
      if (!status?.registered) throw Error('등록된 가격계산식이 없습니다. 제품 관리에서 가격계산식을 등록해 주세요.');
      if (!status.ready || status.stale) throw Error(status.message || '현재 프로세스에 맞게 가격계산식을 검증하고 다시 저장해 주세요.');
      const calculation = await request(`/products/${product.id}/pricing/evaluate`, 'POST', { answers: evaluation.answers });
      if (pendingRoute) return;
      if (!calculation.complete) throw Error(list(calculation.errors).join('\n') || '가격계산을 완료하지 못했습니다.');
      target.innerHTML = `<section class="hb-quote"><span class="hb-eyebrow">YOUR CONFIGURATION</span><h2>나만의 구성이 완성되었습니다.</h2><div class="hb-total">${e(money(calculation.total))}</div><small>저장된 가격식으로 계산한 구성 가격</small><details><summary>가격계산 내역 확인</summary>${list(calculation.terms).map(t => `<div class="hb-term"><span>${e(t.name)}${t.applied ? '' : ' · 적용 안 됨'}</span><span>${t.role === 'FACTOR' ? e(t.amount) + '배' : e(money(t.amount))}</span></div><p class="hb-term-why">${e(t.explanation)}</p>${list(t.multipliers).map(m => `<p class="hb-term-why">${e(m.name)}: ${e(m.value)}배 (${list(m.terms).map(f => e(f.name) + ' ' + e(f.value)).join(' / ')})</p>`).join('')}${list(t.adjustments).map(a => `<p class="hb-term-why">${e(a.name)}: ${e(a.before)} → ${e(a.after)} · ${e(a.range)}</p>`).join('')}`).join('')}</details><p class="hb-result-note">제품 구성 확인용 결과입니다. 배송비·할인·결제는 연결 전이며 주문은 저장되지 않습니다.</p><button id="hb-restart-config">옵션 다시 구성하기</button></section>`;
      S.confirmationTest(target.querySelector('.hb-quote'), product.process);
      document.getElementById('hb-restart-config').onclick = () => resetQuestion(list(product.process?.questions).find(q => !q.fixed)?.key);
      await reveal(target.querySelector('.hb-quote'), placeholder._hbHeight);
    } catch (err) {
      target.innerHTML = `<section class="hb-quote"><h2>옵션 선택이 완료되었습니다.</h2><p class="hb-question-errors">${e(err.message)}</p><button id="hb-retry-price">가격계산 다시 시도</button><p class="hb-result-note">계산되지 않은 가격은 표시하지 않습니다.</p></section>`;
      document.getElementById('hb-retry-price').onclick = () => run(async () => { await calculate(); focusQuestion(); }, '가격을 다시 계산하고 있습니다.', 'question');
      await reveal(target.querySelector('.hb-quote'), placeholder._hbHeight);
    } finally { if (placeholder.isConnected) placeholder.remove(); }
  }
  main.addEventListener('load', ev => ev.target.closest?.('.hb-card-image')?.classList.remove('is-loading'), true);
  // Missing registered images fall back visibly, never silently presented as real product photography.
  main.addEventListener('error', ev => {
    const img = ev.target; if (!img.matches?.('img[data-fallback]')) return;
    const failedUrl = img.currentSrc || img.src, fallback = img.dataset.fallback; delete img.dataset.fallback;
    img.src = fallback; img.alt += ' · 등록 이미지 로드 실패, 예시 이미지로 대체';
    const card = img.closest('.hb-card');
    if (card) {
      const foot = card.querySelector('.hb-card-foot>span');
      foot.textContent = '등록 이미지 로드 실패 · 예시 이미지';
    }
    if (img.closest('.hb-gallery')) {
      album.forEach(a => { if (safeUrl(a.url) === safeUrl(failedUrl)) a.sample = true; });
      main.querySelector('.hb-gallery-caption').textContent = '등록 이미지를 불러오지 못해 예시 이미지를 표시합니다.';
    }
  }, true);
  async function route() {
    const params = new URLSearchParams(location.search); kind = params.get('kind') === 'custom';
    const match = location.pathname.match(/\/(products|series)\/(\d+)\/?$/);
    if (match) {
      if (match[1] === 'series') await openSeries(Number(match[2]), true);
      else await detail(Number(match[2]), true);
    } else await catalog(true, true);
  }
  window.addEventListener('resize', () => main.querySelectorAll('.hb-rail').forEach(rail => rail.dispatchEvent(new Event('hb-resize'))));
  window.addEventListener('popstate', () => { if (running || uploadCount) pendingRoute = true; else run(route); });
  document.querySelectorAll('[data-store-home]').forEach(link => { link.onclick = ev => { if (ev.ctrlKey || ev.metaKey || ev.shiftKey || ev.altKey) return; ev.preventDefault(); run(async () => { await catalog(); window.scrollTo({ top: 0, behavior: 'smooth' }); }); }; });
  document.getElementById('hb-motion-toggle').onclick = event => {
    const reduced = document.body.dataset.motion !== 'reduced';
    document.body.dataset.motion = reduced ? 'reduced' : 'smooth';
    event.currentTarget.textContent = reduced ? '부드러운 모션 켜기' : '모션 줄이기';
    event.currentTarget.setAttribute('aria-pressed', String(reduced));
    try { localStorage.setItem('hb-store-motion-v2', document.body.dataset.motion); } catch (_) { /* Optional preference. */ }
  };
  document.getElementById('hb-motion-toggle').textContent = document.body.dataset.motion === 'reduced' ? '부드러운 모션 켜기' : '모션 줄이기';
  document.getElementById('hb-motion-toggle').setAttribute('aria-pressed', String(document.body.dataset.motion === 'reduced'));
  run(route, '히든바스 컬렉션을 불러오고 있습니다.');
})(window.PMS);
