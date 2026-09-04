// PKJS half of the plugin API test widget (see new-plugin-api.md).
//
// Four quadrants, each showing one property of one thing a plugin knows about. The user picks
// what goes where on the settings page; this subscribes to them and pushes the rendered value
// to the watch.
//
// The settings page talks to this code live over the config message channel: it asks for the
// catalogue, shows each source's real value, and every change applies immediately — the watch
// updates while the page is still open, with no save step.

// Eight readings over two pages of four; the watch turns the page with UP and DOWN.
var QUADRANTS = 8;
var PAGES = 2;

/** One entry per quadrant, so state arrays can't drift out of step with the tiles. */
function perQuadrant(make) {
  var out = [];
  for (var i = 0; i < QUADRANTS; i++) out.push(make());
  return out;
}
var STORE_KEY = 'quadrants';
var TITLES_KEY = 'titles';
var THEME_KEY = 'theme';
// This app's own uuid, as in package.json. A quick-launch button reads as the uuid of the app
// it opens, which is how this app recognises itself on one.
var APP_UUID = '8b1c6b0e-7d6a-4cf2-a9b2-2c3f8b1c6b0e';
// The two quick-launch settings a watchapp can sit on, and the slot each means to the watch.
var QUICK_SETTINGS = { qlSingleClickUp: 1, qlSingleClickDown: 2 };
var quickSlot = 0;

// A title of the reading's own name — "brightness" over the bar showing it.
var TITLE_READING_NAME = 'reading_name';
var CYCLE_PROPERTY = 'cycle_property';
var CYCLE_INSTANCE = 'cycle_instance';
var NO_ACTION = 'none';

// What fills each quadrant until the user says otherwise: a category/item/property, and
// optionally the title it wears (a property name, or `custom` with the text) and what a tap
// does. Left out, the title is the thing's name and a tap invokes whichever action the plugin
// binds to the reading.
var DEFAULTS = [
  { source: 'weather/location/temperature', action: CYCLE_INSTANCE },
  {
    source: 'phone/phone_state/battery_level',
    title: { mode: 'custom', text: 'Phone Battery' },
  },
  { source: 'finance/stock/day_change_percent', action: CYCLE_INSTANCE  },
  { source: 'music/track/artwork', title: { mode: 'title' }, action: 'set_playing' },
  { source: 'calendar/event/name', title: { mode: 'starts_at' } },
  { source: 'notifications/notification/app_icon', action: CYCLE_INSTANCE },
  { source: 'home/room_lights/on' },
  { source: 'weather/location/temperature', title: { mode: 'reading_name' }, action: CYCLE_PROPERTY },
];

var subscriptions = [];
// What each quadrant is subscribed as. Live rather than a snapshot: a tap that cycles the
// instance or property rewrites it, and the next envelope has to draw the new one.
var activeEntries = perQuadrant(function () { return null; });
// Latest rendered value per quadrant, so an open config page can show what the watch shows.
var values = perQuadrant(function () { return ''; });
// Instances the last envelope offered per quadrant, for the config page's instance picker.
var instances = perQuadrant(function () { return []; });
// Properties the chosen thing has per quadrant, and the shapes the chosen property offers.
var properties = perQuadrant(function () { return []; });
var shapes = perQuadrant(function () { return []; });
// The chosen property's last shape payloads, so a tap can act on what is on screen, and the
// whole envelope behind them, so cycling to another reading of it needs no new fetch.
var current = perQuadrant(function () { return null; });
var envelopes = perQuadrant(function () { return null; });
// Actions applicable to what each quadrant is showing, for the settings page's action picker.
var activeActions = perQuadrant(function () { return []; });
// What is actually being drawn per quadrant — the stored choice, or the default we picked.
var activeProperties = perQuadrant(function () { return ''; });
var activeShapes = perQuadrant(function () { return ''; });
// What each property and shape currently reads as, so the config page can label its dropdowns
// with the real thing rather than just a name.
var previews = perQuadrant(function () { return {}; });

// A quadrant is small, so a shape that renders as a glyph or a gauge beats a sentence.
var SHAPE_PREFERENCE = ['image', 'numericValue', 'boolean', 'icon', 'timestamp', 'shortText',
                        'longText'];

// The watch tells us how big a quadrant's artwork can be, since nothing there can scale a
// bitmap. Until it does, this is a conservative guess. Only sent for things that declare an
// icon or image shape — asking makes the plugin encode a bitmap, so it isn't something to ask
// for idly.
var tilePixels = { w: 48, h: 48 };

/** Both bitmap shapes travel and draw the same way; the icon is just the monochrome one. */
function isBitmap(shape) {
  return shape === 'image' || shape === 'icon';
}

function preferredShape(available) {
  return SHAPE_PREFERENCE.filter(function (shape) {
    return available.indexOf(shape) !== -1;
  })[0] || available[0] || '';
}

/** Light unless the user says otherwise; the watch keeps its own copy for offline launches. */
function theme() {
  return localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light';
}

function sendSettings() {
  Pebble.sendAppMessage({
    theme: theme() === 'dark' ? 1 : 0,
    quick: quickSlot,
  }, function () {}, function (e) {
    log('sending settings failed: ' + JSON.stringify(e));
  });
}

/**
 * Which quick-launch button this app sits on, read off the watch's own settings: the watch can
 * tell it was quick-launched but not from which button, so the phone supplies the button and
 * the watch only changes how it pages when it was actually opened that way.
 */
function onQuickLaunch(envelope) {
  var slot = 0;
  envelope.instances.forEach(function (instance) {
    var button = QUICK_SETTINGS[instance.instanceId];
    if (!button) return;
    var app = (((instance.properties || {}).value || {}).longText || {}).text || '';
    if (app.toLowerCase() === APP_UUID) slot = button;
  });
  if (slot === quickSlot) return;
  quickSlot = slot;
  log('quick launch: ' + (slot === 0 ? 'not assigned' : slot === 1 ? 'up' : 'down'));
  sendSettings();
}

/**
 * Per quadrant: which of the thing's readings goes in the title line, or `none` for a tile that
 * is all reading, or `custom` for a fixed string.
 */
function titles() {
  var saved;
  try { saved = JSON.parse(localStorage.getItem(TITLES_KEY)); } catch (e) { saved = null; }
  if (saved && saved.length === QUADRANTS) return saved;
  return DEFAULTS.map(function (wanted) { return wanted.title || {}; });
}

/** Nothing stored: the name, which is what a title is for, when the thing has one. */
function defaultTitleMode(index) {
  return properties[index].indexOf('name') !== -1 ? 'name' : 'none';
}

function titleFor(index, entry, instance) {
  var config = titles()[index] || {};
  var mode = config.mode || defaultTitleMode(index);
  if (mode === 'custom') return config.text || entry.property || entry.item;
  if (mode === TITLE_READING_NAME) return activeProperties[index];
  if (mode === 'none') return '';
  return readingOf((instance.properties || {})[mode]);
}

function log(message) {
  console.log('[PluginTest] ' + message);
}

/** Pebble's app message can carry bytes, so the bitmap travels as-is rather than as base64. */
function bytesOf(base64) {
  var binary = atob(base64);
  var bytes = [];
  for (var i = 0; i < binary.length; i++) bytes.push(binary.charCodeAt(i));
  return bytes;
}

function sendImage(index, image) {
  Pebble.sendAppMessage({
    imgQuad: index,
    imgWidth: image.width,
    imgHeight: image.height,
    imgPalette: bytesOf(image.palette),
    imgPixels: bytesOf(image.pixels),
  }, function () {}, function (e) {
    log('sending artwork failed: ' + JSON.stringify(e));
  });
}

function setQuadrant(index, text) {
  var fields = {};
  fields['quad' + index] = text;
  Pebble.sendAppMessage(fields, function () {}, function (e) {
    log('sendAppMessage failed: ' + JSON.stringify(e));
  });
}

// ---------------------------------------------------------------- catalogue

/** Every kind of thing every registered plugin knows about, as flat pickable entries. */
function catalogue() {
  var entries = [];
  Pebble.enumeratePlugins().forEach(function (plugin) {
    plugin.sources.forEach(function (source) {
      source.items.forEach(function (item) {
        entries.push({
          value: plugin.uuid + '|' + source.category + '|' + item,
          plugin: plugin.uuid,
          pluginName: plugin.name,
          category: source.category,
          item: item,
          properties: source.properties || {},
          multiple: !!source.supportsMultiple,
        });
      });
    });
  });
  return entries;
}

// "<plugin>|<category>|<item>" from the catalogue, plus which instance to show — a weather
// location, a Hue room — for things there are several of, which property of it, and in which
// shape.
//
// The instance part is a position, not an id: "the second weather location" follows the order
// the user set in settings, so reordering them there reorders what the quadrants show.
function parseEntry(value) {
  var parts = (value || '').split('|');
  if (parts.length < 3) return null;
  var index = parseInt(parts[3], 10);
  if (isNaN(index)) index = 0;
  var source = parts.slice(0, 3).join('|');
  return {
    plugin: parts[0],
    category: parts[1],
    item: parts[2],
    instanceIndex: index,
    property: parts[4] || '',
    shape: parts[5] || '',
    // What a tap does: an action name, `none`, one of the cycles, or empty for "whichever
    // action the plugin binds to this reading".
    action: parts[6] || '',
    source: source,
  };
}

/** `name` labels the instance; it is rarely what the tile is for, so it is not the default. */
function defaultProperty(names) {
  return names.filter(function (name) { return name !== 'name'; })[0] || names[0] || '';
}

function rebuild(entry, changes) {
  return [
    entry.plugin,
    entry.category,
    entry.item,
    'instanceIndex' in changes ? changes.instanceIndex : entry.instanceIndex,
    'property' in changes ? changes.property : entry.property,
    'shape' in changes ? changes.shape : entry.shape,
    'action' in changes ? changes.action : entry.action,
  ].join('|');
}

/** The saved choice, falling back to whichever defaults the installed plugins can satisfy. */
function chosen() {
  var saved;
  try { saved = JSON.parse(localStorage.getItem(STORE_KEY)); } catch (e) { saved = null; }
  if (saved && saved.length === QUADRANTS) return saved;

  var entries = catalogue();
  return DEFAULTS.map(function (wanted) {
    var parts = wanted.source.split('/');
    var match = entries.filter(function (entry) {
      return entry.category === parts[0] && entry.item === parts[1];
    })[0];
    // instance 0, the named property, whichever shape suits it, and the tap the default asks for.
    return match ? [match.value, 0, parts[2], '', wanted.action || ''].join('|') : '';
  });
}

// ---------------------------------------------------------------- sources

function unsubscribeAll() {
  subscriptions.forEach(function (subscription) {
    try {
      subscription.unsubscribe();
    } catch (err) {
      log('unsubscribe threw: ' + err);
    }
  });
  subscriptions = [];
}

/** The catalogue entry a quadrant's saved choice points at. */
function sourceOf(entry) {
  return catalogue().filter(function (option) {
    return option.value === entry.source;
  })[0];
}

/** One line of what a property currently says. */
function readingOf(payloads) {
  if (!payloads) return '';
  var shape = payloads.shortText ? 'shortText' : Object.keys(payloads)[0];
  return shape ? previewOf(shape, payloads[shape]) : '';
}

/** The reserved `name` property is what a picker shows for an instance. */
function nameOf(instance) {
  return readingOf(instance && instance.properties && instance.properties.name);
}

function labelsOf(list) {
  return list.map(function (instance) { return instance.label; });
}

function show(index, payload) {
  values[index] = payload;
  setQuadrant(index, payload);
  // Only lands if a config page is open; harmless otherwise.
  Pebble.sendConfigMessage({
    type: 'values',
    chosen: chosen(),
    values: values,
    instances: instances.map(labelsOf),
    properties: properties,
    shapes: shapes,
    activeProperties: activeProperties,
    activeShapes: activeShapes,
    actions: activeActions,
    previews: previews,
  });
}

function render(index, entry, envelope) {
  envelopes[index] = envelope;
  instances[index] = envelope.instances.map(function (instance) {
    return { id: instance.instanceId, label: nameOf(instance) || instance.instanceId };
  });
  // Nothing playing, no rooms paired: the thing can still say which properties it has.
  var declared = sourceOf(entry) || { properties: {} };
  properties[index] = Object.keys(declared.properties);
  var property = entry.property || defaultProperty(properties[index]);
  activeProperties[index] = property;
  activeActions[index] = actionsFor(entry, property).map(function (action) {
    return action.name;
  });

  var instance = envelope.instances[entry.instanceIndex];
  var payloads = instance && instance.properties && instance.properties[property];
  if (!payloads) {
    shapes[index] = declared.properties[property] || [];
    current[index] = null;
    activeShapes[index] = '';
    previews[index] = { instance: nameOf(instance), property: {}, shape: {} };
    show(index, text(property, '--'));
    return;
  }

  shapes[index] = Object.keys(payloads);
  current[index] = payloads;
  previews[index] = {
    instance: nameOf(instance),
    property: readings(instance.properties),
    shape: shapeReadings(payloads),
  };
  var shape = entry.shape || preferredShape(shapes[index]);
  activeShapes[index] = shape;
  var payload = encodeShape(titleFor(index, entry, instance), shape, payloads);
  if (isBitmap(shape) && payloads[shape]) {
    sendImage(index, payloads[shape]);
  }
  show(index, tag(payload, tapFor(entry, property)));
}

/**
 * Upper-case tag == the watch outlines the quadrant and routes taps here. A tap that only
 * changes what the quadrant shows gets the routing without the outline, since there is nothing
 * out there for it to act on.
 */
function tag(payload, tap) {
  if (!tap) return payload;
  if (tap.cycle) return payload.charAt(0) + '+' + payload.slice(1);
  return payload.charAt(0).toUpperCase() + payload.slice(1);
}

/** The reading an action writes, when one of its targets names one: `home/light/on` -> `on`. */
function writtenProperty(action, category, item) {
  var prefix = category + '/' + item + '/';
  var target = (action.targets || []).filter(function (candidate) {
    return candidate.indexOf(prefix) === 0;
  })[0];
  return target ? target.slice(prefix.length) : '';
}

/**
 * The actions a tap could fire on this thing: ones whose arguments a tap can supply. That's the
 * instance and the kind of thing it is, plus `on` — the opposite of the state being written, so
 * an action like Hue's `set_on` needs no separate toggle variant. The reading being written
 * needn't be the one on screen: artwork has no state of its own, but the track it belongs to
 * does, so `set_playing` is still offered over it.
 */
function bindableActions(plugin, category, item, property) {
  var declaration = plugin.sources.filter(function (candidate) {
    return candidate.category === category && candidate.items.indexOf(item) !== -1;
  })[0];
  var declared = (declaration && declaration.properties) || {};
  var prefix = category + '/' + item;

  var usable = plugin.actions.filter(function (action) {
    var required = (action.parameters && action.parameters.required) || [];
    var flipped = writtenProperty(action, category, item) || property;
    var canFlip = (declared[flipped] || []).indexOf('boolean') !== -1;
    return required.filter(function (name) {
      return name !== 'instanceId' && name !== 'item' && !(name === 'on' && canFlip);
    }).length === 0;
  });
  function targeting(target) {
    return usable.filter(function (action) {
      return (action.targets || []).indexOf(target) !== -1;
    });
  }
  // The one that writes this reading first — so it stays the default — then the ones bound to
  // the thing as a whole, then whatever else it can write.
  return (property ? targeting(prefix + '/' + property) : [])
    .concat(targeting(prefix))
    .concat(usable.filter(function (action) {
      var writes = writtenProperty(action, category, item);
      return writes && writes !== property;
    }));
}

function actionsFor(entry, property) {
  var plugin = Pebble.enumeratePlugins().filter(function (candidate) {
    return candidate.uuid === entry.plugin;
  })[0];
  return plugin ? bindableActions(plugin, entry.category, entry.item, property) : [];
}

/** What a tap on this quadrant does: fire an action, cycle what it shows, or nothing. */
function tapFor(entry, property) {
  if (entry.action === NO_ACTION) return null;
  if (entry.action === CYCLE_PROPERTY || entry.action === CYCLE_INSTANCE) {
    return { cycle: entry.action };
  }
  var applicable = actionsFor(entry, property);
  if (!entry.action) return applicable[0] ? { action: applicable[0] } : null;
  var named = applicable.filter(function (action) {
    return action.name === entry.action;
  })[0];
  return named ? { action: named } : null;
}

/** What a tap sends: what it is showing, and the opposite of the state the action writes. */
function actionArgs(action, entry, instanceId, instanceProperties, shown) {
  var args = { instanceId: instanceId };
  var required = (action.parameters && action.parameters.required) || [];
  if (required.indexOf('item') !== -1) args.item = entry.item;
  if (required.indexOf('on') !== -1) {
    var property = writtenProperty(action, entry.category, entry.item) || shown;
    var payloads = (instanceProperties || {})[property];
    args.on = !(payloads && payloads.boolean && payloads.boolean.value);
  }
  return args;
}

/**
 * Show the next property, or the next instance. Every reading of every instance is already in
 * the envelope we last drew from, so this is a repaint rather than a re-fetch — unless the
 * plugin left the property out, which is what happens to artwork nobody asked for.
 */
function cycle(index, entry, what) {
  var next;
  if (what === CYCLE_INSTANCE) {
    if (instances[index].length < 2) return;
    next = rebuild(entry, {
      instanceIndex: (entry.instanceIndex + 1) % instances[index].length,
    });
  } else {
    var list = properties[index];
    if (list.length < 2) return;
    var at = list.indexOf(activeProperties[index]);
    // The shape belonged to the property we are leaving.
    next = rebuild(entry, { property: list[(at + 1) % list.length], shape: '' });
  }
  store(index, next);
  refresh(index);
}

/** Epoch seconds as the phone's wall clock. The watch would do better with the raw value. */
function clockTime(seconds) {
  function pad(n) { return n < 10 ? '0' + n : String(n); }
  var when = new Date(seconds * 1000);
  return pad(when.getHours()) + ':' + pad(when.getMinutes());
}

/** One line of what a shape currently says, for the config page's dropdowns. */
function previewOf(shape, payload) {
  if (!payload) return '';
  switch (shape) {
    case 'numericValue': return payload.value + (payload.unit || '');
    case 'timestamp': return clockTime(payload.value);
    case 'boolean': return payload.value ? 'Yes' : 'No';
    case 'icon':
    case 'image': return payload.width + 'x' + payload.height;
    default: return payload.text || '';
  }
}

function shapeReadings(payloads) {
  var readings = {};
  Object.keys(payloads).forEach(function (shape) {
    readings[shape] = previewOf(shape, payloads[shape]);
  });
  return readings;
}

/** Every property of an instance in one line each, so its picker reads like the values do. */
function readings(instanceProperties) {
  var byProperty = {};
  Object.keys(instanceProperties).forEach(function (property) {
    byProperty[property] = readingOf(instanceProperties[property]);
  });
  return byProperty;
}

/**
 * The watch draws each shape itself — a bar, a checkbox, an icon — so it gets the parts rather
 * than a rendered string. See the protocol comment at the top of src/c/main.c.
 */
function encodeShape(label, shape, payloads) {
  var payload = payloads[shape];
  if (!payload) return text(label, '?');
  switch (shape) {
    case 'numericValue':
      // No range to fill: it is a number with a unit, which is a reading like any other.
      if (payload.min === null || payload.min === undefined ||
          payload.max === null || payload.max === undefined) {
        return text(label, payload.value + (payload.unit || ''));
      }
      return ['r', label, payload.value, payload.min, payload.max, payload.unit || ''].join('|');
    case 'timestamp':
      return text(label, clockTime(payload.value));
    case 'boolean':
      // The shape is the state alone now; a plugin with words for it offers shortText instead.
      return ['b', label, payload.value ? 1 : 0].join('|');
    case 'icon':
    case 'image':
      // The bitmap goes in its own message; this just tells the watch to expect one.
      return ['m', label, payload.width + 'x' + payload.height].join('|');
    default:
      return text(label, payload.text);
  }
}

function text(label, body) {
  return ['t', label, body].join('|');
}

function subscribeAll() {
  unsubscribeAll();
  activeEntries = chosen().map(parseEntry);
  // Quadrants showing two properties of the same thing are one subscription, and one plugin
  // fetch: the envelope carries every property, whichever of them a tile ends up drawing.
  var groups = {};
  activeEntries.forEach(function (entry, index) {
    if (!entry) {
      instances[index] = [];
      properties[index] = [];
      shapes[index] = [];
      current[index] = null;
      activeProperties[index] = '';
      activeShapes[index] = '';
      previews[index] = {};
      show(index, text('', 'Not set'));
      return;
    }
    var declared = sourceOf(entry) || { properties: {} };
    properties[index] = Object.keys(declared.properties);
    activeProperties[index] = entry.property || defaultProperty(properties[index]);
    show(index, text(activeProperties[index], '...'));
    if (!groups[entry.source]) groups[entry.source] = [];
    groups[entry.source].push(index);
  });

  subscriptions.push(Pebble.subscribeToSource({
    category: 'watch',
    item: 'app_setting',
    properties: ['value'],
    onData: onQuickLaunch,
    onError: function (err) { log('watch/app_setting: ' + err.code); },
  }));

  Object.keys(groups).forEach(function (source) {
    var indexes = groups[source];
    var entry = activeEntries[indexes[0]];
    var declared = sourceOf(entry) || { properties: {} };
    var offersImages = indexes.filter(function (index) {
      return (declared.properties[activeProperties[index]] || []).some(isBitmap);
    }).length > 0;
    subscriptions.push(Pebble.subscribeToSource({
      category: entry.category,
      item: entry.item,
      plugin: entry.plugin,
      iconPixelSize: offersImages ? tilePixels : undefined,
      onData: function (envelope) {
        indexes.forEach(function (index) { render(index, activeEntries[index], envelope); });
      },
      onError: function (err) {
        log(entry.category + '/' + entry.item + ': ' + err.code);
        indexes.forEach(function (index) {
          show(index, text(activeEntries[index].property || entry.item, err.code));
        });
      },
    }));
  });
}

function store(index, value) {
  var next = chosen();
  next[index] = value;
  localStorage.setItem(STORE_KEY, JSON.stringify(next));
  activeEntries[index] = parseEntry(value);
}

function save(index, value) {
  store(index, value);
  subscribeAll();
}

/** Redraw one quadrant from the envelope it last drew, leaving its subscription alone. */
function refresh(index) {
  var entry = parseEntry(chosen()[index]);
  var envelope = envelopes[index];
  if (!entry || !envelope) {
    subscribeAll();
    return;
  }
  var instance = envelope.instances[entry.instanceIndex];
  var property = entry.property || defaultProperty(properties[index]);
  if (instance && !(instance.properties || {})[property]) {
    subscribeAll();
    return;
  }
  render(index, entry, envelope);
}

// ---------------------------------------------------------------- settings page

function configPage() {
  return '<!doctype html><html><head><meta charset="utf-8">' +
    '<meta name="viewport" content="width=device-width, initial-scale=1">' +
    '<title>Plugin Demo: Dashboard</title>' +
    '<style>' +
    ':root{color-scheme:light dark}' +
    'body{font:16px system-ui,-apple-system,sans-serif;margin:0;padding:20px}' +
    // Each quadrant is introduced by its own name and a map of where it is on the watch.
    '.head{display:flex;align-items:center;gap:10px;margin:28px 0 8px;' +
    'padding-top:16px;border-top:1px solid rgba(128,128,128,.25)}' +
    '.head h2{font-size:19px;margin:0;font-weight:600}' +
    '.grid{display:grid;grid-template-columns:1fr 1fr;grid-template-rows:1fr 1fr;gap:2px;' +
    'width:24px;height:24px;flex:0 0 auto}' +
    '.grid i{background:rgba(128,128,128,.55);border-radius:2px}' +
    '.grid i.on{background:#ff6b00}' +
    'select{width:100%;padding:10px;font-size:16px;border-radius:8px;' +
    'border:1px solid rgba(128,128,128,.5);background:transparent;color:inherit}' +
    '.value{margin-top:6px;padding:8px 10px;border-radius:8px;font-size:14px;' +
    'background:rgba(128,128,128,.15);white-space:pre-line;min-height:18px}' +
    '.hint{font-size:13px;opacity:.7;margin:0}' +
    // Everything below the thing itself is a labelled row: which reading, in which shape, under
    // which title, and what a tap does.
    '.row{display:flex;align-items:center;gap:8px;margin-top:8px}' +
    '.row span{font-size:12px;opacity:.6;text-transform:uppercase;' +
    'letter-spacing:.05em;flex:0 0 auto;width:64px}' +
    'select.small{font-size:13px;padding:6px 8px;flex:1;width:auto}' +
    'input[type=text]{width:100%;box-sizing:border-box;padding:10px;font-size:16px;' +
    'margin-top:6px;border-radius:8px;border:1px solid rgba(128,128,128,.5);' +
    'background:transparent;color:inherit}' +
    // The pages the watch flips between with UP and DOWN, as tabs.
    '.tabs{display:flex;gap:8px;margin:18px 0 4px}' +
    '.tabs button{flex:1;padding:10px;font-size:15px;font-weight:600;border-radius:8px;' +
    'border:1px solid rgba(128,128,128,.5);background:transparent;color:inherit}' +
    '.tabs button.on{background:#ff6b00;border-color:#ff6b00;color:#fff}' +
    'button.danger{width:100%;margin:28px 0 8px;padding:12px;font-size:15px;' +
    'border-radius:8px;border:1px solid rgba(200,60,60,.6);background:transparent;color:#c83c3c}' +
    '</style></head><body>' +
    '<h1>Plugin Demo: Dashboard</h1><p id="status">Waiting for the app…</p>' +
    '<div class="row"><span>Theme</span>' +
    '<select id="theme" class="small"><option value="light">Light</option>' +
    '<option value="dark">Dark</option></select></div>' +
    '<p class="hint">Put this app on a quick-launch button in the watch\'s settings and that ' +
    'button pages forward while the other one backs out, as the timeline does — only when it ' +
    'was opened that way.</p>' +
    '<div class="tabs" id="tabs"></div>' +
    '<div id="fields"></div>' +
    '<button id="reset" class="danger">Reset to defaults</button>' +
    '<script>' +
    'var corners=["Top left","Top right","Bottom left","Bottom right"];' +
    'var PAGES=' + PAGES + ';' +
    'var pickers=[];var propertyPickers=[];var shapePickers=[];' +
    'var titlePickers=[];var titleTexts=[];var actionPickers=[];' +
    'var rows={property:[],shape:[],action:[]};' +
    'var boxes=[];var chosen=[];var titles=[];var previews=[];var items=[];' +
    'var options=[];var propertyLists=[];var instanceLists=[];' +
    'var sections=[];var shown=0;' +
    // Things that declare supportsMultiple keep their picker even while they only return one,
    // so "which one" stays visible for a plugin the user is still setting up.
    'var multi=[];' +
    // Relabelled in place as values change, so a dropdown never rebuilds under the user.
    'function syncOptions(picker,entries,selected){' +
    'var values=entries.map(function(e){return e.value;}).join("|");' +
    'if(picker.dataset.values!==values){' +
    'picker.dataset.values=values;picker.innerHTML="";' +
    'entries.forEach(function(entry){' +
    'var element=document.createElement("option");element.value=entry.value;' +
    'element.textContent=entry.label;picker.appendChild(element);});}' +
    // What is selected follows the app, not the last time the list was rebuilt: the watch can
    // move a quadrant on by itself, and a fresh pick lands before the reading it settles on.
    'entries.forEach(function(entry,position){' +
    'var option=picker.children[position];if(!option)return;' +
    'option.textContent=entry.label;option.selected=entry.value===selected;});}' +
    'function labelled(name,preview){return preview?name+": "+preview:name;}' +
    'function labelledRow(caption,picker){' +
    'var row=document.createElement("div");row.className="row";' +
    'var span=document.createElement("span");span.textContent=caption;' +
    'picker.className="small";' +
    'row.appendChild(span);row.appendChild(picker);return row;}' +
    'function entryFor(catalogue,value){' +
    'return catalogue.filter(function(option){return option.value===value;})[0];}' +
    // `name` is the instance label rather than the reading the tile is for.
    'function firstProperty(entry){' +
    'var names=Object.keys((entry&&entry.properties)||{});' +
    'return names.filter(function(name){return name!=="name";})[0]||names[0]||"";}' +
    // A thing there can be several of - weather locations, Hue rooms - gets a second dropdown.
    // A single-instance thing doesn't, so the common case stays one choice.
    'function fillInstances(index,list){' +
    'instanceLists[index]=list||[];' +
    'var picker=pickers[index];if(!picker)return;' +
    'if(!list||!list.length||(list.length<2&&!multi[index])){' +
    'picker.style.display="none";return;}' +
    'picker.style.display="block";' +
    'var current=String(parseInt((chosen[index]||"").split("|")[3],10)||0);' +
    'syncOptions(picker,list.map(function(label,position){' +
    'return {value:String(position),label:(position+1)+". "+label};}),current);}' +
    // Which reading of the thing the tile shows, labelled with what each one currently says.
    'function fillProperties(index,list,active){' +
    'propertyLists[index]=list||[];' +
    'var picker=propertyPickers[index];if(!picker)return;' +
    'var current=(chosen[index]||"").split("|")[4]||active||"";' +
    'if(current)items[index]=current;' +
    'if(!list||list.length<2){rows.property[index].style.display="none";return;}' +
    'rows.property[index].style.display="flex";' +
    'var preview=(previews[index]||{}).property||{};' +
    'syncOptions(picker,list.map(function(property){' +
    'return {value:property,label:labelled(property,preview[property])};}),current);}' +
    // Same idea for shapes: a reading usually offers several renderings of itself.
    'function fillShapes(index,list,active){' +
    'var picker=shapePickers[index];if(!picker)return;' +
    'if(!list||list.length<2){rows.shape[index].style.display="none";return;}' +
    'rows.shape[index].style.display="flex";' +
    'var preview=(previews[index]||{}).shape||{};' +
    // The stored choice, or whichever one the app defaulted to and is actually drawing.
    'var current=(chosen[index]||"").split("|")[5]||active||"";' +
    'syncOptions(picker,list.map(function(shape){' +
    'return {value:shape,label:labelled(shape,preview[shape])};}),current);}' +
    // What a tap does: fire one of the actions bound to this reading, or move the tile on to
    // the next reading or the next instance.
    'function fillActions(index,names){' +
    'var picker=actionPickers[index];if(!picker)return;' +
    'var entries=[{value:"none",label:"No action"}];' +
    '(names||[]).forEach(function(name){entries.push({value:name,label:name});});' +
    'if((propertyLists[index]||[]).length>1)' +
    'entries.push({value:"cycle_property",label:"Cycle property"});' +
    'if((instanceLists[index]||[]).length>1)' +
    'entries.push({value:"cycle_instance",label:"Cycle instance"});' +
    'rows.action[index].style.display=entries.length>1?"flex":"none";' +
    'var stored=(chosen[index]||"").split("|")[6]||"";' +
    'syncOptions(picker,entries,stored||(names||[])[0]||"none");}' +
    // Which reading heads the tile, per quadrant. `custom` reveals a field, prefilled with the
    // property name.
    'function fillTitle(index,item){' +
    'var picker=titlePickers[index];var field=titleTexts[index];' +
    'var config=titles[index]||{};' +
    'var preview=(previews[index]||{}).property||{};' +
    'var list=propertyLists[index]||[];' +
    'var mode=config.mode||(list.indexOf("name")!==-1?"name":"none");' +
    'var entries=[{value:"none",label:"No title"}].concat(list.map(function(property){' +
    'return {value:property,label:labelled(property,preview[property])};}));' +
    'entries.push({value:"reading_name",label:labelled("reading name",item)});' +
    'entries.push({value:"custom",label:"custom"});' +
    'syncOptions(picker,entries,mode);' +
    'if(field.value!==(config.text||""))field.value=config.text||"";' +
    'field.placeholder=item;' +
    'field.style.display=mode==="custom"?"block":"none";}' +
    'function sendTitle(index,item){' +
    'var mode=titlePickers[index].value;' +
    'var field=titleTexts[index];' +
    'if(mode==="custom"&&!field.value)field.value=item;' +
    'field.style.display=mode==="custom"?"block":"none";' +
    'titles[index]={mode:mode,text:field.value};' +
    'Pebble.sendMessage("pkjs",{type:"setTitle",index:index,mode:mode,text:field.value});}' +
    'function showPage(page){' +
    'shown=page;' +
    'sections.forEach(function(section,index){' +
    'section.style.display=Math.floor(index/corners.length)===page?"block":"none";});' +
    'Array.prototype.forEach.call(document.getElementById("tabs").children,' +
    'function(tab,index){tab.className=index===page?"on":"";});}' +
    'function buildTabs(){' +
    'var tabs=document.getElementById("tabs");tabs.innerHTML="";' +
    'if(PAGES<2)return;' +
    'for(var page=0;page<PAGES;page++)(function(page){' +
    'var tab=document.createElement("button");tab.textContent="Page "+(page+1);' +
    'tab.onclick=function(){showPage(page);};' +
    'tabs.appendChild(tab);})(page);}' +
    'function build(catalogue,current){' +
    'options=catalogue;chosen=current.slice();sections=[];' +
    'var fields=document.getElementById("fields");fields.innerHTML="";' +
    'for(var index=0;index<corners.length*PAGES;index++)(function(index){' +
    'var corner=corners[index%corners.length];' +
    'var head=document.createElement("div");head.className="head";' +
    'var grid=document.createElement("div");grid.className="grid";' +
    'for(var cell=0;cell<4;cell++){' +
    'var box=document.createElement("i");' +
    'if(cell===index%corners.length)box.className="on";' +
    'grid.appendChild(box);}' +
    'var heading=document.createElement("h2");' +
    'heading.textContent=PAGES>1?corner+" · page "+(Math.floor(index/corners.length)+1):corner;' +
    'head.appendChild(grid);head.appendChild(heading);' +
    'var select=document.createElement("select");' +
    'var itemSelect=document.createElement("select");' +
    'var source=(chosen[index]||"").split("|").slice(0,3).join("|");' +
    'var chosenEntry=entryFor(catalogue,source);' +
    'multi[index]=!!(chosenEntry&&chosenEntry.multiple);' +
    'function fillCategories(){' +
    'var seen={};var entries=[{value:"",label:"Nothing"}];' +
    'catalogue.forEach(function(option){' +
    'if(seen[option.category])return;seen[option.category]=true;' +
    'entries.push({value:option.category,label:option.category});});' +
    'syncOptions(select,entries,chosenEntry?chosenEntry.category:"");}' +
    'function fillItems(category){' +
    'var inCategory=catalogue.filter(function(option){' +
    'return option.category===category;});' +
    'itemSelect.style.display=inCategory.length?"block":"none";' +
    'if(!inCategory.length)return;' +
    'syncOptions(itemSelect,inCategory.map(function(option){' +
    'return {value:option.value,' +
    'label:option.pluginName+": "+option.item};}),source);}' +
    'function pick(value){' +
    'chosen[index]=value;source=value;' +
    'chosenEntry=entryFor(catalogue,value);' +
    'multi[index]=!!(chosenEntry&&chosenEntry.multiple);' +
    'items[index]=firstProperty(chosenEntry);' +
    // Applied the moment it changes: the watch repaints while this page is still open.
    'Pebble.sendMessage("pkjs",{type:"setQuadrant",index:index,value:value});}' +
    'select.onchange=function(){' +
    'var first=catalogue.filter(function(option){' +
    'return option.category===select.value;})[0];' +
    'pick(first?first.value:"");fillItems(select.value);' +
    'if(first)itemSelect.value=first.value;};' +
    'itemSelect.onchange=function(){pick(itemSelect.value);};' +
    'fillCategories();fillItems(chosenEntry?chosenEntry.category:"");' +
    'var picker=document.createElement("select");picker.style.display="none";' +
    'picker.onchange=function(){' +
    'Pebble.sendMessage("pkjs",{type:"setInstance",index:index,' +
    'instanceIndex:parseInt(picker.value,10)});};' +
    'var propertyPicker=document.createElement("select");' +
    'propertyPicker.onchange=function(){' +
    'items[index]=propertyPicker.value;' +
    'Pebble.sendMessage("pkjs",{type:"setProperty",index:index,' +
    'property:propertyPicker.value});};' +
    'var shapePicker=document.createElement("select");' +
    'shapePicker.onchange=function(){' +
    'Pebble.sendMessage("pkjs",{type:"setShape",index:index,shape:shapePicker.value});};' +
    'var titlePicker=document.createElement("select");' +
    'var titleText=document.createElement("input");titleText.type="text";' +
    'titleText.style.display="none";' +
    'var actionPicker=document.createElement("select");' +
    'actionPicker.onchange=function(){' +
    'Pebble.sendMessage("pkjs",{type:"setAction",index:index,action:actionPicker.value});};' +
    'var item=((chosen[index]||"").split("|")[4])||firstProperty(chosenEntry);' +
    'items[index]=item;' +
    'titlePicker.onchange=function(){sendTitle(index,items[index]);};' +
    'titleText.onchange=function(){sendTitle(index,items[index]);};' +
    'var propertyRow=labelledRow("Reading",propertyPicker);' +
    'var shapeRow=labelledRow("Shape",shapePicker);' +
    'var titleRow=labelledRow("Title",titlePicker);' +
    'var actionRow=labelledRow("Tap",actionPicker);' +
    'rows.property[index]=propertyRow;rows.shape[index]=shapeRow;' +
    'rows.action[index]=actionRow;' +
    'var box=document.createElement("div");box.className="value";' +
    'var quad=document.createElement("div");' +
    'quad.appendChild(head);quad.appendChild(select);quad.appendChild(itemSelect);' +
    'quad.appendChild(picker);quad.appendChild(propertyRow);quad.appendChild(shapeRow);' +
    'quad.appendChild(titleRow);quad.appendChild(titleText);' +
    'quad.appendChild(actionRow);quad.appendChild(box);' +
    'fields.appendChild(quad);sections[index]=quad;' +
    'pickers[index]=picker;propertyPickers[index]=propertyPicker;' +
    'shapePickers[index]=shapePicker;actionPickers[index]=actionPicker;' +
    'titlePickers[index]=titlePicker;titleTexts[index]=titleText;' +
    'boxes[index]=box;fillTitle(index,item);})(index);' +
    'buildTabs();showPage(shown);}' +
    'function update(data){' +
    'previews=data.previews||previews;' +
    // The watch can move a quadrant on by itself, so its choices come back with every update.
    'if(data.chosen)chosen=data.chosen.slice();' +
    '(data.values||[]).forEach(function(value,index){' +
    'if(boxes[index])boxes[index].textContent=value;});' +
    '(data.instances||[]).forEach(function(list,index){fillInstances(index,list);});' +
    '(data.properties||[]).forEach(function(list,index){' +
    'fillProperties(index,list,(data.activeProperties||[])[index]);});' +
    '(data.shapes||[]).forEach(function(list,index){' +
    'fillShapes(index,list,(data.activeShapes||[])[index]);});' +
    '(data.actions||[]).forEach(function(names,index){fillActions(index,names);});' +
    'items.forEach(function(item,index){' +
    'if(titlePickers[index])fillTitle(index,item);});}' +
    'var themePicker=document.getElementById("theme");' +
    'themePicker.onchange=function(){' +
    'Pebble.sendMessage("pkjs",{type:"setTheme",theme:themePicker.value});};' +
    'document.getElementById("reset").onclick=function(){' +
    'Pebble.sendMessage("pkjs",{type:"reset"}).then(function(reply){' +
    'titles=reply.titles||[];build(reply.catalogue,reply.chosen);update(reply);});};' +
    'Pebble.addEventListener("message",function(event){' +
    'if(event.data&&event.data.type==="values")update(event.data);});' +
    'Pebble.addEventListener("ready",function(event){' +
    'if(event.target!=="pkjs")return;' +
    'document.getElementById("status").textContent="Live — changes apply straight away.";' +
    'Pebble.sendMessage("pkjs",{type:"catalogue"}).then(function(reply){' +
    'titles=reply.titles||[];themePicker.value=reply.theme||"light";' +
    'build(reply.catalogue,reply.chosen);' +
    'update(reply);});});' +
    '</script></body></html>';
}

// ---------------------------------------------------------------- lifecycle

Pebble.addEventListener('ready', function () {
  log('ready; ' + catalogue().length + ' source(s) available');
  sendSettings();
  hello();
  subscribeAll();
});

/** Ask the watch for its artwork size, which is all a restarted PKJS doesn't already know. */
function hello(retries) {
  Pebble.sendAppMessage({ hello: 1 }, function () {}, function () {
    if (retries === 0) return;
    setTimeout(function () { hello(retries === undefined ? 2 : retries - 1); }, 1000);
  });
}

Pebble.addEventListener('appmessage', function (e) {
  var payload = e.payload || {};
  if (payload.artSize !== undefined) {
    if (payload.artSize !== tilePixels.w) {
      tilePixels = { w: payload.artSize, h: payload.artSize };
      log('watch wants ' + payload.artSize + 'px artwork');
      subscribeAll();
    }
    return;
  }
  if (payload.action === undefined) return;
  var index = payload.action;
  var entry = parseEntry(chosen()[index]);
  var tap = entry && tapFor(entry, activeProperties[index]);
  if (!tap) return;
  if (tap.cycle) {
    cycle(index, entry, tap.cycle);
    return;
  }
  var instance = instances[index][entry.instanceIndex];
  if (!instance) return;
  var action = tap.action;
  var envelope = envelopes[index];
  var live = envelope && envelope.instances[entry.instanceIndex];
  var args = actionArgs(action, entry, instance.id, live && live.properties,
                        activeProperties[index]);
  log('tap on quadrant ' + index + ': ' + action.name + ' ' + JSON.stringify(args));
  Pebble.invokeAction({
    plugin: entry.plugin,
    action: action.name,
    args: args,
  }).then(function (result) {
    log(action.name + (result.ok ? ' ok: ' + result.text : ' failed: ' + result.code));
  });
});

Pebble.addEventListener('showConfiguration', function () {
  Pebble.openURL('data:text/html;charset=utf-8,' + encodeURIComponent(configPage()));
});

Pebble.addEventListener('configmessage', function (e) {
  var message = e.data || {};
  switch (message.type) {
    case 'catalogue':
      e.respond({
        catalogue: catalogue(),
        chosen: chosen(),
        theme: theme(),
        values: values,
        instances: instances.map(labelsOf),
        properties: properties,
        shapes: shapes,
        activeProperties: activeProperties,
        activeShapes: activeShapes,
        actions: activeActions,
        previews: previews,
        titles: titles(),
      });
      return;
    case 'setTheme': {
      localStorage.setItem(THEME_KEY, message.theme === 'dark' ? 'dark' : 'light');
      sendSettings();
      log('theme: ' + theme());
      e.respond({ ok: true });
      return;
    }
    case 'reset': {
      localStorage.removeItem(STORE_KEY);
      localStorage.removeItem(TITLES_KEY);
      log('reset to defaults');
      subscribeAll();
      e.respond({
        catalogue: catalogue(),
        chosen: chosen(),
        theme: theme(),
        values: values,
        instances: instances.map(labelsOf),
        properties: properties,
        shapes: shapes,
        activeProperties: activeProperties,
        activeShapes: activeShapes,
        actions: activeActions,
        previews: previews,
        titles: titles(),
      });
      return;
    }
    case 'setQuadrant': {
      save(message.index, message.value);
      log('quadrant ' + message.index + ' set to ' + (message.value || 'nothing'));
      e.respond({ ok: true });
      return;
    }
    case 'setInstance': {
      var instanceEntry = parseEntry(chosen()[message.index]);
      if (!instanceEntry) {
        e.respond({ error: 'nothing in that quadrant' });
        return;
      }
      save(message.index,
           rebuild(instanceEntry, { instanceIndex: message.instanceIndex || 0 }));
      log('quadrant ' + message.index + ' instance ' + (message.instanceIndex || 0));
      e.respond({ ok: true });
      return;
    }
    case 'setProperty': {
      var propertyEntry = parseEntry(chosen()[message.index]);
      if (!propertyEntry) {
        e.respond({ error: 'nothing in that quadrant' });
        return;
      }
      // The stored shape belonged to the old property and may not exist on the new one.
      save(message.index,
           rebuild(propertyEntry, { property: message.property || '', shape: '' }));
      log('quadrant ' + message.index + ' property ' + (message.property || 'default'));
      e.respond({ ok: true });
      return;
    }
    case 'setTitle': {
      var next = titles();
      next[message.index] = { mode: message.mode, text: message.text || '' };
      localStorage.setItem(TITLES_KEY, JSON.stringify(next));
      log('quadrant ' + message.index + ' title: ' + message.mode);
      subscribeAll();
      e.respond({ ok: true });
      return;
    }
    case 'setAction': {
      var actionEntry = parseEntry(chosen()[message.index]);
      if (!actionEntry) {
        e.respond({ error: 'nothing in that quadrant' });
        return;
      }
      // Only the tile's tag changes, so this never disturbs the subscription.
      store(message.index, rebuild(actionEntry, { action: message.action || '' }));
      refresh(message.index);
      log('quadrant ' + message.index + ' action ' + (message.action || 'default'));
      e.respond({ ok: true });
      return;
    }
    case 'setShape': {
      var shapeEntry = parseEntry(chosen()[message.index]);
      if (!shapeEntry) {
        e.respond({ error: 'nothing in that quadrant' });
        return;
      }
      save(message.index, rebuild(shapeEntry, { shape: message.shape || '' }));
      log('quadrant ' + message.index + ' shape ' + (message.shape || 'default'));
      e.respond({ ok: true });
      return;
    }
    default:
      e.respond({ error: 'unknown message type' });
  }
});
