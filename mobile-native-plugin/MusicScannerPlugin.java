package com.aivy.app.plugins;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.ByteArrayOutputStream;

/**
 * MusicScannerPlugin
 * ------------------
 * Scans the device's MediaStore for audio files and returns metadata
 * (title, artist, album, duration, content uri) to the JS side.
 *
 * INSTALLATION (after `npx cap add android` has been run once):
 * 1. Copy this file to:
 *    android/app/src/main/java/com/aivy/app/plugins/MusicScannerPlugin.java
 * 2. Register it in MainActivity.java:
 *
 *    import com.aivy.app.plugins.MusicScannerPlugin;
 *    ...
 *    registerPlugin(MusicScannerPlugin.class);
 *
 * 3. Add permissions to android/app/src/main/AndroidManifest.xml.
 */
@CapacitorPlugin(
    name = "MusicScanner",
    permissions = {
        @Permission(
            alias = "readAudio",
            strings = { Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE }
        )
    }
)
public class MusicScannerPlugin extends Plugin {

    @PluginMethod
    public void checkPermission(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", hasRequiredPermission());
        call.resolve(ret);
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (hasRequiredPermission()) {
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
            return;
        }
        requestPermissionForAlias("readAudio", call, "permissionCallback");
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", hasRequiredPermission());
        call.resolve(ret);
    }

    private boolean hasRequiredPermission() {
        String perm = Build.VERSION.SDK_INT >= 33
            ? Manifest.permission.READ_MEDIA_AUDIO
            : Manifest.permission.READ_EXTERNAL_STORAGE;
        return getContext().checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Returns a JSON array of every audio track visible to MediaStore, excluding
     * very short clips (ringtones/notification sounds) using a minimum duration filter.
     */
    @PluginMethod
    public void scanTracks(PluginCall call) {
        if (!hasRequiredPermission()) {
            call.reject("Permission not granted. Call requestPermission() first.");
            return;
        }

        int minDurationMs = call.getInt("minDurationMs", 30000);
        JSArray tracks = new JSArray();

        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = new String[] {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " + MediaStore.Audio.Media.DURATION + " >= ?";
        String[] selectionArgs = new String[] { String.valueOf(minDurationMs) };
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        try (Cursor cursor = getContext().getContentResolver().query(
                collection, projection, selection, selectionArgs, sortOrder)) {

            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    Uri contentUri = Uri.withAppendedPath(collection, String.valueOf(id));

                    JSObject track = new JSObject();
                    track.put("id", String.valueOf(id));
                    track.put("title", cursor.getString(titleCol) != null ? cursor.getString(titleCol) : "Unknown");
                    track.put("artist", cursor.getString(artistCol) != null ? cursor.getString(artistCol) : "Unknown");
                    track.put("album", cursor.getString(albumCol) != null ? cursor.getString(albumCol) : "Unknown");
                    track.put("durationMs", cursor.getLong(durationCol));
                    track.put("sizeBytes", cursor.getLong(sizeCol));
                    track.put("path", cursor.getString(dataCol) != null ? cursor.getString(dataCol) : "");
                    track.put("uri", contentUri.toString());
                    String artwork = extractArtworkBase64(contentUri);
                    track.put("artwork", artwork != null ? artwork : "");
                    tracks.put(track);
                }
            }

            JSObject ret = new JSObject();
            ret.put("tracks", tracks);
            ret.put("count", tracks.length());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to scan MediaStore: " + e.getMessage(), e);
        }
    }

    /**
     * Reads the embedded album art for one track, if any, and returns it as a
     * base64 JPEG data URI so the JS side can drop it straight into an <img>
     * or CSS background without a second round-trip.
     *
     * On Android 10+ we use ContentResolver#loadThumbnail, which is the
     * scoped-storage-safe way to get a small preview image for a MediaStore
     * item. On older versions (or if that fails, e.g. no embedded art), we
     * fall back to MediaMetadataRetriever reading the file's own ID3/embedded
     * picture tag directly.
     */
    private String extractArtworkBase64(Uri itemUri) {
        Bitmap bitmap = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                bitmap = getContext().getContentResolver().loadThumbnail(itemUri, new Size(300, 300), null);
            } catch (Exception e) {
                bitmap = null; // No embedded art, or the OS couldn't generate one — fall back below.
            }
        }

        if (bitmap != null) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
                return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return null;
            } finally {
                bitmap.recycle();
            }
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(getContext(), itemUri);
            byte[] embedded = retriever.getEmbeddedPicture();
            if (embedded != null && embedded.length > 0) {
                return "data:image/jpeg;base64," + Base64.encodeToString(embedded, Base64.NO_WRAP);
            }
        } catch (Exception ignored) {
            // File has no readable embedded art — that's fine, thumbnail just stays null.
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }

        return null;
    }
}
