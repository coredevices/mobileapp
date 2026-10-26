// TickTick Open API plugin. CoreApp's trusted HostedOAuth bridge handles the provider
// authorization and keeps the developer client secret out of this PBW.

var API_BASE = 'https://api.ticktick.com/open/v1';
var TOKEN_KEY = 'ticktick_access_token';
var BROKER_REFRESH_KEY = 'ticktick_broker_refresh';
var EXPIRES_AT_KEY = 'ticktick_expires_at';
var PROJECT_REFRESH_MS = 15 * 60 * 1000;

async function secureItem(key) {
  return String(await Pebble.secureStorage.getItem(key) || '').trim();
}

function accessToken() {
  return secureItem(TOKEN_KEY);
}

async function saveTokenBundle(bundle) {
  var token = String(bundle && bundle.access_token || '').trim();
  if (!token) throw new Error('TickTick returned no access token.');
  await Pebble.secureStorage.setItem(TOKEN_KEY, token);
  var brokerRefresh = String(bundle.broker_refresh || '').trim();
  if (brokerRefresh) await Pebble.secureStorage.setItem(BROKER_REFRESH_KEY, brokerRefresh);
  else await Pebble.secureStorage.removeItem(BROKER_REFRESH_KEY);
  var expiresIn = Number(bundle.expires_in || 0);
  if (isFinite(expiresIn) && expiresIn > 0) {
    localStorage.setItem(EXPIRES_AT_KEY, String(Date.now() + expiresIn * 1000));
  } else {
    localStorage.removeItem(EXPIRES_AT_KEY);
  }
}

async function clearTokens() {
  await Pebble.secureStorage.removeItem(TOKEN_KEY);
  await Pebble.secureStorage.removeItem(BROKER_REFRESH_KEY);
  localStorage.removeItem(EXPIRES_AT_KEY);
}

async function ensureAccessToken() {
  var token = await accessToken();
  var expiresAt = Number(localStorage.getItem(EXPIRES_AT_KEY) || 0);
  if (token && (!expiresAt || Date.now() < expiresAt - 60000)) return token;
  var brokerRefresh = await secureItem(BROKER_REFRESH_KEY);
  if (!brokerRefresh) return token;
  await saveTokenBundle(await Pebble.oauth.refresh('ticktick', brokerRefresh));
  return accessToken();
}

async function requestJson(method, path, body) {
  var token = await ensureAccessToken();
  if (!token) return { ok: false, status: 401, json: null, text: '' };
  var headers = { 'Authorization': 'Bearer ' + token };
  if (body !== undefined && body !== null) headers['Content-Type'] = 'application/json';
  try {
    var response = await fetch(API_BASE + path, {
      method: method,
      headers: headers,
      body: body === undefined || body === null ? undefined : JSON.stringify(body),
    });
    var text = await response.text();
    var json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch (e) {
      // Keep the response text for a useful generic HTTP error below.
    }
    return {
      ok: response.status >= 200 && response.status < 300,
      status: response.status,
      json: json,
      text: text || '',
    };
  } catch (e) {
    console.error('TickTick ' + method + ' ' + path + ' failed: ' + e);
    return { ok: false, status: 0, json: null, text: '' };
  }
}

function respondWithHttpError(response, respond, operation) {
  console.error('Failed to ' + operation + ': HTTP ' + response.status + ' ' + response.text);
  if (response.status === 401 || response.status === 403) {
    respond.error('AUTH_REQUIRED', 'TickTick authorization is missing or expired.');
    return;
  }
  if (response.status === 429) {
    respond.error('RATE_LIMITED', 'TickTick rate-limited ' + operation + '.');
    return;
  }
  if (response.status === 0) {
    respond.error('PLUGIN_UNAVAILABLE', 'Could not reach TickTick.');
    return;
  }
  respond.error(
    'UNKNOWN',
    'TickTick failed to ' + operation + ' (HTTP ' + response.status + ').'
  );
}

function shortText(text) {
  return { shortText: { text: text } };
}

function longText(text) {
  return { longText: { text: text } };
}

function tickTickProjectUrl(projectId) {
  return 'https://ticktick.com/webapp/#p/' + projectId + '/tasks';
}

function tickTickTaskUrl(projectId, taskId) {
  return tickTickProjectUrl(projectId) + '/' + taskId;
}

var REMINDER_SOURCES = ['reminders/list', 'reminders/configured'];

function booleanReading(value) {
  return {
    boolean: { value: !!value },
    shortText: { text: value ? 'Connected' : 'Not connected' },
  };
}

async function respondConfigured(respond) {
  var configured = (await accessToken()).length > 0;
  respond.data({
    validUntilMs: Date.now() + 60 * 1000,
    instances: [{
      instanceId: 'ticktick',
      properties: { name: shortText('TickTick'), configured: booleanReading(configured) },
    }],
  });
}

Pebble.registerSourceHandler(async function (request, respond) {
  if (request.category === 'reminders' && request.item === 'configured') {
    await respondConfigured(respond);
    return;
  }
  if (request.category !== 'reminders' || request.item !== 'list') {
    respond.error('INVALID_REQUEST', 'Unsupported TickTick source.');
    return;
  }
  if (!(await accessToken())) {
    respond.error('AUTH_REQUIRED', 'Connect TickTick in the plugin gear page.');
    return;
  }

  var response = await requestJson('GET', '/project');
  if (!response.ok) {
    respondWithHttpError(response, respond, 'load lists');
    return;
  }
  if (!Array.isArray(response.json)) {
    respond.error('UNKNOWN', 'TickTick returned a malformed project list.');
    return;
  }

  var instances = response.json.filter(function (project) {
    return project && project.closed !== true &&
      typeof project.id === 'string' && project.id.length > 0 &&
      typeof project.name === 'string' && project.name.length > 0;
  }).map(function (project) {
    return {
      instanceId: project.id,
      properties: {
        name: shortText(project.name),
        destination: shortText('TickTick reminder list'),
        url: longText(tickTickProjectUrl(project.id)),
      },
    };
  });

  respond.data({
    validUntilMs: Date.now() + PROJECT_REFRESH_MS,
    instances: instances,
  });
});

/**
 * Parse the absolute RFC3339 subset accepted by this action. `Date` alone is deliberately not
 * validation: engines accept timezone-less input and normalize impossible dates such as Feb 30.
 */
function tickTickDate(value) {
  var match = /^(\d{4})-(\d{2})-(\d{2})[Tt](\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(?:[Zz]|[+-](\d{2}):(\d{2}))$/.exec(value);
  if (!match) return null;

  var year = Number(match[1]);
  var month = Number(match[2]);
  var day = Number(match[3]);
  var hour = Number(match[4]);
  var minute = Number(match[5]);
  var second = Number(match[6]);
  var offsetHour = match[7] === undefined ? 0 : Number(match[7]);
  var offsetMinute = match[8] === undefined ? 0 : Number(match[8]);
  var leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  var days = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (
    month < 1 || month > 12 ||
    day < 1 || day > days[month - 1] ||
    hour > 23 || minute > 59 || second > 59 ||
    offsetHour > 23 || offsetMinute > 59
  ) {
    return null;
  }

  var date = new Date(value);
  if (isNaN(date.getTime())) return null;

  function pad(number, length) {
    return String(number).padStart(length || 2, '0');
  }

  return pad(date.getUTCFullYear(), 4) + '-' +
    pad(date.getUTCMonth() + 1) + '-' +
    pad(date.getUTCDate()) + 'T' +
    pad(date.getUTCHours()) + ':' +
    pad(date.getUTCMinutes()) + ':' +
    pad(date.getUTCSeconds()) + '+0000';
}

// Kotlin Duration.toIsoString(), used by the old TickTick integration, expresses
// days as hours and keeps millisecond precision (for example PT30M or PT1.5S).
function durationToIso(milliseconds) {
  var remaining = Math.floor(milliseconds);
  var hours = Math.floor(remaining / 3600000);
  remaining -= hours * 3600000;
  var minutes = Math.floor(remaining / 60000);
  remaining -= minutes * 60000;

  var result = 'PT';
  if (hours) result += hours + 'H';
  if (minutes) result += minutes + 'M';
  if (remaining) {
    var seconds = (remaining / 1000).toFixed(3).replace(/0+$/, '').replace(/\.$/, '');
    result += seconds + 'S';
  }
  return result === 'PT' ? 'PT0S' : result;
}

Pebble.registerActionHandler(async function (request, respond) {
  if (request.action !== 'create_reminder') {
    respond.error('INVALID_REQUEST', 'Unsupported TickTick action.');
    return;
  }
  if (!(await accessToken())) {
    respond.error('AUTH_REQUIRED', 'Connect TickTick in the plugin gear page.');
    return;
  }

  var args = request.args || {};
  var title = typeof args.title === 'string' ? args.title.trim() : '';
  if (!title) {
    respond.error('INVALID_ARGS', 'title must not be empty.');
    return;
  }

  var task = { title: title };
  if (Object.prototype.hasOwnProperty.call(args, 'instanceId')) {
    if (typeof args.instanceId !== 'string' || !args.instanceId.trim()) {
      respond.error('INVALID_ARGS', 'instanceId must be a non-empty TickTick project ID.');
      return;
    }
    task.projectId = args.instanceId.trim();
  }

  if (Object.prototype.hasOwnProperty.call(args, 'due_iso')) {
    if (typeof args.due_iso !== 'string' || !args.due_iso) {
      respond.error('INVALID_ARGS', 'due_iso must be an absolute RFC3339 date-time.');
      return;
    }
    var dueDate = tickTickDate(args.due_iso);
    if (!dueDate) {
      respond.error(
        'INVALID_ARGS',
        'due_iso must be a valid absolute RFC3339 date-time with Z or a numeric offset.'
      );
      return;
    }
    if (typeof args.timezone !== 'string' || !args.timezone.trim()) {
      respond.error('INVALID_ARGS', 'timezone is required with due_iso.');
      return;
    }

    task.dueDate = dueDate;
    task.timeZone = args.timezone.trim();
    task.isAllDay = false;
    task.reminders = ['TRIGGER:PT0S'];

    if (args.notify_before_ms !== undefined && args.notify_before_ms !== null) {
      if (typeof args.notify_before_ms !== 'number' ||
          !isFinite(args.notify_before_ms) ||
          Math.floor(args.notify_before_ms) !== args.notify_before_ms ||
          args.notify_before_ms < 0) {
        respond.error('INVALID_ARGS', 'notify_before_ms must be a non-negative integer.');
        return;
      }
      if (args.notify_before_ms > 0) {
        task.reminders.push('TRIGGER:-' + durationToIso(args.notify_before_ms));
      }
    }
  }

  var response = await requestJson('POST', '/task', task);
  if (!response.ok) {
    respondWithHttpError(response, respond, 'create the reminder');
    return;
  }
  if (!response.json || typeof response.json.id !== 'string' || !response.json.id) {
    respond.error('UNKNOWN', 'TickTick created no identifiable task.');
    return;
  }

  var createdProjectId = typeof response.json.projectId === 'string' && response.json.projectId
    ? response.json.projectId
    : task.projectId;
  var result = { text: 'Created “' + title + '” in TickTick.' };
  if (createdProjectId) {
    result.url = tickTickTaskUrl(createdProjectId, response.json.id);
  }
  respond.ok(result);
});

Pebble.registerConfigHandler(async function (message, respond) {
  switch (message && message.type) {
    case 'status':
      respond({ configured: (await accessToken()).length > 0 });
      return;
    case 'connect':
      try {
        await saveTokenBundle(await Pebble.oauth.authorize('ticktick'));
        var validation = await requestJson('GET', '/project');
        if (!validation.ok) {
          await clearTokens();
          respond({ error: 'TickTick did not accept the new authorization.' });
          return;
        }
        Pebble.refreshSources(REMINDER_SOURCES);
        respond({ configured: true });
      } catch (error) {
        respond({ error: error && error.message ? error.message : 'Could not connect TickTick.' });
      }
      return;
    case 'unlink':
      await clearTokens();
      Pebble.refreshSources(REMINDER_SOURCES);
      respond({ configured: false });
      return;
    default:
      respond({ error: 'Unknown message type.' });
  }
});
