# Office Viewer PDF (Android)

Contoh project Android Studio dengan package `com.namaapp`.

## Fitur
- Memilih dokumen dari penyimpanan Android.
- Membaca teks dasar dari DOCX, XLSX, dan PPTX.
- Membuat PDF teks sederhana dan menyimpannya ke folder Download.
- GitHub Actions untuk membangun debug APK dan mengunggahnya sebagai artifact.

## Batasan penting
Konversi menggunakan pembaca XML Open Office sederhana dan Android `PdfDocument`. Hasil PDF hanya memuat teks yang diekstrak, bukan tata letak Office asli. Gambar, grafik, tabel kompleks, font, rumus, animasi, dan format halaman tidak dipertahankan. XLSX ditampilkan sebagai teks sel sederhana. Format lama DOC/XLS/PPT belum didukung; simpan ke DOCX/XLSX/PPTX terlebih dahulu.

## Cara membangun
1. Buka folder ini di Android Studio versi terbaru.
2. Pastikan Android SDK Platform 35 dan JDK 17 tersedia.
3. Sync Gradle lalu jalankan aplikasi, atau jalankan `gradle assembleDebug`.
4. GitHub Actions akan membuat artifact `OfficeViewerPDF-debug-apk` setelah workflow dijalankan.

## Catatan
- `local.properties` berisi komentar contoh saja karena path SDK berbeda di tiap komputer.
- `release-key.jks` tidak disertakan. Keystore signing berisi private key dan harus dibuat sendiri; jangan membagikannya di repository publik.
