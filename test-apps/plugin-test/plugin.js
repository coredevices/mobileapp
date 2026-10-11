// Demo plugin bundled in the dashboard test app. It serves one `demo/message` source whose text
// the config page sets over the plugin config-message channel — enough to exercise pbw plugin
// sideloading end to end (manifest in appinfo -> registry -> this script -> a served source).
// Its `clear_message` action is destructive and requires confirmation, to exercise the agent's
// allow/deny prompt.

var STORE_KEY = 'demo_message';

function currentText() {
  return localStorage.getItem(STORE_KEY) || 'Hello from the demo plugin';
}

Pebble.registerSourceHandler(function (request, respond) {
  respond.data({
    instances: [
      { instanceId: 'message', properties: { text: { shortText: { text: currentText() } } } },
    ],
  });
});

Pebble.registerConfigHandler(function (message, respond) {
  if (message && message.type === 'setText') {
    localStorage.setItem(STORE_KEY, String(message.text || ''));
    Pebble.refreshSources(['demo/message']);
    respond({ ok: true, text: currentText() });
  } else if (message && message.type === 'status') {
    respond({ text: currentText() });
  } else {
    respond({ ok: false });
  }
});

Pebble.registerActionHandler(function (request, respond) {
  if (request.action !== 'clear_message') {
    respond.error('INVALID_REQUEST', 'Unsupported demo action.');
    return;
  }
  localStorage.removeItem(STORE_KEY);
  Pebble.refreshSources(['demo/message']);
  respond.ok({ text: 'Demo message reset', refreshed: ['demo/message'] });
});
