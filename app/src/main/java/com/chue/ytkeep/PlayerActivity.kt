package com.chue.ytkeep

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture

class PlayerActivity : ComponentActivity() {

    private lateinit var future: ListenableFuture<MediaController>
    private var controller: MediaController? by mutableStateOf(null)
    private var title by mutableStateOf("")
    private var author by mutableStateOf("")
    private var videoId by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        title = intent.getStringExtra("title").orEmpty()
        author = intent.getStringExtra("author").orEmpty()
        videoId = intent.getStringExtra("videoId").orEmpty()

        val token = SessionToken(this, ComponentName(this, PlayerService::class.java))
        future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            runCatching { controller = future.get() }
        }, ContextCompat.getMainExecutor(this))

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = true
                                setShowNextButton(false)
                                setShowPreviousButton(false)
                                player = controller
                            }
                        },
                        update = { it.player = controller }
                    )
                    Overlay(
                        title = title,
                        author = author,
                        videoId = videoId,
                        controller = controller,
                        onBack = { finish() },
                        onOpenComments = {
                            val vid = videoId.ifBlank {
                                controller?.currentMediaItem?.mediaId.orEmpty()
                            }
                            if (vid.isNotBlank()) {
                                startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$vid"))
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (::future.isInitialized) MediaController.releaseFuture(future)
        super.onDestroy()
    }
}

@Composable
private fun Overlay(
    title: String,
    author: String,
    videoId: String,
    controller: MediaController?,
    onBack: () -> Unit,
    onOpenComments: () -> Unit
) {
    val ctx = LocalContext.current
    val qState = QueueManager.state
    var showQueueDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }

    val currentTitle = qState.currentItem?.title?.ifBlank { null }
        ?: title.ifBlank { controller?.mediaMetadata?.title?.toString().orEmpty() }
    val currentAuthor = qState.currentItem?.channel?.ifBlank { null }
        ?: author.ifBlank { controller?.mediaMetadata?.artist?.toString().orEmpty() }
    val currentVid = qState.currentItem?.id ?: videoId.ifBlank {
        controller?.currentMediaItem?.mediaId.orEmpty()
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // แถบหัวเรื่องด้านบน
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "กลับ",
                    tint = Color.White
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = currentTitle.ifBlank { "YTNx Player" },
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                if (qState.items.isNotEmpty()) {
                    Text(
                        text = "${qState.title} (${qState.currentIndex + 1}/${qState.items.size})",
                        color = Color(0xFFE24A56),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (qState.items.isNotEmpty()) {
                IconButton(onClick = { showQueueDialog = true }) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = "คิวการเล่น",
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // แถบควบคุมและข้อมูลด้านล่าง
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xAA0B0B0D), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            if (currentAuthor.isNotBlank()) {
                Text(
                    currentAuthor,
                    color = Color(0xFFD1D5DB),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            val durationMs = controller?.duration ?: 0L
            if (durationMs > 0) {
                Text(
                    "ความยาว ${durationMs / 60000}:${(durationMs / 1000 % 60).toString().padStart(2, '0')}",
                    color = Color(0xFF9CA3AF),
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // แถบปุ่มควบคุมคิว (เล่นต่อเนื่อง / ข้ามเพลง / สุ่ม / วนซ้ำ)
            if (qState.items.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ปุ่ม Shuffle
                    IconButton(onClick = { QueueManager.toggleShuffle() }) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "สุ่มเพลง",
                            tint = if (qState.isShuffle) Color(0xFFE24A56) else Color(0xFF94A3B8)
                        )
                    }

                    // ปุ่ม ข้ามไปเพลงก่อนหน้า
                    IconButton(
                        onClick = { QueueManager.skipToPrevious(ctx, controller) },
                        enabled = qState.hasPrevious
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "ก่อนหน้า",
                            tint = if (qState.hasPrevious) Color.White else Color(0xFF555555)
                        )
                    }

                    // ปุ่ม ดูคิว
                    IconButton(onClick = { showQueueDialog = true }) {
                        Icon(
                            Icons.Default.QueueMusic,
                            contentDescription = "ดูคิว",
                            tint = Color(0xFFE24A56)
                        )
                    }

                    // ปุ่ม ข้ามไปเพลงถัดไป
                    IconButton(
                        onClick = { QueueManager.skipToNext(ctx, controller) },
                        enabled = qState.hasNext
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "ถัดไป",
                            tint = if (qState.hasNext) Color.White else Color(0xFF555555)
                        )
                    }

                    // ปุ่ม Repeat
                    IconButton(onClick = { QueueManager.toggleRepeat() }) {
                        val icon = when (qState.repeatMode) {
                            QueueManager.RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        }
                        val tint = when (qState.repeatMode) {
                            QueueManager.RepeatMode.OFF -> Color(0xFF94A3B8)
                            else -> Color(0xFFE24A56)
                        }
                        Icon(icon, contentDescription = "เล่นซ้ำ", tint = tint)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentVid.isNotBlank()) {
                    Button(
                        onClick = onOpenComments,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF231B22))
                    ) {
                        Text("ดูความคิดเห็น", fontSize = 12.sp)
                    }
                }
                if (qState.items.isNotEmpty()) {
                    Button(
                        onClick = {
                            saveName = qState.title.takeIf { it != "คิวเล่นต่อเนื่อง" } ?: "เพลย์ลิสต์ของฉัน"
                            showSaveDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF812E38))
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("เซฟเป็นเพลย์ลิสต์", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Dialog แสดงคิวที่กำลังเล่น
    if (showQueueDialog) {
        AlertDialog(
            onDismissRequest = { showQueueDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QueueMusic, null, tint = Color(0xFFE24A56))
                    Spacer(Modifier.width(8.dp))
                    Text("คิวการเล่น (${qState.items.size})", fontSize = 16.sp)
                }
            },
            text = {
                Column(Modifier.fillMaxWidth().height(360.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(qState.title, fontSize = 12.sp, color = Color(0xFF94A3B8), maxLines = 1)
                        TextButton(onClick = {
                            saveName = qState.title.takeIf { it != "คิวเล่นต่อเนื่อง" } ?: "เพลย์ลิสต์ใหม่"
                            showSaveDialog = true
                        }) {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("เซฟเป็นเพลย์ลิสต์", fontSize = 11.sp, color = Color(0xFFE24A56))
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(qState.items) { index, item ->
                            val isCurrent = index == qState.currentIndex
                            Surface(
                                color = if (isCurrent) Color(0xFF33161D) else Color(0xFF151216),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        QueueManager.jumpToIndex(ctx, controller, index)
                                    }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isCurrent) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            null,
                                            tint = Color(0xFFE24A56),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Box(
                                        Modifier.size(44.dp, 32.dp).clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black)
                                    ) {
                                        AsyncImage(
                                            model = item.thumb,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            item.title,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isCurrent) Color(0xFFF8FAFC) else Color(0xFFD1D5DB),
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            item.channel,
                                            fontSize = 10.sp,
                                            color = Color(0xFF94A3B8),
                                            maxLines = 1
                                        )
                                    }
                                    IconButton(
                                        onClick = { QueueManager.removeFromQueue(index) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            null,
                                            tint = Color(0xFF71717A),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQueueDialog = false }) {
                    Text("ปิด")
                }
            }
        )
    }

    // Dialog บันทึกเป็นเพลย์ลิสต์
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("บันทึกคิวเป็นเพลย์ลิสต์") },
            text = {
                Column {
                    Text("ตั้งชื่อเพลย์ลิสต์สำหรับบันทึก ${qState.items.size} คลิปในคิว", fontSize = 13.sp, color = Color(0xFF94A3B8))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        label = { Text("ชื่อเพลย์ลิสต์") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pl = QueueManager.saveAsPlaylist(ctx, saveName)
                        if (pl != null) {
                            Toast.makeText(ctx, "บันทึกเพลย์ลิสต์ \"${pl.name}\" แล้ว", Toast.LENGTH_SHORT).show()
                            showSaveDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE24A56))
                ) {
                    Text("บันทึก")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }
}
