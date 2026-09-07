// Keeps the app process (and therefore the <audio> element / MediaSession)
// alive when the app is minimized or the screen is off, using a foreground
// service + wake lock provided by @anuradev/capacitor-background-mode.
//
// No-op on web, so this is safe to import and call unconditionally.

import { Capacitor } from "@capacitor/core";

let BackgroundMode = null;
let enabled = false;

async function getPlugin() {
  if (!Capacitor.isNativePlatform()) return null;
  if (!BackgroundMode) {
    const mod = await import("@anuradev/capacitor-background-mode");
    BackgroundMode = mod.BackgroundMode;
  }
  return BackgroundMode;
}

/**
 * Call this once when a track STARTS playing.
 * Starts the foreground service + notification + wake lock.
 */
export async function enableBackgroundAudio() {
  const plugin = await getPlugin();
  if (!plugin || enabled) return;
  await plugin.enable({
    title: "Aivy sedang memutar musik",
    text: "Ketuk untuk kembali ke aplikasi",
    silent: false,
    hidden: false,
    resume: true,
  });
  // Prevents Android from throttling the WebView / decoder while the
  // screen is off and a track is playing.
  await plugin.disableWebViewOptimizations().catch(() => {});
  enabled = true;
}

/**
 * Call this when playback is fully stopped (not just paused, ideally when
 * the queue is empty) to drop the persistent notification.
 */
export async function disableBackgroundAudio() {
  const plugin = await getPlugin();
  if (!plugin || !enabled) return;
  await plugin.disable();
  enabled = false;
}

export function isBackgroundAudioEnabled() {
  return enabled;
}
