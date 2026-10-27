const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('path');
const Store = require('electron-store');

const store = new Store({ name: 'marketplace-scanner' });

function createWindow() {
  const win = new BrowserWindow({
    width: 1200,
    height: 820,
    title: 'Marketplace Scanner',
    backgroundColor: '#111613',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      webviewTag: true, // the embedded Facebook view
    },
  });
  win.removeMenu();
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));

  // Open normal links (e.g. a listing) in the system browser, not the app.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('http')) { shell.openExternal(url); return { action: 'deny' }; }
    return { action: 'allow' };
  });
}

// Simple persisted key/value store for searches + saved items.
ipcMain.handle('store:get', (_e, key, fallback) => store.get(key, fallback));
ipcMain.handle('store:set', (_e, key, value) => { store.set(key, value); return true; });
ipcMain.handle('open-external', (_e, url) => { if (url) shell.openExternal(url); });

app.whenReady().then(createWindow);
app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
