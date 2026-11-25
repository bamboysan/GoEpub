# Privacy Policy — GoEpub

**Last Updated:** November 25, 2026

**GoEpub** is built with a strict **Privacy-First** architecture. Your e-books and personal documents remain completely private on your device.

## 1. 100% On-Device Processing
- All EPUB extraction, XHTML parsing, language identification, and chapter translation happen **locally on your Android device** using **Google ML Kit On-Device Translation**.
- GoEpub **never** uploads, transmits, collects, or stores your e-book contents, cover images, or metadata on any external server.

## 2. Scoped Storage Access
- GoEpub uses the official **Android Storage Access Framework (`OpenDocument` & `CreateDocument`)**.
- The app never requests broad storage permissions (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, or `MANAGE_EXTERNAL_STORAGE`). It can only read the specific `.epub` file you explicitly pick.

## 3. Network Usage
- `INTERNET` and `ACCESS_NETWORK_STATE` permissions are used solely to download official offline language packs (~30 MB per language) from Google ML Kit servers when a selected language model is not yet cached on your device.
- Once a language pack is downloaded, translations run **100% offline**.

## 4. Background Processing & Notifications
- `FOREGROUND_SERVICE` (`dataSync`) and optional `POST_NOTIFICATIONS` permissions are used by Android `WorkManager` solely to keep multi-chapter translations running reliably when your screen is off and to show live chapter progress.

## 5. User Data Control
- You can delete individual or all translated book history files at any time via **Privacy & About → Clear All Translated Book History**.
- You can inspect and remove downloaded offline language models at any time via **Offline Language Packs** in the top bar.

---

## Kebijakan Privasi (Bahasa Indonesia)

1. **Pemrosesan 100% di Perangkat**: Seluruh isi buku `.epub` diterjemahkan langsung di perangkat Android Anda menggunakan Google ML Kit On-Device Translation dan tidak pernah dikirim ke server eksternal mana pun.
2. **Akses Penyimpanan Terbatas**: Aplikasi hanya membaca satu file `.epub` yang Anda pilih secara eksplisit melalui pemilih dokumen resmi Android.
3. **Penggunaan Internet**: Koneksi internet hanya digunakan untuk mengunduh paket model bahasa resmi Google ML Kit (~30 MB) agar selanjutnya dapat digunakan secara *offline*.
4. **Kendali Data**: Anda bebas menghapus riwayat buku hasil terjemahan maupun paket model bahasa *offline* kapan saja langsung dari dalam aplikasi.
