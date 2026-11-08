# ব্রয়লার ফিড চার্ট (Android, Kotlin + XML)

Open in Android Studio or AndroidIDE → sync → Run.
(Built for Gradle 7.4.2 + AGP 7.3.1 + Kotlin 1.7.20 (works with AndroidIDE on phone).

## What it does
- Enter the number of chicks → all feed totals and sack counts are recalculated for that flock.
- Enter FCR (default 1.48 = reference chart). Switch decides how it is applied:
  - OFF: only flock total feed + bags change (per-bird table stays the original).
  - ON : per-bird daily/cumulative feed also scale by FCR/1.48 (body weight unchanged).
- Optional: bag size in kg (default 50).
- "ছবি তৈরি করুন" draws one tall black PNG: cover + charts + 32-day table.
- Save to gallery (Pictures/BroilerChart) and share via any app.

## Files
- `ChartData.kt` – standard chart data, phases (1–14 / 15–25 / 26–32+), FCR maths, Bengali number format
- `InfographicRenderer.kt` – draws the whole image with android.graphics.Canvas
- `ImageExport.kt` – gallery save (MediaStore) + FileProvider share
- `MainActivity.kt` + `res/layout/activity_main.xml` – UI

minSdk 24, targetSdk 33. Only dependencies: androidx core-ktx + appcompat.
