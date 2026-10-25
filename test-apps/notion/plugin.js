// Notion note destination plugin.
//
// CoreApp's trusted HostedOAuth bridge performs sign-in; credentials are kept in
// Pebble.secureStorage. This uses Notion's additive page
// and block APIs only: create mode creates one child page per request, while append mode inserts a
// new paragraph at the configured start/end position without retrieving or replacing page content.

var STORE_KEY = 'notion';
var SECRETS_KEY = 'notion_auth';
var API_ROOT = 'https://api.notion.com/v1';
var NOTION_VERSION = '2026-03-11';
var DESTINATION_SOURCES = ['notes/destination', 'notes/configured'];
var RECENT_PAGE_LIMIT = 10;
var SEARCH_PAGE_LIMIT = 20;
var MAX_NOTE_CHARS = 100000;
var MAX_RICH_TEXT_CHARS = 1900;
var MAX_RICH_TEXT_PARTS = 100;
var MAX_TITLE_CHARS = 120;
var MAX_REQUEST_BYTES = 500000;
var COMPLETED_REPLAY_MS = 5 * 60 * 1000;
var PENDING_REVIEW_DELAY_MS = 2 * 60 * 1000;
var MAX_COMPLETED_RECEIPTS = 20;

function defaultDestination() {
  return { mode: 'create', pageId: null, pageTitle: null, pageUrl: null, placement: 'bottom' };
}

function normalizeDestination(value, legacy) {
  var source = value && typeof value === 'object' ? value : null;
  var pageId = source && typeof source.pageId === 'string' ? source.pageId.trim() : '';
  var pageTitle = source && typeof source.pageTitle === 'string' ? source.pageTitle.trim() : '';
  var pageUrl = source && typeof source.pageUrl === 'string' ? source.pageUrl : null;
  var mode = source && source.mode === 'append' ? 'append' : 'create';
  var placement = source && source.placement === 'top' ? 'top' : 'bottom';

  // Version 0.1 stored a single append target at the state root. Preserve that choice on upgrade.
  if (!source && legacy && typeof legacy.pageId === 'string' && legacy.pageId.trim()) {
    pageId = legacy.pageId.trim();
    pageTitle = typeof legacy.pageTitle === 'string' ? legacy.pageTitle.trim() : '';
    pageUrl = typeof legacy.pageUrl === 'string' ? legacy.pageUrl : null;
    mode = 'append';
    placement = 'bottom';
  }
  return {
    mode: mode,
    pageId: pageId || null,
    pageTitle: pageTitle || (pageId ? 'Selected page' : null),
    pageUrl: pageUrl,
    placement: placement,
  };
}

function emptyState() {
  return {
    token: null,
    brokerRefresh: null,
    expiresAt: null,
    authGeneration: null,
    destinationGeneration: null,
    destination: defaultDestination(),
    mutation: null,
    completedReceipts: [],
  };
}

function durableReceiptResult(kind) {
  return {
    text: kind === 'append' ? 'Added to Notion' : 'Created in Notion',
    refreshed: DESTINATION_SOURCES.slice(),
  };
}

function normalizeMutation(value) {
  if (!value || typeof value !== 'object') return null;
  var status = value.status === 'completed' ? 'completed' : value.status === 'pending' ? 'pending' : null;
  var kind = value.kind === 'append' ? 'append' : 'create';
  var id = typeof value.id === 'string' ? value.id : '';
  var fingerprint = typeof value.fingerprint === 'string' ? value.fingerprint : '';
  var startedAtMs = Number(value.startedAtMs);
  if (!status || !id || !fingerprint || !isFinite(startedAtMs) || startedAtMs <= 0) return null;
  var result = null;
  var completedAtMs = Number(value.completedAtMs);
  if (status === 'completed') {
    if (!isFinite(completedAtMs) || completedAtMs < startedAtMs || !value.result ||
        typeof value.result.text !== 'string') return null;
    // The live action response can include the created title and deep link, but a durable retry
    // receipt needs only an opaque fingerprint and a generic acknowledgement. In particular, a
    // Notion page URL can contain the note title in its slug, so do not persist it either.
    result = durableReceiptResult(kind);
  }
  return {
    status: status,
    id: id,
    fingerprint: fingerprint,
    kind: kind,
    pageId: status === 'pending' && typeof value.pageId === 'string' ? value.pageId : null,
    pageTitle: status === 'pending' && typeof value.pageTitle === 'string' ?
      value.pageTitle : 'Notion',
    pageUrl: status === 'pending' && typeof value.pageUrl === 'string' ? value.pageUrl : null,
    startedAtMs: startedAtMs,
    completedAtMs: status === 'completed' ? completedAtMs : null,
    result: result,
  };
}

function normalizeCompletedReceipts(value, nowMs) {
  if (!Array.isArray(value)) return [];
  var now = isFinite(Number(nowMs)) ? Number(nowMs) : Date.now();
  var seen = {};
  return value.map(normalizeMutation).filter(function (receipt) {
    if (!receipt || receipt.status !== 'completed' || seen[receipt.fingerprint] ||
        now - receipt.completedAtMs > COMPLETED_REPLAY_MS) return false;
    seen[receipt.fingerprint] = true;
    return true;
  }).sort(function (left, right) {
    return right.completedAtMs - left.completedAtMs;
  }).slice(0, MAX_COMPLETED_RECEIPTS);
}

// Credentials are read once per session (see loadSecrets) so that state() can stay synchronous.
var secrets = { token: null, brokerRefresh: null };

async function loadSecrets() {
  try { secrets = JSON.parse(await Pebble.secureStorage.getItem(SECRETS_KEY)) || secrets; }
  catch (error) {}
}

async function saveSecrets(changes) {
  secrets = {
    token: typeof changes.token === 'string' && changes.token ? changes.token : null,
    brokerRefresh: typeof changes.brokerRefresh === 'string' && changes.brokerRefresh ?
      changes.brokerRefresh : null,
  };
  if (secrets.token || secrets.brokerRefresh) {
    await Pebble.secureStorage.setItem(SECRETS_KEY, JSON.stringify(secrets));
  } else {
    await Pebble.secureStorage.removeItem(SECRETS_KEY);
  }
}

function persist(value) {
  var stored = Object.assign({}, value);
  delete stored.token;
  delete stored.brokerRefresh;
  localStorage.setItem(STORE_KEY, JSON.stringify(stored));
}

function state() {
  var raw = null;
  var saved;
  try {
    raw = localStorage.getItem(STORE_KEY);
    saved = JSON.parse(raw) || {};
  } catch (error) { saved = {}; }
  var storedReceipts = JSON.stringify(Array.isArray(saved.completedReceipts) ? saved.completedReceipts : []);
  var result = emptyState();
  result.token = secrets.token;
  result.brokerRefresh = secrets.brokerRefresh;
  result.expiresAt = isFinite(Number(saved.expiresAt)) && Number(saved.expiresAt) > 0 ?
    Number(saved.expiresAt) : null;
  result.authGeneration = typeof saved.authGeneration === 'string' ? saved.authGeneration : null;
  result.destinationGeneration = typeof saved.destinationGeneration === 'string' ?
    saved.destinationGeneration : null;
  result.destination = normalizeDestination(saved.destination, saved);
  result.mutation = normalizeMutation(saved.mutation);
  result.completedReceipts = normalizeCompletedReceipts(saved.completedReceipts, Date.now());
  // Accept the first v0.3 pre-release shape, which stored one completed receipt in mutation.
  var migratedCompletedMutation = !!(result.mutation && result.mutation.status === 'completed');
  if (result.mutation && result.mutation.status === 'completed') {
    result.completedReceipts = normalizeCompletedReceipts(
      [result.mutation].concat(result.completedReceipts)
    );
    result.mutation = null;
  }
  // Sanitize legacy receipt results and prune expired receipts during ordinary reads. Write the
  // normalized state directly: calling saveState() here would recurse back through state().
  if (raw !== null && (migratedCompletedMutation ||
      storedReceipts !== JSON.stringify(result.completedReceipts))) {
    persist(result);
  }
  return result;
}

function saveState(changes) {
  var next = state();
  Object.keys(changes).forEach(function (key) { next[key] = changes[key]; });
  next.destination = normalizeDestination(next.destination, null);
  next.mutation = normalizeMutation(next.mutation);
  next.completedReceipts = normalizeCompletedReceipts(next.completedReceipts);
  persist(next);
  return next;
}

function tokenChanges(bundle) {
  var token = String(bundle && bundle.access_token || '').trim();
  if (!token) throw new Error('Notion returned no access token.');
  var expiresIn = Number(bundle.expires_in || 0);
  return {
    token: token,
    brokerRefresh: String(bundle.broker_refresh || '').trim() || null,
    expiresAt: isFinite(expiresIn) && expiresIn > 0 ? Date.now() + expiresIn * 1000 : null,
  };
}

async function currentAuthState() {
  var current = stateWithAuthGeneration();
  if (!current.token || !current.expiresAt || Date.now() < current.expiresAt - 60000) return current;
  if (!current.brokerRefresh) return current;
  var refreshed = tokenChanges(await Pebble.oauth.refresh('notion', current.brokerRefresh));
  if (!refreshed.brokerRefresh) refreshed.brokerRefresh = current.brokerRefresh;
  await saveSecrets(refreshed);
  return saveState(refreshed);
}

function randomUuid() {
  var bytes = new Uint8Array(16);
  try {
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) crypto.getRandomValues(bytes);
    else throw new Error('No secure random source');
  } catch (error) {
    for (var i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256);
  }
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  var hex = [];
  for (var index = 0; index < bytes.length; index++) {
    var part = bytes[index].toString(16);
    hex.push(part.length === 1 ? '0' + part : part);
  }
  return hex.slice(0, 4).join('') + '-' + hex.slice(4, 6).join('') + '-' +
    hex.slice(6, 8).join('') + '-' + hex.slice(8, 10).join('') + '-' + hex.slice(10).join('');
}

function stateWithAuthGeneration() {
  var current = state();
  var changes = {};
  if (current.token && !current.authGeneration) changes.authGeneration = randomUuid();
  if (current.token && current.destination.pageId && !current.destinationGeneration) {
    changes.destinationGeneration = randomUuid();
  }
  if (Object.keys(changes).length) current = saveState(changes);
  return current;
}

async function clearState() {
  var previous = state();
  await saveSecrets({});
  var cleared = emptyState();
  // Retain only a fresh, non-secret tombstone so late callbacks cannot restore credentials.
  cleared.authGeneration = randomUuid();
  // A pending fingerprint contains no credential or note text. Preserve it across disconnect so
  // reconnecting cannot silently bypass an ambiguous-write review.
  if (previous.mutation && previous.mutation.status === 'pending') {
    cleared.mutation = previous.mutation;
  }
  cleared.completedReceipts = previous.completedReceipts;
  persist(cleared);
  return cleared;
}

function generationIsActive(expectedGeneration, requireToken) {
  var current = state();
  return current.authGeneration === expectedGeneration && (!requireToken || !!current.token);
}

function staleAuthenticationResult() {
  return {
    ok: false,
    status: 401,
    stale: true,
    error: 'Notion was disconnected or reconfigured while the request was running.',
  };
}

function destinationGenerationIsActive(expectedGeneration) {
  return state().destinationGeneration === expectedGeneration;
}

function utf8ByteLength(value) {
  var text = String(value);
  var bytes = 0;
  for (var index = 0; index < text.length; index++) {
    var code = text.charCodeAt(index);
    if (code <= 0x7f) bytes++;
    else if (code <= 0x7ff) bytes += 2;
    else if (code >= 0xd800 && code <= 0xdbff && index + 1 < text.length &&
        text.charCodeAt(index + 1) >= 0xdc00 && text.charCodeAt(index + 1) <= 0xdfff) {
      bytes += 4;
      index++;
    } else bytes += 3;
  }
  return bytes;
}

function serializedRequestBody(body) {
  var json;
  try { json = JSON.stringify(body); }
  catch (error) {
    return { ok: false, status: 400, error: 'The Notion request could not be encoded.' };
  }
  var bytes = utf8ByteLength(json);
  if (bytes > MAX_REQUEST_BYTES) {
    return {
      ok: false,
      status: 413,
      error: 'This note is too large for Notion’s 500 KB request limit.',
      bytes: bytes,
    };
  }
  return { ok: true, json: json, bytes: bytes };
}

function hash32(text, seed) {
  var hash = seed >>> 0;
  for (var index = 0; index < text.length; index++) {
    hash ^= text.charCodeAt(index);
    // FNV-1a multiplication expressed with shifts for engines without BigInt.
    hash = (hash + ((hash << 1) >>> 0) + ((hash << 4) >>> 0) +
      ((hash << 7) >>> 0) + ((hash << 8) >>> 0) + ((hash << 24) >>> 0)) >>> 0;
  }
  return ('00000000' + hash.toString(16)).slice(-8);
}

function mutationFingerprint(method, path, serializedBody) {
  var value = method + '\n' + path + '\n' + serializedBody;
  return 'v1-' + value.length + '-' +
    hash32(value, 0x811c9dc5) + hash32(value, 0x9e3779b9) +
    hash32(value, 0x85ebca6b) + hash32(value, 0xc2b2ae35);
}

/** Resolve every request, including transport failures, to one result record. */
async function http(method, path, token, body) {
  var encoded = body === undefined || body === null ? null : serializedRequestBody(body);
  if (encoded && !encoded.ok) return encoded;
  var headers = { 'Authorization': 'Bearer ' + token, 'Notion-Version': NOTION_VERSION };
  if (body !== undefined && body !== null) headers['Content-Type'] = 'application/json';
  try {
    var response = await fetch(API_ROOT + path, {
      method: method,
      headers: headers,
      body: encoded ? encoded.json : undefined,
    });
    var text = await response.text();
    var json = null;
    try { json = JSON.parse(text); } catch (error) { /* non-JSON response */ }
    return {
      ok: response.status >= 200 && response.status < 300,
      status: response.status,
      json: json,
      error: json && json.message ? String(json.message) : ('Notion returned HTTP ' + response.status + '.'),
    };
  } catch (error) {
    console.error('Notion ' + method + ' ' + path + ' failed: ' + error);
    return { ok: false, status: 0, json: null, error: 'Could not reach Notion.' };
  }
}

function richTextPlain(parts) {
  if (!Array.isArray(parts)) return '';
  return parts.map(function (part) {
    if (part && typeof part.plain_text === 'string') return part.plain_text;
    if (part && part.text && typeof part.text.content === 'string') return part.text.content;
    return '';
  }).join('');
}

function pageTitle(page) {
  var properties = page && page.properties ? page.properties : {};
  var names = Object.keys(properties);
  for (var i = 0; i < names.length; i++) {
    var property = properties[names[i]];
    if (!property || property.type !== 'title' || !Array.isArray(property.title)) continue;
    var title = richTextPlain(property.title).trim();
    if (title) return title;
  }
  var direct = richTextPlain(page && page.title).trim();
  return direct || 'Untitled';
}

function pageIsAvailable(page) {
  return page && page.object === 'page' && typeof page.id === 'string' &&
    page.in_trash !== true && page.archived !== true && page.is_archived !== true;
}

function pageSummary(page) {
  return {
    id: page.id,
    title: pageTitle(page),
    url: typeof page.url === 'string' ? page.url : null,
    lastEditedTime: typeof page.last_edited_time === 'string' ? page.last_edited_time : null,
  };
}

/** Search is globally sorted newest-first; page through filtered results until ten usable pages. */
async function listPages(token, query) {
  var pages = [];
  var cursor = null;
  var seenCursors = {};
  for (var requestIndex = 0; requestIndex < SEARCH_PAGE_LIMIT && pages.length < RECENT_PAGE_LIMIT; requestIndex++) {
    var body = {
      filter: { property: 'object', value: 'page' },
      sort: { timestamp: 'last_edited_time', direction: 'descending' },
      page_size: RECENT_PAGE_LIMIT,
    };
    if (query && String(query).trim()) body.query = String(query).trim();
    if (cursor) body.start_cursor = cursor;
    var result = await http('POST', '/search', token, body);
    if (!result.ok) return result;
    var response = result.json;
    if (!response || !Array.isArray(response.results)) {
      return { ok: false, status: 500, error: 'Notion returned an invalid page list.' };
    }
    response.results.forEach(function (page) {
      if (pages.length < RECENT_PAGE_LIMIT && pageIsAvailable(page)) pages.push(pageSummary(page));
    });
    if (response.has_more !== true) break;
    var nextCursor = typeof response.next_cursor === 'string' ? response.next_cursor : '';
    if (!nextCursor || seenCursors[nextCursor]) {
      return { ok: false, status: 500, error: 'Notion returned invalid page-list paging data.' };
    }
    seenCursors[nextCursor] = true;
    cursor = nextCursor;
  }
  pages.sort(function (left, right) {
    var rightTime = Date.parse(right.lastEditedTime || '') || 0;
    var leftTime = Date.parse(left.lastEditedTime || '') || 0;
    if (rightTime !== leftTime) return rightTime - leftTime;
    return left.id < right.id ? -1 : left.id > right.id ? 1 : 0;
  });
  return { ok: true, pages: pages.slice(0, RECENT_PAGE_LIMIT) };
}

/** Confirm the configured parent/target is freshly accessible immediately before a write. */
async function verifyPage(token, pageId) {
  var result = await http('GET', '/pages/' + encodeURIComponent(pageId), token);
  if (!result.ok) return result;
  if (!pageIsAvailable(result.json)) {
    return { ok: false, status: 404, error: 'The selected Notion page is unavailable.' };
  }
  return { ok: true, page: result.json, summary: pageSummary(result.json) };
}

function safeSliceEnd(text, start, wantedEnd) {
  var end = Math.min(text.length, wantedEnd);
  if (end > start && end < text.length) {
    var previous = text.charCodeAt(end - 1);
    var next = text.charCodeAt(end);
    if (previous >= 0xd800 && previous <= 0xdbff && next >= 0xdc00 && next <= 0xdfff) end--;
  }
  return end;
}

function richText(text) {
  var value = String(text);
  var parts = [];
  for (var start = 0; start < value.length;) {
    if (parts.length >= MAX_RICH_TEXT_PARTS) throw new Error('The note is too long for one Notion block.');
    var end = safeSliceEnd(value, start, start + MAX_RICH_TEXT_CHARS);
    parts.push({ type: 'text', text: { content: value.substring(start, end) } });
    start = end;
  }
  return parts;
}

function paragraphBlock(text) {
  return { object: 'block', type: 'paragraph', paragraph: { rich_text: richText(text) } };
}

function splitNewPageText(text) {
  var value = String(text).trim();
  var lineEnd = value.indexOf('\n');
  var titleLine = lineEnd === -1 ? value : value.substring(0, lineEnd);
  var titleEnd = safeSliceEnd(titleLine, 0, MAX_TITLE_CHARS);
  var title = titleLine.substring(0, titleEnd).trim() || 'New note';
  var bodyStart = titleEnd;
  if (lineEnd !== -1 && titleEnd === lineEnd) bodyStart++;
  var body = value.substring(bodyStart);
  return { title: title, body: body };
}

function prepareCreateChildPage(parentPageId, text) {
  var prepared = splitNewPageText(text);
  var body = {
    parent: { type: 'page_id', page_id: parentPageId },
    properties: { title: { type: 'title', title: richText(prepared.title) } },
  };
  if (prepared.body) body.children = [paragraphBlock(prepared.body)];
  return { body: body, title: prepared.title };
}

async function createChildPage(token, prepared) {
  var result = await http('POST', '/pages', token, prepared.body);
  if (!result.ok) return result;
  if (!pageIsAvailable(result.json)) {
    return { ok: false, status: 500, error: 'Notion did not return the created page.' };
  }
  return { ok: true, page: result.json, title: prepared.title };
}

function prepareAppendParagraph(text, placement) {
  return {
    position: { type: placement === 'top' ? 'start' : 'end' },
    children: [paragraphBlock(text)],
  };
}

async function appendParagraph(token, pageId, body) {
  var result = await http(
    'PATCH',
    '/blocks/' + encodeURIComponent(pageId) + '/children',
    token,
    body
  );
  if (!result.ok) return result;
  var created = result.json && Array.isArray(result.json.results) && result.json.results[0];
  if (!created || typeof created.id !== 'string') {
    return { ok: false, status: 500, error: 'Notion did not return the appended block.' };
  }
  return { ok: true, block: created };
}

function pluginError(result) {
  console.error('Notion request failed (HTTP ' + result.status + '): ' + result.error);
  if (result.status === 401 || result.status === 403) {
    return { code: 'AUTH_REQUIRED', message: result.error || 'Notion authorization failed.' };
  }
  if (result.status === 429 || result.status === 529) {
    return { code: 'RATE_LIMITED', message: result.error || 'Notion rate limit reached.' };
  }
  if (result.status === 413) {
    return { code: 'INVALID_ARGS', message: result.error || 'The Notion request is too large.' };
  }
  return { code: 'PLUGIN_UNAVAILABLE', message: result.error || 'Notion is unavailable.' };
}

function shortText(text) {
  return { shortText: { text: String(text) } };
}

function textReading(text) {
  return { shortText: { text: String(text) }, longText: { text: String(text) } };
}

function booleanReading(value) {
  return {
    boolean: { value: !!value },
    shortText: { text: value ? 'Connected' : 'Not connected' },
  };
}

function compactNotionId(value) {
  var compact = String(value || '').replace(/-/g, '');
  return /^[a-f0-9]{32}$/i.test(compact) ? compact : '';
}

function safeNotionPageUrl(page, fallbackId) {
  var pageUrl = page && typeof page.url === 'string' ? page.url.replace(/#.*/, '') : '';
  if (/^https:\/\/(?:www\.)?notion\.(?:so|site)\//i.test(pageUrl)) return pageUrl;
  var compact = compactNotionId(fallbackId);
  return compact ? 'https://www.notion.so/' + compact : null;
}

function notionBlockUrl(page, pageId, blockId) {
  var base = safeNotionPageUrl(page, pageId);
  var compactBlock = compactNotionId(blockId);
  return base && compactBlock ? base + '#' + compactBlock : base;
}

function destinationLabel(destination) {
  if (!destination.pageId) return 'Choose a page in Notion settings';
  if (destination.mode === 'append') {
    return 'Append at ' + destination.placement + ' of ' + destination.pageTitle;
  }
  return 'New pages under ' + destination.pageTitle;
}

function mutationNotice(mutation) {
  if (!mutation || mutation.status !== 'pending') return null;
  var reviewAfterMs = mutation.startedAtMs + PENDING_REVIEW_DELAY_MS;
  return {
    id: mutation.id,
    kind: mutation.kind,
    pageTitle: mutation.pageTitle,
    startedAtMs: mutation.startedAtMs,
    reviewAfterMs: reviewAfterMs,
    canAcknowledge: Date.now() >= reviewAfterMs,
  };
}

function pendingMutationMessage(mutation) {
  var destination = mutation && mutation.pageTitle ? ' on “' + mutation.pageTitle + '”' : '';
  return 'Notion may already have saved the previous note' + destination +
    '. Check Notion first, then open plugin settings and acknowledge the pending save before retrying.';
}

/**
 * Atomically claim one mutation in local settings before issuing the network write. Completed
 * records are replayed briefly so a host timeout after a successful response cannot duplicate it.
 */
function beginMutation(expectedGeneration, expectedDestinationGeneration, fingerprint, kind, destination) {
  var current = state();
  if (current.authGeneration !== expectedGeneration || !current.token) {
    return { type: 'error', result: staleAuthenticationResult() };
  }
  if (current.destinationGeneration !== expectedDestinationGeneration) {
    return {
      type: 'blocked',
      message: 'The Notion destination changed while this note was being prepared. Review it and try again.',
    };
  }
  var now = Date.now();
  var receipts = current.completedReceipts.filter(function (receipt) {
    return now - receipt.completedAtMs <= COMPLETED_REPLAY_MS;
  });
  for (var receiptIndex = 0; receiptIndex < receipts.length; receiptIndex++) {
    if (receipts[receiptIndex].fingerprint === fingerprint) {
      return { type: 'replay', result: receipts[receiptIndex].result };
    }
  }
  var existing = current.mutation;
  if (existing && existing.status === 'pending') {
    return {
      type: 'blocked',
      message: pendingMutationMessage(existing),
      identical: existing.fingerprint === fingerprint,
    };
  }
  var mutation = {
    status: 'pending',
    id: randomUuid(),
    fingerprint: fingerprint,
    kind: kind,
    pageId: destination.pageId,
    pageTitle: destination.pageTitle,
    pageUrl: destination.pageUrl,
    startedAtMs: now,
    completedAtMs: null,
    result: null,
  };
  saveState({ mutation: mutation, completedReceipts: receipts });
  return { type: 'proceed', mutation: mutation };
}

function completeMutation(mutationId, expectedGeneration, result) {
  var current = state();
  if (current.authGeneration !== expectedGeneration || !current.token ||
      !current.mutation || current.mutation.id !== mutationId ||
      current.mutation.status !== 'pending') return false;
  current.mutation.status = 'completed';
  current.mutation.completedAtMs = Date.now();
  current.mutation.result = result;
  saveState({
    mutation: null,
    completedReceipts: [current.mutation].concat(current.completedReceipts),
  });
  return true;
}

function clearMutationAfterDefinitiveFailure(mutationId, expectedGeneration) {
  var current = state();
  if (current.authGeneration !== expectedGeneration || !current.mutation ||
      current.mutation.id !== mutationId) return false;
  saveState({ mutation: null });
  return true;
}

function failureCouldHaveMutated(result) {
  return result.status === 0 || result.status >= 500;
}

Pebble.registerSourceHandler(async function (request, respond) {
  await loadSecrets();
  var isConfiguredSource = request.category === 'notes' && request.item === 'configured';
  if (!isConfiguredSource && (request.category !== 'notes' || request.item !== 'destination')) {
    respond.error('PLUGIN_UNAVAILABLE', 'Unknown source ' + request.category + '/' + request.item + '.');
    return;
  }
  var current = state();
  var destination = current.destination;
  var configured = !!current.token && !!destination.pageId;
  if (isConfiguredSource) {
    respond.data({
      validUntilMs: Date.now() + 60 * 1000,
      instances: [{
        instanceId: 'notion',
        properties: { name: shortText('Notion'), configured: booleanReading(configured) },
      }],
    });
    return;
  }
  var properties = {
    name: shortText('Notion'),
    configured: booleanReading(configured),
    page: textReading(destinationLabel(destination)),
  };
  var destinationUrl = safeNotionPageUrl({ url: destination.pageUrl }, destination.pageId);
  if (destinationUrl) properties.url = { longText: { text: destinationUrl } };
  respond.data({
    validUntilMs: Date.now() + 60 * 1000,
    instances: [{ instanceId: 'notion', properties: properties }],
  });
});

Pebble.registerActionHandler(async function (request, respond) {
  await loadSecrets();
  if (request.action !== 'create_note') {
    respond.error('PLUGIN_UNAVAILABLE', 'Unknown action ' + request.action + '.');
    return;
  }
  var args = request.args || {};
  if (args.instanceId !== undefined && args.instanceId !== 'notion') {
    respond.error('INVALID_ARGS', 'Unknown notes destination ' + args.instanceId + '.');
    return;
  }
  var text = typeof args.text === 'string' ? args.text : '';
  if (!text.trim()) {
    respond.error('INVALID_ARGS', 'The note text cannot be empty.');
    return;
  }
  if (text.length > MAX_NOTE_CHARS) {
    respond.error('INVALID_ARGS', 'The note is too long for this Notion test integration.');
    return;
  }

  var current;
  try { current = await currentAuthState(); }
  catch (error) {
    respond.error('AUTH_REQUIRED', error.message || 'Reconnect Notion.');
    return;
  }
  if (!current.token) {
    respond.error('AUTH_REQUIRED', 'Connect Notion in the plugin gear page.');
    return;
  }
  var destination = current.destination;
  if (!destination.pageId) {
    respond.error('AUTH_REQUIRED', 'Choose a Notion destination page first.');
    return;
  }
  var expectedGeneration = current.authGeneration;
  var expectedDestinationGeneration = current.destinationGeneration;

  var verified = await verifyPage(current.token, destination.pageId);
  if (!verified.ok) {
    var verifyError = pluginError(verified);
    respond.error(verifyError.code, verifyError.message);
    return;
  }
  if (!generationIsActive(expectedGeneration, true)) {
    var staleVerify = pluginError(staleAuthenticationResult());
    respond.error(staleVerify.code, staleVerify.message);
    return;
  }
  if (!destinationGenerationIsActive(expectedDestinationGeneration)) {
    respond.error(
      'PLUGIN_UNAVAILABLE',
      'The Notion destination changed while this note was being prepared. Review it and try again.'
    );
    return;
  }
  if (verified.summary.title !== destination.pageTitle || verified.summary.url !== destination.pageUrl) {
    destination.pageTitle = verified.summary.title;
    destination.pageUrl = verified.summary.url;
    saveState({ destination: destination });
  }

  if (destination.mode === 'create') {
    var preparedCreate;
    try { preparedCreate = prepareCreateChildPage(destination.pageId, text); }
    catch (error) {
      respond.error('INVALID_ARGS', error.message || 'The note cannot be encoded for Notion.');
      return;
    }
    var createEncoding = serializedRequestBody(preparedCreate.body);
    if (!createEncoding.ok) {
      var createEncodingError = pluginError(createEncoding);
      respond.error(createEncodingError.code, createEncodingError.message);
      return;
    }
    var createFingerprint = mutationFingerprint('POST', '/pages', createEncoding.json);
    var createClaim = beginMutation(
      expectedGeneration, expectedDestinationGeneration, createFingerprint, 'create', destination
    );
    if (createClaim.type === 'error') {
      var createClaimError = pluginError(createClaim.result);
      respond.error(createClaimError.code, createClaimError.message);
      return;
    }
    if (createClaim.type === 'blocked') {
      respond.error('PLUGIN_UNAVAILABLE', createClaim.message);
      return;
    }
    if (createClaim.type === 'replay') {
      Pebble.refreshSources(DESTINATION_SOURCES);
      respond.ok(createClaim.result);
      return;
    }
    var created = await createChildPage(current.token, preparedCreate);
    if (!created.ok) {
      if (!failureCouldHaveMutated(created)) {
        clearMutationAfterDefinitiveFailure(createClaim.mutation.id, expectedGeneration);
      }
      var createError = pluginError(created);
      respond.error(createError.code, createError.message);
      return;
    }
    var createResult = {
      text: 'Created “' + created.title + '” in Notion',
      url: safeNotionPageUrl(created.page, created.page.id),
      refreshed: DESTINATION_SOURCES,
    };
    if (!completeMutation(createClaim.mutation.id, expectedGeneration, createResult)) {
      var staleCreate = pluginError(staleAuthenticationResult());
      respond.error(staleCreate.code, staleCreate.message);
      return;
    }
    Pebble.refreshSources(DESTINATION_SOURCES);
    respond.ok(createResult);
    return;
  }

  var preparedAppend;
  try { preparedAppend = prepareAppendParagraph(text, destination.placement); }
  catch (error) {
    respond.error('INVALID_ARGS', error.message || 'The note cannot be encoded for Notion.');
    return;
  }
  var appendEncoding = serializedRequestBody(preparedAppend);
  if (!appendEncoding.ok) {
    var appendEncodingError = pluginError(appendEncoding);
    respond.error(appendEncodingError.code, appendEncodingError.message);
    return;
  }
  var appendPath = '/blocks/' + encodeURIComponent(destination.pageId) + '/children';
  var appendFingerprint = mutationFingerprint('PATCH', appendPath, appendEncoding.json);
  var appendClaim = beginMutation(
    expectedGeneration, expectedDestinationGeneration, appendFingerprint, 'append', destination
  );
  if (appendClaim.type === 'error') {
    var appendClaimError = pluginError(appendClaim.result);
    respond.error(appendClaimError.code, appendClaimError.message);
    return;
  }
  if (appendClaim.type === 'blocked') {
    respond.error('PLUGIN_UNAVAILABLE', appendClaim.message);
    return;
  }
  if (appendClaim.type === 'replay') {
    Pebble.refreshSources(DESTINATION_SOURCES);
    respond.ok(appendClaim.result);
    return;
  }
  var appended = await appendParagraph(current.token, destination.pageId, preparedAppend);
  if (!appended.ok) {
    if (!failureCouldHaveMutated(appended)) {
      clearMutationAfterDefinitiveFailure(appendClaim.mutation.id, expectedGeneration);
    }
    var appendError = pluginError(appended);
    respond.error(appendError.code, appendError.message);
    return;
  }
  var appendResult = {
    text: 'Added to the ' + destination.placement + ' of “' + destination.pageTitle + '” in Notion',
    url: notionBlockUrl(verified.page, destination.pageId, appended.block.id),
    refreshed: DESTINATION_SOURCES,
  };
  if (!completeMutation(appendClaim.mutation.id, expectedGeneration, appendResult)) {
    var staleAppend = pluginError(staleAuthenticationResult());
    respond.error(staleAppend.code, staleAppend.message);
    return;
  }
  Pebble.refreshSources(DESTINATION_SOURCES);
  respond.ok(appendResult);
});

Pebble.registerConfigHandler(async function (message, respond) {
  await loadSecrets();
  var current = state();
  switch (message && message.type) {
    case 'status':
      respond({
        configured: !!current.token,
        ready: !!current.token && !!current.destination.pageId,
        destination: current.destination,
        pendingMutation: mutationNotice(current.mutation),
      });
      return;

    case 'connect': {
      if (current.mutation && current.mutation.status === 'pending') {
        respond({
          error: pendingMutationMessage(current.mutation),
          pendingMutation: mutationNotice(current.mutation),
        });
        return;
      }
      // Invalidate any in-flight operation while the browser authorization is active.
      var replacementGeneration = randomUuid();
      saveState({ authGeneration: replacementGeneration });
      var oauthBundle;
      try {
        oauthBundle = await Pebble.oauth.authorize('notion');
      } catch (error) {
        respond({ error: error.message || 'Could not connect Notion.', authRequired: true });
        return;
      }
      var oauthChanges;
      try { oauthChanges = tokenChanges(oauthBundle); }
      catch (error) {
        respond({ error: error.message, authRequired: true });
        return;
      }
      var checked = await listPages(oauthChanges.token, null);
      if (!generationIsActive(replacementGeneration, false)) {
        respond({ error: staleAuthenticationResult().error, authRequired: true });
        return;
      }
      if (!checked.ok) {
        respond({ error: checked.error, authRequired: checked.status === 401 || checked.status === 403 });
        return;
      }
      await saveSecrets(oauthChanges);
      var next = saveState({
        token: oauthChanges.token,
        brokerRefresh: oauthChanges.brokerRefresh,
        expiresAt: oauthChanges.expiresAt,
        authGeneration: randomUuid(),
        destinationGeneration: randomUuid(),
        destination: defaultDestination(),
        mutation: null,
      });
      Pebble.refreshSources(DESTINATION_SOURCES);
      respond({ configured: true, ready: false, pages: checked.pages, destination: next.destination });
      return;
    }

    case 'listPages': {
      try { current = await currentAuthState(); }
      catch (error) {
        respond({ error: error.message || 'Reconnect Notion.', authRequired: true });
        return;
      }
      if (!current.token) {
        respond({ error: 'Connect Notion first.', authRequired: true });
        return;
      }
      var listGeneration = current.authGeneration;
      var pages = await listPages(current.token, message.query);
      if (!pages.ok) {
        respond({ error: pages.error, authRequired: pages.status === 401 || pages.status === 403 });
        return;
      }
      if (!generationIsActive(listGeneration, true)) {
        respond({ error: staleAuthenticationResult().error, authRequired: true });
        return;
      }
      respond({ pages: pages.pages, destination: state().destination });
      return;
    }

    case 'setDestination': {
      try { current = await currentAuthState(); }
      catch (error) {
        respond({ error: error.message || 'Reconnect Notion.', authRequired: true });
        return;
      }
      if (!current.token) {
        respond({ error: 'Connect Notion first.', authRequired: true });
        return;
      }
      var pageId = String(message.pageId || '').trim();
      if (!pageId) {
        respond({ error: 'Choose a Notion page.' });
        return;
      }
      var mode = message.mode === 'append' ? 'append' : message.mode === 'create' ? 'create' : null;
      if (!mode) {
        respond({ error: 'Choose whether to create or append.' });
        return;
      }
      var setGeneration = current.authGeneration;
      var destinationGeneration = randomUuid();
      saveState({ destinationGeneration: destinationGeneration });
      var page = await verifyPage(current.token, pageId);
      if (!page.ok) {
        respond({ error: page.error, authRequired: page.status === 401 || page.status === 403 });
        return;
      }
      var destinationCurrent = state();
      if (!generationIsActive(setGeneration, true) ||
          destinationCurrent.destinationGeneration !== destinationGeneration) {
        respond({
          error: 'A newer Notion destination selection replaced this one. Review the current selection.',
          superseded: true,
        });
        return;
      }
      var selected = {
        mode: mode,
        pageId: page.summary.id,
        pageTitle: page.summary.title,
        pageUrl: page.summary.url,
        placement: message.placement === 'top' ? 'top' : 'bottom',
      };
      saveState({ destination: selected });
      Pebble.refreshSources(DESTINATION_SOURCES);
      respond({ saved: true, ready: true, destination: selected });
      return;
    }

    case 'resolvePendingMutation': {
      current = state();
      var pending = current.mutation;
      if (!pending || pending.status !== 'pending') {
        respond({ cleared: false, pendingMutation: null });
        return;
      }
      if (message.confirmedChecked !== true || message.mutationId !== pending.id ||
          (message.outcome !== 'saved' && message.outcome !== 'not_saved')) {
        respond({ error: 'Check Notion first, then acknowledge the current pending save.' });
        return;
      }
      var reviewAfterMs = pending.startedAtMs + PENDING_REVIEW_DELAY_MS;
      if (Date.now() < reviewAfterMs) {
        respond({
          error: 'The request may still be running. Wait until the review time, check Notion, then try again.',
          pendingMutation: mutationNotice(pending),
        });
        return;
      }
      // Re-read and match the opaque id so this acknowledgement cannot clear a newer mutation.
      var latest = state();
      if (!latest.mutation || latest.mutation.status !== 'pending' ||
          latest.mutation.id !== message.mutationId) {
        respond({ error: 'The pending save changed. Refresh settings before acknowledging it.' });
        return;
      }
      if (message.outcome === 'saved') {
        latest.mutation.status = 'completed';
        latest.mutation.completedAtMs = Date.now();
        latest.mutation.result = {
          text: latest.mutation.kind === 'append' ?
            'Already added to “' + latest.mutation.pageTitle + '” in Notion (confirmed)' :
            'Already created in Notion (confirmed)',
          refreshed: DESTINATION_SOURCES,
        };
        var confirmedUrl = safeNotionPageUrl({ url: latest.mutation.pageUrl }, latest.mutation.pageId);
        if (confirmedUrl) latest.mutation.result.url = confirmedUrl;
        saveState({
          mutation: null,
          completedReceipts: [latest.mutation].concat(latest.completedReceipts),
        });
      } else {
        saveState({ mutation: null });
      }
      respond({ cleared: true, pendingMutation: null });
      return;
    }

    case 'disconnect':
    case 'unlink':
      await clearState();
      Pebble.refreshSources(DESTINATION_SOURCES);
      var disconnected = { configured: false, ready: false };
      var disconnectedPending = mutationNotice(state().mutation);
      if (disconnectedPending) disconnected.pendingMutation = disconnectedPending;
      respond(disconnected);
      return;

    default:
      respond({ error: 'Unknown Notion configuration command.' });
  }
});

globalThis.__notionPbwTest = {
  listPages: listPages,
  splitNewPageText: splitNewPageText,
  richText: richText,
  safeNotionPageUrl: safeNotionPageUrl,
  serializedRequestBody: serializedRequestBody,
  mutationFingerprint: mutationFingerprint,
};
