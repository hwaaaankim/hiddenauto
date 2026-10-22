(function (S) {
  "use strict";
  const e = S.e, M = S.priceTable = {};
  // Fixed six-decimal integers avoid floating-point drift at exact thresholds.
  M.scaled = (value) => {
    const m = String(value ?? "").trim().match(/^\+?(\d+)(?:\.(\d{1,6}))?$/);
    if (!m || m[1].replace(/^0+/, "").length + (m[2]?.length || 0) > 24) throw Error("기준값은 0 이상, 유효 숫자 24자리·소수 6자리 이내로 입력해 주세요.");
    return BigInt(m[1]) * 1000000n + BigInt((m[2] || "").padEnd(6, "0"));
  };
  M.operator = (axis, index) => axis.operators?.[index] || axis.operator;
  M.validateHeaders = (headers) => {
    if (!headers.length || headers.length > 200) throw Error("축의 기준값은 1~200개까지 입력해 주세요.");
    const numbers = headers.map((v, i) => { try { return M.scaled(v); } catch (error) { throw Error(`${i + 1}번째 기준값: ${error.message}`); } });
    numbers.forEach((v, i) => { if (i && v <= numbers[i - 1]) throw Error(`${i}번째 ${headers[i - 1]}, ${i + 1}번째 ${headers[i]} 기준이 ${v === numbers[i - 1] ? "중복됩니다" : "작은 순서가 아닙니다"}. 연결된 금액도 함께 확인해 주세요.`); });
    return headers;
  };
  M.parseHeaders = (value) => M.validateHeaders(String(value).trim().split(/[,;\s]+/).filter(Boolean));
  M.lower = axis => ["GE", "GT"].includes(axis.operator);
  M.validateAxis = axis => {
    M.validateHeaders(axis.headers);
    if (!["LE", "LT", "GE", "GT"].includes(axis.operator)) throw Error("가격을 적용할 기준 방향을 선택해 주세요.");
    if (axis.operators?.length && axis.operators.length !== axis.headers.length) throw Error("기준값과 개별 경계 설정 개수가 다릅니다.");
    axis.headers.forEach((_, i) => {
      const op = M.operator(axis, i);
      if (!["LE", "LT", "GE", "GT"].includes(op) || M.lower(axis) !== ["GE", "GT"].includes(op)) throw Error("한 축에 큰 기준과 작은 기준을 섞을 수 없습니다.");
    });
    return axis;
  };
  M.index = (axis, value) => {
    M.validateAxis(axis); const n = M.scaled(value);
    if (M.lower(axis)) {
      for (let i = axis.headers.length - 1; i >= 0; i--) if (n > M.scaled(axis.headers[i]) || n === M.scaled(axis.headers[i]) && M.operator(axis, i) === "GE") return i;
      return -1;
    }
    return axis.headers.findIndex((v, i) => n < M.scaled(v) || n === M.scaled(v) && M.operator(axis, i) === "LE");
  };
  M.range = (axis, i) => {
    const bound = axis.headers[i];
    if (bound == null || bound === "") return "기준값을 입력하세요";
    if (M.lower(axis)) return `${bound} ${M.operator(axis, i) === "GE" ? "이상" : "초과"} ~ ${axis.headers[i + 1] != null ? axis.headers[i + 1] + (M.operator(axis, i + 1) === "GE" ? " 미만" : " 이하") : "상한 없음 (프로세스 허용 범위 안)"}`;
    const upper = `${bound} ${M.operator(axis, i) === "LE" ? "이하" : "미만"}`;
    return i && axis.headers[i - 1] != null
      ? `${axis.headers[i - 1]} ${M.operator(axis, i - 1) === "LE" ? "초과" : "이상"} ~ ${upper}` : `0 이상 ~ ${upper}`;
  };
  M.sequence = (start, step, count) => {
    if (!/^\d+$/.test(String(count)) || Number(count) < 1 || Number(count) > 200) throw Error("생성 개수는 1~200개로 입력해 주세요.");
    const first = M.scaled(start), delta = M.scaled(step);
    if (delta <= 0n) throw Error("증가 간격은 0보다 커야 합니다.");
    const format = n => { const value = `${n / 1000000n}.${String(n % 1000000n).padStart(6, "0")}`; return value.replace(/0+$/, "").replace(/\.$/, ""); };
    return M.validateHeaders(Array.from({ length: Number(count) }, (_, i) => format(first + delta * BigInt(i))));
  };
  M.assignHeaders = (term, xs, ys) => {
    M.validateHeaders(xs); if (term.yAxis) M.validateHeaders(ys);
    if (xs.length !== term.xAxis.headers.length || term.yAxis && ys.length !== term.yAxis.headers.length) throw Error("금액 위치를 유지하려면 X·Y 기준 개수가 현재 열·행 수와 같아야 합니다. 표 크기까지 바꾸려면 ‘같은 기준값의 금액 유지’를 선택하세요.");
    term.xAxis.headers = xs; if (term.yAxis) term.yAxis.headers = ys;
  };
  M.resize = (term, xs, ys, initial = null) => {
    M.validateHeaders(xs); if (term.yAxis) M.validateHeaders(ys);
    if (xs.length * (term.yAxis ? ys.length : 1) > 20000) throw Error("가격표는 최대 20,000개 금액까지 가능합니다.");
    const identity = (v) => { try { return M.scaled(v).toString(); } catch { return null; } };
    const oldX = new Map(term.xAxis.headers.map((v, i) => [identity(v), i]));
    const oldY = new Map((term.yAxis?.headers || ["0"]).map((v, i) => [identity(v), i]));
    const values = (term.yAxis ? ys : ["0"]).map((y) => xs.map((x) => {
      const r = oldY.get(identity(y)), c = oldX.get(identity(x));
      return r != null && c != null ? term.values[r]?.[c] ?? initial : initial;
    }));
    const ops = (axis, next, before) => next.map((v) => axis.operators?.[before.get(identity(v))] || null);
    term.xAxis.operators = ops(term.xAxis, xs, oldX);
    if (term.yAxis) { term.yAxis.operators = ops(term.yAxis, ys, oldY); term.yAxis.headers = ys; }
    term.xAxis.headers = xs; term.values = values;
  };
  M.money = (value, scale = 2) => {
    const raw = String(value ?? "").trim();
    if (raw.includes(",") && !/^\d{1,3}(?:,\d{3})+(?:\.\d+)?$/.test(raw)) throw Error("금액의 쉼표는 천 단위로 입력해 주세요. 예: 10,000");
    let text = raw.replaceAll(",", "");
    if (text.includes(".")) text = text.replace(/0+$/, "").replace(/\.$/, "");
    if (!/^\d+(?:\.\d+)?$/.test(text) || (text.split(".")[1]?.length || 0) > scale || M.scaled(text) > 9999999999999990000n) throw Error(`금액은 0~9,999,999,999,999.99원, 소수 ${scale}자리까지 입력하세요. 빈칸은 0원과 다릅니다.`);
    return text;
  };
  M.amount = (value) => M.money(value, 2);
  M.parsePaste = (text, two, includeHeaders) => {
    const lines = String(text).replace(/\r/g, "").replace(/\n+$/, "").split("\n");
    if (!text.trim()) throw Error("엑셀에서 복사한 셀을 붙여넣어 주세요.");
    const cells = lines.map((row) => row.split("\t").map((v) => v.trim()));
    if (cells.some((row) => row.length !== cells[0].length)) throw Error("붙여넣은 표의 행마다 열 수가 다릅니다.");
    const issues = [], formulas = [];
    cells.forEach((row, r) => row.forEach((value, c) => { if (value.startsWith("=")) formulas.push(`${r + 1}행 ${c + 1}열`); }));
    if (!includeHeaders && cells.length > 1 && cells[0].length > 1 && !/^\d/.test(cells[0][0])) {
      try { M.validateHeaders(cells[0].slice(1)); if (two) M.validateHeaders(cells.slice(1).map(row => row[0])); issues.push("첫 행·첫 열의 300·400 등 사이즈 기준값을 지워 주세요. 실제 금액 영역만 복사하거나 ‘기준 포함’으로 바꿔 붙여넣으세요."); } catch { /* A blank price alone is not evidence of a header row. */ }
    }
    if (formulas.length) issues.push(`수식 ${formulas.length}개: ${formulas.slice(0, 8).join(", ")}. 엑셀에서 선택하여 붙여넣기 → 값으로 변환한 뒤 금액만 다시 복사하세요.`);
    if (issues.length) throw Error(issues.join("\n"));
    let xHeaders = null, yHeaders = null, amounts = cells;
    if (includeHeaders) {
      if (cells.length < 2 || cells[0].length < 2) throw Error("기준 포함 표는 첫 행에 X 기준, 첫 열에 Y 기준(1차원은 금액 제목)이 필요합니다.");
      xHeaders = M.validateHeaders(cells[0].slice(1));
      if (two) yHeaders = M.validateHeaders(cells.slice(1).map((r) => r[0]));
      amounts = cells.slice(1).map((r) => r.slice(1));
    } else if (!two && cells[0].length === 1) amounts = [cells.map((r) => r[0])];
    if (!two && amounts.length !== 1) throw Error("1차원 가격표의 금액은 한 행 또는 한 열로 붙여넣어 주세요.");
    if (amounts.length > 200 || amounts[0].length > 200 || amounts.length * amounts[0].length > 20000) throw Error("축당 200개, 전체 20,000개 금액까지 가능합니다.");
    return { xHeaders, yHeaders, amounts: amounts.map((row, r) => row.map((value, c) => { try { return M.amount(value); } catch (error) { throw Error(`금액 ${r + 1}행 ${c + 1}열: ${error.message}`); } })) };
  };
  M.snapshot = (term) => JSON.stringify(term);
  M.history = (get, set) => {
    let entries = [M.snapshot(get())], at = 0, lastTag = null;
    return {
      record(tag = null) {
        const value = M.snapshot(get()); if (entries[at] === value) return;
        entries.splice(at + 1);
        if (tag && tag === lastTag && at > 0) entries[at] = value;
        else { entries.push(value); at++; }
        lastTag = tag;
        // Limit both count and memory for large matrices; keep a usable previous state.
        while (entries.length > 2 && (entries.length > 31 || entries.reduce((n, x) => n + x.length, 0) > 6000000)) { entries.shift(); at--; }
      },
      move(direction) { const next = at + direction; if (next < 0 || next >= entries.length) return false; at = next; lastTag = null; set(JSON.parse(entries[at])); return true; },
      canUndo: () => at > 0, canRedo: () => at < entries.length - 1
    };
  };
  M.swapAxes = (term) => {
    if (!term.yAxis) throw Error("2차원 가격표에서만 축을 교환할 수 있습니다.");
    const values = term.xAxis.headers.map((_, c) => term.yAxis.headers.map((_, r) => term.values[r]?.[c] ?? null));
    [term.xAxis, term.yAxis] = [term.yAxis, term.xAxis]; term.values = values;
  };
  M.resizePlan = (term, xs, ys) => {
    const next = JSON.parse(JSON.stringify(term)); M.resize(next, xs, ys);
    const keepX = new Set(xs.map(v => M.scaled(v).toString())), keepY = new Set((term.yAxis ? ys : ["0"]).map(v => M.scaled(v).toString()));
    const kept = (v, set) => { try { return set.has(M.scaled(v).toString()); } catch { return false; } };
    let retained = 0, removed = 0;
    term.values.forEach((row, r) => row.forEach((v, c) => { if (v != null && v !== "") { if (kept(term.xAxis.headers[c], keepX) && kept(term.yAxis?.headers[r] ?? "0", keepY)) retained++; else removed++; } }));
    return { next, retained, removed, blank: next.values.flat().filter(v => v == null || v === "").length };
  };
  M.review = (title, html, action = "변경 반영") => new Promise(resolve => {
    const d = S.dialog(title, html, { wide: false, foot: `<button type="button" data-review-cancel>취소</button><button type="button" class="primary" data-review-apply>${e(action)}</button>`, onClose: () => resolve(false) });
    S.$('[data-review-cancel]', d).onclick = () => d.close();
    S.$('[data-review-apply]', d).onclick = () => { resolve(true); d.close(); };
  });
  M.guardClose = (dialog, dirty) => {
    let asking = false;
    const close = async () => { if (asking) return; asking = true; try { if (!dirty() || await S.confirm("아직 반영하지 않은 작성 내용이 있습니다. 변경을 버리고 닫으시겠습니까?")) dialog.close(); } finally { asking = false; } };
    S.$('[data-close]', dialog).onclick = close;
    dialog.addEventListener("cancel", event => { event.preventDefault(); close(); });
  };
  M.pending = (ui) => !!(ui.dirtyX || ui.dirtyY);
  M.resetDraft = (ui) => { ui.buildX = ui.buildY = null; ui.dirtyX = ui.dirtyY = false; };
  M.remember = (body, ui) => {
    ui.probes ??= {};
    S.$$('[data-probe-field]', body).forEach(input => { ui.probes[input.dataset.probeField] = input.value; });
    for (const [selector, key] of [['[data-probe-axis="x"]', 'probeX'], ['[data-probe-axis="y"]', 'probeY'], ['[data-paste-text]', 'paste'], ['[data-paste-mode]', 'pasteMode']]) {
      const input = S.$(selector, body); if (input) ui[key] = input.value;
    }
  };
  M.toolsHtml = (term, ui = {}) => {
    const missing = [term.xAxis, term.yAxis].filter(Boolean).some(a => a.headers.some(v => v == null || v === ""));
    return `<details class="pm-price-table-builder" open><summary><strong>기준값 입력·자동 생성</strong> · 업로드한 금액에 300, 400, 500 연결</summary><p class="pms-help">X는 열 수 ${term.xAxis.headers.length}개${term.yAxis ? `, Y는 행 수 ${term.yAxis.headers.length}개` : ''}입니다. 시작값·간격·개수를 입력하거나 아래에 기준값을 직접 작성하세요. 자동 생성 후 ‘기준값 반영 미리보기’를 눌러야 표에 반영됩니다.</p><div class="pms-form-grid two">${[['x', term.xAxis], ...(term.yAxis ? [['y', term.yAxis]] : [])].map(([key, axis]) => `<section class="pm-price-axis-builder"><strong>${key.toUpperCase()}축 · ${key === 'x' ? '왼쪽 → 오른쪽' : '위 → 아래'}</strong><div class="pm-price-sequence"><label><span>시작값</span><input type="number" min="0" step="any" data-seq-start="${key}" value="${e(ui['seqStart' + key] ?? '')}" placeholder="300"></label><label><span>증가 간격</span><input type="number" min="0" step="any" data-seq-step="${key}" value="${e(ui['seqStep' + key] ?? '100')}"></label><label><span>생성 개수</span><input type="number" min="1" max="200" step="1" data-seq-count="${key}" value="${e(ui['seqCount' + key] ?? axis.headers.length)}"></label></div><button type="button" data-generate-axis="${key}">이 간격으로 기준 생성</button><label><span>${key.toUpperCase()} 기준값 직접 입력</span><textarea data-build-${key} rows="2" placeholder="300, 400, 500">${e(ui[key === 'x' ? 'dirtyX' : 'dirtyY'] ? ui[key === 'x' ? 'buildX' : 'buildY'] : axis.headers.map(v => v ?? '').join(', '))}</textarea></label></section>`).join('')}</div><label><span>기준을 바꿀 때 금액 처리</span><select data-build-mode><option value="position" ${(ui.buildMode || (missing ? 'position' : 'identity')) === 'position' ? 'selected' : ''}>현재 칸의 금액 유지 · 기준 이름만 연결 (엑셀 업로드 후)</option><option value="identity" ${(ui.buildMode || (missing ? 'position' : 'identity')) === 'identity' ? 'selected' : ''}>같은 기준값의 금액 유지 · 표 크기 변경 가능</option></select></label><p data-build-status class="pms-help" role="status"></p><div class="pms-actions"><button type="button" data-build-table>기준값 반영 미리보기</button><button type="button" data-build-reset>현재 표의 기준으로 되돌리기</button><button type="button" data-fill-zero>빈 금액만 0원으로 채우기</button></div></details><details class="pm-price-paste" data-price-method-panel="paste"><summary>엑셀 셀 복사 → 붙여넣기</summary><p class="pms-help">기준 포함은 첫 행 X·첫 열 Y를 포함한 표 전체입니다. 금액만 붙여넣으면 행·열 수에 맞춰 표를 만들고 위에서 기준값을 입력합니다.</p><label><span>붙여넣는 데이터</span><select data-paste-mode><option value="amounts">금액만</option><option value="table">X·Y 기준값 포함 (좌측 상단 제목칸 포함)</option></select></label><label><span>복사한 셀</span><textarea data-paste-text rows="3"></textarea></label><button type="button" data-paste-apply>붙여넣은 표 반영</button></details>`;
  };
  M.previewHtml = (term, fields) => {
    const name = (axis) => fields.find((f) => f.key === axis.fieldKey)?.labels?.management || fields.find((f) => f.key === axis.fieldKey)?.labels?.customer || axis.fieldKey;
    return `<section class="pm-price-matrix-preview"><strong>4. 입력값으로 교차 금액 확인</strong><p class="pms-help">현재 항의 가격표만 확인합니다. 분기·추가 조건을 포함한 최종금액은 메인 화면의 실제 프로세스 테스트에서 확인하세요.</p><div class="pms-form-grid two">${[["x", term.xAxis], ...(term.yAxis ? [["y", term.yAxis]] : [])].map(([key, axis]) => `<label><span>${e(name(axis))} (${key.toUpperCase()}축)</span><input type="number" min="0" step="any" data-probe-axis="${key}" data-probe-field="${e(axis.fieldKey)}" placeholder="예: 500"></label>`).join("")}</div><div data-probe-result role="status">고객 입력값을 넣으면 적용 구간과 금액을 표시합니다.</div></section>`;
  };
  M.axisExample = axis => {
    const missing = !axis.headers?.length || axis.headers.every(v => v == null || v === '');
    const sample = missing ? { operator: axis.operator, headers: ['400', '500'], operators: [] } : axis;
    try {
      M.validateAxis(sample);
      const start = M.scaled(sample.headers[0]), end = M.scaled(sample.headers[1] ?? sample.headers[0]);
      const format = n => `${n / 1000000n}.${String(n % 1000000n).padStart(6, '0')}`.replace(/0+$/, '').replace(/\.$/, '');
      const inputs = [...new Set([start, (start + end) / 2n, end].map(format))];
      return `<small>${missing ? '작성 전 예시 · 기준 400·500' : '현재 표의 기준·개별 경계 설정으로 계산한 예시'}</small><ul>${inputs.map(value => {
        const at = M.index(sample, value);
        return `<li>입력 <b>${e(value)}</b> → ${at < 0 ? '적용 기준 없음 · 표 범위 밖' : `<b>${e(sample.headers[at])}</b> 기준 가격`}</li>`;
      }).join('')}</ul><small>입력값 자체를 변경하지 않고, 표시된 기준의 금액을 적용합니다. 아래 ‘입력값으로 교차 금액 확인’에서 원하는 값도 테스트하세요.</small>`;
    } catch (error) { return `<small>기준값을 완성하면 실제 적용 예시를 표시합니다: ${e(error.message)}</small>`; }
  };
  M.bind = ({ body, term, draw, error, ui = {} }) => {
    const report = async (fn) => { try { await fn(); } catch (ex) { error(ex.message); } };
    const requireApplied = () => { if (M.pending(ui)) throw Error("작성한 일괄 기준값을 먼저 미리보기로 반영하거나 ‘현재 표의 기준으로 되돌리기’를 눌러 주세요."); };
    const status = () => { S.$('[data-build-status]', body).textContent = M.pending(ui) ? "위 기준값은 아직 표에 반영되지 않았습니다. 미리보기에서 변경을 확인해 주세요. 표를 직접 수정한 경우 두 입력을 비교해 주세요." : "위 기준값과 아래 표가 일치합니다. 표에서 기준을 수정하면 위에도 즉시 반영됩니다."; };
    const refresh = () => {
      S.$$('[data-axis-example]', body).forEach(el => { el.innerHTML = M.axisExample(term[el.dataset.axisExample === 'x' ? 'xAxis' : 'yAxis']); });
      S.$$('[data-bound-range]', body).forEach((el) => { const [key, i] = el.dataset.boundRange.split(":"); el.textContent = M.range(term[key === "x" ? "xAxis" : "yAxis"], Number(i)); });
      S.$$('[data-matrix-price]', body).forEach((input) => input.closest("td").classList.remove("is-price-hit"));
      const result = S.$('[data-probe-result]', body), x = S.$('[data-probe-axis="x"]', body)?.value, y = S.$('[data-probe-axis="y"]', body)?.value;
      if (!result) return;
      result.classList.remove("is-error");
      if (!x || term.yAxis && !y) { result.textContent = "입력값을 채우면 적용 구간과 교차 금액을 표시합니다."; return; }
      try {
        const c = M.index(term.xAxis, x), r = term.yAxis ? M.index(term.yAxis, y) : 0;
        if (c < 0 || r < 0) throw Error(`${c < 0 ? "X" : "Y"}축의 적용 범위를 벗어났습니다. 큰 기준 방식은 최대값, 작은 기준 방식은 최소값을 확인해 주세요.`);
        const value = term.values[r]?.[c]; M.amount(value);
        const amount = String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
        result.textContent = `X: ${M.range(term.xAxis, c)}${term.yAxis ? " / Y: " + M.range(term.yAxis, r) : ""} → ${amount}원`;
        S.$(`[data-matrix-price="${r}:${c}"]`, body)?.closest("td").classList.add("is-price-hit");
      } catch (ex) { result.textContent = ex.message; result.classList.add("is-error"); }
    };
    for (const [selector, key] of [['[data-probe-axis="x"]', 'probeX'], ['[data-probe-axis="y"]', 'probeY'], ['[data-paste-text]', 'paste'], ['[data-paste-mode]', 'pasteMode']]) {
      const input = S.$(selector, body); if (input && ui[key] != null) input.value = ui[key];
    }
    S.$$('[data-probe-field]', body).forEach(input => { input.value = ui.probes?.[input.dataset.probeField] ?? ""; });
    for (const key of ['x', 'y']) S.$(`[data-build-${key}]`, body)?.addEventListener('input', event => { ui[key === 'x' ? 'buildX' : 'buildY'] = event.target.value; ui[key === 'x' ? 'dirtyX' : 'dirtyY'] = true; status(); });
    S.$('[data-build-mode]', body)?.addEventListener('change', event => { ui.buildMode = event.target.value; });
    for (const key of ['x', 'y']) for (const part of ['start', 'step', 'count']) S.$(`[data-seq-${part}="${key}"]`, body)?.addEventListener('input', event => { ui['seq' + part[0].toUpperCase() + part.slice(1) + key] = event.target.value; });
    S.$$('[data-generate-axis]', body).forEach(button => button.onclick = () => report(() => {
      const key = button.dataset.generateAxis;
      const values = M.sequence(S.$(`[data-seq-start="${key}"]`, body).value, S.$(`[data-seq-step="${key}"]`, body).value, S.$(`[data-seq-count="${key}"]`, body).value);
      ui[key === 'x' ? 'buildX' : 'buildY'] = values.join(', '); ui[key === 'x' ? 'dirtyX' : 'dirtyY'] = true;
      S.$(`[data-build-${key}]`, body).value = values.join(', '); status();
    }));
    S.$('[data-build-reset]', body)?.addEventListener('click', () => { M.resetDraft(ui); draw(); });
    S.$('[data-build-table]', body)?.addEventListener("click", () => report(async () => {
      const x = M.parseHeaders(S.$('[data-build-x]', body).value), y = term.yAxis ? M.parseHeaders(S.$('[data-build-y]', body).value) : [];
      const position = S.$('[data-build-mode]', body).value === 'position';
      let plan;
      if (position) {
        const next = JSON.parse(JSON.stringify(term)); M.assignHeaders(next, x, y);
        plan = { next, retained: term.values.flat().filter(v => v != null && v !== '').length, removed: 0, blank: term.values.flat().filter(v => v == null || v === '').length };
      } else plan = M.resizePlan(term, x, y);
      if (M.snapshot(plan.next) !== M.snapshot(term)) {
        const accepted = await M.review("가격표 변경 미리보기", `<p><strong>${term.values.length}행 × ${term.xAxis.headers.length}열 → ${plan.next.values.length}행 × ${x.length}열</strong></p><p>유지할 입력 금액 ${plan.retained}개 · 삭제할 입력 금액 ${plan.removed}개 · 변경 후 빈 금액 ${plan.blank}개</p><p class="pms-help">${position ? "현재 행·열의 금액을 그대로 두고 기준값만 연결합니다." : "같은 기준값의 금액을 유지하고 삭제된 기준의 금액은 제외합니다."} 반영 후 실행 취소로 되돌릴 수 있습니다.</p>`);
        if (!accepted) return;
      }
      Object.assign(term, plan.next); M.resetDraft(ui); draw();
    }));
    S.$('[data-fill-zero]', body)?.addEventListener("click", () => report(async () => {
      requireApplied(); const count = term.values.flat().filter(v => v == null || v === "").length;
      if (!count) return;
      if (!await S.confirm(`빈 금액 ${count}개를 무료 구간인 0원으로 채우시겠습니까? 입력된 금액은 유지됩니다.`)) return;
      term.values = term.values.map(row => row.map(v => v == null || v === "" ? "0" : v)); draw();
    }));
    S.$('[data-paste-apply]', body)?.addEventListener("click", () => report(async () => {
      requireApplied();
      const data = M.parsePaste(S.$('[data-paste-text]', body).value, !!term.yAxis, S.$('[data-paste-mode]', body).value === "table");

      if (!await S.confirm(`${data.amounts.length}행 × ${data.amounts[0].length}열의 붙여넣은 표로 현재 ${data.xHeaders ? "기준값과 금액" : "금액"}을 교체하시겠습니까? 실행 취소로 복구할 수 있습니다.`)) return;
      if (data.xHeaders) M.resize(term, data.xHeaders, data.yHeaders || []);
      if (!data.xHeaders) {
        if (term.xAxis.headers.length !== data.amounts[0].length) { term.xAxis.headers = data.amounts[0].map(() => null); term.xAxis.operators = []; }
        if (term.yAxis && term.yAxis.headers.length !== data.amounts.length) { term.yAxis.headers = data.amounts.map(() => null); term.yAxis.operators = []; }
        ui.buildMode = 'position';
      }
      term.values = data.amounts; M.resetDraft(ui); draw();
    }));
    S.$$('[data-bound-operator]', body).forEach((input) => input.addEventListener("change", () => {
      const [key, i] = input.dataset.boundOperator.split(":"); const axis = term[key === "x" ? "xAxis" : "yAxis"];
      if (!axis.operators?.length) axis.operators = axis.headers.map(() => null);
      axis.operators[Number(i)] = input.value || null; refresh();
    }));
    S.$$('[data-matrix-header]', body).forEach(input => input.addEventListener('input', () => {
      const x = input.dataset.matrixHeader === 'x', axis = x ? term.xAxis : term.yAxis;
      if (!ui[x ? 'dirtyX' : 'dirtyY']) S.$(x ? '[data-build-x]' : '[data-build-y]', body).value = axis.headers.map(v => v ?? '').join(', ');
      status();
    }));
    S.$$('[data-matrix-header], [data-matrix-price], [data-probe-axis]', body).forEach((input) => input.addEventListener("input", refresh));
    const showMethod = mode => {
      ui.method = mode;
      S.$$('[data-price-method-panel]', body).forEach(panel => { panel.hidden = panel.dataset.priceMethodPanel !== mode; if (!panel.hidden && panel.tagName === 'DETAILS') panel.open = true; });
      S.$$('[data-price-method]', body).forEach(button => { const selected = button.dataset.priceMethod === mode; button.setAttribute('aria-pressed', String(selected)); button.classList.toggle('primary', selected); });
    };
    S.$$('[data-price-method]', body).forEach(button => { button.onclick = () => showMethod(button.dataset.priceMethod); });
    showMethod(ui.method || 'direct');
    status(); refresh();
  };
})(window.PMS);
