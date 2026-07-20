# HyTik - Fast All-in-One TikTok Downloader & Saver 🚀

**HyTik** adalah aplikasi Android berspesifikasi tinggi berbasis Kotlin & Material Design 3 untuk mengunduh semua tipe konten dari TikTok dengan kecepatan ekstrem, kualitas HD, tanpa watermark (No Watermark), dan mendukung seluruh format tautan TikTok.

---

## 🔥 Fitur Utama

1. **Mendukung Semua Format Tautan TikTok (`Support All Links`)**:
   - `https://www.tiktok.com/@user/video/1234567890...`
   - `https://vt.tiktok.com/ZS.../` & `https://vm.tiktok.com/...`
   - `https://tiktok.com/t/...` & Mobile shortlinks `https://m.tiktok.com/v/...`
   - Ekstraksi otomatis dari teks campuran atau hasil *Share* langsung dari aplikasi TikTok (`Share -> HyTik`).

2. **Mendukung Semua Tipe Konten TikTok (`Support All Types`)**:
   - 🎬 **Video HD No Watermark** (`High Definition 1080p / 60fps`)
   - 📱 **Video SD No Watermark** (`Fast & Data-saving`)
   - 🏷️ **Video With Watermark** (`Original TikTok Watermark`)
   - 🖼️ **Slide Foto / Carousel** (`Unduh foto individual atau unduh semua foto sekaligus sebagai batch`)
   - 🎵 **Audio MP3 (Musik)** (`Ekstraksi audio berkualitas tinggi dari video maupun postingan foto`)

3. **Kecepatan & Mesin Ekstraksi (`HyTik Hybrid Engine`)**:
   - **⚡ Auto Hybrid Mode**: Menggabungkan 3 *layer scraper/engine* secara bersamaan dengan sistem *auto-fallback* (Jika Engine 1 sibuk, otomatis beralih ke Engine 2/3 dalam hitungan milidetik).
   - **🔧 Engine 1 (TikWM Pro API v2)**: Pengambilan metadata terlengkap beserta opsi HD langsung dari CDN utama TikTok.
   - **🚀 Engine 2 (TikLyDown Fast Extractor)**: *Scraper* berkinerja tinggi untuk respon instan pada koneksi lambat.
   - **🔄 Engine 3 (Direct oEmbed & Mobile Universal Data Parsing)**: *Fallback* lokal langsung menggunakan Jsoup parsing.

4. **Tampilan & Pengalaman Pengguna (`Beautiful & Clean UI`)**:
   - **Material Design 3** dengan dukungan *Dark Mode* dan *Light Mode* yang elegan (`Neon Cyan & Electric Teal`).
   - **Shimmer Loading Skeleton** saat memuat data.
   - **Deteksi Otomatis Papan Klip (`Auto Paste`)** saat aplikasi dibuka.
   - **Riwayat Unduhan Integrated (`Room SQLite Database`)**: Lihat, mainkan/buka langsung via pemutar internal/eksternal, bagikan, atau hapus riwayat.

---

## 📱 Kompatibilitas IDE: "Code on the Go", AIDE, AndroidIDE & Android Studio

Repository ini telah dikonfigurasikan secara khusus dengan **Dua Format Build System (Dual Compatibility)** sekaligus:
- **Format Groovy Gradle & IDE Markers** (`settings.gradle`, `build.gradle`, `app/build.gradle`, `.iml`, `.project`, `project.properties`): Dibuat agar kompatibel 100% dan langsung terdeteksi sebagai modul aplikasi oleh *mobile IDE* seperti **Code on the Go**, **AIDE**, **AndroidIDE**, **JavaIDEdroid**, maupun **Termux**.
- **Format Kotlin DSL Gradle** (`settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`): Dibuat untuk kompatibilitas penuh dengan **Android Studio** dan Gradle modern.

### 🛠️ Cara Membuka di "Code on the Go" / Mobile IDE:
1. Buka aplikasi **Code on the Go** (atau **AIDE** / **AndroidIDE**).
2. Pilih opsi **Open Project / Kloning Git**.
3. Arahkan ke folder root repositori `hytik` (tempat file `settings.gradle` dan direktori `app/` berada).
4. Aplikasi akan otomatis mendeteksi modul `:app` sebagai modul Android.
5. Klik **Build / Run** untuk mengkompilasi APK langsung dari perangkat Android-mu!

---

## 🚀 Cara Membangun (Build & Run via Terminal / PC)

### Menggunakan Android Studio
1. Klon repositori ini:
   ```bash
   git clone https://github.com/AliaBlip/hytik.git
   cd hytik
   ```
2. Buka proyek di **Android Studio**.
3. Tunggu proses sinkronisasi Gradle selesai.
4. Klik tombol **Run** atau **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

### Menggunakan Terminal / Command Line
Run perintah Gradle wrapper:
```bash
./gradlew assembleDebug
```
File APK hasil build akan tersedia di:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📂 Lokasi File Unduhan
Semua file yang diunduh melalui **HyTik** disimpan dengan aman dan terstruktur di direktori publik perangkat Anda:
- 🎬 **Video**: `Internal Storage / Movies / HyTik/`
- 🖼️ **Foto**: `Internal Storage / Pictures / HyTik/`
- 🎵 **Musik/Audio**: `Internal Storage / Music / HyTik/`

---

## 🔒 Lisensi & Kebijakan
Aplikasi ini dibuat untuk tujuan kemudahan pengunduhan konten pribadi, pencadangan (*backup*), dan referensi kreatif. Hak cipta dari video, foto, dan musik tetap milik kreator asli di platform TikTok.
