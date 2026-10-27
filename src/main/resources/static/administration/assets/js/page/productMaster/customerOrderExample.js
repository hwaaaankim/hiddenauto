(function (S) {
  'use strict';
  const main = document.getElementById('hb-main'), overlay = document.getElementById('hb-loading');
  const errorBox = document.getElementById('hb-error');
  const ROOT = '/admin/customer-order-example';
  const originalApi = S.api;
  S.api = async (path, method = 'GET', body, signal) => {
    const controller = new AbortController();
    const cancel = () => controller.abort();
    if (signal?.aborted) cancel(); else signal?.addEventListener('abort', cancel, { once: true });
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; cancel(); }, body instanceof FormData ? 120000 : 40000);
    try { return await originalApi(path, method, body, controller.signal); }
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
  let navigation = [], sections = [], kind = false, category = '', subcategory = '', series = '', busy = 0, running = false;
  let product = null, answers = {}, evaluation = null, completed = [], assets = new Map(), currentQuestion = null;
  let uploadCount = 0, slide = 0, album = [], lastAction = null, pendingRoute = false;
  let loadingProgress = 0, loadingHide = null, galleryAnimation = null, gallerySequence = 0, galleryRequested = 0;
  const railMotions = new WeakMap();
  let catalogEpoch = 0, seriesObserver = null, seriesLoading = false, seriesAutoPaused = false;
  let catalogScrollVersion = 0, consumedScrollVersion = 0;
  let catalogPage = { nextOffset: 0, totalSections: 0, hasMore: false, productTotal: 0 };
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
      if (host) { host.textContent = message || ''; host.hidden = !message; return; }
    }
    errorBox.hidden = !message;
    errorBox.textContent = message || '';
    if (message) {
      const retry = document.createElement('button'); retry.textContent = '다시 시도';
      retry.onclick = () => run(lastAction); errorBox.append(retry);
      errorBox.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }
  }
  function localBusy(on, label) {
    const config = document.getElementById('hb-config');
    if (!config) return;
    config.setAttribute('aria-busy', String(on));
    const current = config.querySelector('.hb-question.is-current');
    if (current) current.inert = on;
    config.querySelectorAll('[data-edit]').forEach(b => { b.disabled = on; });
    const status = document.getElementById('hb-inline-status');
    if (status) { status.textContent = on ? label : ''; status.hidden = !on; }
  }
  async function run(action, label = '화면을 준비하고 있습니다.', scope = 'page') {
    if (running || uploadCount || !action) return;
    running = true; lastAction = action; error('');
    const local = scope === 'question';
    if (local) { error('', true); localBusy(true, label); } else setBusy(true, label);
    try { await action(); }
    catch (err) { error(err.message || '처리 중 오류가 발생했습니다.', local); }
    finally {
      if (local) localBusy(false, label); else setBusy(false);
      running = false;
      armSeriesObserver();
      if (pendingRoute && !uploadCount) { pendingRoute = false; run(route); }
    }
  }
  // Every request has a bounded lifetime so a disconnected server cannot leave the overlay open.
  async function api(path, method = 'GET', body) {
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 40000);
    try { const result = await S.api(path, method, body, controller.signal); progress(Math.min(86, loadingProgress + 24)); return result; }
    catch (err) { if (err.name === 'AbortError') throw Error('서버 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.'); throw err; }
    finally { clearTimeout(timer); }
  }
  const request = (path, method = 'GET', body) => api(S.base + path, method, body);
  function safeUrl(url) {
    if (typeof url !== 'string' || !url.trim()) return '';
    try { const u = new URL(url, location.origin); return ['http:', 'https:'].includes(u.protocol) ? u.href : ''; } catch { return ''; }
  }
  function photo(card) { return list(card.assets).find(a => a.image && safeUrl(a.url)); }
  function cardImage(card, index = 0) { return safeUrl(photo(card)?.url) || sample(card.id ?? index); }
  function routeUrl(id) {
    const url = new URL(id ? `${ROOT}/products/${id}` : ROOT, location.origin);
    if (kind) url.searchParams.set('kind', 'custom');
    if (category) url.searchParams.set('category', category);
    if (subcategory) url.searchParams.set('subcategory', subcategory);
    if (series) url.searchParams.set('series', series);
    return url.pathname + url.search;
  }
  function saveRoute(id, replace = false) { history[replace ? 'replaceState' : 'pushState']({}, '', routeUrl(id)); }
  function selectedSections() {
    return sections.filter(s => (!category || s.category === category) && (!subcategory || s.subcategory === subcategory) && (!series || s.series === series));
  }
  function catalogPath(offset = 0, includeNavigation = false) {
    const query = new URLSearchParams({ nonStandard: String(kind), offset: String(offset), includeNavigation: String(includeNavigation) });
    for (const [key, value] of Object.entries({ category, subcategory, series })) if (value) query.set(key, value);
    return '/customer-showroom/page?' + query.toString();
  }
  function invalidCatalogFilters() {
    let changed = false;
    if (category && !navigation.some(s => s.category === category)) { category = ''; changed = true; }
    if (subcategory && !navigation.some(s => (!category || s.category === category) && s.subcategory === subcategory)) { subcategory = ''; changed = true; }
    if (series && !navigation.some(s => (!category || s.category === category) && (!subcategory || s.subcategory === subcategory) && s.series === series)) { series = ''; changed = true; }
    return changed;
  }
  async function catalog(fetchData = true, replace = false) {
    const epoch = ++catalogEpoch; seriesObserver?.disconnect(); seriesLoading = false; seriesAutoPaused = false;
    consumedScrollVersion = catalogScrollVersion;
    let result = await request(catalogPath(0, fetchData || !navigation.length));
    if (pendingRoute || epoch !== catalogEpoch) return;
    if (fetchData || !navigation.length) navigation = list(result.navigation);
    if (invalidCatalogFilters()) result = await request(catalogPath());
    if (pendingRoute || epoch !== catalogEpoch) return;
    sections = list(result.sections); catalogPage = result;
    product = null; currentQuestion = null; answers = {}; completed = []; evaluation = null;
    document.body.classList.remove('hb-detail-page');
    saveRoute(null, replace); paintCatalog();
  }
  function seriesMoreState(message) {
    const box = document.getElementById('hb-series-more'), button = document.getElementById('hb-more-series');
    if (!box || !button) return;
    box.hidden = !catalogPage.hasMore;
    box.setAttribute('aria-busy', String(seriesLoading)); button.disabled = seriesLoading;
    button.textContent = seriesLoading ? '다음 시리즈를 불러오고 있습니다.' : message ? '시리즈 다시 불러오기' : '시리즈 5개 더 보기';
    document.getElementById('hb-series-more-status').textContent = message || `${sections.length} / ${catalogPage.totalSections}개 시리즈`;
  }
  function armSeriesObserver() {
    seriesObserver?.disconnect();
    const sentinel = document.getElementById('hb-series-sentinel');
    if (!sentinel || !catalogPage.hasMore || product || seriesLoading || seriesAutoPaused || !('IntersectionObserver' in window)) return;
    seriesObserver = new IntersectionObserver(entries => {
      if (catalogScrollVersion > consumedScrollVersion && entries.some(entry => entry.isIntersecting)) loadSeriesBatch(true);
    }, { rootMargin: '260px 0px', threshold: 0 });
    seriesObserver.observe(sentinel);
  }
  async function loadSeriesBatch(automatic = false) {
    if (seriesLoading || running || product || !catalogPage.hasMore || (automatic && (seriesAutoPaused || catalogScrollVersion <= consumedScrollVersion))) return;
    consumedScrollVersion = catalogScrollVersion;
    const epoch = catalogEpoch, offset = catalogPage.nextOffset;
    seriesLoading = true; seriesObserver?.disconnect(); seriesMoreState();
    try {
      const result = await request(catalogPath(offset));
      if (epoch !== catalogEpoch || product) return;
      const keys = new Set(sections.map(section => section.key));
      const added = list(result.sections).filter(section => !keys.has(section.key));
      sections.push(...added); catalogPage = result; seriesAutoPaused = false;
      const target = document.getElementById('hb-collections');
      const y = scrollY, anchor = document.documentElement.style.overflowAnchor;
      document.documentElement.style.overflowAnchor = 'none';
      target.insertAdjacentHTML('beforeend', added.map(sectionHtml).join(''));
      void target.offsetHeight; window.scrollTo({ top: y, behavior: 'instant' });
      requestAnimationFrame(() => { document.documentElement.style.overflowAnchor = anchor; });
      bindCollection(main);
      // Only newly appended sections enter; existing rails keep their DOM and scroll positions.
      const nodes = [...target.querySelectorAll(':scope > .hb-section')].slice(-added.length);
      if (added.length && duration(1)) nodes.forEach(node => node.animate([{ opacity: 0, transform: 'translateY(12px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: duration(360), easing: 'ease-out' }));
    } catch (err) {
      if (epoch !== catalogEpoch) return;
      seriesAutoPaused = true; seriesMoreState(err.message || '시리즈를 불러오지 못했습니다. 다시 시도해 주세요.');
    } finally {
      if (epoch === catalogEpoch) { seriesLoading = false; seriesMoreState(seriesAutoPaused ? document.getElementById('hb-series-more-status')?.textContent : ''); armSeriesObserver(); }
    }
  }
  function selectOptions(values, selected, all) {
    return `<option value="">${all}</option>` + [...new Set(values.filter(Boolean))].map(v => `<option value="${e(v)}" ${v === selected ? 'selected' : ''}>${e(v)}</option>`).join('');
  }
  function railCards(section) {
    const cards = [...section.products];
    if (cards.length && cards.length >= section.total) {
      while (cards.length < 5) cards.push({ ...section.products[cards.length % section.products.length], showroomRepeated: true });
    }
    return cards.map(cardHtml).join('');
  }
  function cardHtml(p, i) {
    const text = p.nonStandard ? '옵션 구성 후 가격 확인' : money(p.unitPrice);
    return `<article class="hb-card"><a href="${e(routeUrl(p.id))}" data-product="${e(p.id)}"><div class="hb-card-copy"><small>${kind ? 'MADE TO ORDER' : 'STANDARD COLLECTION'}</small><h3>${e(p.productName)}</h3><div class="hb-card-price">${e(text)}</div></div><div class="hb-card-image is-loading"><img src="${e(cardImage(p, i))}" alt="${e(p.productName)}${photo(p) ? '' : ' · 예시 이미지'}" loading="lazy" data-fallback="${e(sample(p.id))}"></div><div class="hb-card-foot"><span>${photo(p) ? '등록 이미지' : '<span class="hb-sample-badge">예시 이미지</span>'} · ${e(S.status[p.status] || p.status)}${p.showroomRepeated ? '<br><span class="hb-sample-badge hb-duplicate-label">슬라이드 확인용 중복 제품</span>' : ''}</span><span>자세히 보기 ↗</span></div></a></article>`;
  }
  function sectionHtml(s) { return `<section class="hb-section" data-section="${e(s.key)}"><div class="hb-section-head"><div><span class="hb-eyebrow">${e([s.category, s.subcategory].filter(Boolean).join(' / '))}</span><h2>${e(s.series)}</h2><p class="hb-sample-text">공간과 취향에 맞는 제품을 만나보세요.</p></div><div class="hb-arrows"><button data-scroll="-1" aria-label="${e(s.series)} 이전 제품">‹</button><button data-scroll="1" aria-label="${e(s.series)} 다음 제품">›</button></div></div><div class="hb-rail" tabindex="0" aria-label="${e(s.series)} 제품 슬라이드">${railCards(s)}${s.products.length < s.total ? `<button class="hb-more" data-more="${e(s.key)}">제품 더 보기<br><small>${s.products.length} / ${s.total}</small></button>` : ''}</div></section>`; }
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
  function fitSeriesRail(rail) {
    if (!rail) return;
    const fit = () => {
      if (!rail.isConnected || rail.clientWidth <= 0) return;
      const gap = parseFloat(getComputedStyle(rail).columnGap) || 0;
      const minimum = innerWidth <= 600 ? 82 : innerWidth <= 800 ? 90 : 104;
      const oldStep = rail._hbSeriesStep || minimum + gap;
      const index = Math.round(rail.scrollLeft / oldStep);
      const count = Math.max(1, Math.floor((rail.clientWidth + gap) / (minimum + gap)));
      const slot = (rail.clientWidth - gap * (count - 1)) / count;
      const state = railMotions.get(rail); if (state?.frame) cancelAnimationFrame(state.frame);
      railMotions.delete(rail); delete rail.dataset.moving;
      rail.style.setProperty('--hb-series-slot', slot + 'px'); rail.dataset.visibleItems = String(count);
      rail._hbSeriesStep = slot + gap;
      rail.scrollLeft = Math.min(Math.max(0, rail.children.length - count), index) * rail._hbSeriesStep;
      rail.dispatchEvent(new Event('scroll'));
    };
    fit();
    const resize = new ResizeObserver(fit); resize.observe(rail); rail._hbSizeObserver = resize;
    // Native touch/trackpad movement snaps to the same whole-item boundaries.
    rail.addEventListener('hb-resize', fit);
  }
  function paintCatalog() {
    const oldSeriesRail = main.querySelector('.hb-series-nav');
    const seriesOffset = series ? oldSeriesRail?.scrollLeft || 0 : 0;
    oldSeriesRail?._hbSizeObserver?.disconnect();
    const shown = selectedSections(), scope = navigation.filter(s => !category || s.category === category);
    const navScope = scope.filter(s => !subcategory || s.subcategory === subcategory);
    const uniqueSeries = [...new Map(navScope.map(s => [s.series, s])).values()];
    const markup = `<section class="hb-intro"><div><span class="hb-eyebrow">HIDDENBATH COLLECTION</span><h1>당신의 공간을 위한 선택.</h1><p class="hb-sample-text">정돈된 형태, 섬세한 소재. 나만의 욕실을 차근차근 완성해 보세요.</p></div><div class="hb-kind" role="group" aria-label="제품 구분"><button data-kind="standard" aria-pressed="${!kind}">규격제품</button><button data-kind="custom" aria-pressed="${kind}">비규격제품</button></div></section>
      <div class="hb-filter"><label>대분류<select id="hb-category" aria-label="대분류">${selectOptions(navigation.map(s => s.category), category, '전체 대분류')}</select></label>${scope.some(s => s.subcategory) ? `<label>중분류<select id="hb-subcategory" aria-label="중분류">${selectOptions(scope.map(s => s.subcategory), subcategory, '전체 중분류')}</select></label>` : ''}<small>${kind ? '비규격 · 질문에 답하며 맞춤 구성' : '규격 · 등록된 사양으로 선택'} / ${catalogPage.productTotal}개 제품</small></div>
      ${uniqueSeries.length ? `<section class="hb-series-shell" aria-label="시리즈 탐색"><div class="hb-series-controls"><button data-series-prev aria-label="이전 시리즈">‹</button><button data-series-next aria-label="다음 시리즈">›</button></div><nav class="hb-series-nav" tabindex="0" aria-label="시리즈 선택"><button data-series="" aria-pressed="${!series}"><span class="hb-circle"><span class="hb-eyebrow">ALL</span></span><span>전체 시리즈</span></button>${uniqueSeries.map((s, i) => `<button data-series="${e(s.series)}" aria-pressed="${s.series === series}"><span class="hb-circle"><img src="${e(cardImage({ assets: s.assets, id: s.productId }, i))}" alt="" loading="lazy" data-fallback="${e(sample(i))}"></span><span>${e(s.series)}</span></button>`).join('')}</nav></section>` : ''}
      <div id="hb-collections">${shown.length ? shown.map(sectionHtml).join('') : `<div class="hb-empty"><span class="hb-eyebrow">COLLECTION</span><h2>표시할 ${kind ? '비규격' : '규격'}제품이 없습니다.</h2><p>ProductMaster에서 대분류·중분류·시리즈와 활성 옵션을 확인하고 제품을 등록해 주세요.</p><a href="/admin/product-master/${kind ? 'non-standard' : 'products'}">제품 관리로 이동 ↗</a></div>`}</div>
      <div id="hb-series-more" class="hb-series-more" ${catalogPage.hasMore ? '' : 'hidden'}><div id="hb-series-sentinel" aria-hidden="true"></div><button id="hb-more-series" type="button">시리즈 5개 더 보기</button><p id="hb-series-more-status" role="status" aria-live="polite"></p></div>
      <section class="hb-story"><div><span class="hb-eyebrow">DETAILS THAT MATTER</span><h2 class="hb-sample-text">매일 마주하는 공간,<br>나에게 맞는 균형.</h2><p class="hb-sample-text">소재와 크기, 작은 디테일까지. 질문을 따라 선택하고 완성된 구성의 가격을 확인해 보세요. 이 문단은 디자인 확인을 위한 예시 설명입니다.</p><small class="hb-sample-badge">공간 채우기용 문구·이미지</small></div><div class="hb-story-images"><img src="${sample(0)}" alt="욕실 가구 예시 이미지" loading="lazy"><img src="${sample(10)}" alt="욕실 가구 예시 이미지" loading="lazy"></div></section>`;
    // Preserve the kind tabs themselves so their selected background can transition.
    const previousIntro = main.querySelector(':scope > .hb-intro');
    if (previousIntro) {
      const template = document.createElement('template'); template.innerHTML = markup;
      template.content.querySelector('.hb-intro').remove();
      [...main.children].filter(node => node !== previousIntro).forEach(node => node.remove());
      previousIntro.querySelectorAll('[data-kind]').forEach(button => {
        button.setAttribute('aria-pressed', String((button.dataset.kind === 'custom') === kind));
      });
      main.append(template.content);
    } else main.innerHTML = markup;
    document.querySelectorAll('[data-kind]').forEach(b => b.onclick = () => run(async () => {
      kind = b.dataset.kind === 'custom'; category = subcategory = series = ''; await catalog();
    }, '컬렉션을 전환하고 있습니다.'));
    document.getElementById('hb-category').onchange = ev => run(async () => { category = ev.target.value; subcategory = series = ''; await catalog(false); });
    const sub = document.getElementById('hb-subcategory');
    if (sub) sub.onchange = ev => run(async () => { subcategory = ev.target.value; series = ''; await catalog(false); });
    document.querySelectorAll('[data-series]').forEach(b => b.onclick = () => run(async () => { series = b.dataset.series; await catalog(false); }));
    const seriesRail = main.querySelector('.hb-series-nav');
    bindRail(seriesRail, main.querySelector('[data-series-prev]'), main.querySelector('[data-series-next]'));
    if (seriesRail) { fitSeriesRail(seriesRail); seriesRail.scrollLeft = Math.round(seriesOffset / seriesRail._hbSeriesStep) * seriesRail._hbSeriesStep; seriesRail.dispatchEvent(new Event('scroll')); }
    document.getElementById('hb-more-series').onclick = () => { seriesAutoPaused = false; loadSeriesBatch(); };
    seriesMoreState(); bindCollection(main);
  }
  function bindCollection(root) {
    root.querySelectorAll('[data-more]').forEach(b => b.onclick = () => run(async () => {
      const s = sections.find(x => x.key === b.dataset.more);
      const data = await request('/customer-browse', 'POST', { nonStandard: kind, selections: s.selections, filters: {}, size: 12, page: Math.floor(s.products.length / 12) });
      const ids = new Set(s.products.map(p => p.id));
      s.products.push(...data.products.filter(p => !ids.has(p.id))); s.total = data.total;
      const rail = b.closest('.hb-rail'), x = rail.scrollLeft;
      // Repaint only this rail to preserve other sliders, scroll position and focus.
      rail.querySelectorAll('.hb-card').forEach(c => c.remove()); b.insertAdjacentHTML('beforebegin', railCards(s));
      if (s.products.length >= s.total) b.remove(); else b.innerHTML = `제품 더 보기<br><small>${s.products.length} / ${s.total}</small>`;
      rail.scrollLeft = x; rail.dispatchEvent(new Event('scroll')); bindProductLinks(rail);
    }, '다음 제품을 불러오고 있습니다.'));
    root.querySelectorAll('.hb-section').forEach(section => {
      bindRail(section.querySelector('.hb-rail'), section.querySelector('[data-scroll="-1"]'), section.querySelector('[data-scroll="1"]'));
    });
    bindProductLinks(root);
  }
  function bindProductLinks(root) {
    root.querySelectorAll('.hb-card-image img').forEach(img => {
      if (img.complete) img.closest('.hb-card-image').classList.remove('is-loading');
    });
    root.querySelectorAll('[data-product]').forEach(a => a.onclick = ev => {
      if (ev.metaKey || ev.ctrlKey || ev.shiftKey || ev.altKey) return;
      ev.preventDefault(); run(() => detail(Number(a.dataset.product)), '제품 상세를 불러오고 있습니다.');
    });
  }
  async function detail(id, replace = false) {
    catalogEpoch++; seriesObserver?.disconnect(); seriesLoading = false; seriesMoreState();
    const next = await request(`/products/${id}`);
    if (pendingRoute) return;
    if (next.status === 'DISCONTINUED') throw Error('단종된 제품은 이 예시 화면에서 선택할 수 없습니다.');
    gallerySequence++; galleryAnimation?.cancel();
    product = next; kind = next.nonStandard; answers = {}; completed = []; evaluation = null;
    assets = new Map([...list(product.processAssets), ...list(product.assets)].map(a => [a.id, a]));
    const registered = list(product.assets).filter(a => a.image && safeUrl(a.url));
    // Repeated pictures are intentional and visibly marked, solely for slider inspection.
    album = registered.length ? [...registered] : [{ url: sample(id), name: '공간 채우기용 예시 이미지', sample: true }];
    while (album.length < 3) album.push({ ...album[0], repeated: true });
    slide = 0; galleryRequested = 0;
    if (kind) evaluation = await request(`/products/${id}/customer-evaluate`, 'POST', answers);
    if (pendingRoute) return;
    document.body.classList.add('hb-detail-page');
    saveRoute(id, replace); paintDetail(); window.scrollTo({ top: 0, behavior: 'instant' });
    if (kind) await paintProcess();
    await waitImage(document.querySelector('.hb-gallery-slide img'));
  }
  function waitImage(img) {
    if (!img || img.complete) return Promise.resolve();
    return new Promise(resolve => {
      const done = () => { clearTimeout(timer); img.removeEventListener('load', done); img.removeEventListener('error', done); resolve(); };
      const timer = setTimeout(done, 8000); img.addEventListener('load', done); img.addEventListener('error', done);
    });
  }
  function paintDetail() {
    main.querySelector('.hb-series-nav')?._hbSizeObserver?.disconnect();
    main.innerHTML = `<div class="hb-breadcrumb"><a href="${e(routeUrl())}" id="hb-back">컬렉션</a><span>/</span><span>${kind ? '비규격제품' : '규격제품'}</span><span>/</span><span>${e(product.catalogCode)}</span></div>
      <section class="hb-detail"><div class="hb-gallery"><div class="hb-gallery-stage" role="region" aria-label="제품 이미지 슬라이드" tabindex="0"><div class="hb-gallery-track">${album.map((a, i) => `<div class="hb-gallery-slide" aria-hidden="${i !== 0}"><img src="${e(safeUrl(a.url))}" alt="${e(product.productName)} · ${e(a.name)}${a.repeated ? ' · 슬라이드 확인용 중복' : ''}" data-fallback="${e(sample(product.id))}"></div>`).join('')}</div><div class="hb-image-loading" role="status" hidden>이미지를 불러오는 중입니다.</div><div class="hb-gallery-tools"><button id="hb-prev" aria-label="이전 이미지">‹</button><span class="hb-gallery-count" aria-live="polite"></span><button id="hb-next" aria-label="다음 이미지">›</button></div></div><div class="hb-gallery-thumbs">${album.map((a, i) => `<button data-slide="${i}" aria-label="${i + 1}번 이미지" aria-pressed="${i === 0}"><img src="${e(safeUrl(a.url))}" alt="" data-fallback="${e(sample(product.id))}"></button>`).join('')}</div><p class="hb-gallery-caption"></p></div>
      <div class="hb-info pms"><span class="hb-eyebrow">${kind ? 'MADE TO ORDER' : 'STANDARD COLLECTION'}</span><h1>${e(product.productName)}</h1>${product.description ? `<p class="hb-info-description">${e(product.description)}</p>` : '<p class="hb-info-description hb-sample-text">공간의 분위기를 완성하는 간결한 형태와 섬세한 디테일. 일상에 자연스럽게 스며드는 욕실 가구를 만나보세요. · 디자인 확인용 예시 설명</p>'}<dl class="hb-meta"><dt>제품코드</dt><dd>${e(product.catalogCode || '미등록')}</dd><dt>제품 구분</dt><dd>${kind ? '비규격 · 맞춤 구성' : '규격제품'}</dd><dt>생산기간</dt><dd>${product.productionHours == null ? '미등록' : e(product.productionHours) + '시간'}</dd><dt>등록 상태</dt><dd>${e(S.status[product.status] || product.status)}</dd>${!kind ? `<dt>등록 단가</dt><dd>${e(money(product.unitPrice))}</dd>` : ''}</dl><div id="hb-config"></div><div id="hb-price" aria-live="polite"></div></div></section>`;
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
    if (!kind) {
      document.getElementById('hb-config').innerHTML = `<span class="hb-eyebrow">PRODUCT DETAILS</span><h2 style="font-size:20px;font-weight:500">등록된 제품 사양</h2>${standardSpecs()}`;
      document.getElementById('hb-price').innerHTML = `<section class="hb-quote"><h2>제품 가격</h2><div class="hb-total">${e(money(product.unitPrice))}</div><p class="hb-result-note">등록 단가 기준입니다. 배송비·할인·결제금액은 연결 전이며 주문은 저장되지 않습니다.</p></section>`;
    }
  }
  function standardSpecs() {
    return list(product.variants).map(v => {
      const g = list(product.groups).find(x => x.id === v.groupId); if (!g) return '';
      const selected = list(g.values).filter(x => list(v.valueIds).includes(x.id));
      const fields = Object.entries(v.inputs || {}).map(([key, value]) => {
        const f = list(g.fields).find(x => x.key === key);
        return `${f?.labels?.customer || key}: ${Array.isArray(value) ? value.map(id => assets.get(id)?.name || '등록 첨부파일').join(', ') : value}${f?.unit ? ' ' + f.unit : ''}`;
      });
      return `<div class="hb-answer"><header><strong>${e(g.labels.customer)}</strong></header><p>${e([...selected.map(x => x.labels.customer), ...fields].join(' / '))}</p>${g.guide ? `<p class="hb-guide">${e(g.guide)}</p>` : ''}${S.media([...list(g.assets), ...selected.flatMap(x => list(x.assets)), ...list(g.fields).flatMap(f => list(f.assetIds).map(id => assets.get(id))).filter(Boolean)], '선택 사양 참고자료')}</div>`;
    }).join('');
  }
  function updateSlide() {
    main.querySelector('.hb-gallery-track').style.transform = `translateX(-${slide * 100}%)`;
    main.querySelector('.hb-gallery-count').textContent = `${String(slide + 1).padStart(2, '0')} / ${String(album.length).padStart(2, '0')}`;
    main.querySelector('#hb-prev').disabled = slide === 0;
    main.querySelector('#hb-next').disabled = slide === album.length - 1;
    main.querySelectorAll('[data-slide]').forEach(b => b.setAttribute('aria-pressed', String(Number(b.dataset.slide) === slide)));
    main.querySelectorAll('.hb-gallery-slide').forEach((el, i) => el.setAttribute('aria-hidden', String(i !== slide)));
    const a = album[slide];
    main.querySelector('.hb-gallery-caption').textContent = [a.sample ? '공간 채우기용 예시 이미지 · 실제 제품과 다를 수 있습니다.' : '등록된 제품 이미지', a.repeated ? '슬라이드 동작 확인을 위한 중복 이미지입니다.' : ''].filter(Boolean).join(' / ');
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
  async function reveal(node, oldHeight = 0) {
    if (!node?.isConnected) return;
    const height = node.getBoundingClientRect().height;
    if (!duration(1)) return;
    node.style.overflow = 'hidden';
    const animation = node.animate([
      { height: oldHeight + 'px', opacity: oldHeight ? 1 : 0, transform: oldHeight ? 'translateY(0)' : 'translateY(14px)' },
      { height: height + 'px', opacity: 1, transform: 'translateY(0)' }
    ], { duration: duration(420), easing: 'cubic-bezier(.22,.61,.36,1)' });
    try { await animation.finished; } catch (_) { /* Navigation may remove the thread. */ }
    if (node.isConnected) node.style.overflow = '';
  }
  async function removeMessage(node) {
    if (!duration(1)) { node.remove(); return; }
    node.inert = true; node.style.overflow = 'hidden';
    const animation = node.animate([
      { height: node.getBoundingClientRect().height + 'px', opacity: 1 },
      { height: '0px', opacity: 0 }
    ], { duration: duration(240), easing: 'ease-in-out' });
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
    node._hbComplete = signature; node.classList.remove('is-current', 'hb-current'); node.classList.add('is-complete');
    node.removeAttribute('id'); node.inert = false;
    const key = state.question.key;
    node.querySelector('.hb-question-state').innerHTML = `<span>선택 완료</span><button type="button" data-edit="${e(key)}" aria-label="${e(state.question.labels.customer)} 이 질문부터 초기화">이 질문부터 초기화</button>`;
    node.querySelector('h2').textContent = state.question.question || state.question.labels.customer;
    node.querySelector('.hb-question-body').innerHTML = `<div class="hb-response"><span class="hb-response-label">선택하신 내용</span><p>${e(S.answerText(state.question, state.answer, assets)) || '선택하지 않음'}</p>${S.answerGuides(state.question, state.answer, state).map(t => `<p class="hb-response-guide">${e(t)}</p>`).join('')}${S.answerMedia(state.question, state.answer, assets)}</div>`;
    node.querySelector('[data-edit]').onclick = () => resetQuestion(key);
    return reveal(node, height);
  }
  function mountCurrent(node, state, index, attemptedKey) {
    const q = { ...state.question, choices: list(state.question.choices).filter(c => state.allowed.includes(c.key)) };
    node._hbComplete = null; node.classList.remove('is-complete'); node.classList.add('is-current', 'hb-current'); node.id = 'hb-current';
    node.querySelector('h2').textContent = q.question || q.labels.customer;
    node.querySelector('.hb-question-state').textContent = '선택 중';
    answers[q.key] = answers[q.key] || S.copy(state.answer);
    node.querySelector('.hb-question-body').innerHTML = `${q.guide ? `<div class="hb-guide">${e(q.guide)}</div>` : ''}${S.media(list(q.assetIds).map(id => assets.get(id)).filter(Boolean), '질문 참고자료')}<div id="hb-input" data-control="${e(q.control)}">${S.inputAnswer(q, answers[q.key], false, assets)}</div><div id="hb-guides"></div><div id="hb-question-errors" class="hb-question-errors" role="alert">${attemptedKey === q.key ? state.errors.map(e).join('<br>') : ''}</div><button type="button" class="hb-next" id="hb-answer-next">선택 완료 · 다음으로</button>`;
    const box = node.querySelector('#hb-input');
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
      node.querySelector('#hb-guides').innerHTML = S.answerGuides(q, value, state).map(t => `<div class="hb-guide">${e(t)}</div>`).join('');
    };
    box.addEventListener('input', showGuides); box.addEventListener('change', showGuides); showGuides();
    S.answerFileEvents(box, q, answers[q.key], assets, {
      uploadPath: S.base + '/assets/stage', isCurrent: () => box.isConnected && currentQuestion === q.key,
      onChange: value => { S.dirty = false; answers[q.key] = value; showGuides(); },
      onBusy: on => {
        uploadCount = Math.max(0, uploadCount + (on ? 1 : -1));
        localBusy(on, '첨부파일을 업로드하고 있습니다.');
        if (!on && !uploadCount && pendingRoute && !running) { pendingRoute = false; run(route); }
      },
      onError: message => error(message, true)
    });
    node.querySelector('#hb-answer-next').onclick = () => run(async () => {
      const next = prunedFrom(q.key); next[q.key] = S.readAnswer(box, q, answers[q.key]);
      const result = await request(`/products/${product.id}/customer-evaluate`, 'POST', next);
      if (pendingRoute) return;
      const done = beforeQuestion(q.key), fresh = result.questions.find(s => s.question.key === q.key);
      if (fresh?.visible && !fresh.errors.length) done.push(q.key);
      answers = next; completed = done; evaluation = result;
      document.getElementById('hb-price').replaceChildren();
      const status = document.getElementById('hb-inline-status'); if (status) status.hidden = true;
      await paintProcess(q.key); focusQuestion();
    }, '선택한 내용에 맞는 다음 질문을 확인하고 있습니다.', 'question');
  }
  async function paintProcess(attemptedKey) {
    const visible = mergeAnswers(), active = visible.find(s => !completed.includes(s.question.key));
    currentQuestion = active?.question?.key || null;
    const config = document.getElementById('hb-config');
    let thread = config.querySelector('#hb-thread');
    if (!thread) {
      const fixed = list(evaluation.questions).filter(s => s.question.fixed);
      config.innerHTML = `<div class="hb-process-header"><span>나만의 제품 구성</span><span id="hb-step-count"></span></div><div class="hb-progress"><i></i></div>${fixed.length ? `<details class="hb-fixed-specs"><summary>기본 사양 보기</summary>${fixed.map(s => `<div class="hb-answer"><strong>${e(s.question.labels.customer)}</strong><p>${e(S.answerText(s.question, s.answer, assets))}</p>${S.answerMedia(s.question, s.answer, assets)}</div>`).join('')}</details>` : ''}<div id="hb-thread" aria-label="제품 구성 질문과 답변"></div><div id="hb-flow-error" class="hb-question-errors" role="alert" hidden></div><div id="hb-inline-status" class="hb-inline-status" role="status" aria-live="polite" hidden></div>`;
      thread = config.querySelector('#hb-thread');
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
        const wasComplete = node.classList.contains('is-complete'), oldHeight = node.getBoundingClientRect().height;
        mountCurrent(node, state, index, attemptedKey);
        if (added || wasComplete) motions.push(reveal(node, added ? 0 : oldHeight));
      }
    }
    await Promise.all(motions);
    if (!active) {
      if (evaluation.complete) await calculate();
      else {
        const notice = document.createElement('p'); notice.className = 'hb-question-errors';
        notice.textContent = '구성을 완료할 수 없습니다. 등록된 필수 질문과 기본 사양을 확인해 주세요. ' + evaluation.questions.flatMap(s => s.errors).join(' ');
        thread.append(notice);
      }
    }
  }
  let focusMotion = null;
  function focusQuestion() {
    const target = document.querySelector('.hb-question.is-current h2') || document.querySelector('.hb-quote h2');
    if (!target) return;
    target.tabIndex = -1; target.focus({ preventScroll: true });
    const rect = target.getBoundingClientRect(), header = document.querySelector('.hb-header').getBoundingClientRect().height;
    if (rect.top >= header + 24 && rect.top < innerHeight - 190) return;
    if (focusMotion) cancelAnimationFrame(focusMotion);
    const from = window.scrollY, to = Math.max(0, from + rect.top - header - 36), start = performance.now(), ms = duration(460);
    const tick = now => {
      const t = ms ? Math.min(1, (now - start) / ms) : 1;
      window.scrollTo({ top: from + (to - from) * (1 - Math.pow(1 - t, 3)), behavior: 'instant' });
      if (t < 1) focusMotion = requestAnimationFrame(tick); else focusMotion = null;
    };
    focusMotion = requestAnimationFrame(tick);
  }
  window.addEventListener('wheel', () => { if (focusMotion) cancelAnimationFrame(focusMotion); focusMotion = null; }, { passive: true });
  window.addEventListener('touchstart', () => { if (focusMotion) cancelAnimationFrame(focusMotion); focusMotion = null; }, { passive: true });
  async function calculate() {
    const target = document.getElementById('hb-price');
    try {
      const status = (await request('/pricing/status', 'POST', { productIds: [product.id] }))[0];
      if (!status?.registered) throw Error('등록된 가격계산식이 없습니다. 제품 관리에서 가격계산식을 등록해 주세요.');
      if (!status.ready || status.stale) throw Error(status.message || '현재 프로세스에 맞게 가격계산식을 검증하고 다시 저장해 주세요.');
      const calculation = await request(`/products/${product.id}/pricing/evaluate`, 'POST', { answers: evaluation.answers });
      if (pendingRoute) return;
      if (!calculation.complete) throw Error(list(calculation.errors).join('\n') || '가격계산을 완료하지 못했습니다.');
      target.innerHTML = `<section class="hb-quote"><span class="hb-eyebrow">YOUR CONFIGURATION</span><h2>나만의 구성이 완성되었습니다.</h2><div class="hb-total">${e(money(calculation.total))}</div><small>저장된 가격식으로 계산한 구성 가격</small><details><summary>가격계산 내역 확인</summary>${list(calculation.terms).map(t => `<div class="hb-term"><span>${e(t.name)}${t.applied ? '' : ' · 적용 안 됨'}</span><span>${t.role === 'FACTOR' ? e(t.amount) + '배' : e(money(t.amount))}</span></div><p class="hb-term-why">${e(t.explanation)}</p>${list(t.multipliers).map(m => `<p class="hb-term-why">${e(m.name)}: ${e(m.value)}배 (${list(m.terms).map(f => e(f.name) + ' ' + e(f.value)).join(' / ')})</p>`).join('')}${list(t.adjustments).map(a => `<p class="hb-term-why">${e(a.name)}: ${e(a.before)} → ${e(a.after)} · ${e(a.range)}</p>`).join('')}`).join('')}</details><p class="hb-result-note">제품 구성 확인용 결과입니다. 배송비·할인·결제는 연결 전이며 주문은 저장되지 않습니다.</p><button id="hb-restart-config">옵션 다시 구성하기</button></section>`;
      document.getElementById('hb-restart-config').onclick = () => resetQuestion(list(product.process?.questions).find(q => !q.fixed)?.key);
      await reveal(target.querySelector('.hb-quote'));
    } catch (err) {
      target.innerHTML = `<section class="hb-quote"><h2>옵션 선택이 완료되었습니다.</h2><p class="hb-question-errors">${e(err.message)}</p><button id="hb-retry-price">가격계산 다시 시도</button><p class="hb-result-note">계산되지 않은 가격은 표시하지 않습니다.</p></section>`;
      document.getElementById('hb-retry-price').onclick = () => run(calculate, '가격을 다시 계산하고 있습니다.', 'question');
      await reveal(target.querySelector('.hb-quote'));
    }
  }
  main.addEventListener('load', ev => ev.target.closest?.('.hb-card-image')?.classList.remove('is-loading'), true);
  // Missing registered images fall back visibly, never silently presented as real product photography.
  main.addEventListener('error', ev => {
    const img = ev.target; if (!img.matches?.('img[data-fallback]')) return;
    const failedUrl = img.currentSrc || img.src, fallback = img.dataset.fallback; delete img.dataset.fallback;
    img.src = fallback; img.alt += ' · 등록 이미지 로드 실패, 예시 이미지로 대체';
    const card = img.closest('.hb-card');
    if (card) {
      const foot = card.querySelector('.hb-card-foot>span'), repeated = foot.querySelector('.hb-duplicate-label')?.cloneNode(true);
      foot.textContent = '등록 이미지 로드 실패 · 예시 이미지';
      if (repeated) foot.append(document.createElement('br'), repeated);
    }
    if (img.closest('.hb-gallery')) {
      album.forEach(a => { if (safeUrl(a.url) === safeUrl(failedUrl)) a.sample = true; });
      main.querySelector('.hb-gallery-caption').textContent = '등록 이미지를 불러오지 못해 예시 이미지를 표시합니다.';
    }
  }, true);
  async function route() {
    const params = new URLSearchParams(location.search); kind = params.get('kind') === 'custom';
    category = params.get('category') || ''; subcategory = params.get('subcategory') || ''; series = params.get('series') || '';
    const match = location.pathname.match(/\/products\/(\d+)\/?$/);
    if (match) await detail(Number(match[1]), true); else await catalog(true, true);
  }
  window.addEventListener('scroll', () => {
    if (product) return;
    const freshScroll = catalogScrollVersion <= consumedScrollVersion;
    catalogScrollVersion++;
    if (freshScroll && !seriesLoading && !running) armSeriesObserver();
  }, { passive: true });
  window.addEventListener('resize', () => main.querySelectorAll('.hb-rail, .hb-series-nav').forEach(rail => rail.dispatchEvent(new Event('hb-resize'))));
  window.addEventListener('popstate', () => { if (running || uploadCount) pendingRoute = true; else run(route); });
  document.querySelectorAll('[data-store-home]').forEach(link => { link.onclick = ev => { if (ev.ctrlKey || ev.metaKey || ev.shiftKey || ev.altKey) return; ev.preventDefault(); run(async () => { category = subcategory = series = ''; await catalog(); window.scrollTo({ top: 0, behavior: 'smooth' }); }); }; });
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
