// Wrapper for the custom native "MusicScanner" Capacitor plugin.
// Scans the device's music library via Android MediaStore.
//
// This only works inside the Android app build (Capacitor). In the regular
// browser it safely returns an empty/unsupported result so the web version
// keeps working unchanged.

import { registerPlugin, Capacitor } from "@capacitor/core";

const MusicScanner = registerPlugin("MusicScanner");

export const isNativeAndroid = () =>
  Capacitor.isNativePlatform() && Capacitor.getPlatform() === "android";

/**
 * Checks whether the audio read permission is granted.
 * @returns {Promise<boolean>}
 */
export async function hasLocalMusicPermission() {
  if (!isNativeAndroid()) return false;
  try {
    const { granted } = await MusicScanner.checkPermission();
    return !!granted;
  } catch {
    return false;
  }
}

/**
 * Prompts the Android permission dialog (READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE).
 * @returns {Promise<boolean>}
 */
export async function requestLocalMusicPermission() {
  if (!isNativeAndroid()) return false;
  try {
    const { granted } = await MusicScanner.requestPermission();
    return !!granted;
  } catch {
    return false;
  }
}

/**
 * Scans all music files on the device.
 * Each track: { id, title, artist, album, durationMs, sizeBytes, path, uri }
 * `uri` is a content:// URI usable directly as an <audio> src.
 *
 * @param {{minDurationMs?: number}} opts
 * @returns {Promise<Array<object>>}
 */
export async function scanLocalTracks(opts = {}) {
  if (!isNativeAndroid()) return [];
  const granted = await hasLocalMusicPermission();
  if (!granted) {
    const ok = await requestLocalMusicPermission();
    if (!ok) throw new Error("Izin akses musik ditolak.");
  }
  const { tracks } = await MusicScanner.scanTracks({
    minDurationMs: opts.minDurationMs ?? 30000,
  });
  return tracks || [];
}

/**
 * Maps a native local track into the same shape your app's `currentTrack`
 * objects already use, so it can be dropped straight into the existing
 * player queue/context without touching player logic.
 */
export function localTrackToAppTrack(nativeTrack) {
  return {
    id: `local:${nativeTrack.id}`,
    title: nativeTrack.title,
    artist: nativeTrack.artist,
    album: nativeTrack.album,
    duration: Math.round((nativeTrack.durationMs || 0) / 1000),
    source: "local",
    // Feed this straight into audioRef.current.src in context.jsx.
    //
    // IMPORTANT: we can't use the raw content:// URI here. The app's WebView
    // loads pages as an https:// origin (see capacitor.config.json), and
    // Chrome refuses to load content:// (or file://) resources from an
    // http(s) origin ("Not allowed to load local resource") — this is why
    // scanned tracks showed up but silently failed to play.
    //
    // Capacitor.convertFileSrc() rewrites a real filesystem path into a
    // special https://localhost/_capacitor_file_/... URL that Capacitor's
    // own WebView bridge intercepts and streams the file bytes for, which
    // *is* allowed since it looks like same-origin. MediaStore gives us that
    // real path in `path`, so we prefer it and only fall back to the
    // content:// URI (which may still work on some OEM WebViews) if for some
    // reason no path was available.
    streamUrl: nativeTrack.path
      ? Capacitor.convertFileSrc(nativeTrack.path)
      : nativeTrack.uri,
    fileExt: extOf(nativeTrack.path),
    artwork: nativeTrack.artwork || null,
  };
}

function extOf(path) {
  if (!path) return null;
  const match = /\.([a-z0-9]+)$/i.exec(path);
  return match ? match[1].toLowerCase() : null;
}
