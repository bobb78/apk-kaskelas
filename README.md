# Kas Kelas

Aplikasi manajemen keuangan dan uang kas kelas berbasis Android, dibangun dengan **Kotlin**, **Jetpack Compose**, **Material Design 3**, dan **Room Database**.

---

## 📱 Download & Pasang APK
Pengguna Android dapat langsung mengunduh file APK siap pasang dari halaman [Releases](../../releases):
1. Buka tab **Releases** di repositori GitHub ini.
2. Unduh file `.apk` (misalnya `KasKelas-v1.0.apk` atau `app-debug.apk`) pada bagian **Assets** rilis terbaru.
3. Buka file APK di HP Android Anda dan pilih **Pasang (Install)** (izinkan *Install unknown apps* jika diminta).

---

## ✨ Fitur Utama
- 💵 **Dashboard Saldo Real-Time & Pencatatan Transaksi**: Menampilkan total saldo terkini, total pemasukan, total pengeluaran, pencarian, serta filter jenis & kategori transaksi.
- 📝 **Form Transaksi Lengkap**: Pilihan pemasukan/pengeluaran, input nominal cepat, deskripsi, nama anggota/PIC, kategori, catatan, dan pemilih tanggal (*Date Picker*).
- 👥 **Anggota Kelas, Impor CSV & Tagihan WhatsApp**: Tambah anggota massal lewat file `.csv` atau tempel daftar teks, pantau siapa yang belum bayar tiap bulan, tandai lunas 1-ketuk, dan bagikan daftar tagihan langsung ke grup WhatsApp kelas.
- 📊 **Grafik Arus Kas & Laporan**: Visualisasi ringkasan pemasukan vs pengeluaran bulanan, tren saldo, serta rincian persentase kategori.
- 📄 **Ekspor & Cetak Laporan PDF**: Ekspor riwayat transaksi beserta daftar anggota lunas/belum lunas ke dokumen PDF siap cetak atau bagikan.
- 💾 **Penyimpanan Lokal Room Database**: Seluruh catatan kas tersimpan aman secara offline di perangkat.
- ☁️ **Backup & Pulihkan Data**: Dukungan sinkronisasi ke Google Drive maupun ekspor/impor **file backup JSON lokal** (*offline*) untuk memudahkan pindah HP atau instal ulang.
- 🌗 **Mode Terang & Mode Gelap**: Tombol ganti tema langsung di bar atas untuk kenyamanan mata di siang atau malam hari.

---

## 🛠️ Cara Build APK dari Source
```bash
# 1. Siapkan debug keystore (khusus build lokal/pengujian)
base64 -d debug.keystore.base64 > debug.keystore

# 2. Build APK siap pasang
./gradlew :app:assembleDebug
```
File APK hasil build tersedia di `app/build/outputs/apk/debug/app-debug.apk`.
