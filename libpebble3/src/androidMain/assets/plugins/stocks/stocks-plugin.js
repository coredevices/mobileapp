// Stock prices from Yahoo Finance's chart endpoint: no key, no auth, one call per ticker.
//
// Which tickers is entirely the user's choice, made on the settings page, and each one becomes
// an instance of `finance/stock`. Its properties are the facets of one quote — price, the day's
// change in cash, and that change as a percentage — so a watchface shows whichever fits.

var STORE_KEY = 'stocks';
var QUOTE_URL = 'https://query1.finance.yahoo.com/v8/finance/chart/';
var REFRESH_MS = 60 * 1000;
var SOURCES = ['finance/stock'];

var CURRENCY = { USD: '$', GBP: '£', EUR: '€', JPY: '¥' };
// Shown until the user picks their own. Emptying the list keeps it empty.
var DEFAULT = [{ symbol: '^GSPC', name: 'S&P 500' }];

// Promise wrapper over the host's XMLHttpRequest. Resolves to { ok, json } and never rejects.
function http(url) {
  return new Promise(function (resolve) {
    var request = new XMLHttpRequest();
    request.open('GET', url, true);
    request.onload = function () {
      var json = null;
      try { json = JSON.parse(request.responseText); } catch (e) { /* not json */ }
      resolve({ ok: request.status >= 200 && request.status < 300, json: json });
    };
    request.onerror = function () { resolve({ ok: false, json: null }); };
    request.send(null);
  });
}

/** The user's picks, in the order they added them: [{ symbol, name }]. */
function stocks() {
  var saved;
  try { saved = JSON.parse(localStorage.getItem(STORE_KEY)); } catch (e) { saved = null; }
  return saved || DEFAULT;
}

function saveStocks(list) {
  localStorage.setItem(STORE_KEY, JSON.stringify(list));
}

// ---------------------------------------------------------------- quotes

async function quote(symbol) {
  var response = await http(QUOTE_URL + encodeURIComponent(symbol) + '?interval=1d&range=1d');
  var result = response.json && response.json.chart && response.json.chart.result;
  var meta = result && result[0] && result[0].meta;
  if (!meta || typeof meta.regularMarketPrice !== 'number') return null;

  var price = meta.regularMarketPrice;
  var previous = typeof meta.previousClose === 'number' ? meta.previousClose
    : (typeof meta.chartPreviousClose === 'number' ? meta.chartPreviousClose : price);
  return {
    symbol: meta.symbol || symbol,
    name: meta.shortName || meta.longName || symbol,
    currency: CURRENCY[meta.currency] || ((meta.currency || '') + ' '),
    price: price,
    change: price - previous,
    percent: previous ? ((price - previous) / previous) * 100 : 0,
  };
}

function signed(value, prefix) {
  return (value < 0 ? '-' : '+') + prefix + Math.abs(value).toFixed(2);
}

function propertiesOf(quoted) {
  var price = quoted.currency + quoted.price.toFixed(2);
  var change = signed(quoted.change, quoted.currency);
  var percent = signed(quoted.percent, '') + '%';
  function reading(text) {
    return { shortText: { text: text }, longText: { text: text } };
  }
  return {
    ticker: { shortText: { text: quoted.symbol } },
    name: { shortText: { text: quoted.name } },
    // A tile with room for a line gets price and change together.
    price: {
      shortText: { text: price },
      longText: { text: price + '  ' + percent },
    },
    day_change: reading(change),
    day_change_percent: reading(percent),
  };
}

Pebble.registerSourceHandler(async function (request, respond) {
  var quotes = await Promise.all(stocks().map(function (stock) { return quote(stock.symbol); }));
  respond.data({
    validUntilMs: Date.now() + REFRESH_MS,
    // A ticker that failed to fetch drops out rather than taking the others with it.
    instances: quotes.filter(Boolean).map(function (quoted) {
      return { instanceId: quoted.symbol, properties: propertiesOf(quoted) };
    }),
  });
});

// ---------------------------------------------------------------- config page

Pebble.registerConfigHandler(async function (message, respond) {
  switch (message && message.type) {
    case 'list':
      respond({ stocks: stocks() });
      return;
    case 'add': {
      var symbol = String(message.symbol || '').trim().toUpperCase();
      if (!symbol) {
        respond({ error: 'Enter a ticker symbol.' });
        return;
      }
      var already = stocks().filter(function (stock) { return stock.symbol === symbol; });
      if (already.length) {
        respond({ error: symbol + ' is already on the list.', stocks: stocks() });
        return;
      }
      var quoted = await quote(symbol);
      if (!quoted) {
        respond({ error: 'No quote for ' + symbol + '.', stocks: stocks() });
        return;
      }
      var added = stocks().concat([{ symbol: quoted.symbol, name: quoted.name }]);
      saveStocks(added);
      Pebble.refreshSources(SOURCES);
      respond({ stocks: added });
      return;
    }
    case 'remove': {
      var kept = stocks().filter(function (stock) { return stock.symbol !== message.symbol; });
      saveStocks(kept);
      Pebble.refreshSources(SOURCES);
      respond({ stocks: kept });
      return;
    }
    default:
      respond({ error: 'unknown message type' });
  }
});
