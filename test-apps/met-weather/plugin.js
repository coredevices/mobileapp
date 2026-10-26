// Weather where the phone is, from MET Norway's Locationforecast (api.met.no): no key, but its
// terms want an identifying User-Agent, coordinates to at most 4 decimals, and Expires /
// If-Modified-Since respected. https://api.met.no/doc/TermsOfService

var FORECAST_URL = 'https://api.met.no/weatherapi/locationforecast/2.0/complete';
var USER_AGENT = 'PebbleWeatherPluginDemo/1.0 github.com/coredevices/mobileapp';
var CACHE_KEY = 'forecast';
var MAX_HOURS = 48;
var HOURLY_PROPERTIES = ['hourly_temperature', 'hourly_condition', 'hourly_condition_code'];
var DEFAULT_TTL_MS = 30 * 60 * 1000;

// ---------------------------------------------------------------- location

function currentPosition() {
  return new Promise(function (resolve, reject) {
    navigator.geolocation.getCurrentPosition(
      function (position) { resolve(position.coords); },
      function (error) { reject(error); },
      { maximumAge: 30 * 60 * 1000, timeout: 20 * 1000 }
    );
  });
}

// ---------------------------------------------------------------- forecast

function cached() {
  try { return JSON.parse(localStorage.getItem(CACHE_KEY)); } catch (e) { return null; }
}

/** The cached forecast if it's for the same place and MET says it's still current; else a fresh one. */
async function forecastFor(coords) {
  // ~1km: close enough to share a forecast, and well inside MET's 4-decimal limit.
  var lat = coords.latitude.toFixed(2);
  var lon = coords.longitude.toFixed(2);
  var saved = cached();
  var samePlace = saved && saved.lat === lat && saved.lon === lon;
  if (samePlace && Date.now() < saved.expires) return saved;

  var headers = { 'User-Agent': USER_AGENT };
  if (samePlace && saved.lastModified) headers['If-Modified-Since'] = saved.lastModified;
  var response = await fetch(FORECAST_URL + '?lat=' + lat + '&lon=' + lon, { headers: headers });
  var expires = Date.parse(response.headers.get('expires')) || Date.now() + DEFAULT_TTL_MS;
  var fresh;
  if (response.status === 304 && samePlace) {
    fresh = saved;
  } else if (response.ok) {
    fresh = { lat: lat, lon: lon, body: await response.json(), lastModified: response.headers.get('last-modified') };
  } else {
    if (samePlace) return saved;
    throw new Error('MET returned ' + response.status);
  }
  fresh.expires = expires;
  localStorage.setItem(CACHE_KEY, JSON.stringify(fresh));
  return fresh;
}

// ---------------------------------------------------------------- conditions

/** MET's symbol_code (e.g. `lightrainshowers_day`) onto the shared weather vocabulary. */
function conditionCode(symbol) {
  var name = (symbol || '').split('_')[0];
  var heavy = name.indexOf('light') !== 0;
  if (name.indexOf('sleet') >= 0) return 'rain_and_snow';
  if (name.indexOf('snow') >= 0) return heavy ? 'heavy_snow' : 'light_snow';
  if (name.indexOf('rain') >= 0) return heavy ? 'heavy_rain' : 'light_rain';
  if (name === 'clearsky' || name === 'fair') return 'sun';
  if (name === 'partlycloudy') return 'partly_cloudy';
  if (name === 'cloudy' || name === 'fog') return 'cloudy';
  return 'unknown';
}

function conditionText(symbol) {
  var name = (symbol || '').split('_')[0];
  var special = { clearsky: 'Clear', fair: 'Fair', fog: 'Fog' };
  if (special[name]) return special[name];
  var text = {
    partly_cloudy: 'Partly cloudy', cloudy: 'Cloudy', light_rain: 'Light rain', heavy_rain: 'Rain',
    light_snow: 'Light snow', heavy_snow: 'Snow', rain_and_snow: 'Sleet', sun: 'Clear',
  }[conditionCode(symbol)] || 'Unknown';
  return name.indexOf('thunder') >= 0 ? text + ', thunder' : text;
}

// ---------------------------------------------------------------- glyphs
// As the built-in weather plugin draws them, 4-bpp palettised with index 0 transparent: `image` is
// a disc in the condition's colour (1) with the shape in white (2); `icon` is the shape alone, in
// black (1), a little larger since there's no disc around it.

var DISC = { sun: 0xF8, partly_cloudy: 0xF8, light_snow: 0xCB, heavy_snow: 0xCB, light_rain: 0xC7, heavy_rain: 0xC7, rain_and_snow: 0xC7 };
var GREY = 0xEA;
var WHITE = 0xFF;
var BLACK = 0xC0;

function glyph(code, size, mono) {
  var canvas = new Uint8Array(size * size);
  var ink = mono ? 1 : 2;
  var scale = mono ? 1.25 : 1;
  function circle(cx, cy, r, colour) {
    for (var y = Math.max(0, Math.floor(cy - r)); y <= Math.min(size - 1, Math.ceil(cy + r)); y++) {
      for (var x = Math.max(0, Math.floor(cx - r)); x <= Math.min(size - 1, Math.ceil(cx + r)); x++) {
        var dx = x + 0.5 - cx, dy = y + 0.5 - cy;
        if (dx * dx + dy * dy <= r * r) canvas[y * size + x] = colour;
      }
    }
  }
  function rect(left, top, width, height) {
    for (var y = Math.max(0, Math.floor(top)); y <= Math.min(size - 1, Math.floor(top + height)); y++) {
      for (var x = Math.max(0, Math.floor(left)); x <= Math.min(size - 1, Math.floor(left + width)); x++) {
        canvas[y * size + x] = ink;
      }
    }
  }
  function line(x0, y0, x1, y1, thickness) {
    var steps = Math.floor(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))) * 2 + 1;
    for (var i = 0; i <= steps; i++) {
      var t = i / steps;
      circle(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, thickness / 2, ink);
    }
  }
  function sun(cx, cy, r) {
    r *= scale;
    circle(cx, cy, r, ink);
    for (var i = 0; i < 8; i++) {
      var angle = i * Math.PI / 4;
      line(cx + Math.cos(angle) * r * 1.6, cy + Math.sin(angle) * r * 1.6,
        cx + Math.cos(angle) * r * 2.2, cy + Math.sin(angle) * r * 2.2, Math.max(1.5, size * 0.03));
    }
  }
  function cloud(lift) {
    var base = size * 0.62 - lift;
    var puff = size * 0.17 * scale;
    circle(size * 0.38, base - puff * 0.75, puff, ink);
    circle(size * 0.54, base - puff * 1.15, puff * 0.82, ink);
    circle(size * 0.66, base - puff * 0.55, puff * 0.66, ink);
    var half = puff * 0.42;
    var left = size * 0.30 - (scale - 1) * size * 0.06;
    var right = size * 0.70 + (scale - 1) * size * 0.06;
    circle(left, base - half, half, ink);
    circle(right, base - half, half, ink);
    rect(left, base - half * 2, right - left, half * 2);
  }
  function streaks(count, flakes) {
    cloud(size * -0.06);
    var top = size * 0.72;
    var first = 0.5 - (count - 1) * 0.06;
    for (var i = 0; i < count; i++) {
      var x = size * (first + i * 0.12);
      if (flakes) circle(x, top + size * 0.06, size * 0.04 * scale, ink);
      else line(x, top, x - size * 0.05, top + size * 0.12, Math.max(1.5, size * 0.035 * scale));
    }
  }

  if (!mono) circle(size / 2, size / 2, size / 2, 1);
  switch (code) {
    case 'sun': sun(size / 2, size / 2, size * 0.16); break;
    case 'partly_cloudy': sun(size * 0.62, size * 0.32, size * 0.12); cloud(size * 0.06); break;
    case 'cloudy': cloud(0); break;
    case 'light_rain': streaks(3, false); break;
    case 'heavy_rain': streaks(4, false); break;
    case 'light_snow': streaks(3, true); break;
    case 'heavy_snow': streaks(4, true); break;
    case 'rain_and_snow': streaks(2, false); streaks(1, true); break;
    default:
      // No shape: the disc says "some weather"; without one, an outline does.
      if (mono) {
        circle(size / 2, size / 2, size * 0.44, ink);
        circle(size / 2, size / 2, size * 0.36, 0);
      }
  }

  // Two pixels a byte, even x in the high nibble, rows padded to a whole byte.
  var stride = Math.ceil(size / 2);
  var packed = '';
  for (var y = 0; y < size; y++) {
    for (var b = 0; b < stride; b++) {
      var hi = canvas[y * size + b * 2];
      var lo = b * 2 + 1 < size ? canvas[y * size + b * 2 + 1] : 0;
      packed += String.fromCharCode((hi << 4) | lo);
    }
  }
  var palette = mono ? [0x00, BLACK] : [0x00, DISC[code] || GREY, WHITE];
  return {
    pixels: btoa(packed),
    palette: btoa(String.fromCharCode.apply(null, palette)),
    width: size,
    height: size,
  };
}

/** `lightrainshowersandthunder_day` -> "Light rain showers and thunder". */
function conditionPhrase(symbol) {
  var words = (symbol || '').split('_')[0]
    .replace(/^lights(?=s)/, 'light') // MET spells two of its codes `lightssleet...` / `lightssnow...`
    .replace(/clearsky/, 'clear sky')
    .replace(/partlycloudy/, 'partly cloudy')
    .replace(/(light|heavy|rain|sleet|snow|showers|and)(?=[a-z])/g, '$1 ');
  return words ? words.charAt(0).toUpperCase() + words.slice(1) : 'Unknown';
}

function conditionShapes(symbol, iconPixelSize, withPhrase) {
  var code = conditionCode(symbol);
  var shapes = { shortText: { text: conditionText(symbol) } };
  if (withPhrase) shapes.longText = { text: conditionPhrase(symbol) };
  if (iconPixelSize) {
    var size = Math.min(iconPixelSize.w, iconPixelSize.h);
    shapes.icon = glyph(code, size, true);
    shapes.image = glyph(code, size, false);
  }
  return shapes;
}

// ---------------------------------------------------------------- envelopes

function temperature(celsius) {
  var text = Math.round(celsius) + '°C';
  return { shortText: { text: text }, longText: { text: text }, numericValue: { value: celsius, unit: '°C' } };
}

function symbolOf(entry) {
  var next = entry.data.next_1_hours || entry.data.next_6_hours || entry.data.next_12_hours;
  return next && next.summary && next.summary.symbol_code;
}

function locationEnvelope(series, iconPixelSize, wanted) {
  var now = series[0];
  var details = now.data.instant.details;
  // MET has no daily summary, so the day's range is the next 24 hours'.
  var temps = series.slice(0, 24).map(function (entry) { return entry.data.instant.details.air_temperature; });
  var properties = {
    name: { shortText: { text: 'Current location' } },
    temperature: temperature(details.air_temperature),
    feels_like: temperature(typeof details.apparent_air_temperature === 'number' ? details.apparent_air_temperature : details.air_temperature),
    high: temperature(Math.max.apply(null, temps)),
    low: temperature(Math.min.apply(null, temps)),
    condition: conditionShapes(symbolOf(now), iconPixelSize, true),
    condition_code: { longText: { text: conditionCode(symbolOf(now)) } },
  };
  if (typeof details.ultraviolet_index_clear_sky === 'number') {
    var uv = details.ultraviolet_index_clear_sky;
    properties.uv_index = { numericValue: { value: uv, min: 0, max: 11 }, shortText: { text: String(Math.round(uv)) } };
  }
  // A series of glyphs is costly to draw, so only for a subscriber that asked for the hours.
  if (wanted.some(function (name) { return name.indexOf('hourly_') === 0; })) {
    var hourly = hourlySeries(series, iconPixelSize);
    wanted.forEach(function (name) { if (hourly[name]) properties[name] = hourly[name]; });
  }
  return [{ instanceId: 'current', properties: properties }];
}

/**
 * The hours ahead as series, from the hour we're in. MET's timeseries turns 6-hourly after a couple
 * of days, so this stops at the first step that isn't an hour.
 */
function hourlySeries(series, iconPixelSize) {
  var start = Date.parse(series[0].time);
  var hours = [];
  for (var i = 0; i < series.length && i < MAX_HOURS && Date.parse(series[i].time) === start + i * 3600000; i++) {
    hours.push(series[i]);
  }
  function over(shapes) {
    shapes.start = start / 1000;
    shapes.stepSec = 3600;
    return { series: shapes };
  }
  var temps = hours.map(function (entry) { return entry.data.instant.details.air_temperature; });
  var symbols = hours.map(symbolOf);
  var condition = { shortText: { values: symbols.map(conditionText) } };
  if (iconPixelSize) {
    var size = Math.min(iconPixelSize.w, iconPixelSize.h);
    var drawn = {};
    var glyphFor = function (symbol, mono) {
      var key = conditionCode(symbol) + (mono ? ':mono' : ':colour');
      return drawn[key] || (drawn[key] = glyph(conditionCode(symbol), size, mono));
    };
    condition.icon = { values: symbols.map(function (s) { return glyphFor(s, true); }) };
    condition.image = { values: symbols.map(function (s) { return glyphFor(s, false); }) };
  }
  return {
    hourly_temperature: over({
      numericValue: { values: temps, unit: '°C' },
      shortText: { values: temps.map(function (t) { return Math.round(t) + '°C'; }) },
    }),
    hourly_condition: over(condition),
    hourly_condition_code: over({ longText: { values: symbols.map(conditionCode) } }),
  };
}

Pebble.registerSourceHandler(async function (request, respond) {
  var coords;
  try {
    coords = await currentPosition();
  } catch (e) {
    console.error('No location: ' + ((e && e.message) || e));
    respond.error('PERMISSION_DENIED', (e && e.message) || 'no location');
    return;
  }
  var forecast;
  try {
    forecast = await forecastFor(coords);
  } catch (e) {
    console.error('Fetching the MET forecast failed: ' + e);
    respond.error('PLUGIN_UNAVAILABLE', 'Could not reach MET Norway.');
    return;
  }
  var series = forecast.body.properties.timeseries;
  var hourStart = Date.now() - 60 * 60 * 1000;
  series = series.filter(function (entry) { return Date.parse(entry.time) > hourStart; });
  if (!series.length) {
    respond.data({ instances: [] });
    return;
  }
  respond.data({
    validUntilMs: forecast.expires,
    instances: locationEnvelope(series, request.iconPixelSize, request.properties || HOURLY_PROPERTIES),
  });
});
