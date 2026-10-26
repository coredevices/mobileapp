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
// The catalogue's pseudo-category for tiles that are just a button: an action taking no
// arguments, with no reading behind it. Capitalised, so it can't be a real plugin's category.
var ACTION_CATEGORY = 'Actions';
// Tiles the watch fills itself, with no plugin behind them.
var WATCH_CATEGORY = 'Clock';

// What fills each quadrant until the user says otherwise: a category/item/property, and
// optionally the title it wears (a property name, or `custom` with the text) and what a tap
// does. Left out, the title is the thing's name and a tap invokes whichever action the plugin
// binds to the reading. An entry with only an `action` is an action tile.
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
  { action: 'launch_previous_app', title: { mode: 'custom', text: 'Back' } },
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

/** A tile shows one reading, so a property that only comes as a series has nothing to put in it. */
function drawableProperties(declared) {
  return Object.keys(declared.properties).filter(function (property) {
    return declared.properties[property].some(function (shape) { return shape.indexOf('series.') !== 0; });
  });
}

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
    plugin.actions.filter(takesNoArguments).forEach(function (action) {
      entries.push({
        value: plugin.uuid + '|' + ACTION_CATEGORY + '|' + action.name,
        plugin: plugin.uuid,
        pluginName: plugin.name,
        category: ACTION_CATEGORY,
        item: action.name,
        properties: {},
        multiple: false,
      });
    });
  });
  entries.push({
    value: '|' + WATCH_CATEGORY + '|date',
    plugin: '',
    pluginName: 'Watch',
    category: WATCH_CATEGORY,
    item: 'date',
    properties: {},
    multiple: false,
  });
  return entries;
}

function takesNoArguments(action) {
  return !((action.parameters && action.parameters.required) || []).length;
}

function isActionTile(entry) {
  return entry.category === ACTION_CATEGORY;
}

/** The action an action tile fires, if its plugin still has it. */
function tileAction(entry) {
  var plugin = Pebble.enumeratePlugins().filter(function (candidate) {
    return candidate.uuid === entry.plugin;
  })[0];
  return plugin && plugin.actions.filter(function (action) {
    return action.name === entry.item && takesNoArguments(action);
  })[0];
}

/** `launch_previous_app` -> "Launch previous app". */
function actionLabel(name) {
  var words = name.replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

function renderWatchTile(index, entry) {
  instances[index] = [];
  properties[index] = [];
  shapes[index] = [];
  current[index] = null;
  activeProperties[index] = '';
  activeShapes[index] = '';
  activeActions[index] = [];
  previews[index] = {};
  var config = titles()[index] || {};
  show(index, ['d', config.mode === 'custom' ? config.text || '' : ''].join('|'));
}

function renderActionTile(index, entry) {
  instances[index] = [];
  properties[index] = [];
  shapes[index] = [];
  current[index] = null;
  activeProperties[index] = '';
  activeShapes[index] = '';
  activeActions[index] = [];
  previews[index] = {};
  var action = tileAction(entry);
  if (!action) {
    show(index, text('', 'Not available'));
    return;
  }
  var config = titles()[index] || {};
  var title = config.mode === 'custom' && config.text ? config.text : sourceOf(entry).pluginName;
  show(index, tag(text(title, actionLabel(action.name)), { action: action }));
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
    if (!wanted.source) {
      var button = entries.filter(function (entry) {
        return entry.category === ACTION_CATEGORY && entry.item === wanted.action;
      })[0];
      return button ? button.value : '';
    }
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

/**
 * The instance a tile is pinned to. A stored index is a position, not an id, and the list it
 * indexes can shrink — a notification is dismissed, a saved location removed — so fall back to
 * the last one rather than showing nothing.
 */
function instanceAt(list, wanted) {
  if (!list || !list.length) return null;
  return list[wanted < list.length ? wanted : list.length - 1];
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
  properties[index] = drawableProperties(declared);
  var property = entry.property || defaultProperty(properties[index]);
  activeProperties[index] = property;
  activeActions[index] = actionsFor(entry, property).map(function (action) {
    return action.name;
  });

  var instance = instanceAt(envelope.instances, entry.instanceIndex);
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
  if (entry.category === WATCH_CATEGORY) return null;
  if (isActionTile(entry)) {
    var action = tileAction(entry);
    return action ? { action: action } : null;
  }
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
    if (isActionTile(entry)) {
      renderActionTile(index, entry);
      return;
    }
    if (entry.category === WATCH_CATEGORY) {
      renderWatchTile(index, entry);
      return;
    }
    var declared = sourceOf(entry) || { properties: {} };
    properties[index] = drawableProperties(declared);
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
      properties: drawableProperties(declared),
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
  var instance = instanceAt(envelope.instances, entry.instanceIndex);
  var property = entry.property || defaultProperty(properties[index]);
  if (instance && !(instance.properties || {})[property]) {
    subscribeAll();
    return;
  }
  render(index, entry, envelope);
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
  if (isActionTile(entry)) {
    invoke(entry, tap.action, {});
    return;
  }
  var instance = instanceAt(instances[index], entry.instanceIndex);
  if (!instance) return;
  var action = tap.action;
  var envelope = envelopes[index];
  var live = envelope && instanceAt(envelope.instances, entry.instanceIndex);
  var args = actionArgs(action, entry, instance.id, live && live.properties,
                        activeProperties[index]);
  log('tap on quadrant ' + index + ': ' + action.name + ' ' + JSON.stringify(args));
  invoke(entry, action, args);
});

function invoke(entry, action, args) {
  Pebble.invokeAction({
    plugin: entry.plugin,
    action: action.name,
    args: args,
  }).then(function (result) {
    log(action.name + (result.ok ? ' ok: ' + result.text : ' failed: ' + result.code));
  });
}

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
