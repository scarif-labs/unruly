/**
 * window.unruly - everything an Unruly web skin can see and do. API version 1.
 *
 * The app injects this before any of your scripts run; never include a bridge yourself. It is the
 * skin's entire universe: there is no network, no file access and no other way to reach the
 * player. Ids are opaque strings; never parse them.
 *
 * Calls return Promises. Commands resolve to null once handed to the player; the resulting state
 * change arrives as a "playback"/"queue" event shortly after, so render from events, not from the
 * call's result.
 */
declare global {
  interface Window {
    unruly: Unruly;
  }
  const unruly: Unruly;
}

export interface Unruly {
  readonly apiVersion: 1;

  // ---------------------------------------------------------------- state

  /** Latest playback state, or null before the first push. Also delivered by on("playback"). */
  readonly playback: Playback | null;
  /** The play queue. Also delivered by on("queue"); only re-sent when it or its index changes. */
  readonly queue: Queue;
  /** Safe-area insets in CSS px. Also set as CSS vars --unruly-inset-top/right/bottom/left. */
  readonly insets: Insets;
  readonly loadState: LoadState;
  readonly volume: Volume | null;
  /** False while the app is in the background. Stop animations then. */
  readonly visibility: boolean;
  /** Resolves (with unruly) once the first playback state has arrived. */
  readonly ready: Promise<Unruly>;

  /**
   * Subscribe to an event; returns an unsubscribe function. For state events (playback, queue,
   * insets, loadState, volume, visibility) the callback is called at once with the current value
   * if there is one, so `on` alone is enough to render.
   */
  on(event: "playback", cb: (p: Playback) => void): () => void;
  on(event: "queue", cb: (q: Queue) => void): () => void;
  on(event: "insets", cb: (i: Insets) => void): () => void;
  on(event: "loadState", cb: (s: LoadState) => void): () => void;
  on(event: "volume", cb: (v: Volume) => void): () => void;
  on(event: "visibility", cb: (visible: boolean) => void): () => void;
  /** The library changed (rescan, playlist edit). Re-fetch what's on screen. */
  on(event: "library", cb: () => void): () => void;
  /** System back was pressed while setCanGoBack(true). Pop one level of your navigation. */
  on(event: "back", cb: () => void): () => void;

  /**
   * Current position in ms, interpolated between pushes (playback arrives ~4x a second). Use this
   * in requestAnimationFrame for smooth progress bars, not playback.positionMs.
   */
  position(): number;

  // ---------------------------------------------------------------- playback commands

  playPause(): Promise<null>;
  next(): Promise<null>;
  /** Previous song. (Whether it first rewinds the current one is the user's app setting.) */
  previous(): Promise<null>;
  seekTo(positionMs: number): Promise<null>;
  toggleShuffle(): Promise<null>;
  /** none -> all -> track -> none. */
  cycleRepeatMode(): Promise<null>;
  setRepeatMode(mode: RepeatMode): Promise<null>;

  playAlbum(albumId: string, startIndex?: number): Promise<null>;
  playArtist(artistId: string): Promise<null>;
  playGenre(genreId: string): Promise<null>;
  playPlaylist(playlistId: string): Promise<null>;
  /** Plays exactly these songs as the new queue. Songs or song ids. */
  playSongs(songs: Array<Song | string>, startIndex?: number): Promise<null>;
  /** Plays a song with its context as the queue - prefer these to playSongs for lists. */
  playSongFromAll(songId: string): Promise<null>;
  playSongFromAlbum(songId: string, albumId: string): Promise<null>;
  playSongFromArtist(songId: string, artistId: string): Promise<null>;
  playSongFromGenre(songId: string, genreId: string): Promise<null>;
  playSongFromPlaylist(songId: string, playlistId: string): Promise<null>;
  shuffleAll(): Promise<null>;

  // ---------------------------------------------------------------- queue

  playQueueItem(index: number): Promise<null>;
  addToQueue(songs: Array<Song | string>): Promise<null>;
  playNext(songs: Array<Song | string>): Promise<null>;
  removeFromQueue(index: number): Promise<null>;
  moveInQueue(from: number, to: number): Promise<null>;

  // ---------------------------------------------------------------- playlists

  /** A null/blank name takes the next free "Playlist N". */
  createPlaylist(name?: string | null, songs?: Array<Song | string>): Promise<null>;
  addToPlaylist(playlistId: string, songs: Array<Song | string>): Promise<null>;
  renamePlaylist(playlistId: string, name: string): Promise<null>;
  deletePlaylist(playlistId: string): Promise<null>;
  /** Pass the song id too; it is a no-op if the list moved and they no longer match. */
  removeFromPlaylist(playlistId: string, songId: string, index: number): Promise<null>;

  // ---------------------------------------------------------------- library

  readonly library: Library;

  /** Lyrics for a song. Never "loading": resolves once the lookup has an answer. */
  lyrics(songId: string): Promise<LyricsResult>;

  // ---------------------------------------------------------------- host

  /** Opens Unruly's settings (skin picker, library folders, lyrics, equalizer). */
  openSettings(): Promise<null>;
  /**
   * Tell the app whether system back has somewhere to go inside the skin. While true, back fires
   * the "back" event; while false, back leaves the app. Keep it truthful: true on any sub-screen,
   * false on your root screen. Resets to false on every page load.
   */
  setCanGoBack(canGoBack: boolean): Promise<null>;
  /** Status/navigation bar icon colour: "dark" icons for a light skin, "light" for a dark one. */
  setSystemBars(options: { icons: "dark" | "light" }): Promise<null>;
  haptic(kind?: "click" | "tick" | "long"): Promise<null>;
  /** 0..1 of the music stream. Resolves to the new volume (also pushed as "volume"). */
  setVolume(fraction: number): Promise<Volume>;
  /** +1 / -1 one hardware step. */
  adjustVolume(direction: 1 | -1): Promise<Volume>;
}

export interface Library {
  /**
   * Top-level listings are paged: pass offset/limit (limit max 1000, default 200) and read
   * `total` to know how many there are. Libraries can hold 50,000+ songs - page or virtualise,
   * never fetch everything to draw a screen.
   */
  artists(options?: ListOptions): Promise<Page<Artist>>;
  albums(options?: ListOptions): Promise<Page<Album>>;
  songs(options?: ListOptions): Promise<Page<Song>>;
  genres(options?: PageOptions): Promise<Page<Genre>>;
  playlists(options?: PageOptions): Promise<Page<Playlist>>;
  /** Explicit (the artist's own) and implicit (featured on) albums; see isExplicitForArtist. */
  albumsByArtist(artistId: string, options?: PageOptions): Promise<Page<Album>>;
  songsByGenre(genreId: string, options?: PageOptions): Promise<Page<Song>>;
  /** Small lists, returned whole. Album order is disc/track order. */
  songsByAlbum(albumId: string): Promise<Song[]>;
  songsByPlaylist(playlistId: string): Promise<Song[]>;
  search(query: string): Promise<SearchResults>;
  findAlbum(albumId: string): Promise<Album | null>;
  findArtist(artistId: string): Promise<Artist | null>;
}

export interface PageOptions {
  offset?: number;
  limit?: number;
}
export interface ListOptions extends PageOptions {
  sort?: { by: "name" | "artist" | "album" | "year" | "duration" | "dateAdded"; ascending?: boolean };
}
export interface Page<T> {
  items: T[];
  total: number;
  offset: number;
}

export type RepeatMode = "none" | "all" | "track";

export interface Playback {
  song: Song | null;
  isPlaying: boolean;
  positionMs: number;
  durationMs: number;
  queueIndex: number;
  repeatMode: RepeatMode;
  isShuffled: boolean;
}

export interface Queue {
  items: Song[];
  /** Index of the playing song in items, or -1. */
  index: number;
}

/**
 * `art` fields are same-origin URLs for <img src>, CSS url() and canvas (no CORS taint), or null
 * when there is no cover. Always design the no-art case.
 */
export interface Song {
  id: string;
  title: string;
  artist: string;
  album: string;
  albumId: string;
  artistId: string;
  art: string | null;
  durationMs: number;
  trackNumber: number | null;
  discNumber: number | null;
  year: number | null;
}
export interface Album {
  id: string;
  title: string;
  artist: string;
  artistId: string;
  art: string | null;
  songCount: number;
  durationMs: number;
  year: number | null;
  isExplicitForArtist: boolean;
}
export interface Artist {
  id: string;
  name: string;
  art: string | null;
  albumCount: number;
  songCount: number;
}
export interface Genre {
  id: string;
  name: string;
  art: string | null;
  songCount: number;
}
export interface Playlist {
  id: string;
  name: string;
  art: string | null;
  songCount: number;
  durationMs: number;
}
export interface SearchResults {
  songs: Song[];
  albums: Album[];
  artists: Artist[];
  genres: Genre[];
  playlists: Playlist[];
}

/** Gate the library UI on this: "scanning" on first run, "ready" with isEmpty for no music. */
export type LoadState =
  | { state: "idle" }
  | { state: "scanning"; loaded: number | null; explored: number | null }
  | { state: "ready"; isEmpty: boolean }
  | { state: "failed"; message: string };

/**
 * "disabled" means the user switched lookups off - say so and offer openSettings().
 * "notFound" is ordinary (most tracks have none), not an error.
 */
export type LyricsResult =
  | { state: "disabled" }
  | { state: "noTrack" }
  | { state: "notFound" }
  | { state: "failed"; message: string }
  | { state: "found"; plain: string[]; synced: Array<{ startMs: number; text: string }> };

export interface Insets {
  top: number;
  right: number;
  bottom: number;
  left: number;
}
export interface Volume {
  level: number;
  max: number;
  fraction: number;
}
