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
    radiusKm: 65,
    sort: 'NEWEST',
  };
}

const delay = (ms) => new Promise((r) => setTimeout(r, ms));

function waitForStopLoading(timeoutMs) {
  return new Promise((resolve) => {
    let done = false;
    const finish = () => { if (!done) { done = true; fb.removeEventListener('did-stop-loading', finish); resolve(); } };
    fb.addEventListener('did-stop-loading', finish);
    setTimeout(finish, timeoutMs);
  });
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
    fb.loadURL(url);
    await waitForStopLoading(30000);
    await delay(4000);

    const needLogin = await fb.executeJavaScript(Scanner.LOGIN_JS, true).catch(() => false);
    if (needLogin) {
      setStatus('🔑 Log into Facebook on the right, then press Scan again.');
      return;
    }

    for (let i = 0; i < 6; i++) {
      setStatus(`Scrolling for more… (${i + 1}/6)`);
      await fb.executeJavaScript(Scanner.SCROLL_JS, true).catch(() => {});
      await delay(2200);
    }

    setStatus('Reading listings…');
    const raw = await fb.executeJavaScript(Scanner.EXTRACT_JS, true).catch(() => []);
    const listings = Scanner.normalize(raw);

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
    setStatus(`Found ${results.length} listings · ${deals} deals · market value ~$${mv || '?'}`);
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
