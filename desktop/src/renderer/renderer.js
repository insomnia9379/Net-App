/* global DealScorer, Scanner */
'use strict';

const $ = (id) => document.getElementById(id);
const fb = $('fb');

let results = [];      // current scan results (scored)
let saved = [];        // persisted saved items
let filter = 'all';
let scanning = false;

// ---------- persistence ----------
async function loadState() {
  saved = (await window.api.get('savedItems', [])) || [];
  const last = (await window.api.get('lastParams', null));
  if (last) {
    $('query').value = last.query || '';
    $('city').value = last.city || '';
    $('minPrice').value = last.minPrice || '';
    $('maxPrice').value = last.maxPrice || '';
    $('exclude').value = last.exclude || '';
    if (typeof last.strict === 'boolean') $('strict').checked = last.strict;
  }
  renderSavedCount();
}
function persistSaved() { window.api.set('savedItems', saved); renderSavedCount(); }

// ---------- scanning ----------
function params() {
  return {
    query: $('query').value.trim(),
    city: $('city').value.trim(),
    minPrice: $('minPrice').value.trim(),
    maxPrice: $('maxPrice').value.trim(),
    exclude: $('exclude').value.trim(),
    strict: $('strict').checked,
    radiusKm: 65,
    sort: 'NEWEST',
  };
}

// Facebook pads results with loosely-related junk; Scanner.filterRelevant keeps
// only titles that match the query (and honors the exclude words).

const delay = (ms) => new Promise((r) => setTimeout(r, ms));

// Force the webview to the target URL and wait until the live DOM reflects it.
// Facebook is a single-page app, so `did-stop-loading` is unreliable and a
// plain loadURL can be swallowed as a soft navigation — leaving the old feed on
// screen. We drive the page's own location as a fallback and poll the DOM
// (listing count) until it stabilizes, which is what actually confirms the new
// search/location loaded.
async function navigateAndWait(url) {
  const firstToken = (($('query').value || '').trim().toLowerCase().split(/\s+/)[0]) || '';
  try { if (typeof fb.stop === 'function') fb.stop(); } catch (_) {}
  try { if (typeof fb.loadURL === 'function') fb.loadURL(url); } catch (_) {}

  let probe = null;
  let last = -1;
  let stable = 0;
  let forced = false;
  const start = Date.now();
  while (Date.now() - start < 30000) {
    await delay(1000);
    probe = await fb.executeJavaScript(Scanner.PROBE_JS, true).catch(() => null);
    if (!probe) continue;
    if (probe.login) return probe;

    const href = (probe.href || '').toLowerCase();
    const onSearch = href.indexOf('/marketplace/') > -1 &&
      (href.indexOf('/search') > -1 || href.indexOf('query=') > -1);
    const hasToken = !firstToken || decodeURIComponent(href).indexOf(firstToken) > -1;

    // If loadURL got swallowed and we're not on the search URL after ~5s,
    // hard-navigate from inside the page (SPA can't intercept location.assign).
    if (!forced && !onSearch && Date.now() - start > 5000) {
      forced = true;
      await fb.executeJavaScript('window.location.assign(' + JSON.stringify(url) + ')', true).catch(() => {});
      last = -1; stable = 0;
      continue;
    }

    if (onSearch && hasToken) {
      if (probe.count === last) stable++; else stable = 0;
      last = probe.count;
      if (probe.count > 0 && stable >= 2) break;
    }
  }
  return probe;
}

async function runScan() {
  if (scanning) return;
  scanning = true;
  $('scanBtn').disabled = true;
  const p = params();
  window.api.set('lastParams', p);
  const url = Scanner.buildUrl(p);
  try {
    setStatus('Loading Marketplace…');
    const probe = await navigateAndWait(url);

    if (probe && probe.login) {
      setStatus('🔑 Log into Facebook on the right, then press Scan again.');
      return;
    }
    if (!probe) {
      setStatus('Could not load the page — check the Facebook panel on the right.');
      return;
    }

    for (let i = 0; i < 6; i++) {
      setStatus(`Scrolling for more… (${i + 1}/6)`);
      await fb.executeJavaScript(Scanner.SCROLL_JS, true).catch(() => {});
      await delay(2200);
    }

    setStatus('Reading listings…');
    const raw = await fb.executeJavaScript(Scanner.EXTRACT_JS, true).catch(() => []);
    const all = Scanner.normalize(raw);
    const listings = Scanner.filterRelevant(all, p);
    const hidden = all.length - listings.length;

    // Market-value scoring from the comparables in this scan.
    const prices = listings.map((l) => l.price).filter((n) => typeof n === 'number' && n > 0);
    const mv = DealScorer.median(prices);
    const savedIds = new Set(saved.map((s) => s.id));
    results = listings.map((l) => ({
      ...l,
      score: DealScorer.score(l.price, mv, null, prices.length),
      saved: savedIds.has(l.id),
    }));

    const deals = results.filter((r) => DealScorer.isDeal(r.score)).length;
    const hiddenNote = hidden > 0 ? ` · ${hidden} off-topic hidden` : '';
    setStatus(`Found ${results.length} · ${deals} deals · market ~$${mv || '?'}${hiddenNote}`);
    render();
  } catch (e) {
    setStatus('Error: ' + (e && e.message ? e.message : e));
  } finally {
    scanning = false;
    $('scanBtn').disabled = false;
  }
}

// ---------- rendering ----------
function setStatus(t) { $('status').textContent = t; }

function renderSavedCount() {
  $('savedCount').textContent = String(saved.length);
}

function activeList() {
  if (currentTab === 'saved') return saved;
  return filter === 'deals' ? results.filter((r) => DealScorer.isDeal(r.score)) : results;
}

function render() {
  $('allCount').textContent = String(results.length);
  $('dealCount').textContent = String(results.filter((r) => DealScorer.isDeal(r.score)).length);
  renderSavedCount();

  const list = $('list');
  const items = activeList();
  if (!items.length) {
    list.innerHTML = `<div class="empty">${currentTab === 'saved'
      ? 'No saved items yet. Tap ☆ on any listing.'
      : 'No listings yet. Press Scan.'}</div>`;
    return;
  }
  list.innerHTML = '';
  items.forEach((it) => list.appendChild(card(it)));
}

function card(it) {
  const el = document.createElement('div');
  el.className = 'card';
  el.onclick = (e) => { if (!e.target.classList.contains('star')) window.api.openExternal(it.url); };

  const thumb = it.img
    ? `<img class="thumb" src="${it.img}" />`
    : `<div class="thumb">🏷️</div>`;
  const badge = (it.score && it.score !== 'UNKNOWN')
    ? `<span class="badge ${it.score}">${DealScorer.badge(it.score)}</span>` : '';
  const isSaved = saved.some((s) => s.id === it.id);

  el.innerHTML = `
    ${thumb}
    <div class="body">
      <p class="title">${escapeHtml(it.title)}</p>
      <div><span class="price">${escapeHtml(it.priceText || '—')}</span>${badge}</div>
      <div class="meta">${escapeHtml(it.location || '')}</div>
    </div>
    <button class="star ${isSaved ? 'on' : ''}" title="Save">${isSaved ? '★' : '☆'}</button>
  `;
  el.querySelector('.star').onclick = (e) => { e.stopPropagation(); toggleSave(it); };
  return el;
}

function toggleSave(it) {
  const i = saved.findIndex((s) => s.id === it.id);
  if (i >= 0) saved.splice(i, 1);
  else saved.push({ id: it.id, title: it.title, priceText: it.priceText, price: it.price,
    location: it.location, img: it.img, url: it.url, score: it.score, source: 'APP' });
  persistSaved();
  render();
}

function escapeHtml(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// ---------- tabs / filters ----------
let currentTab = 'scan';
function setTab(tab) {
  currentTab = tab;
  $('tabScan').classList.toggle('active', tab === 'scan');
  $('tabSaved').classList.toggle('active', tab === 'saved');
  render();
}

// ---------- wiring ----------
$('scanBtn').onclick = runScan;
$('tabScan').onclick = () => setTab('scan');
$('tabSaved').onclick = () => setTab('saved');
$('webHome').onclick = () => fb.loadURL('https://www.facebook.com/marketplace/');
document.querySelectorAll('.chip').forEach((c) => {
  c.onclick = () => {
    filter = c.dataset.filter;
    document.querySelectorAll('.chip').forEach((x) => x.classList.toggle('active', x === c));
    render();
  };
});
fb.addEventListener('did-navigate', (e) => { $('webUrl').textContent = e.url || ''; });
$('query').addEventListener('keydown', (e) => { if (e.key === 'Enter') runScan(); });

loadState().then(render);
