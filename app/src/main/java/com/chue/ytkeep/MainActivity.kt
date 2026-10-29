package com.chue.ytkeep

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var future: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var pending: ((MediaController) -> Unit)? = null
    private var sharedUrl by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DownloadService.channel(this)
        sharedUrl = intent?.getStringExtra(Intent.EXTRA_TEXT)?.let { extractUrl(it) } ?: ""

        val token = SessionToken(this, ComponentName(this, PlayerService::class.java))
        future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            runCatching {
                controller = future.get()
                pending?.invoke(controller!!)
                pending = null
            }
        }, ContextCompat.getMainExecutor(this))

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0E0E12))) {
                Surface(color = Color(0xFF0E0E12), modifier = Modifier.fillMaxSize()) {
                    App(
                        sharedUrl = sharedUrl,
                        playChoice = { url, selector, title, author ->
                            playChoice(url, selector, title, author)
                        },
                        playFile = { f, t, a -> play(Uri.fromFile(f).toString(), t, a) },
                        download = { c, info ->
                            DownloadService.enqueue(this, c, info)
                            Toast.makeText(this, "เริ่มดาวน์โหลดแล้ว — ปิดจอได้เลย", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedUrl = extractUrl(it) }
    }

    private fun playChoice(url: String, selector: String, title: String, author: String) {
        val isPage = url.contains("youtube.com/watch") || url.contains("youtu.be/")
        if (!isPage) {
            play(url, title, author)
            return
        }
        lifecycleScope.launch {
            val direct = withContext(Dispatchers.IO) {
                runCatching {
                    Yt.direct(this@MainActivity, url, if (selector.contains("+")) "b" else selector)
                }.getOrNull()
            }
            if (direct != null) play(direct, title, author)
            else Toast.makeText(this@MainActivity, "ดึงลิงก์เล่นไม่สำเร็จ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun play(url: String, title: String, author: String) {
        val run = { c: MediaController ->
            c.setMediaItem(
                MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder().setTitle(title).setArtist(author).build()
                    )
                    .build()
            )
            c.prepare()
            c.play()
        }
        controller?.let(run) ?: run { pending = run }
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    override fun onDestroy() {
        if (::future.isInitialized) MediaController.releaseFuture(future)
        super.onDestroy()
    }
}

@Composable
fun App(
    sharedUrl: String,
    playChoice: (String, String, String, String) -> Unit,
    playFile: (File, String, String) -> Unit,
    download: (Choice, Info) -> Unit
) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(sharedUrl) }
    var info by remember { mutableStateOf<Info?>(null) }
    var loading by remember { mutableStateOf(false) }
    var hdLoading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var lib by remember { mutableStateOf<List<Lib.Item>>(emptyList()) }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Feed.Channel>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("search") }
    var cat by remember { mutableStateOf("") }
    var menuHit by remember { mutableStateOf<Hit?>(null) }
    var defQ by remember { mutableStateOf(Store.defQuality(ctx)) }
    var feed by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var feedLoading by remember { mutableStateOf(false) }
    var ytver by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }
    var confirmClearRecommendations by remember { mutableStateOf(false) }

    LaunchedEffect(sharedUrl) { if (sharedUrl.isNotBlank()) url = sharedUrl }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            tick++
        }
    }
    LaunchedEffect(tick) {
        lib = withContext(Dispatchers.IO) { Lib.all(ctx) }
    }
    val savedIds = remember(tick) { Store.saved(ctx).map { it.id }.toSet() }
    val followedIds = remember(tick) { Store.follows(ctx).map { it.second }.toSet() }
    LaunchedEffect(Unit) {
        ytver = withContext(Dispatchers.IO) { runCatching { Yt.version(ctx) }.getOrDefault("?") }
    }

    val doFetch: (String) -> Unit = { videoUrl ->
        if (videoUrl.isNotBlank() && !loading) {
            loading = true
            err = null
            info = null
            scope.launch {
                var ok = false
                runCatching { withContext(Dispatchers.IO) { Yt.fetchFast(ctx, videoUrl) } }
                    .onSuccess { ok = true; info = it }
                    .onFailure { err = it.message ?: it.javaClass.simpleName }
                loading = false
                if (!ok) return@launch
                hdLoading = true
                runCatching { withContext(Dispatchers.IO) { Yt.fetchHd(ctx, videoUrl) } }
                    .onSuccess { hd -> if (hd.video.size > (info?.video?.size ?: 0)) info = hd }
                hdLoading = false
            }
        }
    }

    val isLink = Regex("https?://|youtu\\.be/|youtube\\.com/").containsMatchIn(url)

    val doSearch: () -> Unit = {
        val q = url.trim()
        if (q.isNotBlank() && !searching) {
            searching = true
            err = null
            hits = emptyList()
            channels = emptyList()
            info = null
            scope.launch {
                val (vids, chans) = withContext(Dispatchers.IO) {
                    runCatching {
                        Feed.search(q, Feed.LATEST, 24) to Feed.searchChannels(q, 6)
                    }.getOrElse { e ->
                        err = e.message ?: e.javaClass.simpleName
                        emptyList<Hit>() to emptyList<Feed.Channel>()
                    }
                }
                hits = vids
                channels = chans
                if (vids.isEmpty() && chans.isEmpty() && err == null)
                    err = "ไม่พบผลลัพธ์ ลองคำอื่น หรือพิมพ์ชื่อช่องให้ตรงขึ้น"
                searching = false
            }
        }
    }

    val quickDownload: (Hit, String) -> Unit = { h, q ->
        scope.launch {
            val v = withContext(Dispatchers.IO) {
                runCatching {
                    if (q == "best" || q == "audio" || q == "360") Yt.fetchFast(ctx, h.url)
                    else Yt.fetchHd(ctx, h.url)
                }.getOrNull()
            }
            if (v == null) {
                err = "ดึงข้อมูลไม่สำเร็จ ลองใหม่หรืออัปเดต yt-dlp"
            } else {
                val c = when (q) {
                    "audio" -> v.audio.firstOrNull() ?: v.video.lastOrNull()
                    "best" -> v.video.firstOrNull() ?: v.audio.firstOrNull()
                    else -> v.video.firstOrNull { it.label.startsWith(q + "p") }
                        ?: v.video.lastOrNull() ?: v.audio.firstOrNull()
                }
                if (c == null) {
                    err = "ไม่พบคุณภาพ $q"
                } else {
                    Store.recordInteraction(ctx, h, Store.InterestSignal.DOWNLOADED)
                    download(c, v)
                }
            }
        }
    }

    val loadFeed: (String) -> Unit = { t ->
        tab = t
        feedLoading = true
        feed = emptyList()
        info = null
        err = null
        scope.launch {
            val got = withContext(Dispatchers.IO) {
                runCatching { feedFor(ctx, if (t == "hot") cat else "") }.getOrElse {
                    err = it.message ?: it.javaClass.simpleName
                    emptyList()
                }
            }
            feed = got
            if (got.isEmpty() && err == null) err = "ว่างเปล่า — ลองอัปเดต yt-dlp หรือติดตามช่องก่อน"
            feedLoading = false
        }
    }

    Scaffold(
        containerColor = Color(0xFF0E0E12),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF16161C)) {
                NAV.forEach { (id, label, icon) ->
                    NavigationBarItem(
                        selected = tab == id,
                        onClick = {
                            when (id) {
                                "search" -> { tab = "search"; info = null }
                                "settings" -> { tab = "settings"; feed = emptyList() }
                                else -> loadFeed(id)
                            }
                        },
                        icon = { Icon(icon, label) },
                        label = { Text(label, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { pad ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(pad)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("YtKeep", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7DD3FC))
        Text("โหลดเก็บไว้ดูตอนไม่มีเน็ต · เล่นจอปิดได้", fontSize = 13.sp, color = Color(0xFF9CA3AF))
        Spacer(Modifier.height(10.dp))
        if (tab == "hot") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = cat.isBlank(),
                    onClick = {
                        cat = ""
                        feedLoading = true
                        feed = emptyList()
                        info = null
                        err = null
                        scope.launch {
                            val got = withContext(Dispatchers.IO) {
                                runCatching { feedFor(ctx, "") }.getOrElse {
                                    err = it.message ?: it.javaClass.simpleName
                                    emptyList()
                                }
                            }
                            feed = got
                            feedLoading = false
                        }
                    },
                    label = { Text("🔥 แนะนำ", fontSize = 12.sp) }
                )
                Feed.topics.forEach { topic ->
                    FilterChip(
                        selected = cat == topic.id,
                        onClick = {
                            cat = topic.id
                            if (tab != "hot") tab = "hot"
                            feedLoading = true
                            feed = emptyList()
                            info = null
                            err = null
                            scope.launch {
                                val got = withContext(Dispatchers.IO) {
                                    runCatching { feedFor(ctx, topic.id) }.getOrElse {
                                        err = it.message ?: it.javaClass.simpleName
                                        emptyList()
                                    }
                                }
                                feed = got
                                feedLoading = false
                            }
                        },
                        label = { Text(topic.label, fontSize = 12.sp) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(10.dp))

        if (tab != "settings") {
        if (tab == "search") {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("ค้นหา YouTube") },
            placeholder = { Text("พิมพ์คำค้นหรือชื่อช่อง · วางลิงก์ก็ได้") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (isLink) doFetch(url) else doSearch()
            }),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                clip.getText()?.text?.let { url = extractUrl(it) }
            }) {
                Icon(Icons.Default.ContentPaste, null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("วาง")
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (isLink) doFetch(url) else doSearch() },
                enabled = !loading && !searching
            ) {
                Text(
                    when {
                        searching -> "กำลังค้นหา…"
                        loading -> "กำลังดึง…"
                        isLink -> "ดึงข้อมูล"
                        else -> "ค้นหา"
                    }
                )
            }
        }

        if (hdLoading) {
            Text(
                "กำลังดึงรายการความละเอียดสูง…",
                fontSize = 12.sp,
                color = Color(0xFF9CA3AF),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (loading) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
        }

        err?.let {
            Text("ผิดพลาด: $it", color = Color(0xFFF87171), modifier = Modifier.padding(vertical = 8.dp))
        }

        if (channels.isNotEmpty()) {
            SectionLabel("ช่อง (${channels.size})")
            channels.forEach { c ->
                val followed = c.url in followedIds
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (followed) Store.unfollow(ctx, c.url)
                        else Store.follow(ctx, c.name, c.url, c.id)
                        tick++
                    }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = c.thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(c.subs.takeIf { it.isNotBlank() }, "ช่อง")
                                .joinToString(" · "),
                            fontSize = 11.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                    Icon(
                        Icons.Default.Star,
                        if (followed) "เลิกติดตาม" else "ติดตาม",
                        tint = if (followed) Color(0xFFFBBF24) else Color(0xFF3A3A46)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (hits.isNotEmpty()) {
            SectionLabel("วิดีโอ (${hits.size})")
            hits.forEach { h ->
                HitRow(
                    h = h,
                    saved = h.id in savedIds,
                    followed = h.channelId.isNotBlank() && h.channelId in followedIds,
                    onOpen = {
                        Store.recordInteraction(ctx, h, Store.InterestSignal.OPENED)
                        url = h.url
                        hits = emptyList()
                        doFetch(h.url)
                    },
                    onLongPress = { menuHit = h },
                    onSave = { Store.toggleSave(ctx, h); tick++ },
                    onFollow = if (h.channelId.isBlank()) null else ({
                        val cu = "https://www.youtube.com/channel/${h.channelId}"
                        if (h.channelId in followedIds) Store.unfollow(ctx, cu)
                        else Store.follow(ctx, h.channel, cu, h.channelId)
                        tick++
                    }),
                    onDownload = { menuHit = h }
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        }

        if (tab != "search" && tab != "settings") {
            if (feedLoading) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            }
            if (feed.isNotEmpty()) {
                val title = when (tab) {
                    "hot" -> Feed.topics.firstOrNull { it.id == cat }?.label ?: "แนะนำ"
                    "follow" -> "ติดตาม"
                    "saved" -> "บันทึก"
                    else -> tab
                }
                SectionLabel("$title (${feed.size})")
                feed.forEach { h ->
                    HitRow(
                        h = h,
                        saved = h.id in savedIds,
                        followed = h.channelId.isNotBlank() && h.channelId in followedIds,
                        onOpen = {
                            Store.recordInteraction(ctx, h, Store.InterestSignal.OPENED)
                            url = h.url
                            tab = "search"
                            feed = emptyList()
                            hits = emptyList()
                            doFetch(h.url)
                        },
                        onLongPress = { menuHit = h },
                        onSave = {
                            Store.toggleSave(ctx, h)
                            tick++
                        },
                        onFollow = if (h.channelId.isBlank()) null else ({
                            val cu = "https://www.youtube.com/channel/${h.channelId}"
                            if (h.channelId in followedIds) Store.unfollow(ctx, cu)
                            else Store.follow(ctx, h.channel, cu, h.channelId)
                            tick++
                        }),
                        onDownload = { menuHit = h }
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        info?.let { v ->
            Spacer(Modifier.height(8.dp))
            Card2 {
                v.thumb?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    v.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(v.uploader, fmtTime(v.seconds)).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF)
                )

                Spacer(Modifier.height(10.dp))
                v.video.firstOrNull()?.let { best ->
                    Button(
                        onClick = { playChoice(best.url.ifBlank { v.videoUrl }, best.selector, v.title, v.uploader) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("เล่นเลย (${best.label.substringBefore(" ·")})")
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            val c = when (defQ) {
                                "audio" -> v.audio.firstOrNull() ?: v.video.lastOrNull()
                                "best" -> v.video.firstOrNull() ?: v.audio.firstOrNull()
                                else -> v.video.firstOrNull { it.label.startsWith(defQ + "p") }
                                    ?: v.video.lastOrNull() ?: v.audio.firstOrNull()
                            }
                            if (c == null) err = "การ์ดนี้ไม่มี $defQ — เลือกจากรายการด้านล่าง"
                            else download(c, v)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ดาวน์โหลด (${if (defQ == "audio") "เสียง — ประหยัดสุด" else if (defQ == "best") "ดีที่สุด" else defQ + "p"})")
                    }
                }

                Spacer(Modifier.height(10.dp))
                SectionLabel("เสียง — เหมาะฟังจอปิด (ประหยัดเน็ตสุด)")
                v.audio.forEach { c -> ChoiceRow(c, v, playChoice, download) }

                Spacer(Modifier.height(6.dp))
                SectionLabel("วิดีโอ — เล่นหรือโหลด")
                v.video.forEach { c -> ChoiceRow(c, v, playChoice, download) }
            }
        }

        }

        if (tab == "settings") {
            SectionLabel("คำแนะนำ")
            Text(
                "ฟีดเรียนรู้จากคำค้นที่เปิดคลิป คลิปที่เปิดหรือบันทึก และช่องที่ติดตาม " +
                    "ประวัติเก็บในเครื่อง แต่คำค้นที่ใช้ดึงฟีดจะส่งไปค้นบน YouTube",
                fontSize = 12.sp,
                color = Color(0xFF9CA3AF)
            )
            TextButton(onClick = { confirmClearRecommendations = true }) {
                Text("ล้างประวัติแนะนำ")
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel("คุณภาพเริ่มต้นเมื่อกดดาวน์โหลด")
            listOf(
                "audio" to "เสียงเท่านั้น (.m4a) — ประหยัดเน็ตสุด สำหรับฟัง",
                "360" to "360p (ประหยัดเน็ตมาก)",
                "480" to "480p",
                "720" to "720p",
                "1080" to "1080p",
                "best" to "ดีที่สุดที่หาได้ (อัตโนมัติ)"
            ).forEach { (id, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        Store.setDefQuality(ctx, id)
                        defQ = id
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = defQ == id, onClick = {
                        Store.setDefQuality(ctx, id)
                        defQ = id
                    })
                    Text(label, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "ใช้กับปุ่มดาวน์โหลดในการ์ดวิดีโอ และการดาวน์โหลดเร็ว (กดค้างที่คลิป → เลือกคุณภาพ)",
                fontSize = 11.sp,
                color = Color(0xFF9CA3AF)
            )
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color(0xFF26262E))
        Spacer(Modifier.height(10.dp))
        Text("ในเครื่อง (${lib.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("เล่นได้แม้ไม่มีเน็ต · กดเล่นแล้วปิดจอได้", fontSize = 12.sp, color = Color(0xFF9CA3AF))

        if (lib.isEmpty()) {
            Text(
                "ยังไม่มีไฟล์ — กดไอคอนดาวน์โหลดด้านบนก่อน",
                fontSize = 13.sp,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        lib.forEach { item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (item.audio) Icons.Default.MusicNote else Icons.Default.PlayArrow,
                    null,
                    tint = Color(0xFF7DD3FC)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${if (item.audio) "เสียง" else "วิดีโอ"} · ${fmtBytes(item.bytes)}",
                        fontSize = 11.sp,
                        color = Color(0xFF9CA3AF)
                    )
                }
                IconButton(onClick = {
                    playFile(File(Lib.dir(ctx), item.file), item.title, item.uploader)
                }) { Icon(Icons.Default.PlayArrow, "เล่น") }
                IconButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { Lib.remove(ctx, item) }
                        tick++
                    }
                }) { Icon(Icons.Default.Delete, "ลบ", tint = Color(0xFF9CA3AF)) }
            }
        }

        menuHit?.let { h ->
            AlertDialog(
                onDismissRequest = { menuHit = null },
                title = { Text(h.title, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column {
                        Text(
                            "ดาวน์โหลดด้วยคุณภาพ",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        listOf(
                            "audio" to "เสียงเท่านั้น (.m4a) — ประหยัดเน็ตสุด สำหรับฟัง",
                            "360" to "360p — ประหยัดเน็ต",
                            "480" to "480p — คมชัดพอดี",
                            "720" to "720p — HD",
                            "1080" to "1080p — Full HD",
                            "best" to "ดีที่สุด (อัตโนมัติ)"
                        ).forEach { (q, label) ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    menuHit = null
                                    quickDownload(h, q)
                                }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, fontSize = 14.sp)
                                if (Store.defQuality(ctx) == q) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("• ค่าเริ่มต้น", fontSize = 11.sp, color = Color(0xFF7DD3FC))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { menuHit = null }) { Text("ปิด") }
                }
            )
        }

        if (confirmClearRecommendations) {
            AlertDialog(
                onDismissRequest = { confirmClearRecommendations = false },
                title = { Text("ล้างประวัติแนะนำ?") },
                text = {
                    Text("แอปจะลบคำค้นที่สนใจ คลิปที่เปิดล่าสุด และคะแนนช่อง แต่จะไม่เลิกติดตามช่อง")
                },
                confirmButton = {
                    TextButton(onClick = {
                        Store.clearRecommendationHistory(ctx)
                        confirmClearRecommendations = false
                        tick++
                        if (tab == "hot" && cat.isBlank()) loadFeed("hot")
                    }) { Text("ล้างประวัติ") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearRecommendations = false }) {
                        Text("ยกเลิก")
                    }
                }
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("yt-dlp $ytver", fontSize = 11.sp, color = Color(0xFF6B7280))
            Spacer(Modifier.weight(1f))
            TextButton(enabled = !updating, onClick = {
                scope.launch {
                    updating = true
                    runCatching { withContext(Dispatchers.IO) { Yt.updateNow(ctx) } }
                        .onSuccess { ytver = it }
                        .onFailure { err = "อัปเดตไม่สำเร็จ: ${it.message ?: it.javaClass.simpleName}" }
                    updating = false
                }
            }) {
                Text(if (updating) "กำลังอัปเดต…" else "อัปเดต yt-dlp", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
    }
}

private val NAV = listOf(
    Triple("search", "ค้นหา", Icons.Default.Search),
    Triple("hot", "แนะนำ", Icons.Default.Home),
    Triple("follow", "ติดตาม", Icons.Default.Person),
    Triple("saved", "บันทึก", Icons.Default.Favorite),
    Triple("settings", "ตั้งค่า", Icons.Default.Settings)
)

private suspend fun feedFor(ctx: android.content.Context, key: String): List<Hit> = when (key) {
    "saved" -> Store.saved(ctx)
    "follow" -> Store.follows(ctx)
        .flatMap { (_, u) -> runCatching { Yt.channelVideos(ctx, u, 6) }.getOrDefault(emptyList()) }
        .distinctBy { it.id }
        .take(30)
    "" -> Feed.recommendations(ctx)
    else -> Feed.topics.firstOrNull { it.id == key }?.let {
        Feed.search(it.query, Feed.LATEST, 24, it.id)
    } ?: emptyList()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HitRow(
    h: Hit,
    saved: Boolean,
    followed: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onSave: () -> Unit,
    onFollow: (() -> Unit)?,
    onDownload: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = h.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(h.title, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    h.channel.takeIf { it.isNotBlank() },
                    h.views.takeIf { it.isNotBlank() },
                    h.age.takeIf { it.isNotBlank() },
                    fmtTime(h.seconds).takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                fontSize = 11.sp,
                color = Color(0xFF9CA3AF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDownload) {
            Icon(
                Icons.Default.Download,
                "ดาวน์โหลด",
                tint = Color(0xFF7DD3FC)
            )
        }
        IconButton(onClick = onSave) {
            Icon(
                if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                "บันทึก",
                tint = if (saved) Color(0xFFF87171) else Color(0xFF9CA3AF)
            )
        }
        onFollow?.let {
            IconButton(onClick = it) {
                Icon(
                    Icons.Default.Star,
                    "ติดตาม",
                    tint = if (followed) Color(0xFFFBBF24) else Color(0xFF3A3A46)
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    c: Choice,
    v: Info,
    playChoice: (String, String, String, String) -> Unit,
    download: (Choice, Info) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            c.label,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = { playChoice(c.url.ifBlank { v.videoUrl }, c.selector, v.title, v.uploader) }) {
            Icon(Icons.Default.PlayArrow, "เล่น", tint = Color(0xFF7DD3FC))
        }
        IconButton(onClick = { download(c, v) }) {
            Icon(Icons.Default.Download, "โหลด")
        }
    }
}

@Composable
private fun SectionLabel(t: String) {
    Text(
        t,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFFA5B4FC),
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun Card2(content: @Composable () -> Unit) {
    Surface(
        color = Color(0xFF16161C),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) { content() }
    }
}
