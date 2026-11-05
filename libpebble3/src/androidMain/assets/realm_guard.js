/*
 * Android only. WebRTC, WebSocket and workers reach the network without passing through
 * shouldInterceptRequest, and every same-origin frame hands out fresh, ungated copies of them.
 * __pebbleGuardRealms(window, allowed, purgeFrames) takes them away from the window and from every
 * same-origin frame in it, leaving WebSocket for the URLs `allowed` passes (or alone, without it).
 * With purgeFrames frames are removed outright; otherwise each new one is torn down and recreated,
 * and only the recreated window is stripped and kept. Page code can reach a frame's first window
 * before any check runs (an indexed <select> setter inserts nodes and can't be wrapped), so that
 * window is never trusted. Destroying it in the same turn stops WebRTC it started, but not always a
 * WebSocket: the page's CSP must restrict connect-src, which every frame inherits.
 * Must run before any page script, and uses only what it captured then: the page can replace any
 * global or prototype method afterwards.
 */
var __pebbleGuardRealms = (function () {
  'use strict';
  var call = Function.prototype.call;
  var uncurry = Function.prototype.bind.bind(call);
  var apply = uncurry(Function.prototype.apply);
  var defineProperty = Object.defineProperty;
  var getOwnPropertyDescriptor = Object.getOwnPropertyDescriptor;
  var getPrototypeOf = Object.getPrototypeOf;
  var hasOwn = uncurry(Object.prototype.hasOwnProperty);
  var removeElement = uncurry(Element.prototype.remove);
  var insertBefore = uncurry(Node.prototype.insertBefore);
  var removeChild = uncurry(Node.prototype.removeChild);
  var parentNode = uncurry(findGetter(Node.prototype, 'parentNode'));
  var nextSibling = uncurry(findGetter(Node.prototype, 'nextSibling'));
  var addListener = uncurry(EventTarget.prototype.addEventListener);
  var observe = uncurry(MutationObserver.prototype.observe);
  var windowLength = uncurry(findGetter(window, 'length'));
  var frameElement = uncurry(findGetter(window, 'frameElement'));
  var performance = uncurry(findGetter(window, 'performance'));
  var entriesByType = uncurry(Performance.prototype.getEntriesByType);
  var entryName = uncurry(findGetter(PerformanceEntry.prototype, 'name'));
  var responseStart = uncurry(findGetter(PerformanceResourceTiming.prototype, 'responseStart'));
  var shadowHost = uncurry(findGetter(ShadowRoot.prototype, 'host'));
  var weakGet = uncurry(WeakMap.prototype.get);
  var weakSet = uncurry(WeakMap.prototype.set);
  var slice = uncurry(String.prototype.slice);
  var eventTarget = uncurry(findGetter(Event.prototype, 'target'));
  var stopImmediatePropagation = uncurry(Event.prototype.stopImmediatePropagation);
  // Every copy of this script, in every frame, shares it.
  var MARK = Symbol.for('pebble.realmGuard');

  var REMOVED = ['RTCPeerConnection', 'webkitRTCPeerConnection', 'WebTransport', 'WebSocketStream', 'Worker',
    'SharedWorker', 'customElements'];
  var FRAMES = ['HTMLIFrameElement', 'HTMLFrameElement', 'HTMLObjectElement', 'HTMLEmbedElement'];
  // Everything that can connect a node, and so a frame, to a document.
  var INSERTING = [
    ['Node', 'appendChild'], ['Node', 'insertBefore'], ['Node', 'replaceChild'],
    ['Element', 'append'], ['Element', 'prepend'], ['Element', 'before'], ['Element', 'after'],
    ['Element', 'replaceWith'], ['Element', 'replaceChildren'], ['Element', 'insertAdjacentElement'],
    ['Element', 'insertAdjacentHTML'], ['Element', 'setHTMLUnsafe'], ['Element', 'moveBefore'],
    ['CharacterData', 'before'], ['CharacterData', 'after'], ['CharacterData', 'replaceWith'],
    ['DocumentType', 'before'], ['DocumentType', 'after'], ['DocumentType', 'replaceWith'],
    ['Document', 'append'], ['Document', 'prepend'], ['Document', 'replaceChildren'], ['Document', 'open'],
    ['Document', 'execCommand'], ['Document', 'moveBefore'],
    ['DocumentFragment', 'append'], ['DocumentFragment', 'prepend'], ['DocumentFragment', 'replaceChildren'],
    ['DocumentFragment', 'moveBefore'], ['ShadowRoot', 'setHTMLUnsafe'],
    ['Range', 'insertNode'], ['Range', 'surroundContents'],
    ['HTMLSelectElement', 'add'], ['HTMLOptionsCollection', 'add'],
  ];
  var INSERTING_SETTERS = [['Element', 'innerHTML'], ['Element', 'outerHTML'], ['ShadowRoot', 'innerHTML'],
    ['HTMLTableElement', 'caption'], ['HTMLTableElement', 'tHead'], ['HTMLTableElement', 'tFoot'],
    ['Document', 'body']];
  // Fire synchronously inside an insertion, before it returns.
  var MUTATION_EVENTS = ['DOMNodeInserted', 'DOMNodeInsertedIntoDocument', 'DOMSubtreeModified'];
  // Every object handed to the platform has no prototype: the page can add properties to Object.prototype.
  var OBSERVED = { __proto__: null, childList: true, subtree: true };

  function findGetter(object, name) {
    for (var o = object; o; o = getPrototypeOf(o)) {
      var d = getOwnPropertyDescriptor(o, name);
      if (d) return d.get;
    }
    return undefined;
  }

  // The navigation that made w, or null for a frame's first window, which has none.
  function navigation(w) {
    var entries = entriesByType(performance(w), 'navigation');
    return entries.length === 1 ? entries[0] : null;
  }

  // A javascript: document takes the URL of the page it replaces, but nothing was fetched for it.
  function isPageLoad(entry) {
    var url = entryName(entry);
    return (slice(url, 0, 5) === 'http:' || slice(url, 0, 6) === 'https:') && responseStart(entry) > 0;
  }

  return function (top, allowed, purgeFrames) {
    var kept = new WeakMap();
    var rebuilding = null;
    var loadTarget = null, staleLoad = false;

    function remove(w, key) {
      try { defineProperty(w, key, { __proto__: null, value: undefined, writable: false, configurable: false }); } catch (e) {
        try { delete w[key]; } catch (e2) { /* nothing more to do */ }
      }
    }

    function gateWebSocket(w) {
      var Native = w.WebSocket, Url = w.URL, urlHref = uncurry(findGetter(w.URL.prototype, 'href'));
      if (!Native) return;
      var Gated = function WebSocket(url, protocols) {
        // Converted once: what's checked is what's connected to.
        var href = urlHref(new Url('' + url, w.location.href));
        if (!allowed(href)) throw new w.DOMException('This app may not connect to ' + href, 'SecurityError');
        return protocols === undefined ? new Native(href) : new Native(href, protocols);
      };
      Gated.prototype = Native.prototype;
      defineProperty(Native.prototype, 'constructor', { __proto__: null, value: Gated });
      var states = ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'];
      for (var i = 0; i < states.length; i++) defineProperty(Gated, states[i], { __proto__: null, value: Native[states[i]] });
      defineProperty(w, 'WebSocket', { __proto__: null, value: Gated, writable: false, configurable: false });
    }

    function wrapMethod(proto, name, before, after) {
      var original = proto && hasOwn(proto, name) ? proto[name] : undefined;
      if (typeof original !== 'function') return;
      defineProperty(proto, name, {
        __proto__: null,
        value: function () {
          if (before) before(this);
          try { return apply(original, this, arguments); } finally { after(); }
        },
        writable: false, configurable: false,
      });
    }

    function wrapAccessor(proto, name, wrapGet, wrapSet) {
      var d = proto && getOwnPropertyDescriptor(proto, name);
      if (!d) return;
      defineProperty(proto, name, {
        __proto__: null,
        enumerable: d.enumerable,
        configurable: false,
        get: d.get && wrapGet ? wrapGet(d.get) : d.get,
        set: d.set && wrapSet ? wrapSet(d.set) : d.set,
      });
    }

    // Strips w and returns the function that keeps it that way.
    function lock(w) {
      var observer;
      var check = function () {
        for (var i = windowLength(w) - 1; i >= 0; i--) {
          var child = w[i], key, element;
          try { key = getPrototypeOf(child); element = frameElement(child); } catch (e) { continue; }
          if (!key || !element) continue; // Cross-origin: unreachable from here.
          try {
            settle(child, key, element, check);
          } catch (e) {
            removeElement(element); // Whatever went wrong, the frame doesn't stay.
          }
        }
      };
      var onLoad = function (event) {
        var outerTarget = loadTarget, outerStale = staleLoad;
        loadTarget = eventTarget(event);
        staleLoad = false;
        try {
          check();
          // The frame was recreated, and the page has had the recreated one's load already.
          if (staleLoad) stopImmediatePropagation(event);
        } finally {
          loadTarget = outerTarget;
          staleLoad = outerStale;
        }
      };
      // document.open() drops every listener on the document, so this re-adds it after each one.
      var refresh = function () {
        addListener(w.document, 'load', onLoad, true);
        observe(observer, w.document, OBSERVED);
        check();
      };
      defineProperty(w, MARK, { __proto__: null, value: refresh });
      observer = new w.MutationObserver(check);
      var i;
      for (i = 0; i < REMOVED.length; i++) remove(w, REMOVED[i]);
      if (allowed) gateWebSocket(w);
      if (w.ServiceWorkerContainer) {
        defineProperty(w.ServiceWorkerContainer.prototype, 'register', {
          __proto__: null,
          value: function () { return w.Promise.reject(new w.DOMException('Service workers are not available', 'SecurityError')); },
          writable: false, configurable: false,
        });
      }
      for (i = 0; i < INSERTING.length; i++) {
        if (w[INSERTING[i][0]]) wrapMethod(w[INSERTING[i][0]].prototype, INSERTING[i][1], null, refresh);
      }
      if (w.Document) {
        // Writing to a closed document opens it first, which drops the load listener before any frame
        // in the markup loads: open it with nothing, put the listener back, then write.
        var write = w.Document.prototype.write;
        var openFirst = function (document) { apply(write, document, ['']); refresh(); };
        wrapMethod(w.Document.prototype, 'write', openFirst, refresh);
        wrapMethod(w.Document.prototype, 'writeln', openFirst, refresh);
      }
      var afterSet = function (set) {
        var setter = uncurry(set);
        return function (v) { try { setter(this, v); } finally { refresh(); } };
      };
      for (i = 0; i < INSERTING_SETTERS.length; i++) {
        if (w[INSERTING_SETTERS[i][0]]) wrapAccessor(w[INSERTING_SETTERS[i][0]].prototype, INSERTING_SETTERS[i][1], null, afterSet);
      }
      var beforeGet = function (get) {
        var getter = uncurry(get);
        return function () { refresh(); return getter(this); };
      };
      for (i = 0; i < FRAMES.length; i++) {
        if (!w[FRAMES[i]]) continue;
        var proto = w[FRAMES[i]].prototype;
        wrapAccessor(proto, 'contentWindow', beforeGet, null);
        wrapAccessor(proto, 'contentDocument', beforeGet, null);
        wrapMethod(proto, 'getSVGDocument', null, refresh);
      }
      // The observer only sees into shadow roots it's told about, and the <select> setter can insert
      // into one: watch every root the page can get hold of.
      var watch = function (root) {
        try { shadowHost(root); observe(observer, root, OBSERVED); } catch (e) { /* not a shadow root */ }
        return root;
      };
      var attachShadow = w.Element.prototype.attachShadow;
      defineProperty(w.Element.prototype, 'attachShadow', {
        __proto__: null,
        value: function () { return watch(apply(attachShadow, this, arguments)); },
        writable: false, configurable: false,
      });
      wrapAccessor(w.Element.prototype, 'shadowRoot', function (get) {
        var getter = uncurry(get);
        return function () { return watch(getter(this)); };
      }, null);
      var composedPath = w.Event.prototype.composedPath;
      defineProperty(w.Event.prototype, 'composedPath', {
        __proto__: null,
        value: function () {
          var path = apply(composedPath, this, arguments);
          for (var j = 0; j < path.length; j++) watch(path[j]);
          return path;
        },
        writable: false, configurable: false,
      });
      var add = w.EventTarget.prototype.addEventListener;
      defineProperty(w.EventTarget.prototype, 'addEventListener', {
        __proto__: null,
        value: function (type) {
          var name = '' + type;
          for (var j = 0; j < MUTATION_EVENTS.length; j++) if (name === MUTATION_EVENTS[j]) return undefined;
          var args = arguments;
          args[0] = name;
          return apply(add, this, args);
        },
        writable: false, configurable: false,
      });
      return refresh;
    }

    function settle(child, key, element, check) {
      if (purgeFrames) {
        removeElement(element);
        return;
      }
      var keep = weakGet(kept, key);
      if (keep) {
        keep();
        return;
      }
      var entry = navigation(child), url = entry && entryName(entry);
      if (!entry || url === '' || url === 'about:blank') {
        if (element === rebuilding) {
          keep = lock(child);
          weakSet(kept, key, keep);
          keep();
        } else {
          rebuild(element, check);
        }
      } else if (isPageLoad(entry) && hasOwn(child, MARK)) {
        // A page load runs this script at document start, before page code can reach the window.
        child[MARK]();
      } else {
        // srcdoc, javascript: and about:blank#… documents, or a page load that didn't get this
        // script: page code may have reached the window first, and recreating the frame would
        // only load it again.
        removeElement(element);
      }
    }

    // Detaching the frame destroys its window, and any WebRTC the page started in it, before the
    // network sees it. Reattaching makes a fresh one, which check() keeps while it's `rebuilding`.
    // A frame's first load can fire inside the reattach; the capture listener sees it before the page.
    function rebuild(element, check) {
      var parent = parentNode(element), next = nextSibling(element), outer = rebuilding;
      rebuilding = element;
      if (element === loadTarget) staleLoad = true;
      try {
        removeChild(parent, element);
        insertBefore(parent, element, next);
        check();
      } finally {
        rebuilding = outer;
      }
    }

    var refresh;
    try { refresh = hasOwn(top, MARK) ? top[MARK] : undefined; } catch (e) { return; }
    (refresh || lock(top))();
  };
})();
