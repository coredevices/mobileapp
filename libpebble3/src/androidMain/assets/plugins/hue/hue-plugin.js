// Philips Hue plugin.
//
// Uses the v1 (classic) bridge API over plain HTTP. The v2 API is HTTPS-only with a
// self-signed certificate, which nothing in the host chain will accept; v1 exposes
// everything this plugin needs and is still served by current bridge firmware.
//
// Pairing is the bridge's physical link button: POST /api returns error type 101 until the
// button has been pressed, then hands back a username that is persisted in localStorage.

var LINK_BUTTON_NOT_PRESSED = 101;

// Promise wrapper over the host's XMLHttpRequest. Resolves to { ok, status, json, error } and
// never rejects, so every caller below can branch on `ok`.
function http(url, options) {
  var opts = options || {};
  return new Promise(function (resolve) {
    var request = new XMLHttpRequest();
    request.open(opts.method || 'GET', url, true);
    Object.keys(opts.headers || {}).forEach(function (header) {
      request.setRequestHeader(header, opts.headers[header]);
    });
    request.onload = function () {
      var json = null;
      try { json = JSON.parse(request.responseText); } catch (e) { /* not json */ }
      resolve({
        ok: request.status >= 200 && request.status < 300,
        status: request.status,
        json: json,
      });
    };
    request.onerror = function () {
      resolve({ ok: false, status: 0, json: null, error: 'request failed' });
    };
    request.send(opts.body === undefined ? null : opts.body);
  });
}

var STORE_KEY = 'hue';

function state() {
  var s;
  try { s = JSON.parse(localStorage.getItem(STORE_KEY)) || {}; } catch (e) { s = {}; }
  // `hidden` is the set of room ids the user switched off in the config page. They stay on the
  // bridge; this plugin just stops exposing them.
  return { ip: s.ip || null, username: s.username || null, hidden: s.hidden || [] };
}

function saveState(next) {
  var merged = state();
  Object.keys(next).forEach(function (key) { merged[key] = next[key]; });
  localStorage.setItem(STORE_KEY, JSON.stringify(merged));
}

function isHidden(roomId) {
  return state().hidden.indexOf(String(roomId)) !== -1;
}

// ---------------------------------------------------------------- bridge plumbing

async function discoverBridge() {
  var response = await http('https://discovery.meethue.com/');
  if (!response.ok || !response.json || !response.json.length) return null;
  return response.json[0].internalipaddress || null;
}

async function bridgeIp() {
  var current = state();
  if (current.ip) return current.ip;
  var ip = await discoverBridge();
  if (ip) saveState({ ip: ip, username: current.username });
  return ip;
}

async function api(path, method, body) {
  var current = state();
  var ip = await bridgeIp();
  if (!ip) return { ok: false, error: 'no bridge found on this network' };
  if (!current.username) return { ok: false, error: 'not paired' };
  var response = await http(
    'http://' + ip + '/api/' + current.username + path,
    { method: method || 'GET', body: body ? JSON.stringify(body) : null }
  );
  if (!response.ok) return { ok: false, error: response.error || ('HTTP ' + response.status) };
  // The v1 API reports auth failures with 200 + an error array.
  if (Array.isArray(response.json) && response.json[0] && response.json[0].error) {
    return { ok: false, error: response.json[0].error.description };
  }
  return { ok: true, json: response.json };
}

async function pair() {
  var ip = await bridgeIp();
  if (!ip) return { ok: false, code: 'PLUGIN_UNAVAILABLE', message: 'no bridge found on this network' };
  var response = await http('http://' + ip + '/api', {
    method: 'POST',
    body: JSON.stringify({ devicetype: 'coreapp#pebble' }),
  });
  if (!response.ok) {
    return { ok: false, code: 'PLUGIN_UNAVAILABLE', message: response.error || ('HTTP ' + response.status) };
  }
  var first = response.json && response.json[0];
  if (first && first.error) {
    var code = first.error.type === LINK_BUTTON_NOT_PRESSED ? 'AUTH_REQUIRED' : 'UNKNOWN';
    return { ok: false, code: code, message: first.error.description };
  }
  if (first && first.success && first.success.username) {
    saveState({ ip: ip, username: first.success.username });
    return { ok: true, text: 'Paired with bridge at ' + ip + '.' };
  }
  return { ok: false, code: 'UNKNOWN', message: 'unexpected pairing response' };
}

// ---------------------------------------------------------------- properties

function isRoomOrZone(group) {
  return group.type === 'Room' || group.type === 'Zone';
}

var BRI_MIN = 1;
var BRI_MAX = 254;
var HOME_ID = '0';

function briToPercent(bri) {
  return Math.round(((bri - BRI_MIN) / (BRI_MAX - BRI_MIN)) * 100);
}

function percentToBri(percent) {
  return Math.round(BRI_MIN + (percent / 100) * (BRI_MAX - BRI_MIN));
}

/** A light, a room and the whole home all read the same way. */
function properties(name, on, percent, reachable) {
  var reading;
  if (!reachable) reading = '--';
  else if (!on) reading = 'Off';
  else reading = percent + '%';
  return {
    name: { shortText: { text: name } },
    on: {
      boolean: { value: !!on },
      shortText: { text: reachable ? (on ? 'On' : 'Off') : '--' },
    },
    brightness: {
      numericValue: { value: on ? percent : 0, unit: '%', min: 0, max: 100 },
      shortText: { text: reading },
    },
  };
}

// ---------------------------------------------------------------- sources

async function lightInstances() {
  var result = await api('/lights');
  if (!result.ok) return result;
  var lights = result.json || {};
  return {
    ok: true,
    instances: Object.keys(lights).map(function (id) {
      var light = lights[id];
      var state = light.state || {};
      return {
        instanceId: id,
        properties: properties(
          light.name || ('Light ' + id),
          state.on,
          state.bri ? briToPercent(state.bri) : 0,
          state.reachable !== false
        ),
      };
    }),
  };
}

async function roomInstances() {
  var result = await api('/groups');
  if (!result.ok) return result;
  var groups = result.json || {};
  return {
    ok: true,
    instances: Object.keys(groups)
      .filter(function (id) { return isRoomOrZone(groups[id]) && !isHidden(id); })
      .map(function (id) {
        var group = groups[id];
        return {
          instanceId: id,
          properties: properties(
            group.name || ('Room ' + id),
            group.state && group.state.any_on,
            group.action && group.action.bri ? briToPercent(group.action.bri) : 0,
            true
          ),
        };
      }),
  };
}

// Group 0 is every light on the bridge, but it reports no state of its own, so the home's
// reading is whatever its lights add up to.
async function homeInstance() {
  var result = await api('/lights');
  if (!result.ok) return result;
  var lights = result.json || {};
  var lit = Object.keys(lights)
    .map(function (id) { return lights[id].state || {}; })
    .filter(function (state) { return state.on; });
  var total = lit.reduce(function (sum, state) {
    return sum + briToPercent(state.bri || BRI_MIN);
  }, 0);
  return {
    ok: true,
    instances: [{
      instanceId: HOME_ID,
      properties: properties(
        'Home',
        lit.length > 0,
        lit.length ? Math.round(total / lit.length) : 0,
        true
      ),
    }],
  };
}

Pebble.registerSourceHandler(async function (request, respond) {
  var result;
  switch (request.item) {
    case 'light': result = await lightInstances(); break;
    case 'room_lights': result = await roomInstances(); break;
    case 'home_lights': result = await homeInstance(); break;
    default:
      respond.error('PLUGIN_UNAVAILABLE', 'unknown item ' + request.item);
      return;
  }
  if (!result.ok) {
    respond.error(result.error === 'not paired' ? 'AUTH_REQUIRED' : 'PLUGIN_UNAVAILABLE', result.error);
    return;
  }
  respond.data({
    validUntilMs: Date.now() + 30 * 1000,
    instances: result.instances,
  });
});

// ---------------------------------------------------------------- actions

var LIGHT_SOURCES = ['home/light', 'home/room_lights', 'home/home_lights'];

/** An instance id only identifies a thing alongside the item it came from. */
function write(item, id, body) {
  switch (item) {
    case 'light': return api('/lights/' + id + '/state', 'PUT', body);
    case 'room_lights': return api('/groups/' + id + '/action', 'PUT', body);
    case 'home_lights': return api('/groups/' + HOME_ID + '/action', 'PUT', body);
    default: return Promise.resolve({ ok: false, error: 'unknown item ' + item });
  }
}

Pebble.registerActionHandler(async function (request, respond) {
  var args = request.args || {};

  if (args.item === 'room_lights' && isHidden(args.instanceId)) {
    respond.error('PLUGIN_UNAVAILABLE', 'that room is hidden');
    return;
  }

  var result;
  var text;
  switch (request.action) {
    case 'set_on':
      result = await write(args.item, args.instanceId, { on: !!args.on });
      text = args.on ? 'Turned on.' : 'Turned off.';
      break;
    case 'set_brightness': {
      var percent = Math.max(0, Math.min(100, args.percent));
      result = percent === 0
        ? await write(args.item, args.instanceId, { on: false })
        : await write(args.item, args.instanceId, { on: true, bri: percentToBri(percent) });
      text = percent === 0 ? 'Turned off.' : ('Set to ' + percent + '%.');
      break;
    }
    default:
      respond.error('PLUGIN_UNAVAILABLE', 'unknown action ' + request.action);
      return;
  }

  if (!result.ok) {
    respond.error(result.error === 'not paired' ? 'AUTH_REQUIRED' : 'PLUGIN_UNAVAILABLE', result.error);
    return;
  }
  respond.ok({ text: text, refreshed: LIGHT_SOURCES });
});

// ---------------------------------------------------------------- config page

// The settings page drives pairing and setup: it can ask the user for a bridge IP, and the
// plugin does the networking, because the page itself can't reach a plain-HTTP LAN device.
Pebble.registerConfigHandler(async function (message, respond) {
  var current = state();
  switch (message && message.type) {
    case 'status':
      respond({ ip: current.ip, paired: !!current.username });
      return;
    case 'discover': {
      var ip = await discoverBridge();
      if (ip) saveState({ ip: ip, username: current.username });
      respond({ ip: ip });
      return;
    }
    case 'pair': {
      var result = await pair();
      if (result.ok) Pebble.refreshSources(LIGHT_SOURCES);
      respond({
        paired: !!result.ok,
        pending: result.code === 'AUTH_REQUIRED',
        message: result.message || result.text || null,
      });
      return;
    }
    case 'setHidden': {
      var hidden = state().hidden.filter(function (id) { return id !== String(message.id); });
      if (message.hidden) hidden.push(String(message.id));
      saveState({ hidden: hidden });
      Pebble.refreshSources(LIGHT_SOURCES);
      respond({ id: message.id, hidden: !!message.hidden });
      return;
    }
    case 'rooms': {
      var rooms = await api('/groups');
      if (!rooms.ok) {
        respond({ error: rooms.error });
        return;
      }
      respond({
        // Hidden rooms are listed here, unlike everywhere else — this is where they get
        // switched back on.
        rooms: Object.keys(rooms.json)
          .filter(function (id) { return isRoomOrZone(rooms.json[id]); })
          .map(function (id) {
            return {
              id: id,
              name: rooms.json[id].name,
              on: !!rooms.json[id].state.any_on,
              hidden: isHidden(id),
            };
          }),
      });
      return;
    }
    case 'forget':
      saveState({ ip: null, username: null });
      Pebble.refreshSources(LIGHT_SOURCES);
      respond({ paired: false, ip: null });
      return;
    default:
      respond({ error: 'unknown message type' });
  }
});
