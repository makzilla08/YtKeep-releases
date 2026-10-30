# YTNx — แอปดาวน์โหลด/เล่นคลิป YouTube แบบออฟไลน์ (Android)

เอกสารส่งต่อ (handoff) สำหรับคนหรือ AI คนถัดไปที่ต้องทำงานต่อ — อัปเดตล่าสุด **2026-10-30**

> **ชื่อโปรเจค**: แอปชื่อ **YTNx** (เดิมชื่อ YtKeep) · `applicationId = com.chue.ytnx` · label "YTNx"
> แต่ **โฟลเดอร์โปรเจค** ยังเป็น `~/android-dev/YtKeep` และ **namespace/package โค้ด** ยังเป็น `com.chue.ytkeep` (ไม่ได้ rename)

## ฟีเจอร์ค้นหา/สำรวจในแอป (2026-10-29)
- แท็บ: ค้นหา · 🔥 แนะนำ · ข่าว · เพลง · กีฬา · เทคโนโลยี · เกม · วิทยาศาสตร์ · ความรู้ · ตลก · ทำอาหาร · ท่องเที่ยว · ธรรมชาติ · หนัง · ติดตาม · บันทึก
- ฟีด/ค้นหาเร็วใช้ InnerTube (`Feed.kt`, API เดียวกับหน้าเว็บ YouTube ~0.5-1 วิ ไม่ต้องล็อกอิน) — ค้นหาแบบกรอง: `Feed.LATEST` (ใหม่สุด) / `Feed.POPULAR` (ยอดวิว), key ใน `Feed` เป็น public key ของเว็บ
- **ห้ามใช้ `FEtrending`/`FEexplore`** — ตอนนี้คืน 400 INVALID_ARGUMENT (ทดสอบ 28/10/69); ใช้ search+params แทน
- แท็บ "ติดตาม" ใช้ yt-dlp ดึงวิดีโอช่อง (`https://www.youtube.com/channel/<id>`) เพราะ browse channel ผ่าน InnerTube คืนวิดีโอเปล่า
- `Store.kt`: เก็บคำค้นที่เปิดคลิป หัวข้อ ช่องที่สนใจ และวิดีโอที่เปิดล่าสุดใน SharedPreferences บนอุปกรณ์; คะแนนค่อย ๆ ลดลงเมื่อมีการใช้งานใหม่ และล้างได้จากหน้า "ตั้งค่า"
- แท็บ "แนะนำ": ใช้คำค้นและหัวข้อที่ได้คะแนนสูงสุด พร้อมค้นหัวข้ออื่นเพื่อเพิ่มความหลากหลาย; ซ่อนคลิปที่เพิ่งเปิดและจำกัดจำนวนคลิปต่อช่อง
- คำค้น/หัวข้อที่เลือกใช้ดึงผลลัพธ์จะถูกส่งไปยัง YouTube; ประวัติและคะแนนส่วนบุคคลเก็บในเครื่องเท่านั้น
- กรองคลิป LIVE ออกจากฟีด (badges มี LIVE) — yt-dlp ดึงไลฟ์ค้าง/ช้า
- ช่องค้นหาเป็น **ค้นหาคำ/ชื่อช่อง** (ไม่ใช่ช่องวางลิงก์) — ตรวจ regex อัตโนมัติ ถ้าเป็นลิงก์จะดึงฟอร์แมตให้, กด Enter (IME Search) ได้
- ผลค้นหามีสองส่วน: `Feed.searchChannels` (channelRenderer, กด = ติดตาม/เลิกติดตาม) และ `Feed.search` (videoRenderer)

### หน้าย่อยช่อง (v1.4, 2026-10-29)
- กดชื่อช่องในผลค้นหา → เข้าแท็บ `channel` แสดงวิดีโอ 30 คลิปแรกของช่อง พร้อมปุ่มย้อนกลับ (`ArrowBack`) กลับไปผลค้นหาเดิม
- ดึงข้อมูลด้วย yt-dlp: `Yt.channelVideos()` → `--flat-playlist --playlist-end 30 --dump-json` (ไม่ใช้ InnerTube browse เพราะคืนวิดีโอเปล่า)
- เรียงได้ 2 แบบด้วย `FilterChip` — **ใหม่สุด** (ค่าเริ่มต้น, ตามลำดับ playlist) / **ยอดวิว** (sort จาก `parseViews`)
- แต่ละแถวใช้ `HitRow` เดิม: กดเปิด (เล่นสตรีม + บันทึก signal `OPENED`), กดค้าง = เมนูดาวน์โหลด, ปุ่มบันทึก
- สถานะ: build ผ่าน (2026-10-30) — **ยังไม่ได้กดทดสอบ UI บน emulator**

## 1. เป้าหมาย

แอป Android ส่วนตัว (ไม่ขึ้น Store) สำหรับมือถือ **Infinix Smart 9 (X6532), Android 14, RAM 3GB** ใช้:

1. วางลิงก์ YouTube → เลือกความละเอียด → ดาวน์โหลดเก็บในเครื่อง (รวมภาพ+เสียงเป็นไฟล์เดียว)
2. ดูย้อนหลังได้ตอนไม่มีเน็ต
3. ฟัง/เล่นต่อได้ตอนปิดจอ (foreground media service + notification)

ไม่ใช่ fork ของ NewPipe/Seal — เป็นแอปเขียนเอง ฝัง `yt-dlp` (Python) + `ffmpeg` รันบนเครื่องผ่าน `youtubedl-android`

> หมายเหตุทางกฎหมาย: การดาวน์โหลดจาก YouTube ผิด ToS — ใช้ส่วนตัวเท่านั้น (แจ้งผู้ใช้แล้ว)

## 2. สถานะปัจจุบัน (สรุปสั้น)

| ส่วน | สถานะ |
|---|---|
| ดึงข้อมูลวิดีโอ (title/ปก/ความยาว/รายการฟอร์แมต) | ✅ ทำงานบน emulator (ป้ายบอก codec + ขนาดตรงกับไฟล์จริง) |
| ดาวน์โหลด + merge ภาพ/เสียง ด้วย ffmpeg | ✅ ทำงาน (ทดสอบ 480p: av1 854×480 + AAC 6ch, 57 MB) |
| คลังในเครื่อง (index.json) | ✅ ทำงาน |
| เล่นไฟล์ในเครื่อง + หน้าจอ PlayerView | ✅ ทำงาน (PLAYING, ภาพขึ้นเต็มจอ letterbox) |
| เล่นสตรีมตรงจาก YouTube | ✅ โค้ดพร้อม ยังไม่ทดสอบครบ |
| เล่นตอนปิดจอ / คุมจาก notification | ✅ ทดสอบบน emulator (Dozing แล้ว position เดินต่อ, `cmd media_session dispatch pause/play` ทำงาน) |
| ฟีดแนะนำเฉพาะบุคคล | ✅ ใช้คำค้น/หัวข้อ/ช่องที่ผู้ใช้สนใจ, ตัดคลิปที่เพิ่งเปิด, ล้างประวัติได้ |
| หน้าย่อยช่อง (กดช่อง → วิดีโอช่อง + เรียงใหม่/ยอดวิว) | ✅ โค้ด + build ผ่าน (v1.4) — ยังไม่ทดสอบ UI บน emulator |
| ทดสอบบนเครื่องจริง (arm64) | ❌ ยังไม่ได้ — ไม่มีเครื่องต่อ adb |
| ตัด ABI x86_64 ออก + build release | ❌ ยังไม่ได้ทำ |

## 3. สถาปัตยกรรม / โฟลว์

```
MainActivity (Compose)
  ├─ Yt.fetchFast(ctx,url)  → client android      → แสดงรายการเร็ว (360p muxed เท่านั้น)
  └─ Yt.fetchHd(ctx,url)    → client tv_embedded  → เติมรายการ HD + audio-only (ช้ากว่า)
         │
         ├─ เล่นสตรีม: Yt.direct() → URL ตรง → PlayerService (ExoPlayer)
         └─ ดาวน์โหลด: DownloadService (FGS dataSync)
                          yt-dlp -f <selector> → ffmpeg merge → Lib.add() → broadcast CHANGED

PlayerService : MediaSessionService + ExoPlayer
PlayerActivity: PlayerView (media3-ui) ผูกกับ MediaController ของ session
Lib           : index.json ใน filesDir, ไฟล์จริงใน getExternalFilesDir("media")
```

### ทำไมต้องแยก fast/HD
`tv_embedded` ให้ความละเอียดถึง 2160p แต่ yt-dlp ต้องแก้ JS challenge (nsig) ผ่าน **QuickJS** ซึ่งบน emulator ใช้เวลาเป็นนาที (เคย 6 นาที) และ **ไม่มี API ยกเลิก** (`getInfo()` ไม่คืน process id) จึงยิง `android` client (ไม่ใช้ JS, ~20 วิ) ให้ผู้ใช้เห็นรายการก่อน แล้วค่อยเติม HD เบื้องหลัง

## 4. ไฟล์สำคัญ

| ไฟล์ | หน้าที่ |
|---|---|
| `app/src/main/java/com/chue/ytkeep/MainActivity.kt` | UI หลัก (Compose), วางลิงก์/ดึงข้อมูล/รายการคุณภาพ/คลัง, รับ share intent |
| `.../Yt.kt` | ห่อ youtubedl-android: `ensure()`, `maybeUpdate()`, `fetch/fetchFast/fetchHd`, `direct()`, สร้าง `Choice` (label/selector), ประมาณขนาดไฟล์ |
| `.../DownloadService.kt` | FGS `dataSync` + semaphore 1, สั่ง yt-dlp ดาวน์โหลด, ย้ายไฟล์, `Lib.add` |
| `.../PlayerService.kt` | `MediaSessionService` + ExoPlayer (UA, `DefaultDataSource`, wake mode) |
| `.../PlayerActivity.kt` | หน้าจอเล่นวิดีโอ (PlayerView) — ต้องมี `Modifier.fillMaxSize()` ไม่งั้นภาพไปชิดบน |
| `.../Lib.kt` | ดัชนีไฟล์ในเครื่อง (`filesDir/index.json`) + add/remove/all |
| `app/build.gradle.kts` | dependency + `abiFilters` |
| `app/src/main/AndroidManifest.xml` | activity/service/permissions/FGS types |

## 5. Dependency ที่สำคัญ (อย่าเปลี่ยนมั่ว)

```kotlin
implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")  // Maven Central
implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")    // Maven Central
implementation("androidx.media3:media3-exoplayer:1.4.1")
implementation("androidx.media3:media3-session:1.4.1")
implementation("androidx.media3:media3-ui:1.4.1")   // PlayerView
```

- **ห้ามกลับไปใช้** `com.github.yausername:youtubedl-android:0.14.0` (JitPack, Python เก่า) และ `com.github.junkfood02:...` (JitPack build ล้ม "No build artifacts found")
- 0.18.1 = Kotlin API, Python 3.12.11, package `com.yausername.youtubedl_android` (เหมือนเดิม)
- `library` **ไม่พก ffmpeg** — ต้องเพิ่ม artifact `ffmpeg` และเรียก `FFmpeg.getInstance().init(ctx)`

## 6. คำสั่ง build / ติดตั้ง / ทดสอบ

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=$HOME/android-dev/sdk
cd ~/android-dev
./gradle-8.11.1/bin/gradle -p YtKeep assembleDebug --no-daemon -q
# APK: ~/android-dev/YtKeep/app/build/outputs/apk/debug/app-debug.apk
adb install -r YtKeep/app/build/outputs/apk/debug/app-debug.apk
```

Emulator ที่ใช้ทดสอบ (มีอยู่แล้วบนเครื่อง dev):

```bash
$ANDROID_HOME/emulator/emulator -avd t35 -no-window -no-audio -no-boot-anim \
  -gpu swiftshader_indirect -no-snapshot &     # AVD: pixel_5 / android-35 / google_apis / x86_64
adb root        # ใช้ดึงไฟล์ใน app data ได้
```

ทดสอบเร็ว ๆ ผ่าน share intent (ไม่ต้องพิมพ์ URL):

```bash
adb shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "https://www.youtube.com/watch?v=aqz-KE-bpKQ" \
  -n com.chue.ytkeep/.MainActivity
```

ตรวจผลดาวน์โหลด/เล่น:

```bash
adb shell "ls -l /storage/emulated/0/Android/data/com.chue.ytkeep/files/media"
adb shell "dumpsys media_session | grep -m1 -oE 'state=[A-Z]+\([0-9]\)'"
adb pull "<ไฟล์.mp4>" /tmp/x.mp4 && ffprobe -v error -show_entries stream=codec_type,codec_name,width,height -of default=nw=1 /tmp/x.mp4
```

## 7. กับดักที่เจอแล้ว (อ่านก่อนแก้)

1. **merge ได้ไฟล์ภาพอย่างเดียว** — เกิดเมื่อไม่ได้เรียก `FFmpeg.getInstance().init(ctx)`; ตัว library จะไม่ตั้ง `--ffmpeg-location`/`LD_LIBRARY_PATH` ให้ และ ffmpeg จริงถูกห่อใน `libffmpeg.zip.so` (38 MB/ABI) ต้องให้ FFmpeg.init แตกออกมาก่อน
2. **เล่นไฟล์ในเครื่องแล้ว `ClassCastException: FileURLConnection → HttpURLConnection`** — เพราะใส่ `DefaultHttpDataSource.Factory` ตรง ๆ ต้องห่อด้วย `DefaultDataSource.Factory(context, http)` (แก้แล้วใน `PlayerService`)
3. **QuickJS ค้าง/ช้ามาก** — อาการ: process `libqjs.so --script …/cache/tmpXXXX.js` กิน CPU ต่อเนื่อง, python idle. ตรวจด้วย `adb shell ps -A | grep libqjs`. แก้เชิงกลยุทธ์ด้วย fast/HD split (ยังช้าอยู่ถ้าเลือก HD)
4. **`getInfo()` ยกเลิกไม่ได้** (ไม่มี process id) → อย่าออกแบบ UI ที่รอ HD แบบ blocking
5. **ขนาดไฟล์เป็น 0** ในหลายฟอร์แมต → ใช้ `fileSizeApproximate` หรือประมาณจาก `tbr * duration * 125`
6. **AV1 (`av01`) เล่นหนักบนเครื่องรุ่นเล็ก** → `Yt.kt` เรียง avc1 ก่อน av1 และ selector พยายาม `[vcodec^=avc1]+ba[acodec^=mp4a]` ก่อน fallback
7. **ป้ายขนาดไม่ตรงกับไฟล์ที่โหลด** — เดิมใช้ selector แบบ `bv*[height<=H][vcodec^=avc1]` ซึ่งอาจไปหยิบฟอร์แมตคนละตัวกับที่ label คำนวณ (เจอจริง: label 83 MB แต่ได้ไฟล์ 173 MB) → ตอนนี้ผูก `formatId` ตรง ๆ (`"<id>+ba[acodec^=mp4a]/<id>+ba/<id>"`) และ label อิงฟอร์แมตเดียวกัน + บวกขนาดเสียง แล้วแสดง codec ในป้าย (h264/vp9/av1)
8. **SSL**: ถ้าเห็น `unable to get local issuer certificate` แปลว่าไม่ได้ตั้ง `SSL_CERT_FILE` (library ตั้งให้เองเมื่อใช้ `init`; site-packages ไม่มี certifi)
9. **adb input text ภาษาไทย/URL มักตกตัวอักษร** — ใช้ `am start -a SEND` หรือพิมพ์เป็นชุดสั้น ๆ
10. `abiFilters` ปัจจุบันมี `x86_64` (ใส่เพื่อทดสอบ emulator) — **ต้องตัดออกก่อนปล่อย APK จริง** (ขนาดลดจาก ~181 MB → ~130 MB)
11. ครั้งแรกหลังติดตั้ง `PlayerService` เคย ANR 21 วิ (dexopt) — ไม่ใช่บั๊กโค้ด

### การรับมือ YouTube เปลี่ยนโครงสร้าง
- แอปอัปเดต yt-dlp เองอัตโนมัติทุก 3 วันผ่าน **nightly channel** (`UpdateChannel._NIGHTLY`) — extractor fix มาก่อน stable
- มีปุ่ม **"อัปเดต yt-dlp"** + แสดงเวอร์ชันที่ฝังอยู่ท้ายหน้าแรก (เช่น `yt-dlp 2026.10.26`) ให้กดเองได้เมื่อมีคลิปที่ดึงไม่ได้
- `Yt.fetch` มี fallback 3 ชั้น: client ที่ขอ → client default → บังคับอัปเดตแล้วลองใหม่
- จุดที่ยังเสี่ยง: YouTube บังคับ PO token / SABR กับบาง client — ถ้าเจอ ให้ลองสลับ `YT_CLIENT_VR` เป็น `tv`, `ios`, `web_embedded` (แก้ที่ `Yt.kt`)

## 8. งานที่ควรทำต่อ (เรียงตามลำดับ)

1. Build APK สำหรับ `arm64-v8a,armeabi-v7a`, ส่งไปติดตั้งบนเครื่องจริง แล้วทดสอบ: ดาวน์โหลด 720p h264, เล่น, ปิดจอ, คุมจาก notification, หูฟัง Bluetooth
2. ยืนยัน ABI ของ Infinix Smart 9 (`adb shell getprop ro.product.cpu.abilist`) — ถ้าเป็น arm64 ตัวเดียวตัด `armeabi-v7a` ได้อีก
3. ทดสอบ `playChoice()` (เล่นสตรีม) กับทุกความละเอียด และเช็กว่า `Yt.direct` + `--extractor-args tv_embedded` ทำงาน
4. ลดเวลา HD: ลอง `--cache-dir` แทน `--no-cache-dir` ของ library (ถ้าทำได้จะ cache nsig) หรือยอมรับว่า HD ช้า
5. ปรับ UX: แสดง % ดาวน์โหลดในแอป (ตอนนี้มีแค่ notification), ปุ่มยกเลิก, ลบ temp dir เมื่อล้มเหลว, ปุ่มอัปเดต yt-dlp
6. พิจารณา build 2 รุ่น: ไม่มี ffmpeg (~90 MB, ได้แค่ 360p muxed) และมี ffmpeg (~130 MB, HD merged)
7. ทำ release build + เซ็น keystore ถ้าจะติดตั้งถาวร

## 9. ผลทดสอบที่ยืนยันแล้ว (2026-10-28, emulator x86_64)

- `Yt.fetchFast` (client `android`): ~22 วิ ได้ 360p muxed 27 MB
- `Yt.fetchHd` (client `tv_embedded`): ~52 วิ ได้ 2160p 2.1 GB / 1440p 1.0 GB / 1080p 561 MB / 720p 83 MB / 480p / 360p (ขนาด 2160–1080 เป็นค่าประมาณจาก tbr)
- ดาวน์โหลด 480p + merge: 57 MB, `ffprobe` = av1 854×480 + aac 6ch, 634.5 วิ
- เล่นไฟล์ในเครื่อง: `state=PLAYING`, ภาพขึ้นเต็มจอ (letterbox), หน้าจอ PlayerActivity
- ปิดจอ (power → `mWakefulness=Dozing`): เล่นต่อเนื่อง position 350767→358734 ms ใน 8 วิ; สั่ง `cmd media_session dispatch pause` → PAUSED(2), `play` → PLAYING(3)
- คลัง: `index.json` ใน `filesDir`, ไฟล์ใน `/storage/emulated/0/Android/data/com.chue.ytnx/files/media/` (path ภายนอกผูกกับ `applicationId`; เปลี่ยนชื่อเป็น `com.chue.ytnx` แล้ว ไฟล์เก่าที่ติดตั้งสมัย `com.chue.ytkeep` จะไม่ถูกเห็น)

- 2026-10-30: `assembleDebug` ผ่านหลังเพิ่มหน้าย่อยช่อง (v1.4, `versionCode 5`, `applicationId com.chue.ytnx`, label "YTNx") — ยังไม่ได้ติดตั้ง/ทดสอบ UI บน emulator

## 10. บริบทเครื่อง dev

- JDK 21: `/usr/lib/jvm/java-21-openjdk-amd64`
- Android SDK: `~/android-dev/sdk` (build-tools + platform 35 + emulator + system-image android-35 google_apis x86_64)
- Gradle: `~/android-dev/gradle-8.11.1`
- yt-dlp บน host (ใช้อ้างอิง/ทดลอง): `~/.local/bin/yt-dlp` (2026.08.19); บนเครื่องแอปอัปเดตเป็น 2026.10.26
- ชุดทดลอง yt-dlp บน emulator: `/data/local/tmp/ydl/` (bin/libpython.so, bin/libqjs.so, yt-dlp, packages/python/usr) — ต้องตั้ง `PYTHONHOME`, `LD_LIBRARY_PATH`, `SSL_CERT_FILE=<pythonHome>/usr/etc/tls/cert.pem`
