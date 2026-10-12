/**
 * Copyright (c) 2006-2024, JGraph Holdings Ltd
 * Copyright (c) 2006-2024, draw.io AG
 */
// Overrides of global vars need to be pre-loaded
window.DRAWIO_PUBLIC_BUILD = true;
window.EXPORT_URL = null; // Replace with the URL of your export server to enable server-side PDF and image export, e.g. https://www.example.com/export. With null, export to PDF uses the print dialog
window.DRAWIO_BASE_URL = null; // Replace with path to base of deployment, e.g. https://www.example.com/folder
window.DRAWIO_VIEWER_URL = null; // Replace your path to the viewer js, e.g. https://www.example.com/js/viewer.min.js
window.DRAWIO_LIGHTBOX_URL = null; // Replace with your lightbox URL, eg. https://www.example.com
window.DRAW_MATH_URL = 'math4/es5';
window.DRAWIO_CONFIG = null; // Replace with your custom draw.io configurations. For more details, https://www.drawio.com/doc/faq/configure-diagram-editor
urlParams['sync'] = 'manual';

// HiddenBATHAuto deployment configuration. Core upstream bundles are unchanged.
// This public script must never contain credentials or API keys.
(function ()
{
    var script = document.currentScript;
    var scriptUrl = script && script.src ? script.src : new URL('js/PreConfig.js', window.location.href).href;
    var base = new URL('../', scriptUrl).href.replace(/\/$/, '');
    window.DRAWIO_BASE_URL = base;
    window.DRAWIO_SERVER_URL = base + '/';
    window.DRAWIO_VIEWER_URL = base + '/js/viewer-static.min.js';
    window.DRAWIO_LIGHTBOX_URL = base;
    window.DRAW_MATH_URL = base + '/math4/es5';
    window.DRAWIO_CONFIG = {enableAi: false};
    window.ALLOW_CUSTOM_PLUGINS = false;
    // Product storage and AI are supplied by the parent application's own API.
    ['db', 'tr', 'gh', 'gapi', 'od', 'ms365', 'gl', 'plugins', 'pwa'].forEach(function (key)
    {
        urlParams[key] = '0';
    });
    urlParams['stealth'] = '1';
    urlParams['lockdown'] = '1';
})();
