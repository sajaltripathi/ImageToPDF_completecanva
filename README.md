# Image to PDF

Privacy-first Android app built with Kotlin and Android's built-in `android.graphics.pdf.PdfDocument`.

## Requirements implemented

- Pick one or multiple images using Android Storage Access Framework.
- Show pages with thumbnail, page number, move up, move down, and remove controls.
- Convert selected images into one multi-page PDF.
- Every image is drawn on its own PDF page using the bitmap's decoded pixel width and height.
- Save using Android's native Save As dialog.
- No `INTERNET` permission.
- No `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE` permission.
- No image caching/persistence in app-private storage.
- Backup and device-transfer backup disabled.
- R8 minification enabled for release builds.

## Build APK on GitHub

1. Upload these files to the root of your GitHub repo.
2. Open **Actions**.
3. Open **Build Android APK**.
4. Click **Run workflow**.
5. Download artifact **ImageToPDF-release-apk**.
