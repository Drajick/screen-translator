# Novel Translator (Kotlin + Gradle) - Build di GitHub

Proyek Android native. Terjemahan memakai Google ML Kit (offline setelah model diunduh).

## File yang dipakai (semua di folder utama repository)
1. `settings.gradle.kts`
2. `build.gradle.kts`
3. `gradle.properties`
4. `app-build.gradle.kts`
5. `AndroidManifest.xml`
6. `MainActivity.kt`
7. `.github/workflows/build.yml`  (dibuat lewat "Create new file", lihat langkah 3)

Workflow otomatis menyusun file nomor 4-6 ke struktur folder Android yang benar,
jadi kamu cukup mengunggah file-file itu apa adanya (tidak perlu membuat folder).

## 1. Buat repository
github.com → **+** → **New repository** → nama `novel-translator`, **Public**,
centang **Add a README** → **Create repository**.

Jika sebelumnya sudah ada file Flutter (`pubspec.yaml`, `main.dart`), hapus dulu
(buka file → ikon tong sampah → Commit).

## 2. Unggah file proyek
**Add file → Upload files**, pilih keenam file proyek (nomor 1-6), lalu **Commit changes**.

## 3. Tambahkan workflow
1. **Add file → Create new file**
2. Nama file ketik persis: `.github/workflows/build.yml`
3. Tempel seluruh isi `build.yml` → **Commit changes**.
   (Jika sudah ada `build.yml` lama, buka file itu, edit ikon pensil, ganti seluruh isinya.)

## 4. Build
Tab **Actions** → **Build APK** → **Run workflow**. Tunggu ±5-10 menit sampai ✅.

## 5. Unduh dan instal
Klik run yang hijau → bagian **Artifacts** → unduh **novel-translator-apk** (zip) →
ekstrak di HP → buka `app-debug.apk` → izinkan "sumber tidak dikenal" → instal.

## Jika ada ❌
Buka langkah **Build APK** yang merah, salin pesan error-nya dan kirim ke saya.

## Jika ingin membuka di Android Studio (di laptop)
Buat folder proyek dengan struktur ini, lalu **File → Open**:

```
NovelTranslator/
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
└─ app/
   ├─ build.gradle.kts        (isi dari app-build.gradle.kts)
   └─ src/main/
      ├─ AndroidManifest.xml
      └─ java/com/noveltranslator/MainActivity.kt
```

## Cara pakai aplikasi
Pertama kali menerjemahkan butuh internet untuk mengunduh model bahasa
(±30 MB per bahasa). Setelah itu bisa offline. Salin teks novel → buka aplikasi →
**Tempel** → **Terjemahkan**.
