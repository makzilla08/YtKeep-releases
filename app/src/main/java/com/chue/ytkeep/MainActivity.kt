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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Brush
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
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFB63D49),
                    onPrimary = Color.White,
                    secondary = Color(0xFFD86A73),
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xFF352025),
                    onSecondaryContainer = Color(0xFFFFDADD),
                    background = Color(0xFF0B0B0D),
                    surface = Color(0xFF151216)
                )
            ) {
                Surface(color = Color(0xFF0B0B0D), modifier = Modifier.fillMaxSize()) {
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
    var downloads by remember { mutableStateOf<List<DownloadTask>>(emptyList()) }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Feed.Channel>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("search") }
    var cat by remember { mutableStateOf("") }
    var channelId by remember { mutableStateOf<String?>(null) }
    var channelName by remember { mutableStateOf("") }
    var channelThumb by remember { mutableStateOf<String?>(null) }
    var channelSubs by remember { mutableStateOf("") }
    var channelOrigin by remember { mutableStateOf("search") }
    var channelSort by remember { mutableStateOf<String>("new") }
    var channelSortExpanded by remember { mutableStateOf(false) }
    var searchSort by remember { mutableStateOf<String>("new") }
    var channelBaseVideos by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var channelVideos by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var channelLoading by remember { mutableStateOf(false) }
    var channelErr by remember { mutableStateOf<String?>(null) }
    var menuHit by remember { mutableStateOf<Hit?>(null) }
    var defQ by remember { mutableStateOf(Store.defQuality(ctx)) }
    var feed by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var feedLoading by remember { mutableStateOf(false) }
    var ytver by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }
    var confirmClearRecommendations by remember { mutableStateOf(false) }
    var libraryFilter by remember { mutableStateOf("downloads") }
    var libraryQuery by remember { mutableStateOf("") }
    var librarySort by remember { mutableStateOf("newest") }
    var pendingDelete by remember { mutableStateOf<Lib.Item?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sharedUrl) { if (sharedUrl.isNotBlank()) url = sharedUrl }

    LaunchedEffect(tab) {
        while (true) {
            val local = withContext(Dispatchers.IO) {
                Lib.all(ctx) to DownloadQueue.all(ctx)
            }
            lib = local.first
            downloads = local.second
            if (tab != "library") break
            delay(1000)
        }
    }
    val savedIds = remember(tick) { Store.saved(ctx).map { it.id }.toSet() }
    val followedIds = remember(tick) { Store.follows(ctx).map { it.second }.toSet() }
    LaunchedEffect(Unit) {
        ytver = withContext(Dispatchers.IO) { runCatching { Yt.version(ctx) }.getOrDefault("?") }
    }

    LaunchedEffect(channelId) {
        if (channelId == null) return@LaunchedEffect
        channelLoading = true
        channelVideos = emptyList()
        channelErr = null
        scope.launch {
            val url = "https://www.youtube.com/channel/$channelId"
            val got = withContext(Dispatchers.IO) {
                runCatching { Feed.channel(ctx, url, 30) }.getOrElse {
                    channelErr = it.message ?: it.javaClass.simpleName
                    emptyList<Hit>()
                }
            }
            channelBaseVideos = got
            channelVideos = sortChannelVideos(got, channelSort)
            channelLoading = false
        }
    }

    LaunchedEffect(channelSort) {
        channelVideos = sortChannelVideos(channelBaseVideos, channelSort)
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
        containerColor = Color(0xFF0B0B0D),
        bottomBar = {
            Column {
                HorizontalDivider(color = Color(0xFF302126))
                NavigationBar(containerColor = Color(0xFF101013)) {
                    NAV.forEach { (id, label, icon) ->
                        NavigationBarItem(
                            selected = tab == id || (tab == "channel" && channelOrigin == id),
                            onClick = {
                                when (id) {
                                    "search" -> {
                                        tab = "search"
                                        channelId = null
                                        info = null
                                    }
                                    "library", "follow" -> {
                                        tab = id
                                        channelId = null
                                        feed = emptyList()
                                    }
                                    else -> {
                                        channelId = null
                                        loadFeed(id)
                                    }
                                }
                            },
                            icon = { Icon(icon, label) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFD86A73),
                                selectedTextColor = Color(0xFFD86A73),
                                unselectedIconColor = Color(0xFF94A3B8),
                                unselectedTextColor = Color(0xFF94A3B8),
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(pad)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (tab == "channel" || tab == "settings") {
                IconButton(onClick = {
                    if (tab == "settings") {
                        tab = "search"
                    } else {
                        tab = channelOrigin
                        channelId = null
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, "กลับ", tint = Color(0xFFF8FAFC))
                }
            }
            Text(
                if (tab == "settings") "ตั้งค่า" else "YTNx",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )
            Spacer(Modifier.weight(1f))
            if (tab != "settings" && tab != "channel") {
                IconButton(onClick = {
                    tab = "settings"
                    feed = emptyList()
                }) {
                    Icon(Icons.Default.Settings, "ตั้งค่า", tint = Color(0xFFD86A73))
                }
            }
        }

        if (tab == "search") {
            Text(
                "ค้นหาช่องและวิดีโอ",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC),
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                "พิมพ์ชื่อช่องหรือคำที่อยากดู",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )
        } else if (tab == "hot" || tab == "follow" || tab == "library") {
                Text(
                    when (tab) {
                        "hot" -> "แนะนำ"
                        "follow" -> "ติดตาม"
                        else -> "คลัง"
                    },
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.padding(top = 12.dp)
                )
        }
        Spacer(Modifier.height(if (tab == "search") 4.dp else 12.dp))
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
        SearchField(
            value = url,
            onValueChange = { url = it },
            onPaste = { clip.getText()?.text?.let { url = extractUrl(it) } },
            onSearch = { if (isLink) doFetch(url) else doSearch() },
            enabled = !loading && !searching,
            buttonLabel = when {
                searching -> "กำลังค้นหา…"
                loading -> "กำลังดึง…"
                isLink -> "ดึงข้อมูล"
                else -> "ค้นหา"
            }
        )

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
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("ช่อง")
                Spacer(Modifier.weight(1f))
                Text(
                    "${channels.size} รายการ",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            channels.forEach { c ->
                val followed = c.url in followedIds
                ChannelResultCard(
                    channel = c,
                    followed = followed,
                    onOpen = {
                        channelId = c.id
                        channelName = c.name
                        channelThumb = c.thumb
                        channelSubs = c.subs
                        channelOrigin = "search"
                        tab = "channel"
                    },
                    onToggleFollow = {
                        if (followed) Store.unfollow(ctx, c.url)
                        else Store.follow(ctx, c.name, c.url, c.id)
                        tick++
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        if (hits.isNotEmpty()) {
            val dispHits = when (searchSort) {
                "views" -> hits.sortedByDescending { parseViews(it.views) }
                else -> hits
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("วิดีโอ")
                Spacer(Modifier.weight(1f))
                Text(
                    "${dispHits.size} รายการ",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = searchSort == "new",
                    onClick = { searchSort = "new" },
                    label = { Text("ใหม่สุด", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = searchSort == "views",
                    onClick = { searchSort = "views" },
                    label = { Text("ยอดวิว", fontSize = 12.sp) }
                )
            }
            Spacer(Modifier.height(6.dp))
            dispHits.forEach { h ->
                VideoCard(
                    hit = h,
                    saved = h.id in savedIds,
                    downloadLabel = downloadQualityLabel(defQ),
                    onOpen = {
                        Store.recordInteraction(ctx, h, Store.InterestSignal.OPENED)
                        url = h.url
                        hits = emptyList()
                        doFetch(h.url)
                    },
                    onLongPress = { menuHit = h },
                    onSave = { Store.toggleSave(ctx, h); tick++ },
                    onDownload = { quickDownload(h, defQ) }
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        }

        if (tab == "hot" || tab == "follow") {
            if (tab == "follow") {
                val followed = Store.follows(ctx)
                if (followed.isEmpty()) {
                    Text(
                        "ยังไม่ได้ติดตามช่องใด ๆ — ค้นหาช่องแล้วกดดาว (ติดตาม) ที่แถวช่องในผลค้นหา แล้วเลือกช่องที่นี่เพื่อดูวิดีโอในช่องนั้น",
                        fontSize = 13.sp,
                        color = Color(0xFF9CA3AF),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    SectionLabel("ช่องที่ติดตาม (${followed.size})")
                    followed.forEach { (name, url) ->
                        val cid = url.substringAfterLast('/').takeWhile { it != '?' && it != '/' }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    channelId = cid
                                    channelName = name
                                    channels.firstOrNull { it.id == cid }?.let {
                                        channelThumb = it.thumb
                                        channelSubs = it.subs
                                    } ?: run {
                                        channelThumb = null
                                        channelSubs = ""
                                    }
                                    channelOrigin = "follow"
                                    tab = "channel"
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                null,
                                modifier = Modifier.size(44.dp).clip(CircleShape),
                                tint = Color(0xFFD86A73)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    url,
                                    fontSize = 11.sp,
                                    color = Color(0xFF9CA3AF),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                null,
                                tint = Color(0xFF9CA3AF)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            } else {
                if (feedLoading) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
                if (feed.isNotEmpty()) {
                    val title = Feed.topics.firstOrNull { it.id == cat }?.label ?: "แนะนำ"
                    SectionLabel("$title (${feed.size})")
                    feed.forEach { h ->
                        VideoCard(
                            hit = h,
                            saved = h.id in savedIds,
                            downloadLabel = "ดาวน์โหลด",
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
                            onDownload = { menuHit = h }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        if (tab == "channel") {
            ChannelBanner(channelName, channelThumb)
            val channelUrl = "https://www.youtube.com/channel/${channelId.orEmpty()}"
            val isFollowed = channelUrl in followedIds
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        channelName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        channelSubs.ifBlank { "ช่อง YouTube" },
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        if (channelLoading) "กำลังโหลดวิดีโอ…" else "${channelVideos.size.coerceAtMost(30)} วิดีโอ",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Button(
                    onClick = {
                        if (isFollowed) Store.unfollow(ctx, channelUrl)
                        else Store.follow(ctx, channelName, channelUrl, channelId.orEmpty())
                        tick++
                    },
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isFollowed) "ติดตามแล้ว" else "ติดตาม", fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = Color(0xFF302126))
            Row(
                Modifier.fillMaxWidth().height(46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        "วิดีโอ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFD86A73),
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                    Spacer(
                        Modifier.width(64.dp).height(3.dp)
                            .background(Color(0xFFB63D49), RoundedCornerShape(2.dp))
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.clickable { channelSortExpanded = true }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (channelSort == "views") "ยอดวิว" else "ใหม่สุด",
                                fontSize = 12.sp,
                                color = Color(0xFFF8FAFC)
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = channelSortExpanded,
                        onDismissRequest = { channelSortExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("ใหม่สุด") },
                            onClick = {
                                channelSort = "new"
                                channelSortExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("ยอดวิว") },
                            onClick = {
                                channelSort = "views"
                                channelSortExpanded = false
                            }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (channelLoading) "กำลังโหลด…" else "${channelVideos.size.coerceAtMost(30)} รายการ",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            channelErr?.let {
                Text("ผิดพลาด: $it", color = Color(0xFFF87171), modifier = Modifier.padding(vertical = 8.dp))
            }
            if (channelLoading) {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            }
            if (channelVideos.isNotEmpty()) {
                channelVideos.forEach { h ->
                    VideoCard(
                        hit = h,
                        saved = h.id in savedIds,
                        downloadLabel = downloadQualityLabel(defQ),
                        onOpen = {
                            Store.recordInteraction(ctx, h, Store.InterestSignal.OPENED)
                            url = h.url
                            tab = channelOrigin
                            channelId = null
                            hits = emptyList()
                            doFetch(h.url)
                        },
                        onLongPress = { menuHit = h },
                        onSave = { Store.toggleSave(ctx, h); tick++ },
                        onDownload = { quickDownload(h, defQ) }
                    )
                }
            } else if (!channelLoading && channelErr == null) {
                Text(
                    "ไม่มีวิดีโอในช่องนี้",
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (tab == "library") {
            SectionLabel("ดาวน์โหลดและคลัง")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "downloads" to "ดาวน์โหลด",
                    "files" to "ไฟล์ของฉัน",
                    "saved" to "บันทึกไว้ดู"
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = libraryFilter == id,
                        onClick = { libraryFilter = id },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            if (libraryFilter == "downloads") {
                val finishedCount = downloads.count {
                    it.state == DownloadState.COMPLETED || it.state == DownloadState.CANCELLED
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("คิวดาวน์โหลด (${downloads.size})")
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = finishedCount > 0,
                        onClick = { DownloadQueue.clearFinished(ctx) }
                    ) {
                        Text("ล้างรายการเสร็จแล้ว", fontSize = 11.sp)
                    }
                }
                if (downloads.isEmpty()) {
                    Text(
                        "ยังไม่มีรายการดาวน์โหลด",
                        fontSize = 13.sp,
                        color = Color(0xFF9CA3AF),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                downloads.forEach { task ->
                    Card2 {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    task.title,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${downloadStateLabel(task.state)} · ${task.quality}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                            }
                            when (task.state) {
                                DownloadState.QUEUED, DownloadState.RUNNING -> {
                                    TextButton(onClick = {
                                        DownloadService.cancel(ctx, task.id)
                                    }) { Text("ยกเลิก") }
                                }
                                DownloadState.CANCELLING -> Text(
                                    "กำลังยกเลิก",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                                DownloadState.FAILED, DownloadState.CANCELLED -> {
                                    TextButton(onClick = {
                                        DownloadService.retry(ctx, task.id)
                                    }) { Text("ลองใหม่") }
                                }
                                DownloadState.COMPLETED -> Unit
                            }
                        }
                        if (task.state == DownloadState.RUNNING) {
                            LinearProgressIndicator(
                                progress = task.progress / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "${task.progress}%",
                                fontSize = 11.sp,
                                color = Color(0xFF9CA3AF),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                        task.error?.let {
                            Text(it, fontSize = 12.sp, color = Color(0xFFD86A73))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            } else {
                OutlinedTextField(
                    value = libraryQuery,
                    onValueChange = { libraryQuery = it },
                    label = { Text(if (libraryFilter == "files") "ค้นหาไฟล์ในเครื่อง" else "ค้นหารายการบันทึก") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val sorts = if (libraryFilter == "files") {
                        listOf("newest" to "ใหม่สุด", "name" to "ชื่อ", "size" to "ขนาด")
                    } else {
                        listOf("newest" to "ใหม่สุด", "name" to "ชื่อ")
                    }
                    sorts.forEach { (id, label) ->
                        FilterChip(
                            selected = librarySort == id,
                            onClick = { librarySort = id },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                if (libraryFilter == "files") {
                    val filtered = Lib.filter(lib, libraryQuery, librarySort)
                    SectionLabel("ไฟล์ในเครื่อง (${filtered.size})")
                    if (filtered.isEmpty()) {
                        Text(
                            if (lib.isEmpty()) "ยังไม่มีไฟล์ดาวน์โหลด" else "ไม่พบไฟล์ที่ค้นหา",
                            fontSize = 13.sp,
                            color = Color(0xFF9CA3AF),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    filtered.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (item.audio) Icons.Default.MusicNote else Icons.Default.PlayArrow,
                                null,
                                tint = Color(0xFFD86A73)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${item.uploader} · ${fmtBytes(item.bytes)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                            }
                            IconButton(onClick = {
                                playFile(File(Lib.dir(ctx), item.file), item.title, item.uploader)
                            }) { Icon(Icons.Default.PlayArrow, "เล่น") }
                            IconButton(onClick = { pendingDelete = item }) {
                                Icon(Icons.Default.Delete, "ลบ", tint = Color(0xFF9CA3AF))
                            }
                        }
                    }
                } else {
                    val savedVideos = Store.saved(ctx)
                        .filter { hit ->
                            val term = libraryQuery.trim()
                            term.isBlank() ||
                                hit.title.contains(term, ignoreCase = true) ||
                                hit.channel.contains(term, ignoreCase = true)
                        }
                        .let { results ->
                            if (librarySort == "name") results.sortedBy { it.title.lowercase() }
                            else results.asReversed()
                        }
                    SectionLabel("รายการที่บันทึก (${savedVideos.size})")
                    if (savedVideos.isEmpty()) {
                        Text(
                            "ยังไม่มีรายการที่บันทึก",
                            fontSize = 13.sp,
                            color = Color(0xFF9CA3AF),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    savedVideos.forEach { hit ->
                        VideoCard(
                            hit = hit,
                            saved = true,
                            downloadLabel = "ดาวน์โหลด",
                            onOpen = {
                                Store.recordInteraction(ctx, hit, Store.InterestSignal.OPENED)
                                url = hit.url
                                tab = "search"
                                doFetch(hit.url)
                            },
                            onLongPress = { menuHit = hit },
                            onSave = { Store.toggleSave(ctx, hit); tick++ },
                            onDownload = { menuHit = hit }
                        )
                    }
                }
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
                                    Text("• ค่าเริ่มต้น", fontSize = 11.sp, color = Color(0xFFD86A73))
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

        pendingDelete?.let { item ->
            AlertDialog(
                onDismissRequest = {
                    pendingDelete = null
                    deleteError = null
                },
                title = { Text("ลบไฟล์นี้หรือไม่?") },
                text = {
                    Column {
                        Text(item.title)
                        deleteError?.let {
                            Text(
                                it,
                                color = Color(0xFFD86A73),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            deleteError = null
                            try {
                                withContext(Dispatchers.IO) { Lib.remove(ctx, item) }
                                lib = withContext(Dispatchers.IO) { Lib.all(ctx) }
                                pendingDelete = null
                            } catch (e: Exception) {
                                deleteError = e.message ?: "ลบไฟล์ไม่สำเร็จ"
                            }
                        }
                    }) { Text("ลบ", color = Color(0xFFD86A73)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingDelete = null
                        deleteError = null
                    }) { Text("ยกเลิก") }
                }
            )
        }

        Spacer(Modifier.height(24.dp))
    }
    }
}

private val NAV = listOf(
    Triple("search", "ค้นหา", Icons.Default.Search),
    Triple("hot", "แนะนำ", Icons.Default.Home),
    Triple("follow", "ติดตาม", Icons.Default.Person),
    Triple("library", "คลัง", Icons.Default.VideoLibrary)
)

private suspend fun feedFor(ctx: android.content.Context, key: String): List<Hit> = when (key) {
    "" -> Feed.recommendations(ctx)
    else -> Feed.topics.firstOrNull { it.id == key }?.let {
        Feed.search(it.query, Feed.LATEST, 24, it.id)
    } ?: emptyList()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    onSearch: () -> Unit,
    enabled: Boolean,
    buttonLabel: String
) {
    Surface(
        color = Color(0xFF171416),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF302126)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.heightIn(min = 54.dp).padding(start = 12.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = Color(0xFF94A3B8))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFE2E8F0)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth()
                ) { innerTextField ->
                    if (value.isBlank()) {
                        Text(
                            "พิมพ์ชื่อช่องหรือคำที่อยากดู",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
            IconButton(onClick = onPaste, enabled = enabled, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ContentPaste, "วางลิงก์", tint = Color(0xFF94A3B8))
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onSearch,
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(buttonLabel, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChannelResultCard(
    channel: Feed.Channel,
    followed: Boolean,
    onOpen: () -> Unit,
    onToggleFollow: () -> Unit
) {
    Surface(
        color = Color(0xFF151216),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onOpen)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF263449)),
                contentAlignment = Alignment.Center
            ) {
                Text(channel.name.take(1), fontWeight = FontWeight.Bold, color = Color.White)
                channel.thumb?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    channel.subs.ifBlank { "ช่อง" },
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleFollow, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Star,
                    if (followed) "เลิกติดตาม" else "ติดตาม",
                    tint = if (followed) Color(0xFFD86A73) else Color(0xFF94A3B8)
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                null,
                tint = Color(0xFFD86A73)
            )
        }
    }
}

@Composable
private fun ChannelBanner(name: String, thumb: String?) {
    Box(
        Modifier.fillMaxWidth()
            .height(126.dp)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF201316), Color(0xFF431B22), Color(0xFF642630))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(92.dp)
        ) {
            Box(
                Modifier.fillMaxSize().padding(4.dp).clip(CircleShape)
                    .background(Color(0xFF0B5CAD)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.take(1).uppercase(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (thumb != null) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = "$name channel",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoCard(
    hit: Hit,
    saved: Boolean,
    downloadLabel: String,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onSave: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        color = Color(0xFF151216),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.width(106.dp).height(76.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF201316))
            ) {
                AsyncImage(
                    model = hit.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                val duration = fmtTime(hit.seconds)
                if (duration.isNotBlank()) {
                    Surface(
                        color = Color(0xD9000000),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    ) {
                        Text(
                            duration,
                            fontSize = 9.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier.weight(1f).heightIn(min = 76.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        hit.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF8FAFC),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            hit.views.takeIf { it.isNotBlank() },
                            hit.age.takeIf { it.isNotBlank() }
                        ).joinToString(" · ").ifBlank { hit.channel },
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.height(30.dp).clickable(onClick = onOpen)
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            null,
                            tint = Color(0xFFD86A73),
                            modifier = Modifier.size(16.dp)
                        )
                        Text("เล่น", fontSize = 10.sp, color = Color(0xFFD86A73))
                    }
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        color = Color(0xFF812E38),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(30.dp).clickable(onClick = onDownload)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Download,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                downloadLabel,
                                fontSize = 9.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onSave, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "บันทึก",
                    tint = if (saved) Color(0xFFD86A73) else Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
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
            Icon(Icons.Default.PlayArrow, "เล่น", tint = Color(0xFFD86A73))
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
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFFF8FAFC),
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

private fun parseViews(s: String): Long {
    val t = s.lowercase().replace(Regex("[^0-9.km ]+"), "").trim()
    if (t.isEmpty()) return 0
    val suffix = if (t.endsWith("m")) "m" else if (t.endsWith("k")) "k" else ""
    val num = t.removeSuffix(suffix).toDoubleOrNull() ?: 0.0
    return when (suffix) {
        "m" -> (num * 1_000_000).toLong()
        "k" -> (num * 1_000).toLong()
        else -> num.toLong()
    }

}

private fun sortChannelVideos(videos: List<Hit>, sort: String): List<Hit> = when (sort) {
    "views" -> videos.sortedByDescending { parseViews(it.views) }
    else -> videos
}

private fun downloadQualityLabel(quality: String): String = when (quality) {
    "audio" -> "เสียง"
    "best" -> "ดีที่สุด"
    else -> "${quality}p"
}

private fun downloadStateLabel(state: DownloadState): String = when (state) {
    DownloadState.QUEUED -> "รอคิว"
    DownloadState.RUNNING -> "กำลังดาวน์โหลด"
    DownloadState.CANCELLING -> "กำลังยกเลิก"
    DownloadState.COMPLETED -> "เสร็จแล้ว"
    DownloadState.FAILED -> "ไม่สำเร็จ"
    DownloadState.CANCELLED -> "ยกเลิกแล้ว"
}
