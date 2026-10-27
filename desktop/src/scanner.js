// Marketplace scraping for the desktop app. The heavy lifting runs inside the
// embedded <webview> via executeJavaScript; this module holds the injected
// script, the URL builder, and the pure parsing of extracted cards (so it can
// be unit-tested in Node).
(function (root) {
  var DESKTOP_UA =
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
    '(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36';

  function enc(s) { return encodeURIComponent(s); }

  // Facebook location in the URL path is a slug (lowercase, no spaces/punct),
  // e.g. "miami", "losangeles". A display name like "Orlando, FL" won't resolve.
  function slugifyCity(city) {
    return String(city || '').trim().toLowerCase().replace(/[^a-z0-9]+/g, '');
  }

  function buildUrl(p) {
    var base = 'https://www.facebook.com/marketplace/';
    var slug = slugifyCity(p.city);
    if (slug) base += slug + '/';
    base += 'search/?query=' + enc(p.query || '');
    if (p.minPrice != null && p.minPrice !== '') base += '&minPrice=' + p.minPrice;
    if (p.maxPrice != null && p.maxPrice !== '') base += '&maxPrice=' + p.maxPrice;
    if (p.radiusKm) base += '&radius_km=' + p.radiusKm;
    base += '&sortBy=' + (p.sort === 'NEAREST' ? 'distance_ascend' : 'creation_time_descend');
    base += '&exact=false';
    return base;
  }

  // Runs in the page: collect every /marketplace/item/ card with its text lines.
  var EXTRACT_JS =
    '(function(){' +
    'var seen={},items=[];' +
    'var A=document.querySelectorAll(\'a[href*="/marketplace/item/"]\');' +
    'for(var i=0;i<A.length;i++){var a=A[i];' +
    'var m=(a.getAttribute("href")||"").match(/\\/marketplace\\/item\\/(\\d+)/);' +
    'if(!m||seen[m[1]])continue;seen[m[1]]=true;' +
    'var texts=[];var spans=a.querySelectorAll(\'span,div[dir="auto"]\');' +
    'for(var j=0;j<spans.length;j++){var el=spans[j];' +
    'if(el.children.length&&el.querySelector(\'span,div[dir="auto"]\'))continue;' +
    'var t=(el.innerText||el.textContent||"").trim();' +
    'if(t&&texts.indexOf(t)<0)texts.push(t);}' +
    'var img=a.querySelector("img");' +
    'items.push({id:m[1],texts:texts,img:img?(img.currentSrc||img.src||""):"",alt:img?(img.alt||""):""});}' +
    'return items;})()';

  var SCROLL_JS =
    '(function(){window.scrollTo(0,document.body.scrollHeight);' +
    'var d=document.scrollingElement||document.documentElement;d.scrollTop=d.scrollHeight;return 1;})()';

  var LOGIN_JS =
    '(function(){return !!(document.querySelector(\'form[action*="login"]\')||' +
    'document.querySelector(\'input[name="email"]\')||' +
    'location.href.indexOf("login")>-1||location.href.indexOf("checkpoint")>-1);})()';

  // Reports where the page actually is and how many cards it shows — used to
  // confirm navigation landed (defeats the SPA "stale feed" problem).
  var PROBE_JS =
    '(function(){return {href:location.href,' +
    'count:document.querySelectorAll(\'a[href*="/marketplace/item/"]\').length,' +
    'login:!!(document.querySelector(\'form[action*="login"]\')||' +
    'document.querySelector(\'input[name="email"]\')||' +
    'location.href.indexOf("login")>-1||location.href.indexOf("checkpoint")>-1)};})()';

  var PRICE = /^(?:[A-Z]{0,3}\$\s?[\d,]+(?:\.\d+)?|Free|FREE)$/;
  var LOCATION = /^[A-Za-z][A-Za-z .'\-]{1,40}, [A-Z]{2}$/;
  var MILEAGE = /^[\d,.]+K? (?:miles|km)$/i;

  // Turn raw extracted cards into listing objects. Pure — unit-tested in Node.
  function normalize(rawItems) {
    var out = [], seen = {};
    (rawItems || []).forEach(function (it) {
      var id = it && it.id;
      if (!id || seen[id]) return;
      var texts = (it.texts || []).map(function (s) { return (s || '').trim(); }).filter(Boolean);
      var priceText = texts.find(function (t) { return PRICE.test(t); }) || '';
      var location = texts.find(function (t) { return LOCATION.test(t) && !PRICE.test(t); }) || '';
      var title = texts.find(function (t) {
        return t !== priceText && t !== location && !PRICE.test(t) && !MILEAGE.test(t) && t.length > 1;
      });
      if (!title && it.alt && it.alt.indexOf('May be') !== 0) title = it.alt;
      if (!title) return;
      var price = null;
      if (/^free$/i.test(priceText)) price = 0;
      else { var digits = priceText.replace(/[^\d]/g, ''); if (digits) price = parseInt(digits, 10); }
      seen[id] = true;
      out.push({
        id: id,
        title: title,
        price: price,
        priceText: priceText || (price != null ? '$' + price : ''),
        location: location,
        img: (it.img && it.img.indexOf('http') === 0) ? it.img : '',
        url: 'https://www.facebook.com/marketplace/item/' + id + '/'
      });
    });
    return out;
  }

  // Facebook pads results with loosely-related items. Keep only titles that
  // actually match: drop any excluded word, and (when strict) require every
  // search word to appear in the title. Pure — unit-tested in Node.
  function filterRelevant(listings, opts) {
    opts = opts || {};
    var tokens = String(opts.query || '').toLowerCase().split(/\s+/).filter(function (w) { return w.length >= 2; });
    var excludes = String(opts.exclude || '').toLowerCase().split(',')
      .map(function (s) { return s.trim(); }).filter(Boolean);
    return (listings || []).filter(function (l) {
      var t = String((l && l.title) || '').toLowerCase();
      if (excludes.some(function (w) { return t.indexOf(w) >= 0; })) return false;
      if (opts.strict && tokens.length) return tokens.every(function (w) { return t.indexOf(w) >= 0; });
      return true;
    });
  }

  var api = {
    DESKTOP_UA: DESKTOP_UA,
    buildUrl: buildUrl,
    slugifyCity: slugifyCity,
    EXTRACT_JS: EXTRACT_JS,
    SCROLL_JS: SCROLL_JS,
    LOGIN_JS: LOGIN_JS,
    PROBE_JS: PROBE_JS,
    normalize: normalize,
    filterRelevant: filterRelevant
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.Scanner = api;
})(typeof window !== 'undefined' ? window : globalThis);
