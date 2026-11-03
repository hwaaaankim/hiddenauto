(() => {
  'use strict';
  const body = document.getElementById('as-keyword-results');
  const form = document.querySelector('input[name="keyword"]');
  const term = (form?.value || '').trim();
  if (!body || !term) return;
  const type = document.querySelector('[name="keywordType"]')?.value || 'all';
  // Display text is handled as text nodes; never interpolate the search into HTML.
  const tokens = term.toLocaleLowerCase();
  const telephone = ['all', 'applicantPhone', 'onsiteContact'].includes(type);
  const walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT);
  const nodes = []; while (walker.nextNode()) nodes.push(walker.currentNode);
  nodes.forEach(node => {
    if (node.parentElement.closest('button,script,style,mark')) return;
    const text = node.data, lower = text.toLocaleLowerCase();
    let spans = [], from = 0, at;
    while ((at = lower.indexOf(tokens, from)) >= 0) { spans.push([at, at + term.length]); from = at + term.length; }
    if (!spans.length && telephone && /^[\d\s-]+$/.test(term) && /\d/.test(term)) {
      const positions = [], digits = [...text].map((c, i) => { if (/\d/.test(c)) { positions.push(i); return c; } return ''; }).join('');
      const needle = term.replace(/\D/g, '');
      for (let i = digits.indexOf(needle); i >= 0; i = digits.indexOf(needle, i + needle.length)) spans.push([positions[i], positions[i + needle.length - 1] + 1]);
    }
    if (!spans.length) return;
    const fragment = document.createDocumentFragment(); let end = 0;
    spans.forEach(([a,b]) => { fragment.append(text.slice(end,a)); const mark = document.createElement('mark'); mark.style.cssText='font-weight:800;text-decoration:underline;text-underline-offset:3px;background:transparent;color:inherit;padding:0'; mark.textContent=text.slice(a,b); fragment.append(mark); end=b; });
    fragment.append(text.slice(end)); node.replaceWith(fragment);
  });
})();
