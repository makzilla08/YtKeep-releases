# AGENTS.md — YTNx (project rules, โฟลเดอร์ `YtKeep`)

อ่าน `README.md` ในโฟลเดอร์นี้ก่อนเสมอ (มีสถานะงาน, กับดัก, ผลทดสอบ, งานค้าง)
ไฟล์นี้คือกฎสั้น ๆ ที่ต้องทำตามเมื่อแก้โปรเจกต์นี้

## ชื่อโปรเจค

- แอปชื่อ **YTNx** (เดิม YtKeep) · `android:label="YTNx"` · `applicationId = com.chue.ytnx`
- โฟลเดอร์โปรเจคยังเป็น `~/android-dev/YtKeep` · namespace/package โค้ดยังเป็น `com.chue.ytkeep` — **อย่า rename ถ้าไม่จำเป็น** (data path ภายนอกผูกกับ applicationId ไม่ใช่ namespace)
- ถ้าเปลี่ยน version ให้ bump `versionCode` + `versionName` และแก้หัวข้อ "สถานะปัจจุบัน" ใน README ทุกครั้ง

## Build / install

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=$HOME/android-dev/sdk
cd ~/android-dev
./gradle-8.11.1/bin/gradle -p YtKeep assembleDebug --no-daemon -q
$ANDROID_HOME/platform-tools/adb install -r YtKeep/app/build/outputs/apk/debug/app-debug.apk
```

- APK: `~/android-dev/YtKeep/app/build/outputs/apk/debug/app-debug.apk`
- มี unit test สำหรับตรรกะแนะนำวิดีโอ; ยังไม่มี lint — รัน `testDebugUnitTest` และตรวจงานบน emulator `t35` (x86_64) พร้อมดู `adb logcat`
- Emulator ใช้ `abiFilters += "x86_64"` ใน `app/build.gradle.kts` — **ห้ามลบก่อนทดสอบ** แต่ **ต้องลบก่อนปล่อย APK จริง**

## ห้าม / ระวัง

1. อย่าเปลี่ยน dependency `youtubedl-android` กลับไป JitPack หรือเวอร์ชัน 0.14.0 — ใช้ `io.github.junkfood02.youtubedl-android:library:0.18.1` + `:ffmpeg:0.18.1` จาก Maven Central เท่านั้น
2. ทุกจุดที่ดาวน์โหลด/merge ต้องเรียก `Yt.ensure(ctx)` (ภายในเรียก `FFmpeg.getInstance().init`) ไม่งั้นไฟล์ที่ได้จะไม่มีเสียง
3. `PlayerService` ต้องใช้ `DefaultDataSource.Factory(this, http)` ห่อ http factory เสมอ (ไม่ใช่ `DefaultHttpDataSource.Factory` เดี่ยว ๆ) ไม่งั้นเล่นไฟล์ `file://` แล้ว crash
4. `PlayerActivity` ต้องมี `Modifier.fillMaxSize()` ที่ `AndroidView` ไม่งั้นวิดีโอไปชิดขอบบน
5. `getInfo()` ของ library ยกเลิกไม่ได้ → ห้ามทำ UI ที่รอ `fetchHd` แบบ blocking; ใช้รูปแบบ fast (`fetchFast`) แล้วเติม HD เบื้องหลัง
6. อย่าเพิ่ม `android.util.Log` ค้างไว้ใน service (เคยมี debug log `YtKeepSvc` ตอนแก้ ANR)
7. อย่าใช้ keyword ภาษาไทยใน `adb shell input text` (ตัวอักษรหาย) — ใช้ `am start -a android.intent.action.SEND` แทน

8. `DownloadService` การเริ่ม service ด้วย `ContextCompat.startForegroundService` ต้องครอบด้วย `runCatching` เสมอ เพื่อป้องกัน `ForegroundServiceStartNotAllowedException` บน Android 12-14 (Target SDK 35) เมื่อแอปถูกจำกัดสิทธิ์ในพื้นหลัง
9. `PlayerService` ต้องปรับแต่ง `LoadControl` ให้พอเหมาะกับอุปกรณ์ Low RAM (RAM 3GB เช่น Infinix) ไม่ปล่อยให้ ExoPlayer บัฟเฟอร์ไม่จำกัดจนเกิดแรงกดดัน LowMemoryKiller (LMK)

## จุดที่แก้บ่อย

| ต้องแก้อะไร | แก้ที่ |
|---|---|
| รายการความละเอียด/ป้ายชื่อ/ขนาด/selector | `Yt.kt` (`fetch`, `byQuality`, `size`) |
| ตัวเลือก client ของ YouTube | `Yt.kt` (`YT_CLIENT_VR`, `fetchFast`) |
| args ของ yt-dlp ตอนดาวน์โหลด | `DownloadService.kt` |
| พฤติกรรม player / wake mode / data source | `PlayerService.kt`, `PlayerActivity.kt` |
| หน้าย่อยช่อง (แท็บ `channel`) | `MainActivity.kt` (แท็บ `"channel"`) + `Yt.channelVideos()` + `Feed.channel()` |
| คลังไฟล์ (index) | `Lib.kt` |
| permission / FGS type / activity | `app/src/main/AndroidManifest.xml` |

## ก่อนบอกว่างานเสร็จ

- [x] build ผ่าน (`testDebugUnitTest assembleDebug`)
- [x] ติดตั้ง + เปิดแอปบน emulator `t35` ได้ ไม่ crash
- [x] ทดสอบ UI บน emulator: ค้นหา NASA → เปิดช่องได้ 30 คลิป, เปิดคลังและตั้งค่าได้
- [ ] ถ้าแตะเรื่องดาวน์โหลด: ดึงไฟล์ออกมาดูด้วย `ffprobe` ว่ามีทั้ง video + audio
- [ ] ถ้าแตะเรื่องเล่น: `adb shell dumpsys media_session | grep PlaybackState` ต้องเป็น `PLAYING`
- [x] อัปเดต `README.md` หัวข้อ "สถานะปัจจุบัน" และ "งานที่ควรทำต่อ"

## เป้าหมายใหญ่ถัดไป (Target: Instant Playback Like Brave)
- พัฒนาระบบแกะสตรีม/เล่นวิดีโอให้เริ่มเล่นได้รวดเร็วทันทีระดับ Brave Browser (< 1 วินาที) โดยไม่ต้องรอรอบการถอดรหัสของ Python `yt-dlp` นาน
- นำร่องด้วยกลยุทธ์ **Pre-fetching Stream URL** สำหรับรายการถัดไปในคิว และการศึกษา **Lightweight Direct Extractor** เพื่อข้ามภาระของ Python Runtime บนมือถือ

## สถานะปัจจุบัน (2026-11-24)
- YTNx 3.9.2 (`versionCode 21`) = 3.9.1 + แก้แถบ overlay ทับภาพในตัวเล่นเต็มจอแนวนอน (ซ่อน overlay ตาม `PlayerView`, immersive ซ่อน system bars, `resizeMode` ZOOM แนวนอน/FIT แนวตั้ง) + ปุ่ม "เล่น" เด่นขึ้นในการ์ดวิดีโอ
- YTNx 3.9.1 (`versionCode 20`) = 3.9-instant ที่แก้บั๊กแล้ว:player `singleTop` (ไม่มี activity ซ้อน), เปิด loading ทันที, `StreamCache` single-flight + serialize yt-dlp (กัน prefetch แย่งคิว), `InnerTube.fastStreamUrl()` (~0.3 วิ) ก่อน fallback yt-dlp, และ guard `PlayerActivity.current`/`playToken` กันเสียงหลุดเมื่อกด BACK ระหว่างโหลด — วัดบน Infinix ได้ `PLAYING` 3.2–3.6 วิ
- YTNx v3.9 (`versionCode 18`) แก้ไขปัญหา Crash จาก `ForegroundServiceStartNotAllowedException` และปรับแต่ง `DefaultLoadControl` ป้องกัน LMK บน Android 14 (Infinix X6532) พร้อมติดตั้งและรันบนเครื่องจริงแล้ว
- YTNx 2.0 (`versionCode 7`) เผยแพร่ที่ GitHub Release `v2.1`
- YTNx 2.1 (`versionCode 8`) เผยแพร่ที่ GitHub Release `v2.2`; ARM APK มี arm64-v8a + armeabi-v7a และใช้ Android Debug certificate เดียวกับรุ่นก่อน
- YTNx v3 (`versionCode 9`, `versionName 3.0`) เผยแพร่ที่ GitHub Release `v2.3`; รองรับการบราวซ์แบ่งหน้าครั้งละ 30 คลิป พร้อมปุ่มแสดงผลเพิ่มเติม (+30 คลิป) และข้อกำหนดดีไซน์ V3-DESIGN.md
- อัปเดต asset `app-debug.apk` ใน release `v2.3` เมื่อ 2026-11-04; APK ARM มี arm64-v8a + armeabi-v7a และรวม Mini-Player กับการตั้งค่าแสดง/ซ่อนหัวข้อความสนใจ
- ทดสอบ Mini-Player บน emulator `t35`: เล่น, หยุดชั่วคราว, เล่นต่อ และปิด; ซ่อนหัวข้อความสนใจแล้วแท็บหายจากหน้าคำแนะนำ
- ทดสอบบน emulator `t35`: ค้นหา NASA, เปิดหน้าช่องและยืนยันป้าย "30 รายการ", เปิดคลัง 3 ส่วนและหน้าตั้งค่าได้
- ปรับหน้า Search และ Channel ตามม็อกอัป v2; รายการช่อง/วิดีโอเป็นการ์ดและหน้าช่องมีแบนเนอร์กับปุ่มดาวน์โหลด
- คิวดาวน์โหลด v2 ยังต้องทดสอบเพิ่ม: progress, cancel/retry และการกู้สถานะหลัง service เริ่มใหม่; ดู [V2-DESIGN.md](./V2-DESIGN.md)
- หน้าย่อยช่องมีปุ่มเรียงใหม่สุด/ยอดวิวและปุ่มย้อนกลับ
- แท็บ "ติดตาม" แสดงรายชื่อช่องให้เลือกแล้วกดเข้าไปดูวิดีโอในช่องนั้น
- ค้นหา/ฟีดในแอปทำงาน: `Feed.kt` (InnerTube) + `Store.kt` (ความสนใจ/ติดตาม/บันทึก) + UI แท็บใน `MainActivity`
- ทดสอบบน emulator แล้ว: แนะนำ 24 รายการ, ข่าว, กดคลิป→ดึงฟอร์แมต (1080p/720p/480p), ติดตามช่อง, บันทึก
- ยังไม่ทดสอบ APK v1.5 บนเครื่องจริง (Infinix Smart 9); USB เสียและเชื่อมต่อ ADB ผ่าน Wi-Fi ไม่สำเร็จ
