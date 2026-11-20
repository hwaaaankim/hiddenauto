(() => {
  'use strict';
  const links = [...document.querySelectorAll('[data-store-count]')];
  if (!links.length) return;
  const user = document.body.dataset.storeUser || links.find(link => link.dataset.storeUser)?.dataset.storeUser;
  if (!user) return;
  const cartKey = 'hb-preview:' + user + ':cart';
  const savedKey = 'hb-preview-company:' + user.split(':')[0] + ':saved-revision';
  let request;
  function paint(kind, count) {
    for (const link of links.filter(link => link.dataset.storeCount === kind)) {
      let badge = link.querySelector('.store-count-badge');
      if (!badge) {
        badge = document.createElement('sup');
        badge.className = 'store-count-badge';
        badge.setAttribute('aria-hidden', 'true');
        link.dataset.storeCountLabel = link.getAttribute('aria-label') || link.textContent.trim();
        const label = link.matches('.store-count-menu') ? link.querySelector('span') : link;
        (label || link).append(badge);
      }
      if (badge.dataset.count === String(count)) continue;
      badge.dataset.count = String(count);
      badge.textContent = count > 99 ? '99+' : count;
      badge.classList.toggle('is-empty', count === 0);
      link.setAttribute('aria-label', link.dataset.storeCountLabel + ' ' + count + '개');
      badge.getAnimations?.().forEach(animation => animation.cancel());
      if (count && badge.animate && document.body.dataset.motion !== 'reduced' && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
        badge.animate([{transform:'scale(.8)', opacity:.6}, {transform:'scale(1)', opacity:1}], {duration:200, easing:'ease-out'});
      }
    }
  }
  function updateCart() {
    try {
      const rows = JSON.parse(localStorage.getItem(cartKey));
      if (rows === null || Array.isArray(rows)) paint('cart', rows?.length || 0);
    } catch (_) { /* Keep the previous count if storage is unavailable. */ }
  }
  async function updateSaved() {
    request?.abort();
    request = new AbortController();
    const signal = request.signal;
    try {
      const response = await fetch('/customer/store-preview/api/counts', {
        headers: {Accept: 'application/json'}, signal, cache: 'no-store', credentials: 'same-origin'
      });
      if (!response.ok || response.redirected) return;
      const result = await response.json();
      if (signal.aborted || !result.data) return;
      for (const kind of ['interest', 'bookmark']) {
        const count = result.data[kind];
        if (Number.isSafeInteger(count) && count >= 0) paint(kind, count);
      }
    } catch (_) { /* Preserve the last confirmed counts when refreshing fails. */ }
  }
  function refresh() {
    updateCart();
    void updateSaved();
  }
  window.addEventListener('store:cart-changed', updateCart);
  window.addEventListener('store:saved-changed', () => {
    try { localStorage.setItem(savedKey, crypto.randomUUID()); } catch (_) { /* Same-tab refresh still works. */ }
    void updateSaved();
  });
  window.addEventListener('storage', event => {
    let kind;
    if (event.key === null) { kind = 'all'; refresh(); }
    else if (event.key === cartKey) { kind = 'cart'; updateCart(); }
    else if (event.key === savedKey) { kind = 'saved'; void updateSaved(); }
    if (kind) window.dispatchEvent(new CustomEvent('store:external-list-change', {detail: {kind}}));
  });
  window.addEventListener('pageshow', event => { if (event.persisted) refresh(); });
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
  refresh();
})();
