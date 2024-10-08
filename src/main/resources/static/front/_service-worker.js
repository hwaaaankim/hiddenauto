// To clear cache on devices, always increase APP_VER number after making changes.
// The app will serve fresh content right away or after 2-3 refreshes (open / close)
var APP_NAME = 'HIDDENBATH AUTO TASK';
var APP_VER = '1.0.0';
var CACHE_NAME = APP_NAME + '-' + APP_VER;

// Files required to make this app work offline.
// Add all files you want to view offline below.
// Leave REQUIRED_FILES = [] to disable offline.
var REQUIRED_FILES = [
	'/index',
	// Styles
	'/front/styles/style.css',
	'/front/styles/bootstrap.css',
	// Scripts
	'/front/scripts/custom.js',
	'/front/scripts/bootstrap.min.js',
	// Plugins
	'/front/plugins/before-after/before-after.css',
	'/front/plugins/before-after/before-after.js',
	'/front/plugins/charts/charts.js',
	'/front/plugins/charts/charts-call-graphs.js',
	'/front/plugins/countdown/countdown.js',
	'/front/plugins/filterizr/filterizr.js',
	'/front/plugins/filterizr/filterizr.css',
	'/front/plugins/filterizr/filterizr-call.js',
	'/front/plugins/galleryViews/gallery-views.js',
	'/front/plugins/glightbox/glightbox.js',
	'/front/plugins/glightbox/glightbox.css',
	'/front/plugins/glightbox/glightbox-call.js',
	'/front/plugins/calendar.js',
	// Fonts
	'/front/fonts/css/fontawesome-all.min.css',
	'/front/fonts/webfonts/fa-brands-400.woff2',
	'/front/fonts/webfonts/fa-regular-400.woff2',
	'/front/fonts/webfonts/fa-solid-900.woff2',
	// Images
	'/front/images/empty.png',
	
];

// Service Worker Diagnostic. Set true to get console logs.
var APP_DIAG = false;

//Service Worker Function Below.
self.addEventListener('install', function(event) {
	event.waitUntil(
		caches.open(CACHE_NAME)
		.then(function(cache) {
			//Adding files to cache
			return cache.addAll(REQUIRED_FILES);
		}).catch(function(error) {
			//Output error if file locations are incorrect
			if(APP_DIAG){console.log('Service Worker Cache: Error Check REQUIRED_FILES array in _service-worker.js - files are missing or path to files is incorrectly written -  ' + error);}
		})
		.then(function() {
			//Install SW if everything is ok
			return self.skipWaiting();
		})
		.then(function(){
			if(APP_DIAG){console.log('Service Worker: Cache is OK');}
		})
	);
	if(APP_DIAG){console.log('Service Worker: Installed');}
});

self.addEventListener('fetch', function(event) {
	event.respondWith(
		//Fetch Data from cache if offline
		caches.match(event.request)
			.then(function(response) {
				if (response) {return response;}
				return fetch(event.request);
			}
		)
	);
	if(APP_DIAG){console.log('Service Worker: Fetching '+APP_NAME+'-'+APP_VER+' files from Cache');}
});

self.addEventListener('activate', function(event) {
	event.waitUntil(self.clients.claim());
	event.waitUntil(
		//Check cache number, clear all assets and re-add if cache number changed
		caches.keys().then(cacheNames => {
			return Promise.all(
				cacheNames
					.filter(cacheName => (cacheName.startsWith(APP_NAME + "-")))
					.filter(cacheName => (cacheName !== CACHE_NAME))
					.map(cacheName => caches.delete(cacheName))
			);
		})
	);
	if(APP_DIAG){console.log('Service Worker: Activated')}
});