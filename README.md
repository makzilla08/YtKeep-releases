# YTNx — แอปดาวน์โหลด/เล่นคลิป YouTube แบบออฟไลน์ (Android)

เอกสารส่งต่อ (handoff) สำหรับคนหรือ AI คนถัดไปที่ต้องทำงานต่อ — อัปเดตล่าสุด **2026-11-05**

> **ชื่อโปรเจค**: แอปชื่อ **YTNx** (เดิมชื่อ YtKeep) · `applicationId = com.chue.ytnx` · label "YTNx"
> แต่ **โฟลเดอร์โปรเจค** ยังเป็น `~/android-dev/YtKeep` และ **namespace/package โค้ด** ยังเป็น `com.chue.ytkeep` (ไม่ได้ rename)

## ฟีเจอร์ค้นหา/สำรวจในแอป (2026-10-29)
- แถบหลักของ v2: ค้นหา · แนะนำ · ติดตาม · คลัง; หมวดข่าว เพลง กีฬา และหัวข้ออื่นอยู่ในหน้าคำแนะนำ
- คลังแบ่งเป็นคิวดาวน์โหลด ไฟล์ในเครื่อง และรายการบันทึก
- ฟีด/ค้นหาเร็วใช้ InnerTube (`Feed.kt`, API เดียวกับหน้าเว็บ YouTube ~0.5-1 วิ ไม่ต้องล็อกอิน) — ค้นหาแบบกรอง: `Feed.LATEST` (ใหม่สุด) / `Feed.POPULAR` (ยอดวิว), key ใน `Feed` เป็น public key ของเว็บ
- **ห้ามใช้ `FEtrending`/`FEexplore`** — ตอนนี้คืน 400 INVALID_ARGUMENT (ทดสอบ 28/10/69); ใช้ search+params แทน
- แท็บ "ติดตาม" ใช้ yt-dlp ดึงวิดีโอช่อง (`https://www.youtube.com/channel/<id>`) เพราะ browse channel ผ่าน InnerTube คืนวิดีโอเปล่า
- `Store.kt`: เก็บคำค้นที่เปิดคลิป หัวข้อ ช่องที่สนใจ และวิดีโอที่เปิดล่าสุดใน SharedPreferences บนอุปกรณ์; คะแนนค่อย ๆ ลดลงเมื่อมีการใช้งานใหม่ และล้างได้จากหน้า "ตั้งค่า"
- แท็บ "แนะนำ": ใช้คำค้นและหัวข้อที่ได้คะแนนสูงสุด พร้อมค้นหัวข้ออื่นเพื่อเพิ่มความหลากหลาย; ซ่อนคลิปที่เพิ่งเปิดและจำกัดจำนวนคลิปต่อช่อง
- หน้า "ตั้งค่า" เลือกแสดง/ซ่อนหัวข้อความสนใจได้; หัวข้อที่ซ่อนจะไม่แสดงเป็นแท็บและไม่ถูกเลือกเป็นหัวข้อในฟีดแนะนำ
- Mini-Player ลอยเหนือแถบนำทางตามต้นแบบ v3: แตะเพื่อกลับไปหน้าผู้เล่น, เล่น/หยุดชั่วคราว, ปิด และแสดงภาพปกกับความคืบหน้า
- คำค้น/หัวข้อที่เลือกใช้ดึงผลลัพธ์จะถูกส่งไปยัง YouTube; ประวัติและคะแนนส่วนบุคคลเก็บในเครื่องเท่านั้น
- กรองคลิป LIVE ออกจากฟีด (badges มี LIVE) — yt-dlp ดึงไลฟ์ค้าง/ช้า
- ช่องค้นหาเป็น **ค้นหาคำ/ชื่อช่อง** (ไม่ใช่ช่องวางลิงก์) — ตรวจ regex อัตโนมัติ ถ้าเป็นลิงก์จะดึงฟอร์แมตให้, กด Enter (IME Search) ได้
- ผลค้นหามีสองส่วน: `Feed.searchChannels` (channelRenderer, กด = ติดตาม/เลิกติดตาม) และ `Feed.search` (videoRenderer)

### หน้าย่อยช่อง (v1.4, 2026-10-29)
- กดชื่อช่องในผลค้นหา → เข้าแท็บ `channel` แสดงวิดีโอ 30 คลิปแรกของช่อง พร้อมปุ่มย้อนกลับ (`ArrowBack`) กลับไปผลค้นหาเดิม
- ดึงข้อมูลด้วย yt-dlp: `Yt.channelVideos()` → `--flat-playlist --playlist-end 30 --dump-json` (ไม่ใช้ InnerTube browse เพราะคืนวิดีโอเปล่า)
- เรียงได้ 2 แบบด้วย `FilterChip` — **ใหม่สุด** (ค่าเริ่มต้น, ตามลำดับ playlist) / **ยอดวิว** (sort จาก `parseViews`)
- แต่ละแถวใช้ `HitRow` เดิม: กดเปิด (เล่นสตรีม + บันทึก signal `OPENED`), กดค้าง = เมนูดาวน์โหลด, ปุ่มบันทึก
- สถานะ: ทดสอบบน emulator t35 (2026-11-02) — เปิดช่อง NASA และยืนยันว่าหน้าแสดง 30 คลิป

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
| คิวและการเล่นต่อเนื่อง (Queue & Continuous) | ✅ เล่นต่อเนื่องอัตโนมัติเมื่อเพลงจบ (Background/Dozing), ควบคุมคิว, เล่นแบบไม่ต้องเซฟ |
| เพลย์ลิสต์ (Playlists) | ✅ สร้าง/บันทึก/ลบ/เล่นเพลย์ลิสต์ในแท็บคลัง และเซฟคิวเป็นเพลย์ลิสต์ได้ทันที |
| การ์ดบราวซ์/เซิร์ชกดเข้าช่องได้ | ✅ แสดงชื่อช่องเด่นชัดพร้อมไอคอน และแตะเพื่อเปิดหน้าช่องได้ทันที |
| หน้าช่องแยกหมวด & โหลดจนหมด | ✅ แยก 4 หมวด (วิดีโอ, Shorts, ไลฟ์, เพลย์ลิสต์), ปุ่มโหลด +30 และโหลดจนหมด (Load All) |
| ฟีดแนะนำเฉพาะบุคคล | ✅ ใช้คำค้น/หัวข้อ/ช่องที่ผู้ใช้สนใจ, ตัดคลิปที่เพิ่งเปิด, ล้างประวัติได้ |
| หน้าย่อยช่อง (กดช่อง → วิดีโอช่อง + เรียงใหม่/ยอดวิว) | ✅ ทดสอบบน emulator t35: แสดงไม่เกิน 30 วิดีโอ |
| แท็บ "ติดตาม" เป็นรายชื่อช่องให้เลือก แล้วกดเข้าไปดูวิดีโอในช่องนั้น | ✅ มีใน v1.5; เคยทดสอบ flow บน emulator |
| เวอร์ชันล่าสุดที่พัฒนา | ✅ YTNx v3.2 / `versionCode 11`, `versionName "3.2"` (Build & Test ผ่านบน emulator t35) |
| APK ล่าสุดที่เผยแพร่ | ✅ [GitHub Release `v2.4`](https://github.com/makzilla08/YtKeep-releases/releases/tag/v2.4): `app-debug.apk`, YTNx v3.1 / versionCode 10, ARM 64/32-bit |
| YTNx v3.1 | ✅ เพิ่มหัวข้อความสนใจกำหนดเอง (พิมพ์เอง) ในหน้าตั้งค่า; แสดงเป็นแท็บในหน้าแนะนำและดึงฟีดตามคำค้น |
| YTNx v2.1 | ✅ ปรับหน้า Search และ Channel ตามม็อกอัป; unit tests ผ่านและทดสอบ flow ค้นหา/เปิดช่องบน t35 |
| คิวดาวน์โหลด v2 | ⚠️ มี progress/cancel/retry/recovery; ยังต้องทดสอบ flow เหล่านี้บน emulator |
| ทดสอบบนเครื่องจริง (arm64) | ❌ ยังไม่ได้ — Infinix Smart 9 เชื่อมต่อ ADB ไม่ได้ |
| APK ที่เผยแพร่ | เซ็นด้วย Android Debug certificate เดียวกับรุ่นก่อน; ยังไม่ได้ทดสอบติดตั้งบน Infinix Smart 9 |

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
- มีปุ่ม **"อัปเดต yt-dlp"** + แสดงเวอร์ชันที่ฝังอยู่ในหน้า "ตั้งค่า" (เช่น `yt-dlp 2026.10.26`) ให้กดเองได้เมื่อมีคลิปที่ดึงไม่ได้
- `Yt.fetch` มี fallback 3 ชั้น: client ที่ขอ → client default → บังคับอัปเดตแล้วลองใหม่
- จุดที่ยังเสี่ยง: YouTube บังคับ PO token / SABR กับบาง client — ถ้าเจอ ให้ลองสลับ `YT_CLIENT_VR` เป็น `tv`, `ios`, `web_embedded` (แก้ที่ `Yt.kt`)

## 8. งานที่ควรทำต่อ (เรียงตามลำดับ)

0. ทำตาม [ข้อกำหนดและแผนงานออกแบบ YTNx v3](./V3-DESIGN.md): Storage Health Meter, Bottom Sheet เลือกความละเอียด และแบ่งหน้า Feed/Search
1. ติดตาม [ข้อกำหนดออกแบบ YTNx v2](./V2-DESIGN.md): ตรวจ state machine ของคิวดาวน์โหลดและ race ระหว่างยกเลิก/เริ่มงาน
1. ทดสอบคิวดาวน์โหลดบน emulator ให้ครบ: เพิ่มงาน, ความคืบหน้า, ยกเลิก, ลองใหม่ และการกู้สถานะเมื่อ service เริ่มใหม่
2. ทดสอบ Infinix Smart 9 เมื่อเชื่อมต่อได้; USB เสียและเชื่อม ADB ผ่าน Wi-Fi ไม่สำเร็จ
3. ตรวจ `playChoice()` กับความละเอียดต่าง ๆ และ `Yt.direct` ที่ใช้ `tv_embedded`
4. ประเมินเวลาเรียกข้อมูล HD บนเครื่องจริง; `getInfo()` ยกเลิกไม่ได้ จึงต้องไม่บล็อก UI
5. ทดสอบ APK v2.1 ที่เผยแพร่บน Infinix Smart 9 เมื่อผู้ใช้สะดวก; ยืนยัน flow คิวดาวน์โหลดบน emulator

## 9. ผลทดสอบที่ยืนยันแล้ว (2026-10-28, emulator x86_64)

- `Yt.fetchFast` (client `android`): ~22 วิ ได้ 360p muxed 27 MB
- `Yt.fetchHd` (client `tv_embedded`): ~52 วิ ได้ 2160p 2.1 GB / 1440p 1.0 GB / 1080p 561 MB / 720p 83 MB / 480p / 360p (ขนาด 2160–1080 เป็นค่าประมาณจาก tbr)
- ดาวน์โหลด 480p + merge: 57 MB, `ffprobe` = av1 854×480 + aac 6ch, 634.5 วิ
- เล่นไฟล์ในเครื่อง: `state=PLAYING`, ภาพขึ้นเต็มจอ (letterbox), หน้าจอ PlayerActivity
- ปิดจอ (power → `mWakefulness=Dozing`): เล่นต่อเนื่อง position 350767→358734 ms ใน 8 วิ; สั่ง `cmd media_session dispatch pause` → PAUSED(2), `play` → PLAYING(3)
- คลัง: `index.json` ใน `filesDir`, ไฟล์ใน `/storage/emulated/0/Android/data/com.chue.ytnx/files/media/` (path ภายนอกผูกกับ `applicationId`; เปลี่ยนชื่อเป็น `com.chue.ytnx` แล้ว ไฟล์เก่าที่ติดตั้งสมัย `com.chue.ytkeep` จะไม่ถูกเห็น)

- 2026-10-30: `assembleDebug` ผ่านหลังเพิ่มหน้าย่อยช่อง (v1.4, `versionCode 5`, `applicationId com.chue.ytnx`, label "YTNx")
- 2026-11-02: APK จาก GitHub tag `v1.9` ตรวจพบว่าเป็น app version 1.4 (`versionCode 5`); ติดตั้ง APK v1.3 แล้วอัปเดตเป็น v1.4 บน emulator ผ่าน
- 2026-11-02: เผยแพร่ YTNx 1.5 (`versionCode 6`) ใน GitHub Release `v2.0`; unit tests, build, APK metadata, alignment และลายเซ็นผ่าน
- 2026-11-02: APK v1.5 มี arm64-v8a + armeabi-v7a และใช้ Android Debug certificate เดียวกับ APK รุ่นก่อน; ผู้ใช้ยังต้องทดสอบติดตั้งและ flow บนเครื่องจริง
- 2026-11-02: เผยแพร่ YTNx 2.0 (`versionCode 7`) ใน GitHub Release `v2.1`; ARM APK ใช้ลายเซ็นเดียวกับ v1.5 และ unit tests/build ผ่าน
- 2026-11-02: emulator `t35` ยืนยันค้นหา NASA, เปิดหน้าช่องได้ 30 คลิป, เปิดคลังและตั้งค่าได้; คิวดาวน์โหลดและเครื่อง Infinix ยังรอทดสอบ
- 2026-11-02: ปรับหน้า Search และ Channel ให้ใช้หัวข้อค้นหา, การ์ดช่อง/วิดีโอ, แบนเนอร์ช่อง และปุ่มดาวน์โหลดตามม็อกอัป; YTNx 2.1 (`versionCode 8`) เผยแพร่ใน GitHub Release `v2.2`
- 2026-11-02: YTNx v3 (`versionCode 9`, `versionName 3.0`) รองรับการแสดงผลบราวซ์ครั้งละ 30 คลิป พร้อมปุ่ม "แสดงผลเพิ่มเติม (+30 รายการ)", อัปเดตสถาปัตยกรรมและแผนงานดีไซน์ใน `V3-DESIGN.md`; เผยแพร่ใน GitHub Release `v2.3` (ชื่อ "YTNx v3")
- 2026-11-04: เพิ่ม Mini-Player ตาม prototype v3 และตัวเลือกแสดง/ซ่อนหัวข้อความสนใจ; `testDebugUnitTest` และ build สำหรับ x86_64/ARM ผ่าน
- 2026-11-04: ทดสอบบน emulator `t35`: ซ่อนหัวข้อแล้วแท็บหาย, เล่นเสียงได้สถานะ `PLAYING`, ปุ่มพักเป็น `PAUSED`, เล่นต่อกลับ `PLAYING`, ปิดแล้ว MediaSession เป็น `NONE`
- 2026-11-04: เพิ่มหัวข้อความสนใจกำหนดเอง (พิมพ์เอง) ในหน้าตั้งค่า; แสดงเป็นแท็บและใช้ดึงฟีด; build/test ผ่านและอัปโหลด APK ไป GitHub Release `v2.3` แล้ว

## 10. บริบทเครื่อง dev

- JDK 21: `/usr/lib/jvm/java-21-openjdk-amd64`
- Android SDK: `~/android-dev/sdk` (build-tools + platform 35 + emulator + system-image android-35 google_apis x86_64)
- Gradle: `~/android-dev/gradle-8.11.1`
- yt-dlp บน host (ใช้อ้างอิง/ทดลอง): `~/.local/bin/yt-dlp` (2026.08.19); บนเครื่องแอปอัปเดตเป็น 2026.10.26
- ชุดทดลอง yt-dlp บน emulator: `/data/local/tmp/ydl/` (bin/libpython.so, bin/libqjs.so, yt-dlp, packages/python/usr) — ต้องตั้ง `PYTHONHOME`, `LD_LIBRARY_PATH`, `SSL_CERT_FILE=<pythonHome>/usr/etc/tls/cert.pem`
