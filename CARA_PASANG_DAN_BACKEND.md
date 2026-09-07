# Cara pasang

1. Extract zip ini ke root folder project kamu, biar overwrite:
   - `.github/workflows/build-android.yml`
   - `frontend/capacitor.config.json`
   - `frontend/package.json`
   - `frontend/src/context.jsx`
   - `frontend/src/lib/api.js`
   - `frontend/android-icons/` (folder baru)
2. Commit & push ke branch `main` → GitHub Actions otomatis build APK baru.

Catatan: `appId` sengaja TIDAK diganti (masih `com.aivy.app`), biar aman —
ganti appId itu setara ganti "identitas" app di Android/Play Store, lebih baik
dipikirkan terpisah kalau memang perlu.

---

# Fix #1 & #2 (nama + icon) — SELESAI, tidak perlu kerjaan tambahan

- `capacitor.config.json`: `appName` → `CosmicX`.
- Icon launcher (semua ukuran + adaptive icon) sekarang diambil dari
  `frontend/public/icons/icon-512.png` dan `icon-maskable-512.png`, di-generate
  ke `frontend/android-icons/` dan ditempel oleh workflow setelah
  `npx cap add android` (soalnya folder `android/` kamu tidak di-commit ke
  repo, jadi dibuat ulang dari nol tiap build — icon harus ditempel ulang tiap
  kali juga).

# Fix #3 (login Discord/Google nyangkut di browser) — perlu 1 perubahan di BACKEND

Yang sudah saya benerin di frontend:
- `login()` / `loginGoogle()` di `context.jsx`: kalau jalan di app (native),
  buka URL login pakai in-app browser (`@capacitor/browser`) + tambahin
  `?platform=app`, bukan `window.location.href` biasa.
- `AndroidManifest.xml` (lewat workflow): app sekarang bisa nangkep
  `cosmicx://auth?ticket=...` sebagai deep link.
- `context.jsx`: ada listener yang nangkep deep link itu, nutup in-app
  browser-nya, terus tukar `ticket` jadi sesi login lewat endpoint baru
  `Api.exchangeTicket()`.

**Kenapa tidak bisa cuma pasang intent-filter doang:** login kamu sekarang
pakai cookie session (`credentials: "include"`). Kalau proses OAuth-nya
kejadian di tab browser terpisah (baik system browser maupun in-app browser),
cookie session itu ke-simpan di cookie jar tab itu — BUKAN di cookie jar
WebView aplikasi kamu. Jadi walaupun app-nya berhasil "ketarik balik", dia
tetap keliatan belum login. Makanya perlu satu langkah tukar-tiket dari
DALAM app itu sendiri.

## Yang perlu ditambahin di backend (`api.cosmicx.fun`)

1. `/auth/discord` dan `/auth/google`: terima query param `?platform=app`,
   dan simpan info ini supaya bisa dibaca lagi pas callback selesai (misal
   diselipin ke parameter `state` yang dikirim ke Discord/Google).

2. Di callback (setelah proses OAuth sukses & sesi/cookie normal sudah
   dibuat persis seperti sekarang):
   - Kalau **bukan** dari app → perilaku SAMA seperti sekarang, tidak berubah.
   - Kalau **dari app** (`platform=app`) →
     - generate `ticket` random sekali-pakai (misal 32-byte hex),
       simpan di server terhubung ke sesi user itu, kasih masa berlaku
       pendek (±60 detik),
     - redirect ke `cosmicx://auth?ticket=<ticket>` (bukan redirect ke
       website).
   - Kalau user membatalkan login (`discord_denied` dst) dan `platform=app`
     → redirect ke `cosmicx://auth?error=discord_denied`.

3. Endpoint baru: `GET /auth/exchange?ticket=<ticket>`
   - Cek ticket valid & belum expired → hapus ticket itu (sekali pakai),
     set cookie sesi seperti proses login normal, balikin JSON user
     (bentuknya sama kayak response `GET /auth/me` yang sudah ada sekarang).
   - Kalau ticket invalid/expired → balikin 401.

Setelah backend nambahin 3 poin di atas, alur login di app: tombol Discord →
kebuka in-app browser → user login di Discord → otomatis balik ke app →
langsung login, tanpa nyangkut di browser.
