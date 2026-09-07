# Aivy → Android App (Capacitor) — Panduan Setup

Kode React kamu **tidak diubah struktur/UI-nya**. Yang ditambahkan:
- `capacitor.config.json` — konfigurasi wrapper Android
- `src/lib/localMusic.js` — akses plugin native scan lagu
- `src/lib/backgroundAudio.js` — jaga musik tetap jalan di background
- Sedikit hook di `src/context.jsx` (play/pause) untuk panggil background-mode
- `mobile-native-plugin/` — kode Kotlin plugin native (belum otomatis terpasang, ada di panduan ini)

Yang **BELUM** saya sambungkan otomatis: UI untuk memicu `scanLocalTracks()` (misal tombol "Impor musik dari HP" di halaman Library). Itu tinggal kamu panggil dari komponen React manapun, contoh ada di bagian bawah.

---

## Prasyarat di komputer kamu

1. **Node.js 20+** (Capacitor 8 mensyaratkan ini — cek dengan `node -v`; kalau Termux kamu masih Node 18, jalankan `pkg upgrade nodejs` atau install `nodejs-lts`)
2. **Android Studio** (untuk SDK, emulator/build, dan membuka project native) — versi Ladybug (2024.2.1) atau lebih baru
3. JDK 21 (biasanya sudah dibundel Android Studio versi terbaru)

---

## Langkah 1 — Install dependency

```bash
cd frontend
npm install
```

Ini akan menarik `@capacitor/core`, `@capacitor/android`, `@capacitor/cli`, dan `@anuradev/capacitor-background-mode` yang sudah saya tambahkan ke `package.json`.

## Langkah 2 — Build web app & tambahkan platform Android

```bash
npm run build
npx cap add android
```

Perintah kedua ini membuat folder `android/` (project native Gradle) berdasarkan `capacitor.config.json`.

## Langkah 3 — Pasang plugin native scan musik

1. Copy `mobile-native-plugin/MusicScannerPlugin.java` ke:
   ```
   android/app/src/main/java/com/aivy/app/plugins/MusicScannerPlugin.java
   ```
   (buat folder `plugins` kalau belum ada)

2. Buka `android/app/src/main/java/com/aivy/app/MainActivity.java`, daftarkan plugin-nya:
   ```java
   import com.aivy.app.plugins.MusicScannerPlugin;

   public class MainActivity extends BridgeActivity {
     @Override
     public void onCreate(Bundle savedInstanceState) {
       registerPlugin(MusicScannerPlugin.class);
       super.onCreate(savedInstanceState);
     }
   }
   ```

3. Buka `android/app/src/main/AndroidManifest.xml`, tambahkan semua blok dari
   `mobile-native-plugin/AndroidManifest-additions.xml` ke tempat yang sesuai (ada komentar penanda di file itu).

## Langkah 4 — Sinkronkan & buka di Android Studio

```bash
npx cap sync android
npx cap open android
```

Android Studio akan terbuka dengan project native. Klik **Run** (▶) untuk build & install ke HP/emulator.

Setelah setup awal ini, tiap kali ada perubahan kode React, cukup jalankan:
```bash
npm run android:sync
```

## Langkah 5 — Ganti backend URL ke server production

Di `.env` frontend, pastikan `VITE_API_BASE` menunjuk ke backend yang **online** (bukan `localhost`) — karena APK di HP tidak bisa akses `localhost` komputer kamu.

```env
VITE_API_BASE=https://api-kamu.contoh.com
```

Backend juga perlu `FRONTEND_URL`/`EXTRA_CORS_ORIGINS` mengizinkan origin `https://localhost` (skema default Capacitor WebView) atau custom scheme yang kamu set.

---

## Contoh pemakaian fitur "scan lagu di HP"

```jsx
import { scanLocalTracks, localTrackToAppTrack } from "../lib/localMusic";

async function handleImportLocalMusic() {
  try {
    const native = await scanLocalTracks({ minDurationMs: 30000 });
    const appTracks = native.map(localTrackToAppTrack);
    // masukkan appTracks ke playlist/library state kamu yang sudah ada
  } catch (err) {
    alert(err.message); // misal izin ditolak
  }
}
```

`uri` yang dikembalikan (`content://media/external/audio/media/123`) bisa langsung dipakai sebagai `src` di `<audio>` — tidak perlu upload/copy file.

---

## Kenapa musik gak berhenti lagi setelah ini

- `enableBackgroundAudio()` dipanggil otomatis tiap kali `isPlaying` jadi `true` (sudah saya sambungkan di `context.jsx`)
- Ini menjalankan **foreground service** Android dengan notifikasi persisten + wake lock
- Karena prosesnya gak di-kill sistem, `<audio>` tag + Web MediaSession API yang sudah ada di kode kamu tetap jalan normal, termasuk kontrol di layar kunci/notifikasi

---

## Checklist sebelum build APK release

- [ ] Ganti ikon app (`android/app/src/main/res/mipmap-*`)
- [ ] Ganti `appId` di `capacitor.config.json` kalau mau publish ke Play Store dengan package name sendiri
- [ ] Test izin `READ_MEDIA_AUDIO` di HP Android 13+ dan Android ≤12
- [ ] Generate signed APK/AAB lewat Android Studio (Build → Generate Signed Bundle/APK)
