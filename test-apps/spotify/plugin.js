'use strict';

var API_ROOT = 'https://api.spotify.com/v1';
var SPOTIFY_HOME = 'https://open.spotify.com/';
var SOURCE_KEYS = ['music/track', 'music/device'];
var STORE = {
  accessToken: 'spotify_access_token',
  brokerRefresh: 'spotify_broker_refresh',
  expiresAt: 'spotify_expires_at',
  profile: 'spotify_profile',
  deviceId: 'spotify_device_id',
  deviceName: 'spotify_device_name',
};

function PluginError(code, message) {
  this.name = 'PluginError';
  this.code = code;
  this.message = message;
}
PluginError.prototype = Object.create(Error.prototype);

function nonBlank(value) {
  var text = typeof value === 'string' ? value.trim() : '';
  return text || null;
}

function stored(key) {
  return nonBlank(localStorage.getItem(key));
}

function save(key, value) {
  if (value === null || value === undefined || value === '') localStorage.removeItem(key);
  else localStorage.setItem(key, String(value));
}

function readJson(key) {
  var raw = stored(key);
  if (!raw) return null;
  try { return JSON.parse(raw); } catch (_) { return null; }
}

function encodeForm(values) {
  return Object.keys(values).filter(function (key) {
    return values[key] !== undefined && values[key] !== null;
  }).map(function (key) {
    return encodeURIComponent(key) + '=' + encodeURIComponent(String(values[key]));
  }).join('&');
}

function addQuery(url, values) {
  var query = encodeForm(values || {});
  return query ? url + (url.indexOf('?') === -1 ? '?' : '&') + query : url;
}

async function http(method, url, options) {
  options = options || {};
  try {
    var response = await fetch(url, {
      method: method,
      headers: options.headers || {},
      body: options.body === undefined ? undefined : options.body,
    });
    var text = await response.text();
    var json = null;
    if (text) {
      try { json = JSON.parse(text); } catch (_) { /* response may intentionally be empty */ }
    }
    var retryAfter = null;
    try { retryAfter = response.headers.get('Retry-After'); } catch (_) { /* unavailable */ }
    return { status: response.status, text: text, json: json, retryAfter: retryAfter };
  } catch (_) {
    return { status: 0, text: '', json: null, retryAfter: null };
  }
}

function responseMessage(response, fallback) {
  var message = response && response.json && response.json.error;
  if (message && typeof message === 'object') message = message.message;
  if (typeof message !== 'string' || !message.trim()) message = fallback;
  return message;
}

function throwApiError(response, operation) {
  if (!response || response.status === 0) {
    throw new PluginError('PLUGIN_UNAVAILABLE', 'Could not reach Spotify while ' + operation + '.');
  }
  if (response.status === 401) {
    throw new PluginError('AUTH_REQUIRED', 'Spotify authorization expired. Reconnect Spotify in the plugin gear page.');
  }
  if (response.status === 403) {
    throw new PluginError('PLUGIN_UNAVAILABLE', responseMessage(response,
      'Spotify refused this playback command. Remote playback control requires Spotify Premium and an unrestricted device.'));
  }
  if (response.status === 404) {
    throw new PluginError('PLUGIN_UNAVAILABLE', responseMessage(response,
      'No available Spotify playback device was found. Open Spotify on a device or select one in the plugin gear page.'));
  }
  if (response.status === 429) {
    var suffix = response.retryAfter ? ' Try again in ' + response.retryAfter + ' seconds.' : ' Try again shortly.';
    throw new PluginError('RATE_LIMITED', 'Spotify rate-limited this request.' + suffix);
  }
  if (response.status < 200 || response.status > 299) {
    throw new PluginError('PLUGIN_UNAVAILABLE', responseMessage(response,
      'Spotify could not complete this request (HTTP ' + response.status + ').'));
  }
}

function tokenExpiry(bundle) {
  var seconds = bundle ? Number(bundle.expires_in) : NaN;
  if (!Number.isFinite(seconds) || seconds <= 0) seconds = 3600;
  return Date.now() + seconds * 1000;
}

function storeTokenBundle(bundle) {
  var token = bundle && nonBlank(bundle.access_token);
  if (!token) throw new PluginError('AUTH_REQUIRED', 'Spotify returned no access token. Start authorization again.');
  save(STORE.accessToken, token);
  save(STORE.expiresAt, tokenExpiry(bundle));
  var refresh = bundle && nonBlank(bundle.broker_refresh);
  if (refresh) save(STORE.brokerRefresh, refresh);
  return token;
}

async function refreshAccessToken() {
  var refresh = stored(STORE.brokerRefresh);
  if (!refresh) {
    throw new PluginError('AUTH_REQUIRED', 'Connect Spotify in the plugin gear page.');
  }
  var response;
  try {
    response = await Pebble.oauth.refresh('spotify', refresh);
  } catch (error) {
    throw new PluginError('AUTH_REQUIRED', 'Spotify sign-in can no longer be refreshed. Reconnect in the plugin gear page.');
  }
  return storeTokenBundle(response);
}

async function accessToken(forceRefresh) {
  var access = stored(STORE.accessToken);
  var expiresAt = Number(stored(STORE.expiresAt));
  if (!forceRefresh && access && Number.isFinite(expiresAt) && Date.now() < expiresAt - 60000) return access;
  if (stored(STORE.brokerRefresh)) return refreshAccessToken();
  if (access && !forceRefresh) return access;
  throw new PluginError('AUTH_REQUIRED', 'Connect Spotify in the plugin gear page.');
}

async function api(method, path, options) {
  options = options || {};
  var url = addQuery(API_ROOT + path, options.query);
  var token = await accessToken(false);
  var headers = { Authorization: 'Bearer ' + token };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  var response = await http(method, url, {
    headers: headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  if (response.status === 401 && stored(STORE.brokerRefresh)) {
    token = await accessToken(true);
    headers.Authorization = 'Bearer ' + token;
    response = await http(method, url, {
      headers: headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  }
  throwApiError(response, options.operation || 'contacting Spotify');
  return response;
}

function spotifyReference(value) {
  var input = nonBlank(value);
  if (!input) return null;
  var uri = /^spotify:(track|album|artist|playlist):([A-Za-z0-9]+)$/i.exec(input);
  if (uri) return { type: uri[1].toLowerCase(), id: uri[2], uri: 'spotify:' + uri[1].toLowerCase() + ':' + uri[2] };
  var url = /^https:\/\/open\.spotify\.com\/(?:intl-[^/]+\/)?(track|album|artist|playlist)\/([A-Za-z0-9]+)(?:[/?#].*)?$/i.exec(input);
  if (url) return { type: url[1].toLowerCase(), id: url[2], uri: 'spotify:' + url[1].toLowerCase() + ':' + url[2] };
  return null;
}

function spotifyUrl(item) {
  if (item && item.external_urls && nonBlank(item.external_urls.spotify)) return item.external_urls.spotify;
  var ref = item && spotifyReference(item.uri);
  return ref ? 'https://open.spotify.com/' + ref.type + '/' + ref.id : SPOTIFY_HOME;
}

function artistsText(item) {
  var artists = item && Array.isArray(item.artists) ? item.artists : [];
  return artists.map(function (artist) { return artist && artist.name; }).filter(Boolean).join(', ');
}

function itemLabel(item, type) {
  if (!item) return 'Spotify item';
  if (type === 'track') {
    var artists = artistsText(item);
    return '“' + item.name + '”' + (artists ? ' by ' + artists : '');
  }
  if (type === 'album') {
    var albumArtists = artistsText(item);
    return 'album “' + item.name + '”' + (albumArtists ? ' by ' + albumArtists : '');
  }
  if (type === 'artist') return 'artist “' + item.name + '”';
  if (type === 'playlist') return 'playlist “' + item.name + '”';
  return '“' + item.name + '”';
}

function collectionForSearch(json, type) {
  var key = type + 's';
  var collection = json && json[key];
  return collection && Array.isArray(collection.items) ? collection.items.filter(Boolean) : [];
}

async function searchSpotify(query, type, limit) {
  var text = nonBlank(query);
  if (!text) throw new PluginError('INVALID_ARGS', 'Provide a Spotify search query.');
  if (['track', 'album', 'artist', 'playlist'].indexOf(type) === -1) {
    throw new PluginError('INVALID_ARGS', 'Search type must be track, album, artist, or playlist.');
  }
  var count = Math.max(1, Math.min(10, Number(limit) || 5));
  var response = await api('GET', '/search', {
    query: { q: text, type: type, limit: count },
    operation: 'searching Spotify',
  });
  return collectionForSearch(response.json, type);
}

async function privatePlaylists() {
  var response = await api('GET', '/me/playlists', {
    query: { limit: 50 },
    operation: 'loading your Spotify playlists',
  });
  return response.json && Array.isArray(response.json.items) ? response.json.items.filter(Boolean) : [];
}

async function resolvePlaylist(value) {
  var ref = spotifyReference(value);
  if (ref) {
    if (ref.type !== 'playlist') throw new PluginError('INVALID_ARGS', 'Expected a Spotify playlist URI or URL.');
    return { uri: ref.uri, name: 'playlist', external_urls: { spotify: spotifyUrl(ref) } };
  }
  var name = nonBlank(value);
  if (!name) throw new PluginError('INVALID_ARGS', 'Provide a Spotify playlist name or URI.');
  var mine = await privatePlaylists();
  var lower = name.toLowerCase();
  var exact = mine.filter(function (item) { return nonBlank(item.name) && item.name.trim().toLowerCase() === lower; });
  if (exact.length === 1) return exact[0];
  var results = await searchSpotify(name, 'playlist', 5);
  if (!results.length && exact.length) return exact[0];
  if (!results.length) throw new PluginError('INVALID_ARGS', 'No Spotify playlist matched “' + name + '”.');
  var globalExact = results.filter(function (item) { return nonBlank(item.name) && item.name.trim().toLowerCase() === lower; });
  return globalExact[0] || results[0];
}

async function resolvePlayable(args, forcedType) {
  var ref = spotifyReference(args.uri);
  if (args.uri && !ref) throw new PluginError('INVALID_ARGS', 'Use a spotify: URI or open.spotify.com track, album, artist, or playlist URL.');
  if (ref) {
    if (forcedType && ref.type !== forcedType) throw new PluginError('INVALID_ARGS', 'Expected a Spotify ' + forcedType + ' URI or URL.');
    return { type: ref.type, item: { uri: ref.uri, name: ref.type, external_urls: { spotify: spotifyUrl(ref) } } };
  }
  var query = nonBlank(args.query);
  if (!query) return null;
  var type = forcedType || nonBlank(args.type) || 'track';
  if (type === 'playlist') return { type: type, item: await resolvePlaylist(query) };
  var matches = await searchSpotify(query, type, 5);
  if (!matches.length) throw new PluginError('INVALID_ARGS', 'No Spotify ' + type + ' matched “' + query + '”.');
  return { type: type, item: matches[0] };
}

async function devices() {
  var response = await api('GET', '/me/player/devices', { operation: 'loading Spotify devices' });
  return response.json && Array.isArray(response.json.devices) ? response.json.devices.filter(function (device) {
    return device && nonBlank(device.id);
  }) : [];
}

function preferredDeviceId(args) {
  return nonBlank(args && args.device_id) || stored(STORE.deviceId);
}

async function resolveDevice(value) {
  var requested = nonBlank(value);
  if (!requested) throw new PluginError('INVALID_ARGS', 'Provide a Spotify Connect device name or ID.');
  var list = await devices();
  var byId = list.filter(function (device) { return device.id === requested; });
  if (byId.length) return byId[0];
  var lower = requested.toLowerCase();
  var exact = list.filter(function (device) { return nonBlank(device.name) && device.name.trim().toLowerCase() === lower; });
  if (exact.length === 1) return exact[0];
  if (exact.length > 1) throw new PluginError('INVALID_ARGS', 'More than one Spotify device is named “' + requested + '”. Use its device ID.');
  var partial = list.filter(function (device) { return nonBlank(device.name) && device.name.toLowerCase().indexOf(lower) !== -1; });
  if (partial.length === 1) return partial[0];
  var known = list.map(function (device) { return '“' + device.name + '”'; }).join(', ');
  throw new PluginError('INVALID_ARGS', 'No Spotify device matched “' + requested + '”.' + (known ? ' Available: ' + known + '.' : ' Open Spotify on a device first.'));
}

async function currentPlayback() {
  var response = await api('GET', '/me/player', { operation: 'reading Spotify playback' });
  return response.status === 204 ? null : response.json;
}

async function resultUrl() {
  try {
    var playback = await currentPlayback();
    return playback && playback.item ? spotifyUrl(playback.item) : SPOTIFY_HOME;
  } catch (_) {
    return SPOTIFY_HOME;
  }
}

function formatMs(ms) {
  var total = Math.max(0, Math.floor((Number(ms) || 0) / 1000));
  var minutes = Math.floor(total / 60);
  var seconds = String(total % 60);
  return minutes + ':' + (seconds.length < 2 ? '0' : '') + seconds;
}

function shortText(text) { return { shortText: { text: String(text) } }; }
function longText(text) { return { longText: { text: String(text) } }; }
function bool(value, yes, no) {
  return { boolean: { value: !!value }, shortText: { text: value ? yes : no } };
}
function number(value, unit, min, max) {
  return { numericValue: { value: Number(value), unit: unit, min: min, max: max }, shortText: { text: String(value) + unit } };
}

async function loadAccount() {
  var profileResponse = await api('GET', '/me', { operation: 'checking the Spotify account' });
  var profile = profileResponse.json || {};
  var safe = {
    id: nonBlank(profile.id),
    name: nonBlank(profile.display_name) || nonBlank(profile.id) || 'Spotify account',
    product: nonBlank(profile.product),
  };
  save(STORE.profile, JSON.stringify(safe));
  var list = await devices();
  return { profile: safe, devices: list };
}

function configStatus() {
  var profile = readJson(STORE.profile);
  return {
    configured: !!(stored(STORE.accessToken) || stored(STORE.brokerRefresh)),
    ready: !!(stored(STORE.accessToken) || stored(STORE.brokerRefresh)),
    profile: profile,
    preferredDevice: stored(STORE.deviceId) ? {
      id: stored(STORE.deviceId),
      name: stored(STORE.deviceName) || stored(STORE.deviceId),
    } : null,
  };
}

function actionError(error, respond) {
  var code = error && error.code ? error.code : 'UNKNOWN';
  var message = error && error.message ? error.message : String(error);
  respond.error(code, message);
}

Pebble.registerSourceHandler(async function (request, respond) {
  try {
    if (request.category !== 'music') {
      respond.error('PLUGIN_UNAVAILABLE', 'Spotify provides only music sources.');
      return;
    }
    if (request.item === 'track') {
      var playback = await currentPlayback();
      if (!playback || !playback.item) {
        respond.data({ validUntilMs: Date.now() + 15000, instances: [] });
        return;
      }
      var item = playback.item;
      var progress = Number(playback.progress_ms) || 0;
      var duration = Number(item.duration_ms) || 0;
      var properties = {
        title: shortText(item.name || 'Unknown track'),
        artist: shortText(artistsText(item) || 'Unknown artist'),
        album: shortText(item.album && item.album.name ? item.album.name : ''),
        playing: bool(playback.is_playing, 'Playing', 'Paused'),
        progress: { numericValue: { value: progress, unit: 'ms', min: 0, max: duration }, shortText: { text: formatMs(progress) } },
        duration: { numericValue: { value: duration, unit: 'ms', min: 0, max: duration }, shortText: { text: formatMs(duration) } },
        device: shortText(playback.device && playback.device.name ? playback.device.name : ''),
        shuffle: bool(playback.shuffle_state, 'Shuffle', 'In order'),
        repeat: shortText(playback.repeat_state || 'off'),
        url: longText(spotifyUrl(item)),
      };
      respond.data({
        validUntilMs: Date.now() + 15000,
        instances: [{ instanceId: item.id || 'current', properties: properties }],
      });
      return;
    }
    if (request.item === 'device') {
      var list = await devices();
      respond.data({
        validUntilMs: Date.now() + 30000,
        instances: list.map(function (device) {
          var properties = {
            name: shortText(device.name || 'Spotify device'),
            type: shortText(device.type || 'unknown'),
            active: bool(device.is_active, 'Active', 'Inactive'),
            restricted: bool(device.is_restricted, 'Restricted', 'Available'),
            supports_volume: { boolean: { value: !!device.supports_volume } },
          };
          if (typeof device.volume_percent === 'number') properties.volume = number(device.volume_percent, '%', 0, 100);
          return { instanceId: device.id, properties: properties };
        }),
      });
      return;
    }
    respond.error('PLUGIN_UNAVAILABLE', 'Unknown Spotify music item.');
  } catch (error) {
    actionError(error, respond);
  }
});

async function playResolved(resolved, args) {
  var deviceId = preferredDeviceId(args);
  var query = deviceId ? { device_id: deviceId } : {};
  if (!resolved) {
    await api('PUT', '/me/player/play', { query: query, operation: 'resuming Spotify' });
    return { text: 'Resumed Spotify.', url: await resultUrl() };
  }
  var body;
  if (resolved.type === 'track') {
    body = { uris: [resolved.item.uri] };
    if (Number.isInteger(args.position_ms) && args.position_ms >= 0) body.position_ms = args.position_ms;
  } else {
    body = { context_uri: resolved.item.uri };
  }
  await api('PUT', '/me/player/play', { query: query, body: body, operation: 'starting Spotify playback' });
  return { text: 'Playing ' + itemLabel(resolved.item, resolved.type) + ' on Spotify.', url: spotifyUrl(resolved.item) };
}

Pebble.registerActionHandler(async function (request, respond) {
  var action = request.action;
  var args = request.args || {};
  try {
    if (action === 'set_playing') {
      if (typeof args.on !== 'boolean') throw new PluginError('INVALID_ARGS', 'on must be true or false.');
      if (args.on) {
        var resumed = await playResolved(null, args);
        respond.ok({ text: resumed.text, url: resumed.url, refreshed: SOURCE_KEYS });
      } else {
        await api('PUT', '/me/player/pause', { query: preferredDeviceId(args) ? { device_id: preferredDeviceId(args) } : {}, operation: 'pausing Spotify' });
        respond.ok({ text: 'Paused Spotify.', url: await resultUrl(), refreshed: SOURCE_KEYS });
      }
      return;
    }
    if (action === 'play') {
      var resolved = await resolvePlayable(args, null);
      var played = await playResolved(resolved, args);
      respond.ok({ text: played.text, url: played.url, refreshed: SOURCE_KEYS });
      return;
    }
    if (action === 'play_playlist') {
      var playlistInput = nonBlank(args.playlist);
      if (!playlistInput) throw new PluginError('INVALID_ARGS', 'Provide a playlist name, Spotify URI, or URL.');
      var playlistRef = spotifyReference(playlistInput);
      var playlist = playlistRef ? await resolvePlayable({ uri: playlistInput }, 'playlist') : { type: 'playlist', item: await resolvePlaylist(playlistInput) };
      var playlistResult = await playResolved(playlist, args);
      respond.ok({ text: playlistResult.text, url: playlistResult.url, refreshed: SOURCE_KEYS });
      return;
    }
    if (action === 'pause') {
      var pauseDevice = preferredDeviceId(args);
      await api('PUT', '/me/player/pause', { query: pauseDevice ? { device_id: pauseDevice } : {}, operation: 'pausing Spotify' });
      respond.ok({ text: 'Paused Spotify.', url: await resultUrl(), refreshed: SOURCE_KEYS });
      return;
    }
    if (action === 'next_track' || action === 'previous_track') {
      var direction = action === 'next_track' ? 'next' : 'previous';
      var skipDevice = preferredDeviceId(args);
      await api('POST', '/me/player/' + direction, { query: skipDevice ? { device_id: skipDevice } : {}, operation: 'skipping Spotify playback' });
      respond.ok({ text: direction === 'next' ? 'Skipped to the next Spotify track.' : 'Returned to the previous Spotify track.', url: await resultUrl(), refreshed: SOURCE_KEYS });
      return;
    }
    if (action === 'seek') {
      if (!Number.isInteger(args.position_ms) || args.position_ms < 0) throw new PluginError('INVALID_ARGS', 'position_ms must be a non-negative integer.');
      var seekDevice = preferredDeviceId(args);
      await api('PUT', '/me/player/seek', {
        query: { position_ms: args.position_ms, device_id: seekDevice || undefined },
        operation: 'seeking Spotify playback',
      });
      respond.ok({ text: 'Moved Spotify playback to ' + formatMs(args.position_ms) + '.', url: await resultUrl(), refreshed: SOURCE_KEYS });
      return;
    }
    if (action === 'set_volume') {
      if (!Number.isInteger(args.volume_percent) || args.volume_percent < 0 || args.volume_percent > 100) {
        throw new PluginError('INVALID_ARGS', 'volume_percent must be a whole number from 0 to 100.');
      }
      var volumeDevice = preferredDeviceId(args);
      await api('PUT', '/me/player/volume', {
        query: { volume_percent: args.volume_percent, device_id: volumeDevice || undefined },
        operation: 'changing Spotify volume',
      });
      respond.ok({ text: 'Set Spotify volume to ' + args.volume_percent + '%.', url: await resultUrl(), refreshed: SOURCE_KEYS });
      return;
    }
    if (action === 'set_shuffle') {
      if (typeof args.enabled !== 'boolean') throw new PluginError('INVALID_ARGS', 'enabled must be true or false.');
      var shuffleDevice = preferredDeviceId(args);
      await api('PUT', '/me/player/shuffle', {
        query: { state: args.enabled, device_id: shuffleDevice || undefined },
        operation: 'changing Spotify shuffle',
      });
      respond.ok({ text: args.enabled ? 'Enabled Spotify shuffle.' : 'Disabled Spotify shuffle.', url: await resultUrl(), refreshed: SOURCE_KEYS });
      return;
    }
    if (action === 'set_repeat') {
      if (['off', 'context', 'track'].indexOf(args.mode) === -1) throw new PluginError('INVALID_ARGS', 'Repeat mode must be off, context, or track.');
      var repeatDevice = preferredDeviceId(args);
      await api('PUT', '/me/player/repeat', {
        query: { state: args.mode, device_id: repeatDevice || undefined },
        operation: 'changing Spotify repeat mode',
      });
      respond.ok({ text: 'Set Spotify repeat to ' + args.mode + '.', url: await resultUrl(), refreshed: SOURCE_KEYS });
      return;
    }
    if (action === 'search') {
      var matches = await searchSpotify(args.query, args.type, args.limit);
      if (!matches.length) throw new PluginError('INVALID_ARGS', 'No Spotify ' + args.type + ' matched “' + args.query + '”.');
      var summary = matches.slice(0, Math.max(1, Math.min(10, Number(args.limit) || 5))).map(function (item, index) {
        return (index + 1) + '. ' + itemLabel(item, args.type) + ' — ' + item.uri;
      }).join('\n');
      respond.ok({ text: 'Spotify results:\n' + summary, url: spotifyUrl(matches[0]), refreshed: [] });
      return;
    }
    if (action === 'add_to_queue') {
      var queued = await resolvePlayable({ uri: args.uri, query: args.query, type: 'track' }, 'track');
      if (!queued) throw new PluginError('INVALID_ARGS', 'Provide a track name, URI, or URL.');
      var queueDevice = preferredDeviceId(args);
      await api('POST', '/me/player/queue', {
        query: { uri: queued.item.uri, device_id: queueDevice || undefined },
        operation: 'adding a track to the Spotify queue',
      });
      respond.ok({ text: 'Added ' + itemLabel(queued.item, 'track') + ' to the Spotify queue.', url: spotifyUrl(queued.item), refreshed: SOURCE_KEYS });
      return;
    }
    if (action === 'transfer_playback') {
      var device = await resolveDevice(args.device);
      await api('PUT', '/me/player', {
        body: { device_ids: [device.id], play: args.play === true },
        operation: 'transferring Spotify playback',
      });
      respond.ok({ text: 'Transferred Spotify playback to “' + device.name + '”' + (args.play === true ? ' and started playing.' : '.'), url: await resultUrl(), refreshed: SOURCE_KEYS });
      return;
    }
    throw new PluginError('PLUGIN_UNAVAILABLE', 'Spotify has no action named ' + action + '.');
  } catch (error) {
    actionError(error, respond);
  }
});

Pebble.registerConfigHandler(async function (message, respond) {
  try {
    switch (message && message.type) {
      case 'status':
        respond(configStatus());
        return;
      case 'connect': {
        storeTokenBundle(await Pebble.oauth.authorize('spotify'));
        var account = await loadAccount();
        Pebble.refreshSources(SOURCE_KEYS);
        respond({ configured: true, ready: true, profile: account.profile, devices: account.devices, preferredDevice: configStatus().preferredDevice });
        return;
      }
      case 'testConnection': {
        var loaded = await loadAccount();
        respond({ configured: true, ready: true, profile: loaded.profile, devices: loaded.devices, preferredDevice: configStatus().preferredDevice });
        return;
      }
      case 'listDevices': {
        var knownDevices = await devices();
        respond({ configured: true, devices: knownDevices, preferredDevice: configStatus().preferredDevice });
        return;
      }
      case 'selectDevice': {
        var selectedId = nonBlank(message.deviceId);
        if (!selectedId) {
          save(STORE.deviceId, null);
          save(STORE.deviceName, null);
          Pebble.refreshSources(SOURCE_KEYS);
          respond({ configured: true, preferredDevice: null });
          return;
        }
        var available = await devices();
        var selected = available.filter(function (device) { return device.id === selectedId; })[0];
        if (!selected) throw new PluginError('INVALID_ARGS', 'That Spotify device is no longer available. Refresh devices and try again.');
        save(STORE.deviceId, selected.id);
        save(STORE.deviceName, selected.name || selected.id);
        Pebble.refreshSources(SOURCE_KEYS);
        respond({ configured: true, preferredDevice: { id: selected.id, name: selected.name || selected.id } });
        return;
      }
      case 'disconnect':
        [STORE.accessToken, STORE.brokerRefresh, STORE.expiresAt, STORE.profile, STORE.deviceId, STORE.deviceName, 'spotify_refresh_token', 'spotify_pkce_pending'].forEach(function (key) {
          save(key, null);
        });
        Pebble.refreshSources(SOURCE_KEYS);
        respond(configStatus());
        return;
      default:
        respond({ error: 'Unknown Spotify configuration message.' });
    }
  } catch (error) {
    respond({ error: error && error.message ? error.message : String(error), code: error && error.code ? error.code : 'UNKNOWN' });
  }
});
