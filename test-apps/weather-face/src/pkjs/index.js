// PKJS half of the plugin API weather face (see new-plugin-api.md).
//
// Two subscriptions: `weather/location` for the place itself — one instance per saved location,
// and this face draws the first — and `weather/hour` for the hours ahead of it. Both feed one
// snapshot, so the watch gets a single message however the two updates interleave.

var HOURS = 3;
var UNKNOWN = -1;
var THEME_KEY = 'theme';

// Until the watch says how much room it has. The hero disc and the small hourly ones are
// different sizes, so they are different subscriptions.
var iconSize = { hero: 88, hour: 26 };
// The bitmap last sent per slot, so an unchanged condition doesn't re-send 4KB every refresh.
var sent = ['', '', '', ''];

var snapshot = { uv: UNKNOWN, err: '' };
var pending = null;
var subscriptions = [];

/** Light unless the user says otherwise; the watch keeps its own copy for offline launches. */
function theme() {
  return localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light';
}

function log(message) {
  console.log('[WeatherFace] ' + message);
}

function readingOf(property, shape) {
  var payload = property && property[shape];
  return payload ? payload.text : '';
}

/** Pebble's app message can carry bytes, so the bitmap travels as-is rather than as base64. */
function bytesOf(base64) {
  var binary = atob(base64);
  var bytes = [];
  for (var i = 0; i < binary.length; i++) bytes.push(binary.charCodeAt(i));
  return bytes;
}

/** Slot 0 is the hero; 1..3 are the hours. Each bitmap needs a message of its own. */
function sendIcon(slot, property) {
  // The colour disc rather than the monochrome `icon`: this face has the screen for it.
  var icon = property && property.image;
  if (!icon || sent[slot] === icon.pixels) return;
  sent[slot] = icon.pixels;
  Pebble.sendAppMessage({
    iconSlot: slot,
    iconWidth: icon.width,
    iconHeight: icon.height,
    iconPalette: bytesOf(icon.palette),
    iconPixels: bytesOf(icon.pixels),
  }, function () {}, function (e) {
    sent[slot] = '';
    log('sending icon ' + slot + ' failed: ' + JSON.stringify(e));
  });
}

/** Coalesced: a location update and an hourly one land together far more often than not. */
function push() {
  if (pending) return;
  pending = setTimeout(function () {
    pending = null;
    Pebble.sendAppMessage(snapshot, function () {}, function (e) {
      log('sendAppMessage failed: ' + JSON.stringify(e));
    });
  }, 200);
}

function onLocation(envelope) {
  var location = envelope.instances[0];
  if (!location) {
    snapshot.err = 'No location';
    push();
    return;
  }
  var properties = location.properties;
  var uv = properties.uv_index && properties.uv_index.numericValue;
  snapshot.err = '';
  snapshot.place = readingOf(properties.name, 'shortText');
  snapshot.temp = readingOf(properties.temperature, 'shortText');
  snapshot.high = readingOf(properties.high, 'shortText');
  snapshot.low = readingOf(properties.low, 'shortText');
  snapshot.condition = readingOf(properties.condition, 'shortText');
  snapshot.uv = uv ? Math.round(uv.value) : UNKNOWN;
  snapshot.precip = readingOf(properties.precipitation, 'shortText');
  push();
  sendIcon(0, properties.condition);
}

function onHours(envelope) {
  for (var i = 0; i < HOURS; i++) {
    var hour = envelope.instances[i];
    snapshot['h' + i + 't'] = hour ? readingOf(hour.properties.temperature, 'shortText') : '';
    if (hour) sendIcon(i + 1, hour.properties.condition);
  }
  push();
}

// ---------------------------------------------------------------- settings page

function configPage() {
  return '<!doctype html><html><head><meta charset="utf-8">' +
    '<meta name="viewport" content="width=device-width, initial-scale=1">' +
    '<title>Plugin Demo: Weather Face</title>' +
    '<style>' +
    ':root{color-scheme:light dark}' +
    'body{font:16px system-ui,-apple-system,sans-serif;margin:0;padding:20px}' +
    'h1{font-size:22px;margin:0 0 4px}' +
    '.hint{font-size:13px;opacity:.7;margin:0 0 20px}' +
    '.row{display:flex;align-items:center;gap:8px;margin-top:8px}' +
    '.row span{font-size:12px;opacity:.6;text-transform:uppercase;' +
    'letter-spacing:.05em;flex:0 0 auto;width:64px}' +
    'select{flex:1;padding:10px;font-size:16px;border-radius:8px;' +
    'border:1px solid rgba(128,128,128,.5);background:transparent;color:inherit}' +
    '</style></head><body>' +
    '<h1>Plugin Demo: Weather Face</h1>' +
    '<p class="hint" id="status">Waiting for the app…</p>' +
    '<div class="row"><span>Theme</span>' +
    '<select id="theme"><option value="light">Light</option>' +
    '<option value="dark">Dark</option></select></div>' +
    '<script>' +
    'var picker=document.getElementById("theme");' +
    'picker.onchange=function(){' +
    'Pebble.sendMessage("pkjs",{type:"setTheme",theme:picker.value});};' +
    'Pebble.addEventListener("ready",function(event){' +
    'if(event.target!=="pkjs")return;' +
    'document.getElementById("status").textContent="Applies straight away.";' +
    'Pebble.sendMessage("pkjs",{type:"theme"}).then(function(reply){' +
    'picker.value=reply.theme;});});' +
    '</script></body></html>';
}

Pebble.addEventListener('showConfiguration', function () {
  Pebble.openURL('data:text/html;charset=utf-8,' + encodeURIComponent(configPage()));
});

Pebble.addEventListener('configmessage', function (e) {
  var message = e.data || {};
  switch (message.type) {
    case 'theme':
      e.respond({ theme: theme() });
      return;
    case 'setTheme':
      localStorage.setItem(THEME_KEY, message.theme === 'dark' ? 'dark' : 'light');
      snapshot.theme = theme() === 'dark' ? 1 : 0;
      push();
      log('theme: ' + theme());
      e.respond({ ok: true });
      return;
    default:
      e.respond({ error: 'unknown message type' });
  }
});

// ---------------------------------------------------------------- lifecycle

Pebble.addEventListener('ready', function () {
  log('ready');
  snapshot.theme = theme() === 'dark' ? 1 : 0;
  hello();
  subscribe();
});

/** Ask the watch for its glyph sizes, which is all a restarted PKJS doesn't already know. */
function hello(retries) {
  Pebble.sendAppMessage({ hello: 1 }, function () {}, function () {
    if (retries === 0) return;
    setTimeout(function () { hello(retries === undefined ? 2 : retries - 1); }, 1000);
  });
}

/** Re-subscribed when the watch reports its sizes: the glyphs are rendered to fit it. */
function subscribe() {
  subscriptions.forEach(function (subscription) { subscription.unsubscribe(); });
  subscriptions = [];
  sent = ['', '', '', ''];
  subscriptions.push(Pebble.subscribeToSource({
    category: 'weather',
    item: 'location',
    properties: ['name', 'temperature', 'high', 'low', 'condition', 'uv_index', 'precipitation'],
    iconPixelSize: { w: iconSize.hero, h: iconSize.hero },
    onData: onLocation,
    onError: function (err) {
      log('weather/location: ' + err.code);
      snapshot.err = err.code;
      push();
    },
  }));
  subscriptions.push(Pebble.subscribeToSource({
    category: 'weather',
    item: 'hour',
    properties: ['temperature', 'condition'],
    iconPixelSize: { w: iconSize.hour, h: iconSize.hour },
    onData: onHours,
    onError: function (err) { log('weather/hour: ' + err.code); },
  }));
}

// The watch knows how much room each glyph has; nothing on it can scale a bitmap.
Pebble.addEventListener('appmessage', function (e) {
  var payload = e.payload || {};
  if (payload.heroSize === undefined) return;
  if (payload.heroSize === iconSize.hero && payload.hourSize === iconSize.hour) return;
  iconSize = { hero: payload.heroSize, hour: payload.hourSize };
  log('watch wants ' + iconSize.hero + 'px and ' + iconSize.hour + 'px glyphs');
  subscribe();
});
