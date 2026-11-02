# YTNx v2 design and handoff

This document records the approved v2 scope, behavior, constraints, and implementation handoff state. YTNx 2.0 is published; the remaining emulator and device checks are listed below.

## Goal

Make YTNx easier to use for its main task: find a YouTube channel or video, choose a download quality, and play downloaded files later. Keep the app usable on an Infinix Smart 9 with Android 14 and 3 GB of RAM.

The approved visual direction is a near-black interface with restrained, muted red accents. The v2 channel mockup is in the session artifacts as `ytnx-v2-channel-mockup.svg` and `ytnx-v2-channel-mockup.png`.

## Navigation

Use four bottom navigation items:

1. **ค้นหา**: Search for channels or videos. Accept a text query or a pasted YouTube link.
2. **แนะนำ**: Show personalized recommendations. Keep topic chips inside this screen instead of giving every topic a bottom navigation item.
3. **ติดตาม**: Show followed channels. Selecting a channel opens its videos.
4. **คลัง**: Show download tasks, downloaded files, and saved videos.

Keep settings accessible from the top app bar. Do not add a settings bottom navigation item.

Keep channel details as a subpage with a back action. Show up to 30 videos and allow sorting by newest or views.

## Search and channel behavior

- Search results include matching channels and videos.
- Selecting a channel opens its channel page.
- Let the user follow or unfollow a channel from search results. The Following screen lists channels and opens their pages.
- Each video supports playback, saving, and download-quality selection.
- Keep the current fast video-info fetch for initial results. Fetch HD formats in the background. Do not block the UI while waiting for HD formats.
- Keep existing YouTube and yt-dlp data paths. Do not add account sign-in or cloud sync.

## Library behavior

The Library screen has three sections selected within the screen:

- **ดาวน์โหลด**: Show queued, active, completed, failed, and cancelled download tasks.
- **ไฟล์ของฉัน**: Show downloaded files. Search by title or uploader. Sort by newest, name, or size. Allow playback and deletion.
- **บันทึกไว้ดู**: Show saved video links. Search by title or channel. Sort by newest or name. Keep the existing save and unsave behavior.

Ask for confirmation before deleting a downloaded file. If deletion fails, show the error and keep the file in the list.

## Download behavior

- Offer audio-only, 360p, 480p, 720p, 1080p, and best-available options when those formats exist.
- Default the quality picker to the user's configured default, but allow a different choice for each download.
- Queue downloads and process one task at a time. Do not run multiple yt-dlp downloads concurrently. This limits CPU, memory, and storage pressure on the target phone.
- Persist each task's ID, video URL, selector, title, uploader, quality label, media type, creation time, state, progress, and failure message.
- Show task state and percentage progress in the app. A queued task becomes cancelled immediately. A running task enters a cancelling state until yt-dlp stops. A failed or cancelled task can be retried.
- Retain completed and cancelled tasks until the user clears finished entries. Keep failed tasks available for retry.
- If Android stops the service while a task is running, mark that task failed with a useful retry message when the service starts again.
- On cancellation or failure, remove that task's temporary directory. Add a file to `Lib` only after yt-dlp has produced the final media file.
- Report errors in the Library screen. Do not rely on a notification as the only error message.

## Performance and scope

- Keep recommendation and channel result counts bounded. Do not prefetch every topic or load a large feed in the background.
- Refresh download progress only while the Library screen is visible. Do not keep a timer running across unrelated screens.
- Keep downloads sequential. Do not add background playback changes, account features, cloud storage, or a complex recommendation engine as part of this work.
- Keep saved videos in the Library rather than restoring a fifth bottom navigation item.

## Existing project constraints

- Application ID: `com.chue.ytnx`. Namespace and source package: `com.chue.ytkeep`. Do not rename them.
- Do not change `io.github.junkfood02.youtubedl-android:library:0.18.1` or `:ffmpeg:0.18.1` to JitPack or version 0.14.0.
- Call `Yt.ensure(ctx)` before download or merge operations. It initializes FFmpeg.
- In `PlayerService`, keep `DefaultDataSource.Factory(this, http)` around the HTTP factory so local `file://` playback works.
- In `PlayerActivity`, keep `Modifier.fillMaxSize()` on the `AndroidView`.
- `getInfo()` cannot be cancelled. Keep the fast-fetch then background-HD pattern.
- Do not leave `android.util.Log` calls in `DownloadService`.
- The default ABI configuration is for arm64 and armeabi-v7a. Keep the x86_64 test override available. Do not remove it before emulator testing.
- The user cannot connect the Infinix phone over USB. Do not request device testing as a prerequisite. Report that real-device verification remains outstanding.
- When changing the app version, set `versionCode` to 7 and `versionName` to `2.0`. Update the README's current status and next-work sections.

## Current implementation state

The implementation includes the existing followed-channel screen, release metadata and handoff edits, plus these v2 changes:

- `DownloadQueue.kt` persists queue tasks and owns their state transitions.
- `DownloadService.kt` processes tasks one at a time, reports progress, cancels yt-dlp using the same process ID passed to `execute()`, and removes temporary files.
- `Lib.kt` searches and sorts downloaded files. Failed file deletion raises an error.
- `LibraryFilterTest.kt` tests local-file search and sorting. `DownloadTaskTest.kt` tests cancellation, completion, recovery, and retry transitions.
- `MainActivity.kt` has four bottom tabs, settings in the header, and the download, local-file, and saved-video sections in Library. File deletion has confirmation and displays errors.
- `app/build.gradle.kts` sets version 2.0 and version code 7. `README.md` and `AGENTS.md` point to this handoff.
- `testDebugUnitTest assembleDebug` pass. YTNx 2.0 was published at GitHub Release `v2.1`. The normal debug APK contains `arm64-v8a` and `armeabi-v7a`; metadata reports `com.chue.ytnx`, version code 7, and version name 2.0.
- On emulator `t35`, the x86_64 build launches, search returns channels and videos, and a channel page displays exactly 30 results. The three Library sections and Settings screen open successfully.
- Download queue actions and service-restart recovery still need emulator testing. No Infinix Smart 9 testing was possible because the phone cannot connect over USB or ADB over Wi-Fi.

Review the current diff before editing. Some modified lines predate this task.

## Completion checklist

- [x] Add four-item navigation and keep settings reachable from the header.
- [x] Add the download-state label helper and the three Library sections.
- [x] Confirm file deletion and display deletion errors.
- [x] Use the same yt-dlp process ID for `execute()` and `destroyProcessById()`.
- [x] Add unit tests for queue state transitions and Library filtering and sorting.
- [x] Remove stale temporary files during download completion and service recovery.
- [x] Stop polling download progress when the user leaves Library.
- [x] Use the muted-red palette for active controls and accents.
- [x] Set version 2.0 and version code 7. Update `README.md` and `AGENTS.md`.
- [x] Run `testDebugUnitTest`, `assembleDebug`, APK metadata inspection, and `git diff --check`.
- [x] Install the x86_64 debug build on `t35`; verify app launch, search, the 30-video channel cap, Library sections, and Settings.
- [ ] Test download progress, cancellation, retry, and service-restart recovery on an emulator.
- [ ] Review the final diff and test all remaining UI and download flows.
- [ ] Do not claim real Infinix testing. Do not publish until the UI and download flow pass testing.

## Build commands

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=$HOME/android-dev/sdk
cd ~/android-dev
./gradle-8.11.1/bin/gradle -p YtKeep testDebugUnitTest assembleDebug --no-daemon -q
```
