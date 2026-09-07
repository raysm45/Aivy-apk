# Build APK Lewat GitHub Actions (dari Termux)

Cara ini biar server GitHub yang build APK-nya — HP kamu cuma dipakai buat push kode & download hasilnya. Gak perlu Android SDK sama sekali di Termux.

## 1. Siapkan Git & repo GitHub

Di Termux:
```bash
pkg install git -y
cd ~/storage/music/mobile   # folder project kamu
git init
git add .
git commit -m "Initial commit - Aivy mobile"
```

Buat repo baru (kosong) di https://github.com/new lewat browser HP kamu, misal namanya `aivy-mobile`. **Set ke Private** kalau gak mau kode kamu publik.

Sambungkan & push:
```bash
git remote add origin https://github.com/USERNAME/aivy-mobile.git
git branch -M main
git push -u origin main
```

Termux akan minta login GitHub — pakai **Personal Access Token** (bukan password akun):
- Buat token di: https://github.com/settings/tokens → "Generate new token (classic)" → centang scope `repo`
- Waktu `git push` minta password, paste token itu

## 2. Workflow sudah disiapkan

File `.github/workflows/build-android.yml` di project ini otomatis akan:
1. Install Node & JDK 21
2. `npm install` + `npm run build`
3. `npx cap add android`
4. Pasang plugin native `MusicScannerPlugin.java` + daftarkan di `MainActivity`
5. Tambahkan semua permission Android yang dibutuhkan (scan musik, background service)
6. `./gradlew assembleDebug` → hasilnya APK

Ini jalan **otomatis setiap kamu `git push` ke branch `main`**.

## 3. Cek proses build & download APK

1. Buka `https://github.com/USERNAME/aivy-mobile/actions` di browser HP
2. Klik run terbaru (nama "Build Android APK") → tunggu sampai centang hijau (biasanya 3-6 menit)
3. Scroll ke bawah ke bagian **Artifacts** → download `aivy-debug-apk` (berupa file .zip)
4. Ekstrak zip itu (pakai app File Manager / ZArchiver) → dapat `app-debug.apk`
5. Install APK-nya (mungkin perlu izinkan "Install dari sumber tidak dikenal" di setting HP)

## 4. Update backend URL sebelum push

Sebelum push, edit `frontend/.env`:
```env
VITE_API_BASE=https://api-kamu.contoh.com
```
Ganti dengan URL backend yang sudah online (bukan `localhost`), karena APK di HP gak bisa akses `localhost` Termux/komputer kamu kecuali kamu deploy backend-nya juga.

## Kalau build gagal di GitHub Actions

Klik run yang gagal → klik step yang merah (ada tanda ❌) → baca error log-nya, lalu kirim ke saya. Ini jauh lebih gampang di-debug dibanding error dari Termux karena environment-nya konsisten (selalu Ubuntu bersih tiap run).

## Setiap ada perubahan kode

```bash
git add .
git commit -m "update fitur X"
git push
```
Build APK baru otomatis jalan lagi.
