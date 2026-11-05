/*
 * A fake Unruly for the browser preview. It plays the app's half of the wire protocol
 * (WebSkinBridge.kt) so the real runtime/unruly-bridge.js runs unchanged on top of it.
 *
 * Library: ~2000 songs over 160 albums and 60 artists, with some long names, some albums with no
 * art, multi-disc albums and playlists. The player "plays" in real time.
 *
 * Scenarios, as URL query parameters (the preview passes them through):
 *   library=full|empty|scanning|failed   lyrics=found|plain|notFound|disabled|failed
 *   playing=1|0   song=<n> (queue position to start on)   insets=top,right,bottom,left
 *   run=<js>      code to run once the skin has loaded (for scripted screenshots)
 *   runAt=<ms>    when to run it (default 1200)
 */
(function () {
  "use strict";
  var q = new URLSearchParams(location.search);
  var scenario = {
    library: q.get("library") || "full",
    lyrics: q.get("lyrics") || "found",
    playing: q.get("playing") !== "0",
    song: parseInt(q.get("song") || "0", 10),
    insets: (q.get("insets") || "32,0,24,0").split(",").map(Number),
  };

  // ---- deterministic fake library ----
  var seed = 7;
  function rnd() {
    seed = (seed * 16807) % 2147483647;
    return (seed - 1) / 2147483646;
  }
  function pick(a) {
    return a[Math.floor(rnd() * a.length)];
  }
  var W1 = ["Glass", "Velvet", "Northern", "Paper", "Static", "Golden", "Hollow", "Electric", "Silent", "Neon", "Wild", "Lunar", "Crimson", "Blue", "Slow", "Tidal", "Broken", "Midnight", "Copper", "Distant"];
  var W2 = ["Harbour", "Signals", "Gardens", "Machines", "Rivers", "Hearts", "Light", "Summer", "Engines", "Echoes", "Cities", "Weather", "Animals", "Satellites", "Mirrors", "Highways", "Orchards", "Waves", "Ghosts", "Parade"];
  var NAMES = ["The", "Los", "Saint", "Little", "Big", "Young", "Dr.", "Lady"];
  var GENRES = ["Rock", "Electronic", "Jazz", "Hip-Hop", "Folk", "Ambient", "Classical", "Pop"];
  var LONG = "An Unreasonably Long Title That Will Not Fit On Any Reasonable Screen (Extended Remix)";

  var artists = [], albums = [], songs = [], genres = [], playlists = [];
  var empty = scenario.library !== "full";
  if (!empty) {
    for (var i = 0; i < 60; i++) {
      artists.push({ id: "ar" + i, name: i === 13 ? "Sigur Rós & The Very Long Named Orchestra of Everywhere" : pick(NAMES) + " " + pick(W1) + " " + pick(W2), art: null, albumCount: 0, songCount: 0 });
    }
    GENRES.forEach(function (g, i) {
      genres.push({ id: "g" + i, name: g, art: null, songCount: 0 });
    });
    var n = 0;
    for (var a = 0; a < 160; a++) {
      var ar = artists[a % 60];
      var hasArt = a % 7 !== 3;
      var album = { id: "al" + a, title: a === 5 ? LONG : pick(W1) + " " + pick(W2), artist: ar.name, artistId: ar.id, art: hasArt ? "/__unruly/art/al" + a : null, songCount: 0, durationMs: 0, year: 1968 + (a * 7) % 56, isExplicitForArtist: true };
      var genre = genres[a % genres.length];
      var discs = a % 23 === 0 ? 2 : 1;
      var count = 8 + (a % 9);
      for (var d = 1; d <= discs; d++) {
        for (var t = 1; t <= count; t++) {
          var dur = 95000 + Math.floor(rnd() * 330000);
          var song = { id: "s" + n, title: n % 97 === 11 ? LONG : pick(W1) + " " + pick(W2), artist: ar.name, album: album.title, albumId: album.id, artistId: ar.id, art: album.art, durationMs: dur, trackNumber: t, discNumber: discs > 1 ? d : null, year: album.year, _genre: genre.id };
          songs.push(song);
          album.songCount++;
          album.durationMs += dur;
          ar.songCount++;
          genre.songCount++;
          n++;
        }
      }
      ar.albumCount++;
      if (!ar.art) ar.art = album.art;
      if (!genre.art) genre.art = album.art;
      albums.push(album);
    }
    playlists.push(mkPlaylist("pl0", "Late Night Drive", songs.filter(function (_, i) { return i % 37 === 0; })));
    playlists.push(mkPlaylist("pl1", "Running", songs.filter(function (_, i) { return i % 53 === 1; })));
    playlists.push(mkPlaylist("pl2", "Empty Playlist", []));
  }
  function mkPlaylist(id, name, list) {
    return { id: id, name: name, art: list.length ? list[0].art : null, songCount: list.length, durationMs: list.reduce(function (s, x) { return s + x.durationMs; }, 0), _songs: list.map(function (s) { return s.id; }) };
  }
  var byId = {};
  songs.forEach(function (s) { byId[s.id] = s; });
  function pub(o) {
    var c = {};
    for (var k in o) if (k[0] !== "_") c[k] = o[k];
    return c;
  }

  // ---- fake player ----
  var queue = empty ? [] : songs.slice(0, 40);
  var index = empty ? -1 : Math.min(scenario.song, queue.length - 1);
  var playing = !empty && scenario.playing;
  var position = empty ? 0 : 42000;
  var repeat = "none", shuffled = false;
  var volume = { level: 9, max: 15, fraction: 0.6 };

  var native = { onmessage: null, postMessage: function (s) { setTimeout(function () { receive(JSON.parse(s)); }, 0); } };
  window.__unrulyNative = native;

  function send(o) {
    setTimeout(function () {
      if (native.onmessage) native.onmessage({ data: JSON.stringify(o) });
    }, 4);
  }
  function ev(n, v) { send({ t: "ev", n: n, v: v }); }
  function current() { return index >= 0 ? queue[index] : null; }
  function pushPlayback() {
    var s = current();
    ev("playback", { song: s ? pub(s) : null, isPlaying: playing, positionMs: position, durationMs: s ? s.durationMs : 0, queueIndex: index, repeatMode: repeat, isShuffled: shuffled });
  }
  function pushQueue() { ev("queue", { items: queue.map(pub), index: index }); }
  function setQueue(list, start) {
    queue = list.slice();
    index = list.length ? Math.max(0, Math.min(start || 0, list.length - 1)) : -1;
    position = 0;
    playing = index >= 0;
    pushQueue();
    pushPlayback();
  }
  function go(delta) {
    if (index < 0) return;
    var next = index + delta;
    if (next >= queue.length) next = repeat === "all" ? 0 : queue.length - 1;
    if (next < 0) next = 0;
    index = next;
    position = 0;
    pushQueue();
    pushPlayback();
  }
  var lastTick = Date.now();
  setInterval(function () {
    var now = Date.now();
    if (playing && current()) {
      position += now - lastTick;
      if (position >= current().durationMs) {
        if (repeat === "track") position = 0;
        else if (index < queue.length - 1 || repeat === "all") go(1);
        else { playing = false; position = current().durationMs; }
      }
      pushPlayback();
    }
    lastTick = now;
  }, 250);

  function page(list, o) {
    o = o || {};
    var off = Math.max(0, o.offset || 0), lim = Math.min(1000, Math.max(1, o.limit || 200));
    if (o.sort) {
      var key = { name: "title", artist: "artist", album: "album", year: "year", duration: "durationMs", dateAdded: "id" }[o.sort.by] || "title";
      list = list.slice().sort(function (a, b) {
        var x = a[key] != null ? a[key] : a.name, y = b[key] != null ? b[key] : b.name;
        return (x < y ? -1 : x > y ? 1 : 0) * (o.sort.ascending === false ? -1 : 1);
      });
    }
    return { items: list.slice(off, off + lim).map(pub), total: list.length, offset: off };
  }
  function songList(ids) { return (ids || []).map(function (id) { return byId[id]; }).filter(Boolean); }
  function lyricsFor(id) {
    if (scenario.lyrics === "disabled") return { state: "disabled" };
    if (!byId[id]) return { state: "noTrack" };
    if (scenario.lyrics === "notFound" || (scenario.lyrics === "found" && Number(id.slice(1)) % 5 === 4)) return { state: "notFound" };
    if (scenario.lyrics === "failed") return { state: "failed", message: "Couldn't reach lrclib.net" };
    var lines = [];
    for (var i = 0; i < 40; i++) lines.push(pick(W1) + " " + pick(W2).toLowerCase() + (i % 4 === 3 ? "" : ", " + pick(W1).toLowerCase() + " " + pick(W2).toLowerCase()));
    var synced = scenario.lyrics === "plain" ? [] : lines.map(function (t, i) { return { startMs: 4000 + i * 4500, text: t }; });
    return { state: "found", plain: lines, synced: synced };
  }

  var methods = {
    playPause: function () { if (current()) { playing = !playing; pushPlayback(); } },
    next: function () { go(1); },
    previous: function () { if (position > 3000) { position = 0; pushPlayback(); } else go(-1); },
    seekTo: function (ms) { position = Math.max(0, Math.min(ms, current() ? current().durationMs : 0)); pushPlayback(); },
    toggleShuffle: function () { shuffled = !shuffled; pushPlayback(); },
    cycleRepeatMode: function () { repeat = { none: "all", all: "track", track: "none" }[repeat]; pushPlayback(); },
    setRepeatMode: function (m) { repeat = m; pushPlayback(); },
    playAlbum: function (id, start) { setQueue(songs.filter(function (s) { return s.albumId === id; }), start); },
    playArtist: function (id) { setQueue(songs.filter(function (s) { return s.artistId === id; }), 0); },
    playGenre: function (id) { setQueue(songs.filter(function (s) { return s._genre === id; }), 0); },
    playPlaylist: function (id) { var p = playlists.find(function (x) { return x.id === id; }); setQueue(songList(p && p._songs), 0); },
    playSongs: function (ids, start) { setQueue(songList(ids), start); },
    playSongFromAll: function (id) { setQueue(songs, songs.indexOf(byId[id])); },
    playSongFromAlbum: function (id, al) { var l = songs.filter(function (s) { return s.albumId === al; }); setQueue(l, l.indexOf(byId[id])); },
    playSongFromArtist: function (id, ar) { var l = songs.filter(function (s) { return s.artistId === ar; }); setQueue(l, l.indexOf(byId[id])); },
    playSongFromGenre: function (id, g) { var l = songs.filter(function (s) { return s._genre === g; }); setQueue(l, l.indexOf(byId[id])); },
    playSongFromPlaylist: function (id, pl) { var p = playlists.find(function (x) { return x.id === pl; }); var l = songList(p && p._songs); setQueue(l, l.indexOf(byId[id])); },
    shuffleAll: function () { var l = songs.slice().sort(function () { return rnd() - 0.5; }); shuffled = true; setQueue(l, 0); },
    playQueueItem: function (i) { if (queue[i]) { index = i; position = 0; playing = true; pushQueue(); pushPlayback(); } },
    addToQueue: function (ids) { queue = queue.concat(songList(ids)); pushQueue(); },
    playNext: function (ids) { queue.splice(index + 1, 0, ...songList(ids)); pushQueue(); },
    removeFromQueue: function (i) { if (i !== index && queue[i]) { queue.splice(i, 1); if (i < index) index--; pushQueue(); pushPlayback(); } },
    moveInQueue: function (from, to) { var s = queue.splice(from, 1)[0]; queue.splice(to, 0, s); if (from === index) index = to; else if (from < index && to >= index) index--; else if (from > index && to <= index) index++; pushQueue(); pushPlayback(); },
    createPlaylist: function (name, ids) { playlists.push(mkPlaylist("pl" + Date.now(), name || "Playlist " + (playlists.length + 1), songList(ids))); ev("library", null); },
    addToPlaylist: function (id, ids) { var p = playlists.find(function (x) { return x.id === id; }); if (p) { Object.assign(p, mkPlaylist(p.id, p.name, songList(p._songs.concat(ids)))); ev("library", null); } },
    renamePlaylist: function (id, name) { var p = playlists.find(function (x) { return x.id === id; }); if (p) { p.name = name; ev("library", null); } },
    deletePlaylist: function (id) { playlists = playlists.filter(function (x) { return x.id !== id; }); ev("library", null); },
    removeFromPlaylist: function (id, songId, i) { var p = playlists.find(function (x) { return x.id === id; }); if (p && p._songs[i] === songId) { p._songs.splice(i, 1); Object.assign(p, mkPlaylist(p.id, p.name, songList(p._songs))); ev("library", null); } },
    artists: function (o) { return page(artists, o); },
    albums: function (o) { return page(albums, o); },
    songs: function (o) { return page(songs, o); },
    genres: function (o) { return page(genres, o); },
    playlists: function (o) { return page(playlists, o); },
    albumsByArtist: function (id, o) { return page(albums.filter(function (a) { return a.artistId === id; }), o); },
    songsByGenre: function (id, o) { return page(songs.filter(function (s) { return s._genre === id; }), o); },
    songsByAlbum: function (id) { return songs.filter(function (s) { return s.albumId === id; }).map(pub); },
    songsByPlaylist: function (id) { var p = playlists.find(function (x) { return x.id === id; }); return songList(p && p._songs).map(pub); },
    search: function (text) {
      var t = String(text || "").toLowerCase().trim();
      function m(x) { return t && (x.title || x.name).toLowerCase().indexOf(t) >= 0; }
      return { songs: songs.filter(m).slice(0, 50).map(pub), albums: albums.filter(m).slice(0, 20).map(pub), artists: artists.filter(m).slice(0, 20).map(pub), genres: genres.filter(m).map(pub), playlists: playlists.filter(m).map(pub) };
    },
    findAlbum: function (id) { var a = albums.find(function (x) { return x.id === id; }); return a ? pub(a) : null; },
    findArtist: function (id) { var a = artists.find(function (x) { return x.id === id; }); return a ? pub(a) : null; },
    lyrics: function (id) { return lyricsFor(id); },
    openSettings: function () { console.log("[mock] openSettings() - in the app this opens Unruly's settings"); },
    setCanGoBack: function (b) { canGoBack = !!b; },
    setSystemBars: function () {},
    haptic: function () {},
    setVolume: function (f) { volume = { level: Math.round(f * 15), max: 15, fraction: Math.round(f * 15) / 15 }; ev("volume", volume); return volume; },
    adjustVolume: function (d) { var l = Math.max(0, Math.min(15, volume.level + (d >= 0 ? 1 : -1))); volume = { level: l, max: 15, fraction: l / 15 }; ev("volume", volume); return volume; },
  };
  var canGoBack = false;

  function receive(msg) {
    if (msg.t === "hello") {
      canGoBack = false;
      var ins = scenario.insets;
      ev("insets", { top: ins[0] || 0, right: ins[1] || 0, bottom: ins[2] || 0, left: ins[3] || 0 });
      ev("loadState", scenario.library === "scanning" ? { state: "scanning", loaded: 312, explored: 1840 } : scenario.library === "failed" ? { state: "failed", message: "Couldn't read the music folder" } : { state: "ready", isEmpty: empty });
      ev("volume", volume);
      ev("visibility", true);
      pushQueue();
      pushPlayback();
      return;
    }
    if (msg.t !== "call") return;
    var fn = methods[msg.m];
    if (!fn) {
      console.error('[mock] unknown unruly method "' + msg.m + '"');
      send({ t: "res", id: msg.id, ok: false, err: 'Unknown unruly method "' + msg.m + '"' });
      return;
    }
    try {
      var v = fn.apply(null, msg.a || []);
      send({ t: "res", id: msg.id, ok: true, v: v === undefined ? null : v });
    } catch (e) {
      console.error("[mock] " + msg.m + " threw", e);
      send({ t: "res", id: msg.id, ok: false, err: String(e && e.message) });
    }
  }

  // Dev helpers, also usable from ?run=: press system back the way the app would.
  window.__unrulyPreview = {
    back: function () { if (canGoBack) ev("back", null); else console.log("[mock] back with setCanGoBack(false): the app would exit here"); return canGoBack; },
    canGoBack: function () { return canGoBack; },
  };
  var run = q.get("run");
  if (run) {
    window.addEventListener("load", function () {
      setTimeout(function () {
        try { (0, eval)(run); } catch (e) { console.error("[preview] run= threw: " + e.message); }
      }, parseInt(q.get("runAt") || "1200", 10));
    });
  }
})();
