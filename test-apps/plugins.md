# Pebble Plugins - Preview

Plugins are nearly here (finally). A plugin is a small piece of JavaScript that runs on the phone and:

- **provides data**: (*sources*) that any Pebble watchface/app (and soon Index) can read
- **takes actions**: turn a light off, add a task, that a watchface/app (and soon Index) can trigger

It has a generic API, designed to allow any type of data to be exposed, in multiple formats.

This doc lives here while plugins are under active development, and will move once they're stable.
Right now they're supported in the Pebble mobile app (v1.14.0), but are experimental and disabled by
default.

## Why?

- A watchface that shows the weather shouldn't need its own weather
  API and key. The Pebble app already fetches weather for the user's saved locations; as a plugin,
  any app can display that.
- If you have an interesting data source (say, a blood
  sugar monitor, or next-bus data), users can show it on whichever watchface they like instead of the one design you
  made.
- Plugins let us expose data the Pebble app has but developers
  couldn't get at before very easily: music, notifications, phone state, and more.
- Actions let a watch (and, later, Index) control things like lights or
  tasks through a plugin, without every app integrating with every service.
- Once Index can use plugins, adding a to-do list
  service (like the Todoist, TickTick and Notion demos) is just a plugin, with no change to the
  Pebble app.

# What can I try now?

- Try the example plugins, watchface and app.
- Build a watchface or app that uses plugin data.
- Build a plugin.

First, enable plugins in the mobile app (v1.14.0 or above): **Settings → Debug → Show debug options**, then **Use
experimental plugins**. The demo apps below are installed automatically, and a **Plugins** tab appears in the Apps
screen listing plugins and their settings pages.

## Try the demo apps and plugins

Source for each is in this folder:

- **`weather-face`** ("Plugin Demo: Weather Face"): a watchface built entirely from a weather
  plugin: any installed one (the built-in one, `met-weather`, or yours), picked on its settings
  page.
- **`met-weather`**: weather for the phone's current location from MET Norway (api.met.no), an
  alternative to the built-in weather plugin. Shows `navigator.geolocation` and drawing condition
  images in JS.
- **`plugin-test`** ("Plugin Demo: Dashboard"): a watchapp with eight tiles, each set from its
  settings page to any reading from any installed plugin. This shows what data is available through the example/built-in plugins. You can also configure taps to take actions, or make a tile a button for any action that takes no arguments (e.g. "Back", the default, goes to the previous app). A tile can also show the date, from the watch's own clock.
- **`stocks`**: share prices for tickers you pick on its settings page.
- **`hue`**: Control Philips Hue lights on your local bridge (pair from its settings page).
- **`notion`**, **`ticktick`**, **`todoist`**: examples of plugins that sign in with OAuth.

## What can't I do yet?

**Don't ship anything that uses/offers plugins to the app store yet.** The API is unstable and will change
before plugins are released to everyone. We want your feedback on it first.

See [Future](#future) for what's planned but not built yet.

# Concepts

## Sources and actions

A **source** is something an app can read from a plugin, addressed in layers:

```
category            home
  item              room_lights        the kind of thing
    instance        "3"                one particular room (its name property: "Living room")
      property      brightness         one reading of it
        shape       numericValue -> { value: 64, unit: "%", min: 0, max: 100 }
```

You can subscribe to an *item* and get every property of every instance of it, and updates when they
change. Instances have a stable `instanceId`; `name` is the property to display for one.

An **action** is something a plugin can do (`set_on`, `add_task`). It takes JSON arguments described
by a JSON Schema in the manifest, and can say which sources it affects (`targets`), so a tile showing
a light knows which actions apply to it.

## Categories

Apps ask for a category and item, not a plugin (`weather/location`, not "AcmeWeather"). The host
serves it from whichever installed plugin provides it.

Category, item and property names are free-form, so a plugin can offer any kind of source or action.
But swapping one plugin for another only works if plugins serving the same category use the same
names. So if an existing category fits, like weather, match its item and property names rather than
inventing your own. We'll publish the names/shapes for common categories.

Examples now:

| Category        | Items                                                             | From      |
| --------------- | ----------------------------------------------------------------- | --------- |
| `weather`       | `location`, `hour`                                                | built-in  |
| `calendar`      | `event`                                                           | built-in  |
| `phone`         | `phone_state`                                                     | built-in  |
| `music`         | `track`                                                           | built-in (Android) |
| `notifications` | `notification`                                                    | built-in (Android) |
| `watch`         | `switch_setting`, `number_setting`, `text_setting`, `app_setting` | built-in  |
| `home`          | `home_lights`, `room_lights`, `light`                             | Hue demo  |
| `finance`       | `stock`                                                           | Stocks demo |
| `notes`         | `destination`, `configured`                                       | Notion demo |
| `reminders`     | `list`, `configured`                                              | Todoist, TickTick demos |

A plugin is offered as a notes / reminders destination for the Index agent when it declares a
`create_note` / `create_reminder` action *and* serves `notes/configured` / `reminders/configured`:
one instance whose `configured` property is a `boolean` that is true once the user has signed in and
set everything the action needs. While it reads false the destination shows a Connect button that
opens the plugin's settings page. An action without its `configured` source is not offered.

The built-in Watch State plugin has no sources, only actions, both needing `LaunchApps`:
`launch_app` (`{ uuid }`) opens an app or watchface in the user's locker, and
`launch_previous_app` goes back to whatever ran before the current app.

## Shapes

A property isn't typed as "a number" or "a string". It offers one or more **shapes**: fixed types
that a consuming app can be confident of being able to render. The plugin sends every shape it can,
and you pick one.

| Shape          | For                                    | Payload                                      |
| -------------- | -------------------------------------- | -------------------------------------------- |
| `shortText`    | up to ~7 characters                    | `{ text: "72°F" }`                           |
| `longText`     | a line of text                         | `{ text: "Partly cloudy, breezy" }`          |
| `numericValue` | a number, with a range if it has one   | `{ value: 84, unit: "%", min: 0, max: 100 }` |
| `timestamp`    | a moment, in epoch seconds UTC         | `{ value: 1716940800 }`                      |
| `boolean`      | on/off state                           | `{ value: true }`                            |
| `icon`         | a monochrome glyph                     | `{ pixels, palette, width, height }`         |
| `image`        | a colour bitmap                        | `{ pixels, palette, width, height }`         |

- Some shapes carry optional metadata (unit, min, max) so an app can render the data in context.
- `icon` and `image` are 4-bpp palettised bitmaps (base64), only produced if you ask for them with
  `iconPixelSize`.

A missing property or shape means the plugin can't serve it right now. Check before drawing.

Shapes are designed so you can build for a *slot in your design* rather than for specific data. A
face with a gauge widget can offer the user anything that has a `numericValue` with a range: battery,
UV index, a light's brightness. A small text slot can take any `shortText`, whatever it came from. An
`icon` suits a small monochrome mark beside a reading, and an `image` suits a full-colour one.

## Permissions

Plugins introduce a fine-grained permission system, covering your plugin and your app's PKJS. This
ships in v1.15 of the mobile app, and is only in place when plugins are enabled.

### Declaring

Declare what your app and its plugin need once, in the `pebble` block of `package.json`:

```jsonc
"pebble": {
  "usesPermissions": [
    "Location",
    "LocalNetwork",
    { "name": "Internet", "parameters": { "domains": ["api.example.com"] } }
  ]
}
```

The built-in permissions are `Internet`, `LocalNetwork`, `Location`, `Notifications`, `Calendar`,
`Contacts`, `Timeline`, `ReadWatchSettings`/`WriteWatchSettings`, `ReadMusic`/`ControlMusic`,
`LaunchApps`, `LegacyPebbleKit` and `Health`.

- Each `Internet` domain is its own permission and covers its subdomains. `LocalNetwork` covers
  devices on the LAN.
- `LegacyPebbleKit` lets your watchapp talk to a companion app over PebbleKit Classic. A PebbleKit 2
  companion doesn't need declaring: each package your appinfo lists is asked about on its own.
- `Health` isn't enforced yet: health data is read on the watch, so enforcing it needs permissions
  synced to the watch, which isn't built yet. Declare it anyway if you use health data.

The [packaging script](#packaging-and-sideloading) marks the pbw as using permissions
(`manifestVersion: 2` in its appinfo). Without that it's a legacy app, which keeps what apps had
before permissions: internet access, and what its `capabilities` declare.

Sources and actions guard themselves with **caller permissions**: what an app must hold to use
them. `calendar/event` needs `Calendar`, `notifications/notification` needs `Notifications`,
`weather/*` needs `Location`.

A plugin can also define its own permissions, to guard its sources and actions:

```jsonc
"plugin": {
  "definesPermissions": [{ "name": "HomeControl", "label": "Control your lights", "kind": "write" }],
  "actions": [{ "name": "set_on", "callerPermissions": ["HomeControl"], ... }]
}
```

`kind` is `read`, `write` or `exfiltrating`, and decides when users are asked, as for a built-in
permission. The name is scoped to your plugin, and users see your label with where it came from
("Control your lights (via Hue)").

Actions get one more check: if your plugin can send data off the phone, an app invoking its actions
needs the user's permission for that too (`InvokeAction`), since the arguments go wherever your plugin
can send them.

To use another plugin, declare its permissions in your `usesPermissions` with its uuid, so users
can approve them when installing your app (if that plugin is already installed):

```jsonc
"usesPermissions": [
  { "name": "HomeControl", "plugin": "<Hue's uuid>" },
  { "name": "InvokeAction", "plugin": "<Hue's uuid>" }
]
```

### Approval

Network access and companion apps let data leave the phone, so they're what make reading someone's
data risky. Those, and permissions that change things (`Timeline`, `WriteWatchSettings`,
`ControlMusic`, `LaunchApps`), need the user's approval unless they've set apps to never ask. Reading is up to a
setting; by default:

- **An app that can send data off the phone** has the user approve each permission: the ones it
  declares when it's installed, and anything else (an undeclared permission, or a new domain) when
  it first uses it.
- **Any other app** can read without asking, since the data can't go anywhere.

While your app is running (or, for a plugin, while something reads from it), a request shows the
user a notification and a card in the Pebble app. Until they answer, it fails with
`PERMISSION_DENIED` (a blocked `fetch` rejects like a network error), possibly after waiting a few
seconds. Try again later rather than treating it as permanent.

Users can change any permission on the app's page, and choose in settings when apps have to ask.
Declare what you need up front, and keep `Internet` to the domains you use: fewer prompts, and
users can trust you. An app holding `Location` whose only network access is
`api.openweathermap.org` can't send the user's location anywhere else.

# Writing a watchface or app that uses plugins

Your app uses plugins from its PKJS (`pebble-js-app.js`).

## Three ways to use plugins

1. **You know exactly what you want.** Hard-code the category, item and properties, as the
   `weather-face` demo does with `weather/location`. Whichever weather plugin the user has, the face
   works. It uses the condition `image` the plugin provides - but a face that wants its own artwork could
   read the `condition_code` property instead (`sun`, `partly_cloudy`, `heavy_rain`, ...) and draw its
   own.
2. **Let the user choose.** Use `Pebble.enumeratePlugins()` to see everything available, and a
   settings page for the user to pick what goes where, as the `plugin-test` dashboard does. Filter
   by the shapes your design can draw (see [Shapes](#shapes)).
3. **Use what the user already prefers** *(not built yet)*. Users will pick their favourite readings
   in the Pebble app: e.g. a user might select weather for home location first, then phone battery,
   then the Google stock price. An API will expose that ordered list, so any face with widget slots
   can display those items with no configuration required.

**Reading a source:**

```javascript
const subscription = Pebble.subscribeToSource({
  category: "weather",
  item: "location",
  properties: ["name", "temperature", "condition"],  // optional hint; omit for everything
  iconPixelSize: { w: 48, h: 48 },                   // optional; ask only if you want bitmaps
  onData: ({ instances }) => {
    const here = instances[0];                       // first saved location (can be multiple if supportsMultiple==true)
    if (!here) return;
    const p = here.properties;
    send(p.name.shortText.text, p.temperature.shortText.text);
  },
  onError: (err) => console.log(err.code, err.message),
});

subscription.unsubscribe();   // or let it end with the app
```

Every update is an envelope:

```js
{
  pluginUuid: "...",
  validUntilMs: 1716940000000,
  instances: [
    {
      instanceId: "3",
      properties: {
        name:       { shortText: { text: "Kitchen" } },
        brightness: { numericValue: { value: 64, unit: "%", min: 0, max: 100 },
                      shortText:    { text: "64%" } },
      },
    },
  ],
}
```

**Reading from a specific plugin:** pass `plugin: "<uuid>"`, for example your own pbw's plugin. This
is a preference. If that plugin isn't installed or doesn't serve the item, another plugin serving it
is used. Check `pluginUuid` on the envelope if you need to know which one answered.

**Invoking an action**: `plugin` is required here, and never falls back to another plugin:

```javascript
const result = await Pebble.invokeAction({
  plugin: "<plugin's app uuid>",
  action: "set_on",
  args: { item: "light", instanceId: "3", on: false },
});
// { ok: true, text: "Kitchen off.", refreshed: ["home/light"] }
// { ok: false, code: "AUTH_REQUIRED", message: "..." }
```

**Discovering what's installed:** `Pebble.enumeratePlugins()` returns every installed plugin with
its sources (items, properties and shapes) and actions.

Error codes: `PLUGIN_UNAVAILABLE`, `PERMISSION_DENIED`, `RATE_LIMITED`, `TIMEOUT`,
`INVALID_REQUEST`, `UNKNOWN`, plus `AUTH_REQUIRED` and `INVALID_ARGS` for actions.

# Writing a plugin

A plugin ships in a `.pbw`: either alongside a watchapp/watchface, or on its own as a
**plugin-only** pbw (no watch binary). It uses the pbw's uuid and name. The simplest example to
copy is `stocks/`.

**If your app fetches data from a service and displays it, consider splitting it into a plugin plus
the app, in one pbw.** The app reads from its own plugin (pass your pbw's uuid as `plugin`), and
behaves the same as it does now. But the data is also available to every other watchface and app,
and later to Index - and users can show it in a design other than yours.

## The manifest

Add a `plugin` block to the `pebble` section of `package.json`:

```jsonc
"pebble": {
  "displayName": "Stocks",
  "uuid": "2c4b7f10-9a3d-4e6b-8f21-5d0c7e9a1b34",
  "sdkVersion": "3",
  "configPage": "config.html",            // optional settings page (a file in the pbw, or a URL)
  "usesPermissions": [
    { "name": "Internet", "parameters": { "domains": ["query1.finance.yahoo.com"] } }
  ],
  "plugin": {
    "description": "Share prices for the tickers the user picked.",   // also read by index MCP
    "script": "plugin.js",
    "sources": [
      {
        "category": "finance",
        "items": ["stock"],
        "properties": {
          "name":  ["shortText"],
          "price": ["shortText", "longText", "numericValue"]
        },
        "supportsMultiple": true,
        "suggestedRefreshIntervalSec": 60
      }
    ],
    "actions": [
      {
        "name": "add_stock",                 // illustrative; the real Stocks plugin has no actions
        "description": "Start tracking a ticker symbol.",
        "parameters": {                    // JSON Schema
          "type": "object",
          "properties": { "symbol": { "type": "string" } },
          "required": ["symbol"]
        },
        "targets": ["finance/stock"]
      }
    ]
  }
}
```

## The script

A plugin only runs when it's needed: for a subscription, an action, or while its settings page is
open. It doesn't stay running in the background. While an app is subscribed, the plugin is called
regularly for fresh data (currently every `suggestedRefreshIntervalSec`, but this may change).

```javascript
// plugin.js
Pebble.registerSourceHandler(async (request, respond) => {
  // request: { category, item, properties?, iconPixelSize? }
  if (request.category === "finance" && request.item === "stock") {
    const quotes = await loadQuotes();
    respond.data({
      validUntilMs: Date.now() + 60_000,
      instances: quotes.map((q) => ({
        instanceId: q.symbol,
        properties: {
          name:  { shortText: { text: q.name } },
          price: { shortText: { text: `$${q.price}` }, numericValue: { value: q.price, unit: "$" } },
        },
      })),
    });
    return;
  }
  respond.error("PLUGIN_UNAVAILABLE");
});

Pebble.registerActionHandler(async (request, respond) => {
  // request: { action, args }
  try {
    await addStock(request.args.symbol);
    respond.ok({ text: "Added.", refreshed: ["finance/stock"] });
  } catch (e) {
    respond.error("UNKNOWN", e.message);
  }
});
```

What a plugin has to work with:

- **`fetch`** for HTTP, to hosts the user has allowed (see [Approval](#approval)). A blocked host
  rejects like a network error. Plain-HTTP LAN devices are reachable with `LocalNetwork`.
- **`WebSocket`** under the same rules: a blocked host fires `error` then `close`. Binary frames
  arrive as `ArrayBuffer`. Sockets are closed when the session ends, so one only lives for the
  request that opened it. These aren't intended for long-running connections.
- **`navigator.geolocation`** (`getCurrentPosition`, `watchPosition`, `clearWatch`), as in PKJS.
  Needs `Location`; refused, it calls your error callback. Watches end with the session.
- **`localStorage`** for state, shared with your app's PKJS. A plugin is started fresh for each
  request, so don't keep state in variables.
- **`Pebble.secureStorage`** for credentials (tokens, API keys): `await getItem(key)`,
  `setItem(key, value)` and `removeItem(key)`, encrypted by the phone's keystore. Private to your
  plugin (PKJS can't read it) and deleted when the app is removed.
- **`Pebble.refreshSources(["finance/stock"])`** when data changes outside a request (for example,
  the user added a ticker on the settings page).
- **`Pebble.oauth`** for sign-in with OAuth providers, without the plugin ever holding a client
  secret. Declare the connector in the manifest (`"oauth": { "todoist": {} }`), then
  `await Pebble.oauth.authorize("todoist")` for a token and
  `Pebble.oauth.refresh("todoist", broker_refresh)` to renew it; keep both in `Pebble.secureStorage`. The connector has to be registered
  with the Pebble app store (see the `todoist/` example).
- Nothing else: unlike PKJS, a plugin has no WebView APIs.

Return every property and shape you can; text shapes cost nothing once you have the data, and the
app picks. Only produce `icon`/`image` when the request has `iconPixelSize`.

## Settings pages

Until now, a PKJS settings page had to be hosted somewhere (or generated with Clay), and could only
pass data through its URL + when it closed. Settings pages can now be bundled in the pbw, and
can message your plugin/PKJS code in real-time while it's open.

Set `configPage` in `package.json` to an HTML file in the project (bundled into the pbw) or a URL.
If it's set, it's used instead of the legacy `showConfiguration` flow.

```javascript
// in the page
Pebble.addEventListener("ready", (e) => { /* e.target ('plugin' or 'pkjs') is up */ });
const reply = await Pebble.sendMessage("plugin", { type: "listTickers" });

// in plugin.js
Pebble.registerConfigHandler((message, respond) => respond({ tickers: tickers() }));

// in pebble-js-app.js
Pebble.addEventListener("configmessage", (e) => e.respond({ theme: theme() }));
```

Wait for `ready` before sending: the plugin is started when the page opens.

The `plugin-test` dashboard shows what this makes possible: its settings page asks the app's PKJS
for the catalogue of everything installed (built from `enumeratePlugins`) to fill its dropdowns. Each
change is sent straight to PKJS, so the watch updates as the user edits, with no save button. PKJS
pushes the live readings back, so each option is labelled with what it currently reads (for example
"temperature: 21°C"), and each tile shows its current value.

## Packaging and sideloading

The standard `pebble build` doesn't put `plugin.js` or `config.html` in the pbw (or support
plugin-only PBWs), so use [`scripts/pack-plugin-pbw.py`](../scripts/pack-plugin-pbw.py):

```sh
scripts/pack-plugin-pbw.py path/to/my-plugin
```

It works out what to do from the project. If there's a watchapp (`src/c`), it runs `pebble build`,
then adds the plugin files. Otherwise it builds a plugin-only pbw directly. Either way it marks the
pbw as using [permissions](#declaring).

Sideloading plugins is supported from v1.14.0 of the mobile app - but the manifest format changed
in v1.15.0 (along with several other parts of the API).

# Future

We're planning:

- Index integration (plugins as MCP tools).
- Tooling support (the Pebble tool, CloudPebble).
- Permission metadata in the app store, so users can review an app's permissions before adding it.
- Choosing favourite readings in the app, and a preferred plugin when several serve the same thing
  (e.g. weather), plus an API to read those choices.
- More extensive documentation.
- A watch-side (C / Alloy) API, so apps can use plugins without any phone-side JS.
- A way to cache images on the watch (see the Weather demo face which needs a phone connection to
  display images after restarting, right now)
- PebbleKit-Android plugin support.
- Plugins calling other plugins.
- App store support, including a Plugins section for discovery.
- More built-in plugins exposing more things.
