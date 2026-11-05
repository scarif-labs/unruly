// Starter skin: albums grid -> album -> play, plus a mini player. Shows the patterns every skin
// needs: render from events, a navigation stack wired to system back, paging, no-art and
// empty/scanning states, insets, and pausing animation in the background.
"use strict";

const $ = (s) => document.querySelector(s);
const el = (tag, cls, text) => {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
};

// ---- navigation: a stack of screens, mirrored to system back ----
const stack = [];
function push(screen) {
  stack.push(screen);
  render();
}
function pop() {
  if (stack.length > 1) stack.pop();
  render();
}
unruly.on("back", pop);
function render() {
  // Keep this truthful: true whenever there is a screen to go back to.
  unruly.setCanGoBack(stack.length > 1);
  const screen = $("#screen");
  screen.replaceChildren();
  screen.scrollTop = 0;
  stack[stack.length - 1](screen);
}

// ---- screens ----
function cover(art, cls = "cover") {
  const c = el("div", cls);
  if (art) c.style.backgroundImage = `url("${art}")`;
  else c.textContent = "♪"; // Always design the no-art case.
  return c;
}

function albumsScreen(screen) {
  screen.append(el("h1", null, "Albums"));
  const state = unruly.loadState;
  if (state.state === "scanning") return screen.append(el("p", "empty", "Scanning your music…"));
  if (state.state === "failed") return screen.append(el("p", "empty", state.message));
  const grid = el("div", "grid");
  screen.append(grid);

  // Page as the user scrolls; never fetch the whole library at once.
  let offset = 0, total = Infinity, loading = false;
  async function more() {
    if (loading || offset >= total) return;
    loading = true;
    const page = await unruly.library.albums({ offset, limit: 60, sort: { by: "name" } });
    total = page.total;
    offset += page.items.length;
    if (total === 0) screen.append(el("p", "empty", "No music yet. Add a folder in settings."));
    for (const album of page.items) {
      const card = el("button", "card");
      card.append(cover(album.art), el("div", "title", album.title), el("div", "sub", album.artist));
      card.onclick = () => push((s) => albumScreen(s, album));
      grid.append(card);
    }
    loading = false;
    if (screen.scrollHeight <= screen.clientHeight + 200) more();
  }
  screen.onscroll = () => {
    if (screen.scrollTop + screen.clientHeight > screen.scrollHeight - 600) more();
  };
  more();
}

async function albumScreen(screen, album) {
  screen.onscroll = null;
  const back = el("button", "back", "‹ Albums");
  back.onclick = pop;
  screen.append(back, el("h1", "title", album.title), el("p", "sub meta", [album.artist, album.year].filter(Boolean).join(" · ")));
  const songs = await unruly.library.songsByAlbum(album.id);
  songs.forEach((song, i) => {
    const row = el("button", "row");
    row.dataset.song = song.id;
    const text = el("div", "text");
    text.append(el("div", "title", song.title), el("div", "sub", fmt(song.durationMs)));
    row.append(el("span", "n", song.trackNumber ?? i + 1), text);
    row.onclick = () => unruly.playSongFromAlbum(song.id, album.id);
    screen.append(row);
  });
  markPlaying();
}

// ---- mini player ----
function fmt(ms) {
  const s = Math.floor(ms / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}
function markPlaying() {
  const id = unruly.playback?.song?.id;
  document.querySelectorAll(".row[data-song]").forEach((r) => r.classList.toggle("now", r.dataset.song === id));
}
unruly.on("playback", (p) => {
  $("#mini").hidden = !p.song;
  if (!p.song) return;
  $("#mini-title").textContent = p.song.title;
  $("#mini-artist").textContent = p.song.artist;
  if (p.song.art) $("#mini-art").src = p.song.art;
  else $("#mini-art").removeAttribute("src");
  $("#mini-play").textContent = p.isPlaying ? "❚❚" : "▶";
  markPlaying();
});
$("#mini-play").onclick = () => {
  unruly.haptic("click");
  unruly.playPause();
};

// Smooth progress from the interpolated position; stop drawing while in the background.
let visible = true;
unruly.on("visibility", (v) => {
  visible = v;
  if (v) requestAnimationFrame(tick);
});
function tick() {
  const p = unruly.playback;
  if (p?.durationMs) $("#mini-progress").style.width = (100 * unruly.position()) / p.durationMs + "%";
  if (visible) requestAnimationFrame(tick);
}
requestAnimationFrame(tick);

// Re-render the current screen when the library or its load state changes.
unruly.on("library", render);
unruly.on("loadState", () => stack.length && render());
unruly.setSystemBars({ icons: "light" });
push(albumsScreen);
