# Pebble Plugins - Preview

Plugins are nearly here (finally). A plugin is a small piece of JavaScript that runs on the phone and:

- **provides data**: (*sources*) that any Pebble watchface/app (and Index) can read
- **takes actions**: turn a light off, add a task, that a watchface/app (and Index) can trigger

It has a generic API, designed to allow any type of data to be exposed, in multiple formats.

This doc lives here while plugins are under active development, and will move once they're stable.
Right now they're supported in the Pebble mobile app (v1.16.x), but are in preview, and disabled by
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
- Actions let a watch (and Index) control things like lights or
  tasks through a plugin, without every app integrating with every service.
- Index uses plugins too, so adding a to-do list
  service (like the Todoist, TickTick and Notion demos) is just a plugin, with no change to the
  Pebble app.

# What can I try now?

- Try the example plugins, watchface and app.
- Build a watchface or app that uses plugin data.
- Build a plugin.

First, enable plugins in the mobile app (v1.16.x or above): **Settings → Plugins → Plugins / permissions preview**. A **Plugins** tab appears in the Apps
screen listing plugins and their settings pages. The demo apps below are on the app store.

Note: this document represents the API/functionality of mobile app version 1.16.x. Apps/plugins built
previously may not work, as there have been many changes to both in 1.16.

## Try the demo apps and plugins

Source for each is in [coredevices/pebble-plugins](https://github.com/coredevices/pebble-plugins):

- **[`weather-face`](https://github.com/coredevices/pebble-plugins/tree/main/weather-face)** ("Weather Face (Plugin Demo)"): a watchface built entirely from a weather
  plugin: any installed one (the built-in one, `met-weather`, or yours), picked on its settings
  page.
- **[`complication-face`](https://github.com/coredevices/pebble-plugins/tree/main/complication-face)** ("Complication Face (Plugin Demo)"): the time and date with two slots,
  a number and a bitmap, filled from the user's preferred readings (`preferences/complications`):
  each takes the first entry offering its shape that the other hasn't taken.
- **[`met-weather`](https://github.com/coredevices/pebble-plugins/tree/main/met-weather)**: weather for your weather locations from MET Norway (api.met.no), in your
  units, an alternative to the built-in weather plugin. It can also be the watch's weather
  provider (Settings → Weather → Weather provider). Shows `Pebble.subscribeSource`,
  `navigator.geolocation` and drawing condition images in JS.
- **[`plugin-test`](https://github.com/coredevices/pebble-plugins/tree/main/plugin-test)** ("Dashboard (Plugin Demo)"): a watchapp with eight tiles, each set from its
  settings page to any reading from any installed plugin. This shows what data is available through the example/built-in plugins. You can also configure taps to take actions, or make a tile a button for any action that takes no arguments (e.g. "Back", the default, goes to the previous app). A tile can also show the date, from the watch's own clock.
- **[`stocks`](https://github.com/coredevices/pebble-plugins/tree/main/stocks)**: share prices for tickers you pick on its settings page.
- **[`hue`](https://github.com/coredevices/pebble-plugins/tree/main/hue)**: Control Philips Hue lights on your local bridge (pair from its settings page).
- **[`notion`](https://github.com/coredevices/pebble-plugins/tree/main/notion)**, **[`ticktick`](https://github.com/coredevices/pebble-plugins/tree/main/ticktick)**, **[`todoist`](https://github.com/coredevices/pebble-plugins/tree/main/todoist)**: examples of plugins that sign in with OAuth.

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
    instance        "3"                one particular room (its name property: "Kitchen")
      property      brightness         one reading of it
        shape       numericValue -> { value: 64, unit: "%", min: 0, max: 100 }
```

You can subscribe to an *item* and get every property of every instance of it, and updates when they
change. Instances have a stable `instanceId`; `name` is the property to display for one.

An **action** is something a plugin can do (`set_on`, `add_task`). It takes JSON arguments described
by a JSON Schema in the manifest, and can say which sources it affects (`targets`), so a tile showing
a light knows which actions apply to it.

## Multiple instances

Updates carry `instances: [...]`, one per thing (a room, a saved location), in an order that means
something: `instances[0]` is the first ([example](#three-ways-to-use-plugins)). `supportsMultiple`
in a manifest just tells a settings page to offer a picker. Many readings of one thing are a
[series](#series) instead.

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
| `weather`       | `location`                                                        | built-in  |
| `calendar`      | `event`                                                           | built-in  |
| `phone`         | `phone_state`, `find_my_phone`                                    | built-in  |
| `music`         | `track`                                                           | built-in (Android) |
| `notifications` | `notification`                                                    | built-in (Android) |
| `watch`         | `switch_setting`, `number_setting`, `text_setting`, `app_setting` | built-in  |
| `home`          | `home_lights`, `room_lights`, `light`                             | Hue demo  |
| `finance`       | `stock`                                                           | Stocks demo |
| `notes`         | `destination`, `configured`                                       | Notion demo |
| `reminders`     | `list`, `configured`                                              | Todoist, TickTick demos |
| `agent`         | `chats`, `configured`                                             |           |
| `preferences`   | `units`, `complications`, `weather_location`                      | built-in  |

**Weather.** `weather/location` serves one instance per place, with the user's weather locations
(`preferences/weather_location`) under the same `instanceId`s. Temperatures are `numericValue`s
in `°C` or `°F` (say which in `unit`), with `shortText`/`longText` in the user's units. Series
start at the place's local midnight: `stepSec` 3600 for `hourly_*`, 86400 for `daily_*`.

| Property | Shapes | |
| --- | --- | --- |
| `name`, `temperature`, `feels_like`, `high`, `low` | `shortText`, `longText`, `numericValue` | `high`/`low` for the local day |
| `condition`, `condition_code` | `shortText`, `longText` (+ `icon`, `image`) | codes: `sun`, `partly_cloudy`, `cloudy`, `light_rain`, `heavy_rain`, `light_snow`, `heavy_snow`, `rain_and_snow`, `unknown` |
| `uv_index`, `precipitation` (chance, %), `humidity` (%) | `numericValue`, `shortText` | |
| `wind_speed` (`m/s`, `km/h`, `mph` or `knots`), `wind_direction` (degrees it's from) | `numericValue` | |
| `utc_offset` (minutes east of UTC) | `numericValue` | |
| `sunrise`, `sunset` | `timestamp` | today's |
| `hourly_temperature`, `hourly_condition`, `hourly_condition_code` | `series` | |
| `daily_high`, `daily_low`, `daily_condition`, `daily_condition_code`, `daily_precipitation`, `daily_sunrise`, `daily_sunset` | `series` (`timestamp` for the sun) | |

The built-in weather plugin serves all of these, from whichever provider the user chose.

A weather plugin the user picks as their weather provider (Settings → Weather) feeds the watch's
weather app and timeline pins, as well as apps. For that it needs at least `temperature`,
`condition_code`, `utc_offset`, and today's and tomorrow's `daily_high`, `daily_low`,
`daily_condition_code`, `daily_sunrise` and `daily_sunset`; the rest fills in what the watch
shows if it's there. A place it doesn't serve, or a read that fails, falls back to Pebble's own.

A plugin is offered as a notes / reminders / agent destination for the Index agent when it declares
a `create_note` / `create_reminder` / `send_to_agent` action *and* serves `notes/configured` /
`reminders/configured` / `agent/configured`: one instance whose `configured` property is a `boolean`
that is true once the user has signed in and set everything the action needs. While it reads false
the destination shows a Connect button that opens the plugin's settings page. An action without its
`configured` source is not offered.

`send_to_agent` takes `{ message, chat_id? }` and forwards the user's message to an external agent.
A plugin that keeps several chats can serve `agent/chats`, one instance per chat with its id as the
`instanceId` and its nickname as the `name` property; the host lists these and passes the chosen one
as `chat_id`. Without that source the chat sent to is decided by the plugin (usually a new chat each
time). Unlike `create_note` and `create_reminder`, `send_to_agent` is also offered to the Index agent
as a tool, so the user can ask for something to be sent to the agent by name.

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

### Series

A property can also be a **series**: many readings of one thing along an axis, like the
temperature for each hour ahead. Its value is a `series` wrapping the shapes it can be drawn as,
each with `values` in place of its single reading:

```js
hourly_temperature: {
  series: {
    start: 1729468800,   // epoch seconds UTC of values[0] (optional, with stepSec)
    stepSec: 3600,
    numericValue: { values: [14, 15, null, 16], unit: "°C" },   // unit, min, max are shared
    shortText:    { values: ["14°C", "15°C", null, "16°C"] },
  },
},
```

- Every `values` array is the same length, and `null` is a missing point. `icon` and `image`
  series carry one bitmap per point.
- Use `start` and `stepSec` to find *now* in the series, rather than assuming `values[0]` is.
- A series is declared as `series.<shape>`, e.g. `"hourly_temperature": ["series.numericValue",
  "series.shortText"]`. Any shape can be one, timestamps included: `"daily_sunrise":
  ["series.timestamp"]` is `{ "series": { "start": …, "stepSec": 86400, "timestamp": { "values":
  [1798354800, …] } } }`.
- The built-in weather plugin offers `hourly_*` and `daily_*` series on `weather/location`.

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

The built-in permissions are `Internet`, `LocalNetwork`, `Location`, `Microphone`, `Notifications`, `Calendar`,
`Contacts`, `Timeline`, `ReadWatchSettings`/`WriteWatchSettings`, `ReadMusic`/`ControlMusic`,
`LaunchApps`, `FindMyPhone`, `ReadFiles`/`WriteFiles`, `LegacyPebbleKit` and `Health`.

- Each `Internet` domain is its own permission and covers its subdomains. `LocalNetwork` covers
  devices on the LAN.
- `Notifications` can be narrowed to some apps (Android):
  `{ "name": "Notifications", "parameters": { "packages": ["com.google.android.apps.maps"] } }`.
  Each package is its own permission ("Read your notifications from …"), and you only see those
  apps' notifications (by their `package` property). Bare `Notifications` is all of them, and
  allowing it allows every package. Ask for just the apps you need if you can: users are far
  more likely to allow one app's notifications than all of them.
- `ReadFiles` and `WriteFiles` let your settings page ask the user for a file or folder. They take
  no parameters: you can't name paths in the manifest. Each entry the user picks is its own
  permission (`ReadFiles/<path>`, or `WriteFiles/<path>` for read-write), listed on the app's page
  like any other, and nothing is granted without a pick. `WriteFiles` includes reading: declare
  both to let the user allow reading but not writing, and allowing `WriteFiles` allows `ReadFiles`
  with it. See [File access](#file-access).
- `LegacyPebbleKit` lets your watchapp talk to a companion app over PebbleKit Classic. A PebbleKit 2
  companion doesn't need declaring: each package your appinfo lists is asked about on its own.
- `Microphone` allows watch microphone streaming to PebbleKit JS and local `mic_capture_*`
  capture (SDK revision 111); declare it in `usesPermissions`.
- `Health` isn't enforced yet: health data is read on the watch, so enforcing it needs permissions
  synced to the watch, which isn't built yet. Declare it anyway if you use health data.

The [packaging script](#packaging-and-sideloading) marks the pbw as using permissions
(`manifestVersion: 2` in its appinfo). Without that it's a legacy app, which keeps what apps had
before permissions: internet access, location and timeline pins if it has PebbleKit JS, and
`LegacyPebbleKit` if it has an Android companion app. Users can have legacy apps ask too, with
**Ask before older apps use permissions** in settings.

Sources and actions guard themselves with **caller permissions**: what an app must hold to use
them. `calendar/event` needs `Calendar`, `notifications/notification` needs `Notifications`,
`weather/*` needs `Location`. Caller permissions take the same parameters as `usesPermissions`,
so a source can ask its callers for one app's notifications rather than all of them. If your
source needs bare `Notifications`, a caller holding only some apps' is refused
(`PERMISSION_DENIED`) unless your instances have a `package` property to narrow them by; so
either add one, or ask for those apps specifically.

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

Users can allow or deny any permission on the app's page, and choose in settings when apps have to ask.
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
3. **Use what the user already prefers.** Users pick their favourite readings in the Pebble app
   (Settings → Plugins → Complications, up to five), e.g. phone battery first, then the temperature for their first
   weather location, then their next calendar event. `preferences/complications` lists them in
   order, so a face with N slots can show the first N with no configuration (see
   [User preferences](#user-preferences)).

**Reading a source:**

```javascript
const subscription = Pebble.subscribeToSource({
  category: "weather",
  item: "location",
  properties: ["name", "temperature", "condition"],  // optional hint; omit for everything
  iconPixelSize: { w: 48, h: 48 },                   // optional; ask only if you want bitmaps
  onData: ({ instances }) => {
    // the location picked on your settings page, else the user's first
    const here = instances.find((i) => i.instanceId === savedId) ?? instances[0];
    if (!here) return;
    const p = here.properties;
    send(p.name?.shortText?.text, p.temperature?.shortText?.text ?? "--");   // any property can be missing
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
  instances: [                                   // one per room
    {
      instanceId: "3",                           // stable: store this to remember a choice
      properties: {
        name:       { shortText: { text: "Kitchen" } },
        on:         { boolean: { value: true }, shortText: { text: "On" } },
        brightness: { numericValue: { value: 64, unit: "%", min: 0, max: 100 },
                      shortText:    { text: "64%" } },
      },
    },
    {
      instanceId: "5",
      properties: {
        name:       { shortText: { text: "Living room" } },
        on:         { boolean: { value: false }, shortText: { text: "Off" } },
      },                                         // off, so no brightness: draw what is there
    },
  ],
}
```

**Which plugin answers:** pass `plugin: "<uuid>"` only if you depend on that plugin, for example
your own pbw's plugin: you always get it while it's installed and serves the item, whatever the
user prefers. Otherwise (or if it isn't installed) you get the plugin the user chose for that item
in the Pebble app (Settings → Plugins → Preferred sources), else a built-in, else the first by name.
Check `pluginUuid` on the envelope if you need to know which one answered.

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
`INVALID_REQUEST`, `UNKNOWN`, plus `AUTH_REQUIRED` and `INVALID_ARGS` for actions. An error doesn't
end a subscription: it carries on, and the next update may succeed.

## User preferences

The built-in Preferences plugin tells apps how the user wants things shown and what they want to
see. Each source updates when the user changes the setting.

**`preferences/units`** (no permission): one instance, every property a `shortText`.

| Property        | Values                        |
| --------------- | ----------------------------- |
| `locale`        | e.g. `en-GB`, `de-DE`: the watch's language, else the phone's. Use it with `Intl` for decimal separators, dates and so on. |
| `temperature`   | `°C`, `°F`                    |
| `distance`      | `km`, `mi`                    |
| `wind_speed`    | `km/h`, `mph`, `m/s`, `knots` |
| `pressure`      | `hPa`, `inHg`                 |
| `precipitation` | `mm`, `in`                    |
| `clock`         | `12h`, `24h`                  |

**`preferences/complications`** (no permission): the user's readings, one instance per filled
slot in order (`instanceId` is the slot number; empty slots are left out). Each has `category`, `item` and `property` (`shortText`) and `index` (`numericValue`): read
`category/item` as usual and use instance `index`'s `property`. An entry names no plugin and
reveals nothing about the data, so the list is never filtered: reading an entry may ask the user
for permission like any other read, and fails with `PERMISSION_DENIED` until they answer. Skip to
the next entry when one can't be read.

```javascript
Pebble.subscribeToSource({
  category: "preferences",
  item: "complications",
  onData: ({ instances }) => {
    const slot = instances[0]?.properties;
    if (!slot) return;
    const property = slot.property.shortText.text;
    Pebble.subscribeToSource({
      category: slot.category.shortText.text,
      item: slot.item.shortText.text,
      properties: [property],
      onData: ({ instances }) => show(instances[slot.index.numericValue.value]?.properties[property]),
      onError: () => show(null),
    });
  },
});
```

Because slots refer to instances by position, list your instances in a meaningful, stable order,
most important first.

**`preferences/weather_location`** (needs `Location`): the weather locations the user set up in the
Pebble app, in their order. `instanceId` is the location's key; properties are `name`, `latitude`
and `longitude` (absent for the current-location entry) and `current` (`boolean`). A weather plugin
that serves one instance per location under the same `instanceId`s means the same place as the
built-in one.

# Writing a plugin

A plugin ships in a `.pbw`: either alongside a watchapp/watchface, or on its own as a
**plugin-only** pbw (no watch binary). It uses the pbw's uuid and name. The simplest example to
copy is [`stocks`](https://github.com/coredevices/pebble-plugins/tree/main/stocks).

**If your app fetches data from a service and displays it, consider splitting it into a plugin plus
the app, in one pbw.** The app reads from its own plugin (pass your pbw's uuid as `plugin`), and
behaves the same as it does now. But the data is also available to every other watchface and app,
and to Index - and users can show it in a design other than yours.

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
  if (request.action !== "add_stock") return respond.error("INVALID_REQUEST");
  try {
    await addStock(request.args.symbol);
    // Sources this changed: subscriptions to them (the caller's included) are re-read now, so
    // they get the new data without waiting for the next refresh.
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
- **`await Pebble.readSource({ category, item, plugin?, properties?, iconPixelSize? })`** to read
  another plugin's source (built-in or not), e.g. `preferences/units` (see
  [User preferences](#user-preferences)). It rejects with `PLUGIN_UNAVAILABLE`,
  `PERMISSION_DENIED`, `INVALID_REQUEST` or `TIMEOUT`.
- **`Pebble.subscribeSource(sameRequest)`** in your source handler, so the handler runs again
  whenever that source changes (`request.trigger` says which), while anything is subscribed to
  yours. Call it on every run: whatever a run doesn't subscribe to is dropped. While subscribed,
  `readSource` answers from the subscription. At most 8; changes are debounced by a second.

  ```javascript
  Pebble.registerSourceHandler(async function (request, respond) {
    var units = { category: 'preferences', item: 'units' };
    Pebble.subscribeSource(units);
    var unit = (await Pebble.readSource(units)).instances[0].properties.temperature.shortText.text;
    // ...
  });
  ```

  Either way you read as your plugin, with its permissions. What you read can reach your callers,
  so for each caller permission the source needs, every one of your sources and actions must
  require at least as much of its callers as you declared yourself: a plugin that declared
  `Notifications` for Maps only must require (at least) Maps' notifications of its callers, and
  one that declared all notifications must require all of them. A plugin can't read itself, or
  one that already reads it.
- **`Pebble.refreshSources(["finance/stock"])`** when data changes outside a request (for example,
  the user added a ticker on the settings page).
- **`Pebble.oauth`** for sign-in with OAuth providers, without the plugin ever holding a client
  secret. Declare the connector in the manifest (`"oauth": { "todoist": {} }`), then
  `await Pebble.oauth.authorize("todoist")` for a token and
  `Pebble.oauth.refresh("todoist", broker_refresh)` to renew it; keep both in `Pebble.secureStorage`. The connector has to be registered
  with the Pebble app store (see the [`todoist`](https://github.com/coredevices/pebble-plugins/tree/main/todoist) example).
- **`Pebble.fileSystem`** for files and folders on the phone the user has handed your plugin from
  its settings page (see [File access](#file-access)); needs `ReadFiles` / `WriteFiles`.
  `await Pebble.fileSystem.getHandle(id)` gives a `FileSystemFileHandle` /
  `FileSystemDirectoryHandle` with the browser
  [File System Access API](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API)
  surface: `getFile()`, `createWritable()`, `getFileHandle()`, `getDirectoryHandle()`,
  `removeEntry()`, `entries()`, `queryPermission()`.
- **`console.log`** shows up in `pebble logs`, beside your app's PKJS logs.
- Nothing else: unlike PKJS, a plugin has no WebView APIs.

Return every property and shape you can; text shapes cost nothing once you have the data, and the
app picks. Only produce `icon`/`image` when the request has `iconPixelSize`.

The Pebble app checks every update before an app sees it: a shape your manifest doesn't declare, or
one that doesn't match its format (above), is dropped. Why shows up in `pebble logs`.

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

## File access

A plugin can read and write to files/directories the user permits. Declare `ReadFiles` 
(and/or `WriteFiles` to change files; allowing it allows reading too) in `usesPermissions`.

The plugin must have the read/write permission to use the associated mode with the file picker
(called from the settings page) which then grants additional access to that specific file or
directory in that mode.

The resulting file(s) or folder can be stored and reused outside the settings page, such as during 
plugin use to read/write files in response to user requests or to facilitate providing data to apps.

```javascript
// in the settings page
const folder = await Pebble.showDirectoryPicker({ mode: "readwrite" });         // or
const [file] = await Pebble.showOpenFilePicker({ types: [{ accept: { "text/markdown": [".md"] } }] });
await Pebble.sendMessage("plugin", { type: "setFolder", id: folder.id });
const held = await Pebble.fileSystem.grants();     // [{ id, kind, name, mode }]
await Pebble.fileSystem.revoke(held[0].id);

// in plugin.js
Pebble.registerConfigHandler((message, respond) => {
  if (message.type === "setFolder") localStorage.setItem("folder", message.id);
  respond({ ok: true });
});

Pebble.registerActionHandler(async (request, respond) => {
  const dir = await Pebble.fileSystem.getHandle(localStorage.getItem("folder"));
  const file = await dir.getFileHandle("inbox.md", { create: true });
  const existing = await (await file.getFile()).text();
  const writable = await file.createWritable();
  await writable.write(existing + "\n- " + request.args.text);
  await writable.close();
  for await (const [name, entry] of dir.entries()) console.log(name, entry.kind);
  respond.ok({ text: "Added." });
});
```

Cancelling the picker rejects with an `AbortError`; a plugin the user hasn't allowed `ReadFiles`
(or `WriteFiles`, for a read-write pick) gets `NotAllowedError`, as does attempting to write through 
a read-only pick or any use of an entry the user has since withdrawn.

Plugins can revoke access they no longer need (such as if the user is re-configuring the plugin) via 
`Pebble.fileSystem.revoke(id)`.

The `plugin-test` dashboard shows what this makes possible: its settings page asks the app's PKJS
for the catalogue of everything installed (built from `enumeratePlugins`) to fill its dropdowns. Each
change is sent straight to PKJS, so the watch updates as the user edits, with no save button. PKJS
pushes the live readings back, so each option is labelled with what it currently reads (for example
"temperature: 21°C"), and each tile shows its current value.

## Packaging and sideloading

Build with `pebble build`, using SDK 4.42 or later and a `pebble-tool` with plugin support. A
plugin-only project sets `"projectType": "plugin"` in `package.json`. The SDK marks the pbw as using
[permissions](#declaring) (`manifestVersion` 2) by default. The plugins in
[coredevices/pebble-plugins](https://github.com/coredevices/pebble-plugins) are worked examples.

# Future

We're planning:

- More extensive documentation.
- A watch-side (C / Alloy) API, so apps can use plugins without any phone-side JS.
- A way to cache images on the watch (the Weather and Complication demo faces need a phone
  connection to display images after restarting, right now)
- PebbleKit-Android plugin support.
- More built-in plugins exposing more things.
