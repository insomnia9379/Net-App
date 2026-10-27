const { contextBridge, ipcRenderer } = require('electron');

// Minimal, safe bridge: just persistence + opening external links.
contextBridge.exposeInMainWorld('api', {
  get: (key, fallback) => ipcRenderer.invoke('store:get', key, fallback),
  set: (key, value) => ipcRenderer.invoke('store:set', key, value),
  openExternal: (url) => ipcRenderer.invoke('open-external', url),
});
