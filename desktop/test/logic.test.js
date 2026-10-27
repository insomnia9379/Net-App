// Pure-logic tests — run with `node test/logic.test.js` (no Electron needed).
const DealScorer = require('../src/dealScorer.js');
const Scanner = require('../src/scanner.js');

let failed = 0;
function eq(a, b, msg) {
  const pass = JSON.stringify(a) === JSON.stringify(b);
  console.log((pass ? 'PASS' : 'FAIL') + ' — ' + msg + (pass ? '' : ` (got ${JSON.stringify(a)}, want ${JSON.stringify(b)})`));
  if (!pass) failed++;
}

// --- DealScorer ---
eq(DealScorer.median([100, 200, 300]), 200, 'median odd');
eq(DealScorer.median([100, 200, 300, 400]), 250, 'median even');
eq(DealScorer.median([]), null, 'median empty');
// market value 2000, 4 comparables: 1200 is 60% -> STEAL, 1900 -> FAIR, 2500 -> HIGH
eq(DealScorer.score(1200, 2000, null, 4), 'STEAL', 'steal under 70%');
eq(DealScorer.score(1900, 2000, null, 4), 'FAIR', 'fair under market');
eq(DealScorer.score(2500, 2000, null, 4), 'HIGH', 'high above market');
eq(DealScorer.score(5000, 2000, 6000, 4), 'STEAL', 'under explicit ceiling = steal');
eq(DealScorer.score(1500, 2000, null, 2), 'UNKNOWN', 'too few comparables');
eq(DealScorer.score(null, 2000, null, 9), 'UNKNOWN', 'no price');

// --- Scanner.buildUrl ---
const url = Scanner.buildUrl({ query: 'sur ron', city: 'miami', minPrice: '', maxPrice: '3000', radiusKm: 65 });
eq(/facebook\.com\/marketplace\/miami\/search\/\?query=sur%20ron/.test(url), true, 'buildUrl path+query');
eq(/maxPrice=3000/.test(url), true, 'buildUrl maxPrice');
eq(/minPrice=/.test(url), false, 'buildUrl omits empty minPrice');

// --- city slug handling (typed display names must become FB slugs) ---
eq(Scanner.slugifyCity('Orlando'), 'orlando', 'slug lowercases');
eq(Scanner.slugifyCity('Los Angeles'), 'losangeles', 'slug strips spaces');
eq(Scanner.slugifyCity('  '), '', 'slug blank');
eq(/\/marketplace\/losangeles\/search\//.test(Scanner.buildUrl({ query: 'bike', city: 'Los Angeles' })), true, 'buildUrl uses slug in path');
eq(/\/marketplace\/search\//.test(Scanner.buildUrl({ query: 'bike', city: '' })), true, 'buildUrl omits location when blank');

// --- Scanner.normalize (desktop DOM extraction output) ---
const raw = [
  { id: '111', texts: ['$1,200', 'Sur Ron Light Bee', 'Miami, FL', '1,000 miles'], img: 'https://x/a.jpg', alt: '' },
  { id: '111', texts: ['$1,200', 'dup'], img: '', alt: '' }, // duplicate id -> dropped
  { id: '222', texts: ['Free', 'Old bike frame', 'Tampa, FL'], img: 'data:x', alt: '' },
  { id: '333', texts: ['$900'], img: '', alt: 'Talaria XXX' }, // only a price -> title from alt
];
const norm = Scanner.normalize(raw);
eq(norm.length, 3, 'normalize dedupes by id');
eq(norm[0].price, 1200, 'normalize parses $1,200');
eq(norm[0].title, 'Sur Ron Light Bee', 'normalize picks title over price/loc/mileage');
eq(norm[0].location, 'Miami, FL', 'normalize location');
eq(norm[0].img, 'https://x/a.jpg', 'normalize keeps http img');
eq(norm[1].price, 0, 'normalize Free = 0');
eq(norm[1].img, '', 'normalize drops non-http img');
eq(norm[2].title, 'Talaria XXX', 'normalize falls back to alt when only a price line');

// --- Scanner.filterRelevant (the "electric buggy returned clothes" fix) ---
const mixed = [
  { id: '1', title: 'Electric Buggy 48V' },
  { id: '2', title: "Men's T-Shirt, size L" },
  { id: '3', title: 'Kids electric ride-on buggy' },
  { id: '4', title: 'Electric scooter' },        // has "electric" but not "buggy"
  { id: '5', title: 'Dune buggy (gas)' },         // has "buggy" but not "electric"
];
const strict = Scanner.filterRelevant(mixed, { query: 'electric buggy', strict: true });
eq(strict.map((x) => x.id), ['1', '3'], 'strict keeps only titles with all query words (drops clothes)');
const loose = Scanner.filterRelevant(mixed, { query: 'electric buggy', strict: false });
eq(loose.length, 5, 'non-strict keeps everything when no excludes');
const excl = Scanner.filterRelevant(mixed, { query: 'electric buggy', strict: false, exclude: 'shirt, gas' });
eq(excl.map((x) => x.id), ['1', '3', '4'], 'exclude words drop matching titles');

console.log(failed === 0 ? '\nALL TESTS PASSED' : `\n${failed} TEST(S) FAILED`);
process.exit(failed === 0 ? 0 : 1);
