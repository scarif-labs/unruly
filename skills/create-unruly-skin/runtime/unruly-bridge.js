/*
 * window.unruly - the API an Unruly web skin talks to.
 *
 * This exact file is injected into every web skin by the app (packaged from here into the APK's
 * assets), and by the kit's preview server, which swaps the native channel for a mock player. Do
 * not include it in a skin yourself.
 *
 * Types and docs: ../reference/unruly.d.ts. Wire format: WebSkinBridge.kt.
 */
(function () {
  "use strict";
  if (window.unruly) return;
  var native = window.__unrulyNative;
  if (!native) {
    console.error("unruly: no native channel; is this running inside Unruly or the preview?");
    return;
  }

  var nextId = 1;
  var pending = {};
  var listeners = {};
  // Events whose last value is kept and replayed to late subscribers.
  var STICKY = ["playback", "queue", "insets", "loadState", "volume", "visibility"];
  var last = {};
  var playbackAt = 0;

  function call(method, args) {
    return new Promise(function (resolve, reject) {
      var id = nextId++;
      pending[id] = { resolve: resolve, reject: reject };
      native.postMessage(JSON.stringify({ t: "call", id: id, m: method, a: args || [] }));
    });
  }

  function emit(name, value) {
    (listeners[name] || []).slice().forEach(function (cb) {
      try {
        cb(value);
      } catch (e) {
        console.error("unruly: '" + name + "' listener threw", e);
      }
    });
  }

  function applyInsets(v) {
    var s = document.documentElement && document.documentElement.style;
    if (!s) return;
    ["top", "right", "bottom", "left"].forEach(function (k) {
      s.setProperty("--unruly-inset-" + k, (v[k] || 0) + "px");
    });
  }

  native.onmessage = function (event) {
    var msg;
    try {
      msg = JSON.parse(event.data);
    } catch (e) {
      return;
    }
    if (msg.t === "res") {
      var p = pending[msg.id];
      if (!p) return;
      delete pending[msg.id];
      if (msg.ok) p.resolve(msg.v);
      else p.reject(new Error(msg.err));
    } else if (msg.t === "ev") {
      if (msg.n === "playback") playbackAt = Date.now();
      if (msg.n === "insets") applyInsets(msg.v);
      if (STICKY.indexOf(msg.n) >= 0) last[msg.n] = msg.v;
      if (msg.n === "playback" && !readyFired) {
        readyFired = true;
        resolveReady(api);
      }
      emit(msg.n, msg.v);
    }
  };

  var readyFired = false;
  var resolveReady;
  var ready = new Promise(function (r) {
    resolveReady = r;
  });

  function ids(songs) {
    return (songs || []).map(function (s) {
      return typeof s === "string" ? s : s.id;
    });
  }

  function fire(name) {
    return function () {
      return call(name, Array.prototype.slice.call(arguments));
    };
  }

  var api = {
    apiVersion: 1,
    // Latest values of the sticky events; null/defaults until the first push.
    get playback() { return last.playback || null; },
    get queue() { return last.queue || { items: [], index: -1 }; },
    get insets() { return last.insets || { top: 0, right: 0, bottom: 0, left: 0 }; },
    get loadState() { return last.loadState || { state: "idle" }; },
    get volume() { return last.volume || null; },
    get visibility() { return "visibility" in last ? last.visibility : true; },
    ready: ready,

    on: function (name, cb) {
      (listeners[name] = listeners[name] || []).push(cb);
      if (name in last) {
        try {
          cb(last[name]);
        } catch (e) {
          console.error("unruly: '" + name + "' listener threw", e);
        }
      }
      return function off() {
        listeners[name] = (listeners[name] || []).filter(function (x) {
          return x !== cb;
        });
      };
    },

    position: function () {
      var p = api.playback;
      if (!p) return 0;
      if (!p.isPlaying) return p.positionMs;
      return Math.min(p.positionMs + (Date.now() - playbackAt), p.durationMs || Infinity);
    },

    // Playback
    playPause: fire("playPause"),
    next: fire("next"),
    previous: fire("previous"),
    seekTo: fire("seekTo"),
    toggleShuffle: fire("toggleShuffle"),
    cycleRepeatMode: fire("cycleRepeatMode"),
    setRepeatMode: fire("setRepeatMode"),
    playAlbum: function (albumId, startIndex) {
      return call("playAlbum", [albumId, startIndex || 0]);
    },
    playArtist: fire("playArtist"),
    playGenre: fire("playGenre"),
    playPlaylist: fire("playPlaylist"),
    playSongs: function (songs, startIndex) {
      return call("playSongs", [ids(songs), startIndex || 0]);
    },
    playSongFromAll: fire("playSongFromAll"),
    playSongFromAlbum: fire("playSongFromAlbum"),
    playSongFromArtist: fire("playSongFromArtist"),
    playSongFromGenre: fire("playSongFromGenre"),
    playSongFromPlaylist: fire("playSongFromPlaylist"),
    shuffleAll: fire("shuffleAll"),

    // Queue
    playQueueItem: fire("playQueueItem"),
    addToQueue: function (songs) {
      return call("addToQueue", [ids(songs)]);
    },
    playNext: function (songs) {
      return call("playNext", [ids(songs)]);
    },
    removeFromQueue: fire("removeFromQueue"),
    moveInQueue: fire("moveInQueue"),

    // Playlists
    createPlaylist: function (name, songs) {
      return call("createPlaylist", [name == null ? null : name, ids(songs)]);
    },
    addToPlaylist: function (playlistId, songs) {
      return call("addToPlaylist", [playlistId, ids(songs)]);
    },
    renamePlaylist: fire("renamePlaylist"),
    deletePlaylist: fire("deletePlaylist"),
    removeFromPlaylist: fire("removeFromPlaylist"),

    library: {
      artists: function (o) { return call("artists", [o || {}]); },
      albums: function (o) { return call("albums", [o || {}]); },
      songs: function (o) { return call("songs", [o || {}]); },
      genres: function (o) { return call("genres", [o || {}]); },
      playlists: function (o) { return call("playlists", [o || {}]); },
      albumsByArtist: function (id, o) { return call("albumsByArtist", [id, o || {}]); },
      songsByGenre: function (id, o) { return call("songsByGenre", [id, o || {}]); },
      songsByAlbum: fire("songsByAlbum"),
      songsByPlaylist: fire("songsByPlaylist"),
      search: fire("search"),
      findAlbum: fire("findAlbum"),
      findArtist: fire("findArtist"),
    },

    lyrics: fire("lyrics"),

    // Host
    openSettings: fire("openSettings"),
    setCanGoBack: function (canGoBack) {
      return call("setCanGoBack", [!!canGoBack]);
    },
    setSystemBars: function (options) {
      return call("setSystemBars", [options || {}]);
    },
    haptic: function (kind) {
      return call("haptic", [kind || "click"]);
    },
    setVolume: fire("setVolume"),
    adjustVolume: fire("adjustVolume"),
  };

  Object.defineProperty(window, "unruly", { value: Object.freeze(api), writable: false });
  window.addEventListener("error", function (e) {
    console.error("unruly skin error: " + e.message + " @ " + e.filename + ":" + e.lineno);
  });
  // A rejected call nobody caught (e.g. a misspelt method) would otherwise vanish silently.
  window.addEventListener("unhandledrejection", function (e) {
    var r = e.reason;
    console.error("unruly: unhandled rejection: " + (r && r.message ? r.message : r));
  });
  native.postMessage(JSON.stringify({ t: "hello" }));
})();
