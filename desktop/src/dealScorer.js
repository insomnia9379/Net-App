// Market-value deal scoring, ported from the Android DealScorer.
// "Market value" = the median price of the other matching listings in the same
// scan (the comparables). Well under it = STEAL, at/under = FAIR, above = HIGH.
// An explicit price ceiling always counts as a STEAL. Too few comparables = UNKNOWN.
(function (root) {
  var STEAL = 'STEAL', FAIR = 'FAIR', HIGH = 'HIGH', UNKNOWN = 'UNKNOWN';
  var MIN_COMPARABLES = 4;
  var STEAL_RATIO = 0.70;

  function median(prices) {
    if (!prices || prices.length === 0) return null;
    var s = prices.slice().sort(function (a, b) { return a - b; });
    var mid = Math.floor(s.length / 2);
    return s.length % 2 === 1 ? s[mid] : Math.floor((s[mid - 1] + s[mid]) / 2);
  }

  function score(price, marketValue, underThreshold, comparableCount) {
    if (price == null) return UNKNOWN;
    if (underThreshold != null && price <= underThreshold) return STEAL;
    if (marketValue == null || marketValue <= 0 || comparableCount < MIN_COMPARABLES) return UNKNOWN;
    if (price <= marketValue * STEAL_RATIO) return STEAL;
    if (price <= marketValue) return FAIR;
    return HIGH;
  }

  function badge(level) {
    switch (level) {
      case STEAL: return '🔥 STEAL';
      case FAIR: return '✓ FAIR';
      case HIGH: return '⚠ HIGH';
      default: return '• ?';
    }
  }

  function isDeal(level) { return level === STEAL || level === FAIR; }

  var api = { STEAL: STEAL, FAIR: FAIR, HIGH: HIGH, UNKNOWN: UNKNOWN, median: median, score: score, badge: badge, isDeal: isDeal };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.DealScorer = api;
})(typeof window !== 'undefined' ? window : globalThis);
