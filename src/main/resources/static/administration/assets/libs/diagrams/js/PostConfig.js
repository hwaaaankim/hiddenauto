/**
 * Copyright (c) 2006-2024, JGraph Holdings Ltd
 * Copyright (c) 2006-2024, draw.io AG
 */
// null'ing of global vars need to be after init.js
window.ICONSEARCH_PATH = null;
window.ICON_SERVICE_PATH = null;

// HiddenBATHAuto uses its own backend AI and product drawing persistence.
Editor.enableAi = false;
Editor.enableServiceWorker = false;
Editor.enableRealtime = false;
Editor.enableRealtimeCache = false;
