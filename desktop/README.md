# Marketplace Scanner — Desktop (Windows)

An Electron desktop version of the Marketplace deal scanner. It opens a real
Facebook window you log into once, scans Marketplace on the live page, scores
each listing against the going rate, and lets you save the ones you like.

## Why Electron

Facebook only renders its results into a real, on-screen browser. Electron
embeds Chromium, so the scan runs in a genuine browser view with a desktop
user-agent — the same approach that makes the Android "foreground scan" work,
but native on Windows. Your login persists in a dedicated profile
(`persist:fb`), so you sign in once.

## Features

- **Search + scan**: query, city slug, and min/max price.
- **Market-value deal scoring**: each listing is scored against the median of
  the comparable results in the same scan — 🔥 STEAL / ✓ FAIR / ⚠ HIGH.
- **All / 🔥 Deals filter** and **Saved** list (★ any listing).
- Listings open in your system browser; data is stored locally.

## Run from source

```bash
cd desktop
npm install
npm start      # launch the app
npm test       # pure-logic tests (no Electron needed)
```

## Build the Windows installer

Handled by GitHub Actions on a Windows runner (`.github/workflows/desktop.yml`):
it produces an NSIS installer (`MarketplaceScanner-Setup-<version>.exe`) and a
portable `.exe`, uploaded as the `marketplace-scanner-windows` artifact. To build
locally on Windows:

```bash
cd desktop
npm install
npm run dist   # outputs to desktop/dist/
```

## Layout

```
desktop/
├── src/
│   ├── main.js            # Electron main process (window + local storage)
│   ├── preload.js         # safe IPC bridge
│   ├── dealScorer.js      # market-value scoring (shared, unit-tested)
│   ├── scanner.js         # URL builder + injected extraction JS + parser
│   └── renderer/          # UI (index.html, styles.css, renderer.js)
└── test/logic.test.js     # Node tests for the scoring + parsing
```

> Personal-use tool. It uses your own Facebook session; respect Facebook's
> Terms of Service and applicable law.
