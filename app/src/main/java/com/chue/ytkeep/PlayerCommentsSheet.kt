package com.chue.ytkeep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerCommentsSheet(
    videoId: String,
    onDismiss: () -> Unit,
    repository: CommentsRepository = InnerTubeCommentsRepository
) {
    val scope = rememberCoroutineScope()
    var status by remember(videoId) { mutableStateOf<CommentsStatus>(CommentsStatus.Loading) }
    var page by remember(videoId) { mutableStateOf<CommentPage?>(null) }
    var loadingMore by remember(videoId) { mutableStateOf(false) }
    var loadMoreFailed by remember(videoId) { mutableStateOf(false) }
    var retryCount by remember(videoId) { mutableIntStateOf(0) }
    var pageJob by remember(videoId) { mutableStateOf<Job?>(null) }

    LaunchedEffect(videoId, retryCount) {
        status = CommentsStatus.Loading
        page = null
        loadMoreFailed = false
        when (val result = repository.load(videoId, null)) {
            is CommentsResult.Page -> {
                page = result.value
                status = if (result.value.comments.isEmpty() && result.value.next == null) {
                    CommentsStatus.Empty
                } else {
                    CommentsStatus.Content
                }
            }
            CommentsResult.Disabled -> status = CommentsStatus.Disabled
            CommentsResult.Unavailable -> status = CommentsStatus.Unavailable
            CommentsResult.Failure -> status = CommentsStatus.Failure
        }
    }

    DisposableEffect(videoId) {
        onDispose { pageJob?.cancel() }
    }

    fun loadMore() {
        val cursor = page?.next ?: return
        if (loadingMore) return
        pageJob?.cancel()
        pageJob = scope.launch {
            loadingMore = true
            loadMoreFailed = false
            when (val result = repository.load(videoId, cursor)) {
                is CommentsResult.Page -> {
                    val previous = page
                    page = if (previous == null) result.value else CommentPage(
                        comments = (previous.comments + result.value.comments).distinctBy { it.id },
                        next = result.value.next
                    )
                }
                else -> loadMoreFailed = true
            }
            loadingMore = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ความคิดเห็น", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) { Text("ปิด") }
            }

            when (status) {
                CommentsStatus.Loading -> {
                    Row(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                CommentsStatus.Empty -> Text("ยังไม่มีความคิดเห็น", modifier = Modifier.padding(16.dp))
                CommentsStatus.Disabled -> Text("คลิปนี้ปิดความคิดเห็นไว้", modifier = Modifier.padding(16.dp))
                CommentsStatus.Unavailable -> RetryMessage("ยังแสดงความคิดเห็นไม่ได้", onRetry = { retryCount++ })
                CommentsStatus.Failure -> RetryMessage("โหลดความคิดเห็นไม่สำเร็จ", onRetry = { retryCount++ })
                CommentsStatus.Content -> {
                    val currentPage = page
                    if (currentPage != null) {
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(max = 560.dp)
                        ) {
                            items(currentPage.comments, key = { it.id }) { comment ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(comment.author.ifBlank { "ผู้ใช้ YouTube" }, fontWeight = FontWeight.SemiBold)
                                        Text(comment.publishedLabel, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(comment.text, modifier = Modifier.padding(top = 4.dp))
                                    comment.likeCount?.takeIf { it > 0 }?.let {
                                        Text("ถูกใจ $it", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            if (currentPage.next != null) {
                                item {
                                    when {
                                        loadingMore -> CircularProgressIndicator(
                                            modifier = Modifier.padding(16.dp)
                                        )
                                        loadMoreFailed -> RetryMessage(
                                            "โหลดความคิดเห็นเพิ่มเติมไม่สำเร็จ",
                                            onRetry = ::loadMore
                                        )
                                        else -> Button(
                                            onClick = ::loadMore,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                        ) {
                                            Text("โหลดความคิดเห็นเพิ่มเติม")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RetryMessage(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message)
        TextButton(onClick = onRetry) { Text("ลองอีกครั้ง") }
    }
}

private enum class CommentsStatus {
    Loading,
    Content,
    Empty,
    Disabled,
    Unavailable,
    Failure
}
