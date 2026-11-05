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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
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
    private var controller: MediaController? by mutableStateOf(null)
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
                val connectedController = future.get()
                controller = connectedController
                pending?.invoke(connectedController)
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
                        mediaController = controller,
                        playChoice = { url, selector, title, author, thumb ->
                            playChoice(url, selector, title, author, thumb)
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

    private fun playChoice(
        url: String,
        selector: String,
        title: String,
        author: String,
        thumb: String?
    ) {
        val isPage = url.contains("youtube.com/watch") || url.contains("youtu.be/")
        val videoId = if (isPage) {
            url.substringAfter("v=", "").substringBefore("&").ifBlank {
                url.substringAfterLast("/").substringBefore("?")
            }
        } else ""
        if (!isPage) {
            play(url, title, author, thumb, videoId)
            return
        }
        lifecycleScope.launch {
            val direct = withContext(Dispatchers.IO) {
                runCatching {
                    Yt.direct(this@MainActivity, url, if (selector.contains("+")) "b" else selector)
                }.getOrNull()
            }
            if (direct != null) play(direct, title, author, thumb, videoId)
            else Toast.makeText(this@MainActivity, "ดึงลิงก์เล่นไม่สำเร็จ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun play(url: String, title: String, author: String, thumb: String? = null, videoId: String = "") {
        val run = { c: MediaController ->
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(author)
            if (!thumb.isNullOrBlank()) metadata.setArtworkUri(Uri.parse(thumb))
            c.setMediaItem(
                MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(metadata.build())
                    .build()
            )
            c.prepare()
            c.play()
        }
        controller?.let(run) ?: run { pending = run }
        startActivity(
            Intent(this, PlayerActivity::class.java).apply {
                putExtra("title", title)
                putExtra("author", author)
                putExtra("videoId", videoId)
            }
        )
    }

    override fun onDestroy() {
        if (::future.isInitialized) MediaController.releaseFuture(future)
        super.onDestroy()
    }
}

@Composable
fun App(
    sharedUrl: String,
    mediaController: MediaController?,
    playChoice: (String, String, String, String, String?) -> Unit,
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
    var channelLoadingMore by remember { mutableStateOf(false) }
    var channelHasMore by remember { mutableStateOf(true) }
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
    var disabledTopicIds by remember { mutableStateOf(Store.disabledTopicIds(ctx)) }
    var customTopics by remember { mutableStateOf(Store.customTopics(ctx)) }
    var newTopicText by remember { mutableStateOf("") }
    var channelCategory by remember { mutableStateOf("videos") }
    var channelLoadingAll by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Store.Playlist?>(null) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var playlistToAddHit by remember { mutableStateOf<Hit?>(null) }

    fun channelCategoryUrl(cid: String, category: String): String {
        val base = if (cid.startsWith("UC") || cid.startsWith("HC")) {
            "https://www.youtube.com/channel/$cid"
        } else {
            "https://www.youtube.com/@$cid"
        }
        return when (category) {
            "shorts" -> "$base/shorts"
            "streams" -> "$base/streams"
            "playlists" -> "$base/playlists"
            else -> "$base/videos"
        }
    }

    val openChannel: (channelId: String, channelName: String, channelThumb: String?, origin: String) -> Unit =
        { cid, cname, cthumb, origin ->
            channelId = cid
            channelName = cname
            channelThumb = cthumb
            channelSubs = ""
            channelOrigin = origin
            channelCategory = "videos"
            tab = "channel"
        }

    val playHitsContinuous: (List<Hit>, String) -> Unit = { list, queueTitle ->
        if (list.isNotEmpty()) {
            QueueManager.playQueue(ctx, mediaController, list, 0, queueTitle) {
                val first = list.first()
                ctx.startActivity(
                    Intent(ctx, PlayerActivity::class.java).apply {
                        putExtra("title", first.title)
                        putExtra("author", first.channel)
                        putExtra("videoId", first.id)
                    }
                )
            }
        }
    }

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

    LaunchedEffect(channelId, channelCategory) {
        if (channelId == null) return@LaunchedEffect
        channelLoading = true
        channelLoadingMore = false
        channelLoadingAll = false
        channelHasMore = true
        channelVideos = emptyList()
        channelBaseVideos = emptyList()
        channelErr = null
        scope.launch {
            val targetUrl = channelCategoryUrl(channelId!!, channelCategory)
            val got = withContext(Dispatchers.IO) {
                runCatching { Feed.channel(ctx, targetUrl, 1, 30) }.getOrElse {
                    channelErr = it.message ?: it.javaClass.simpleName
                    emptyList<Hit>()
                }
            }
            channelBaseVideos = got
            channelVideos = sortChannelVideos(got, channelSort)
            channelHasMore = got.size >= 30
            channelLoading = false
        }
    }

    val loadMoreChannelVideos: () -> Unit = {
        if (!channelLoading && !channelLoadingMore && channelHasMore && channelId != null) {
            channelLoadingMore = true
            scope.launch {
                val nextStart = channelBaseVideos.size + 1
                val targetUrl = channelCategoryUrl(channelId!!, channelCategory)
                val more = withContext(Dispatchers.IO) {
                    runCatching { Feed.channel(ctx, targetUrl, nextStart, 30) }.getOrElse { emptyList() }
                }
                if (more.isNotEmpty()) {
                    val merged = (channelBaseVideos + more).distinctBy { it.id }
                    channelBaseVideos = merged
                    channelVideos = sortChannelVideos(merged, channelSort)
                    channelHasMore = more.size >= 30
                } else {
                    channelHasMore = false
                }
                channelLoadingMore = false
            }
        }
    }

    val loadAllChannelVideos: () -> Unit = {
        if (!channelLoading && !channelLoadingMore && !channelLoadingAll && channelHasMore && channelId != null) {
            channelLoadingAll = true
            scope.launch {
                val targetUrl = channelCategoryUrl(channelId!!, channelCategory)
                var currentList = channelBaseVideos
                while (channelLoadingAll) {
                    val nextStart = currentList.size + 1
                    val more = withContext(Dispatchers.IO) {
                        runCatching { Feed.channel(ctx, targetUrl, nextStart, 30) }.getOrElse { emptyList() }
                    }
                    if (more.isEmpty()) {
                        channelHasMore = false
                        break
                    }
                    val merged = (currentList + more).distinctBy { it.id }
                    if (merged.size == currentList.size) {
                        channelHasMore = false
                        break
                    }
                    currentList = merged
                    channelBaseVideos = currentList
                    channelVideos = sortChannelVideos(currentList, channelSort)
                    if (more.size < 30) {
                        channelHasMore = false
                        break
                    }
                }
                channelLoadingAll = false
            }
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
                if (!Yt.FLASH) {
                    hdLoading = true
                    runCatching { withContext(Dispatchers.IO) { Yt.fetchHd(ctx, videoUrl) } }
                        .onSuccess { hd -> if (hd.video.size > (info?.video?.size ?: 0)) info = hd }
                    hdLoading = false
                }
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
                        Feed.search(q, Feed.LATEST, 30) to Feed.searchChannels(q, 6)
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

    val setTopicEnabled: (Feed.Topic, Boolean) -> Unit = { topic, enabled ->
        Store.setTopicEnabled(ctx, topic.id, enabled)
        disabledTopicIds = Store.disabledTopicIds(ctx)
        if (!enabled && cat == topic.id) cat = ""
        if (tab == "hot") loadFeed("hot")
    }

    Scaffold(
        containerColor = Color(0xFF0B0B0D),
        bottomBar = {
            Column {
                MiniPlayerBar(
                    controller = mediaController,
                    onExpand = { ctx.startActivity(Intent(ctx, PlayerActivity::class.java)) }
                )
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
                Feed.enabledTopics(ctx, disabledTopicIds).forEach { topic ->
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
                SectionLabel("วิดีโอ (${dispHits.size})")
                Spacer(Modifier.weight(1f))
                if (dispHits.isNotEmpty()) {
                    Button(
                        onClick = { playHitsContinuous(dispHits, "ผลค้นหา: $url") },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE24A56)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("เล่นต่อเนื่อง", fontSize = 11.sp)
                    }
                }
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
                    onDownload = { quickDownload(h, defQ) },
                    onOpenChannel = { openChannel(h.channelId.ifBlank { h.channel }, h.channel, null, tab) }
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
                    val title = Feed.allTopics(ctx).firstOrNull { it.id == cat }?.label ?: "แนะนำ"
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel("$title (${feed.size})")
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { playHitsContinuous(feed, "แนะนำ: $title") },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE24A56)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("เล่นต่อเนื่อง", fontSize = 11.sp)
                        }
                    }
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
                            onDownload = { menuHit = h },
                            onOpenChannel = { openChannel(h.channelId.ifBlank { h.channel }, h.channel, null, tab) }
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
                        if (channelLoading) "กำลังโหลดวิดีโอ…" else "${channelVideos.size} วิดีโอ",
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
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "videos" to "🎬 วิดีโอ",
                    "shorts" to "⚡ Shorts",
                    "streams" to "🔴 ไลฟ์",
                    "playlists" to "📋 เพลย์ลิสต์"
                ).forEach { (catId, label) ->
                    FilterChip(
                        selected = channelCategory == catId,
                        onClick = { channelCategory = catId },
                        label = { Text(label, fontSize = 12.sp) }
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
                if (channelVideos.isNotEmpty()) {
                    Button(
                        onClick = {
                            val catTitle = when (channelCategory) {
                                "shorts" -> "Shorts"
                                "streams" -> "ไลฟ์"
                                "playlists" -> "เพลย์ลิสต์"
                                else -> "วิดีโอ"
                            }
                            playHitsContinuous(channelVideos, "$channelName - $catTitle")
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE24A56)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("เล่นทั้งหมด", fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (channelLoading) "กำลังโหลด…" else "${channelVideos.size} รายการ",
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
                if (channelHasMore) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = !channelLoadingMore && !channelLoadingAll, onClick = loadMoreChannelVideos)
                        ) {
                            Row(
                                Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (channelLoadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFFD86A73)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("กำลังโหลด…", fontSize = 12.sp, color = Color(0xFFF8FAFC))
                                } else {
                                    Icon(Icons.Default.KeyboardArrowDown, null, tint = Color(0xFFD86A73), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("+30 คลิป", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF8FAFC))
                                }
                            }
                        }

                        Surface(
                            color = Color(0xFF812E38),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = {
                                    if (channelLoadingAll) channelLoadingAll = false
                                    else loadAllChannelVideos()
                                })
                        ) {
                            Row(
                                Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (channelLoadingAll) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("หยุด (${channelVideos.size})", fontSize = 12.sp, color = Color.White)
                                } else {
                                    Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("โหลดจนหมด", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                }
                            }
                        }
                    }
                } else if (channelVideos.size >= 30) {
                    Text(
                        "แสดงครบทั้งหมดแล้ว (${channelVideos.size} รายการ)",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
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
                    "saved" to "บันทึกไว้ดู",
                    "playlists" to "เพลย์ลิสต์"
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = libraryFilter == id,
                        onClick = {
                            libraryFilter = id
                            selectedPlaylist = null
                        },
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
            } else if (libraryFilter == "playlists") {
                val playlists = Store.playlists(ctx)
                val currentPl = selectedPlaylist?.let { sp ->
                    playlists.firstOrNull { it.id == sp.id } ?: sp
                }
                if (currentPl != null) {
                    // แสดงรายละเอียดเพลย์ลิสต์ที่เลือก
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedPlaylist = null }) {
                            Icon(Icons.Default.ArrowBack, "กลับ", tint = Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                currentPl.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${currentPl.items.size} คลิป",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        IconButton(onClick = {
                            Store.deletePlaylist(ctx, currentPl.id)
                            selectedPlaylist = null
                            tick++
                        }) {
                            Icon(Icons.Default.Delete, "ลบเพลย์ลิสต์", tint = Color(0xFF9CA3AF))
                        }
                    }
                    if (currentPl.items.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { playHitsContinuous(currentPl.items, currentPl.name) },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE24A56)),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("เล่นทั้งหมด", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { playHitsContinuous(currentPl.items.shuffled(), "${currentPl.name} (สุ่ม)") },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF231B22)),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("เล่นสุ่ม", fontSize = 12.sp)
                            }
                        }
                        currentPl.items.forEach { hit ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.weight(1f)) {
                                    VideoCard(
                                        hit = hit,
                                        saved = hit.id in savedIds,
                                        downloadLabel = "ดาวน์โหลด",
                                        onOpen = {
                                            Store.recordInteraction(ctx, hit, Store.InterestSignal.OPENED)
                                            url = hit.url
                                            tab = "search"
                                            doFetch(hit.url)
                                        },
                                        onLongPress = { menuHit = hit },
                                        onSave = { Store.toggleSave(ctx, hit); tick++ },
                                        onDownload = { menuHit = hit },
                                        onOpenChannel = { openChannel(hit.channelId.ifBlank { hit.channel }, hit.channel, null, tab) }
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        Store.removeFromPlaylist(ctx, currentPl.id, hit.id)
                                        tick++
                                    },
                                    modifier = Modifier.padding(start = 2.dp)
                                ) {
                                    Icon(Icons.Default.Delete, "ลบจากเพลย์ลิสต์", tint = Color(0xFF71717A), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    } else {
                        Text(
                            "ยังไม่มีคลิปในเพลย์ลิสต์นี้ — แตะเมนูตัวเลือกที่คลิปใดๆ แล้วเลือก 'เพิ่มลงในเพลย์ลิสต์'",
                            fontSize = 13.sp,
                            color = Color(0xFF9CA3AF),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    // หน้ารวมเพลย์ลิสต์
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel("เพลย์ลิสต์ของฉัน (${playlists.size})")
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                newPlaylistName = ""
                                showNewPlaylistDialog = true
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE24A56)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("สร้างใหม่", fontSize = 11.sp)
                        }
                    }
                    if (playlists.isEmpty()) {
                        Text(
                            "ยังไม่มีเพลย์ลิสต์ — แตะปุ่ม 'สร้างใหม่' หรือบันทึกจากคิวที่กำลังเล่น",
                            fontSize = 13.sp,
                            color = Color(0xFF9CA3AF),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    playlists.forEach { pl ->
                        Surface(
                            color = Color(0xFF151216),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedPlaylist = pl }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(54.dp).clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF281318)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val firstThumb = pl.items.firstOrNull()?.thumb
                                    if (!firstThumb.isNullOrBlank()) {
                                        AsyncImage(
                                            model = firstThumb,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.QueueMusic,
                                            null,
                                            tint = Color(0xFFE24A56),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        pl.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${pl.items.size} คลิป",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                if (pl.items.isNotEmpty()) {
                                    IconButton(onClick = { playHitsContinuous(pl.items, pl.name) }) {
                                        Icon(Icons.Default.PlayArrow, "เล่น", tint = Color(0xFFE24A56))
                                    }
                                }
                                IconButton(onClick = {
                                    Store.deletePlaylist(ctx, pl.id)
                                    tick++
                                }) {
                                    Icon(Icons.Default.Delete, "ลบ", tint = Color(0xFF71717A), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
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
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel("รายการที่บันทึก (${savedVideos.size})")
                        Spacer(Modifier.weight(1f))
                        if (savedVideos.isNotEmpty()) {
                            Button(
                                onClick = { playHitsContinuous(savedVideos, "รายการที่บันทึก") },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE24A56)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(32.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("เล่นทั้งหมด", fontSize = 11.sp)
                            }
                        }
                    }
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
                            onDownload = { menuHit = hit },
                            onOpenChannel = { openChannel(hit.channelId.ifBlank { hit.channel }, hit.channel, null, tab) }
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
                        onClick = {
                            playChoice(
                                best.url.ifBlank { v.videoUrl },
                                best.selector,
                                v.title,
                                v.uploader,
                                v.thumb
                            )
                        },
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
            SectionLabel("หัวข้อความสนใจ")
            Text(
                "เลือกหัวข้อที่ต้องการเห็นเป็นแท็บและใช้ในฟีดแนะนำ ปิดหัวข้อที่ไม่สนใจได้ และเปิดกลับได้ทุกเมื่อ",
                fontSize = 12.sp,
                color = Color(0xFF9CA3AF)
            )
            (Feed.topics + customTopics).forEach { topic ->
                val enabled = topic.id !in disabledTopicIds
                val isCustom = topic.id.startsWith("custom:")
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = enabled,
                        onCheckedChange = { setTopicEnabled(topic, it) }
                    )
                    Text(
                        topic.label,
                        modifier = Modifier.weight(1f).clickable {
                            setTopicEnabled(topic, !enabled)
                        },
                        fontSize = 14.sp
                    )
                    if (isCustom) {
                        IconButton(onClick = {
                            Store.removeCustomTopic(ctx, topic.id)
                            customTopics = Store.customTopics(ctx)
                            disabledTopicIds = Store.disabledTopicIds(ctx)
                            if (cat == topic.id) cat = ""
                        }) {
                            Icon(Icons.Default.Delete, "ลบ", tint = Color(0xFF94A3B8))
                        }
                    } else {
                        Text(
                            if (enabled) "แสดง" else "ซ่อน",
                            fontSize = 11.sp,
                            color = if (enabled) Color(0xFFD86A73) else Color(0xFF9CA3AF)
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTopicText,
                    onValueChange = { newTopicText = it },
                    label = { Text("เพิ่มหัวข้อใหม่") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val added = Store.addCustomTopic(ctx, newTopicText)
                    if (added != null) {
                        customTopics = Store.customTopics(ctx)
                        newTopicText = ""
                    }
                }, enabled = newTopicText.isNotBlank()) {
                    Text("เพิ่ม")
                }
            }
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
                        // หมวดหมู่การเล่นและคิว
                        Text(
                            "คิวและการเล่น",
                            fontSize = 12.sp,
                            color = Color(0xFFE24A56),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                QueueManager.playNext(h)
                                Toast.makeText(ctx, "แทรกเล่นต่อจากนี้แล้ว", Toast.LENGTH_SHORT).show()
                                menuHit = null
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SkipNext, null, tint = Color(0xFFD86A73), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("เล่นต่อจากนี้ (Play Next)", fontSize = 13.sp)
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                QueueManager.addToQueue(h)
                                Toast.makeText(ctx, "เพิ่มลงในคิวแล้ว", Toast.LENGTH_SHORT).show()
                                menuHit = null
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.QueueMusic, null, tint = Color(0xFFD86A73), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("เพิ่มลงในคิว (Add to Queue)", fontSize = 13.sp)
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                playlistToAddHit = h
                                menuHit = null
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlaylistAdd, null, tint = Color(0xFFD86A73), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("เพิ่มลงในเพลย์ลิสต์…", fontSize = 13.sp)
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                menuHit = null
                                openChannel(h.channelId.ifBlank { h.channel }, h.channel, null, tab)
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccountCircle, null, tint = Color(0xFFD86A73), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ไปยังหน้าช่อง (${h.channel})", fontSize = 13.sp)
                        }

                        HorizontalDivider(color = Color(0xFF302126), modifier = Modifier.padding(vertical = 6.dp))

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
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, fontSize = 13.sp)
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

        // Dialog เลือกเพลย์ลิสต์ที่จะเพิ่มคลิป
        playlistToAddHit?.let { hit ->
            val allPlaylists = Store.playlists(ctx)
            AlertDialog(
                onDismissRequest = { playlistToAddHit = null },
                title = { Text("เพิ่มลงในเพลย์ลิสต์", fontSize = 16.sp) },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        Text(hit.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF9CA3AF))
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                newPlaylistName = ""
                                showNewPlaylistDialog = true
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, null, tint = Color(0xFFE24A56), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("+ สร้างเพลย์ลิสต์ใหม่", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE24A56))
                        }
                        HorizontalDivider(color = Color(0xFF302126))
                        if (allPlaylists.isEmpty()) {
                            Text(
                                "ยังไม่มีเพลย์ลิสต์ แตะสร้างเพลย์ลิสต์ใหม่ด้านบน",
                                fontSize = 12.sp,
                                color = Color(0xFF71717A),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            allPlaylists.forEach { pl ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        Store.addToPlaylist(ctx, pl.id, hit)
                                        Toast.makeText(ctx, "เพิ่มลงใน \"${pl.name}\" แล้ว", Toast.LENGTH_SHORT).show()
                                        playlistToAddHit = null
                                        tick++
                                    }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.QueueMusic, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(pl.name, fontSize = 14.sp, color = Color.White)
                                        Text("${pl.items.size} คลิป", fontSize = 10.sp, color = Color(0xFF71717A))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { playlistToAddHit = null }) { Text("ยกเลิก") }
                }
            )
        }

        // Dialog สร้างเพลย์ลิสต์ใหม่
        if (showNewPlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showNewPlaylistDialog = false },
                title = { Text("สร้างเพลย์ลิสต์ใหม่") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text("ชื่อเพลย์ลิสต์") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val initial = playlistToAddHit?.let { listOf(it) } ?: emptyList()
                            val pl = Store.createPlaylist(ctx, newPlaylistName, initial)
                            Toast.makeText(ctx, "สร้างเพลย์ลิสต์ \"${pl.name}\" แล้ว", Toast.LENGTH_SHORT).show()
                            showNewPlaylistDialog = false
                            playlistToAddHit = null
                            tick++
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE24A56))
                    ) {
                        Text("สร้าง")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewPlaylistDialog = false }) { Text("ยกเลิก") }
                }
            )
        }

        if (confirmClearRecommendations) {
            AlertDialog(
                onDismissRequest = { confirmClearRecommendations = false },
                title = { Text("ล้างประวัติแนะนำ?") },
                text = {
                    Text(
                        "แอปจะลบคำค้นที่สนใจ คลิปที่เปิดล่าสุด และคะแนนช่อง " +
                            "แต่จะไม่เลิกติดตามช่องหรือเปลี่ยนหัวข้อความสนใจที่เลือกแสดง"
                    )
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
    else -> Feed.allTopics(ctx).firstOrNull { it.id == key }?.let {
        Feed.search(it.query, Feed.LATEST, 30, it.id)
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
private fun MiniPlayerBar(
    controller: MediaController?,
    onExpand: () -> Unit
) {
    var hasItem by remember(controller) {
        mutableStateOf(controller?.currentMediaItem != null)
    }
    var isPlaying by remember(controller) {
        mutableStateOf(controller?.isPlaying == true)
    }
    var title by remember(controller) {
        mutableStateOf(controller?.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty())
    }
    var artist by remember(controller) {
        mutableStateOf(controller?.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty())
    }
    var artworkUri by remember(controller) {
        mutableStateOf(controller?.currentMediaItem?.mediaMetadata?.artworkUri?.toString())
    }
    var position by remember(controller) { mutableStateOf(0L) }
    var duration by remember(controller) { mutableStateOf(0L) }

    DisposableEffect(controller) {
        if (controller == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    hasItem = player.currentMediaItem != null
                    isPlaying = player.isPlaying
                    title = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
                    artist = player.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty()
                    artworkUri = player.currentMediaItem?.mediaMetadata?.artworkUri?.toString()
                    position = player.currentPosition.coerceAtLeast(0L)
                    duration = player.duration.takeIf { it > 0L } ?: 0L
                }
            }
            controller.addListener(listener)
            onDispose { controller.removeListener(listener) }
        }
    }

    LaunchedEffect(controller, hasItem, isPlaying) {
        while (hasItem && isPlaying) {
            position = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            duration = controller?.duration?.takeIf { it > 0L } ?: 0L
            delay(500)
        }
    }

    if (!hasItem) return

    val progress = if (duration > 0L) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    Surface(
        color = Color(0xFF1C1A25),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2F2A3D)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)
            .clickable(onClick = onExpand)
    ) {
        Box {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                ) {
                    if (!artworkUri.isNullOrBlank()) {
                        AsyncImage(
                            model = artworkUri,
                            contentDescription = "ภาพปก ${title.ifBlank { "สื่อที่กำลังเล่น" }}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFFD86A73),
                            modifier = Modifier.align(Alignment.Center).size(24.dp)
                        )
                    }
                    if (isPlaying) {
                        Box(
                            Modifier.fillMaxSize().background(Color(0x66000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.size(8.dp).background(Color(0xFFE24A56), CircleShape)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable(onClick = onExpand)) {
                    Text(
                        title.ifBlank { "กำลังเล่น" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF8FAFC)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            artist.ifBlank { "YTNx" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp,
                            color = Color(0xFFB7AEB3),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(" · ", fontSize = 10.sp, color = Color(0xFFB7AEB3))
                        Text(
                            if (isPlaying) "กำลังเล่นจอปิด" else "หยุดชั่วคราว",
                            maxLines = 1,
                            fontSize = 10.sp,
                            color = if (isPlaying) Color(0xFF34D399) else Color(0xFFB7AEB3)
                        )
                        if (QueueManager.state.items.isNotEmpty()) {
                            Text(
                                " (${QueueManager.state.currentIndex + 1}/${QueueManager.state.items.size})",
                                fontSize = 10.sp,
                                color = Color(0xFFE24A56),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                if (QueueManager.state.hasNext) {
                    val localCtx = LocalContext.current
                    IconButton(onClick = { QueueManager.skipToNext(localCtx, controller) }) {
                        Icon(Icons.Default.SkipNext, "ถัดไป", tint = Color.White)
                    }
                }
                IconButton(
                    onClick = {
                        if (controller?.isPlaying == true) controller?.pause()
                        else controller?.play()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFE24A56),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "หยุดชั่วคราว" else "เล่นต่อ",
                        tint = Color(0xFFF8FAFC)
                    )
                }
                IconButton(onClick = {
                    controller?.stop()
                    controller?.clearMediaItems()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "ปิดการเล่น", tint = Color(0xFFB7AEB3))
                }
            }
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
                color = Color(0xFFE24A56),
                trackColor = Color.Transparent
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
    onDownload: () -> Unit,
    onOpenChannel: (() -> Unit)? = null
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
                    // แสดงชื่อช่องเด่นชัด พร้อมไอคอน และคลิกเข้าหน้าช่องได้
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .then(
                                if (onOpenChannel != null) Modifier.clickable(onClick = onOpenChannel)
                                else Modifier
                            )
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "ช่อง",
                            tint = Color(0xFFE24A56),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            hit.channel.ifBlank { "ไม่ระบุช่อง" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (onOpenChannel != null) Color(0xFFF1F5F9) else Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val stats = listOfNotNull(
                        hit.views.takeIf { it.isNotBlank() },
                        hit.age.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (stats.isNotBlank()) {
                        Text(
                            stats,
                            fontSize = 9.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onSave, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "บันทึก",
                        tint = if (saved) Color(0xFFD86A73) else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onLongPress, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        "ตัวเลือกเพิ่มเติม",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    c: Choice,
    v: Info,
    playChoice: (String, String, String, String, String?) -> Unit,
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
        IconButton(
            onClick = {
                playChoice(c.url.ifBlank { v.videoUrl }, c.selector, v.title, v.uploader, v.thumb)
            }
        ) {
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
