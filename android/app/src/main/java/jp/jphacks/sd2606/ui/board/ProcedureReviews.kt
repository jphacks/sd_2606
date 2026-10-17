package jp.jphacks.sd2606.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.jphacks.sd2606.data.BoardSummary
import jp.jphacks.sd2606.data.Post
import jp.jphacks.sd2606.data.PostQuery
import jp.jphacks.sd2606.ui.UiResult

private const val PREVIEW_COUNT = 3

/** 手続きの画面の下に出す「みんなの体験談」。件数・大変さ・かかった時間と、役に立った順の上位3件。 */
@Composable
fun ProcedureReviews(vm: BoardViewModel, procedureId: String, onOpen: (Int) -> Unit, onMore: () -> Unit, onWrite: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val meta = (state.meta as? UiResult.Success)?.data
    var summary by remember(procedureId, state.version) { mutableStateOf<BoardSummary?>(null) }
    var posts by remember(procedureId, state.version) { mutableStateOf<List<Post>?>(null) }
    var error by remember(procedureId, state.version) { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(procedureId, state.version, retry) {
        error = null
        vm.summary(procedureId).onSuccess { summary = it }.onFailure { error = it.message }
        vm.page(PostQuery(procedure = procedureId, sort = "helpful"), 0)
            .onSuccess { posts = it.items.take(PREVIEW_COUNT) }
            .onFailure { error = it.message }
    }

    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("みんなの体験談", style = MaterialTheme.typography.titleMedium)
        }
        when {
            meta == null && state.meta is UiResult.Error -> RetryText((state.meta as UiResult.Error).message) { vm.loadMeta() }
            error != null -> RetryText(error.orEmpty()) { retry++ }
            meta == null || summary == null || posts == null -> CircularProgressIndicator(Modifier.size(24.dp))
            else -> {
                val s = summary!!
                if (s.count == 0) {
                    Text("まだ体験談がありません。この手続きをした人は、次の人のために書いてみませんか？", style = MaterialTheme.typography.bodyMedium)
                } else {
                    SummaryLine(s, meta)
                    posts!!.forEach { p -> PostCard(p, meta, state.helpfulCounts[p.id] ?: p.helpful, onClick = { onOpen(p.id) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.count > PREVIEW_COUNT) OutlinedButton(onClick = onMore) { Text("すべて見る（${s.count}件）") }
                    FilledTonalButton(onClick = onWrite) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("体験談を書く")
                    }
                }
            }
        }
    }
}

@Composable
private fun RetryText(message: String, onRetry: () -> Unit) {
    TextButton(onClick = onRetry) { Text("$message（タップで再試行）", color = MaterialTheme.colorScheme.error) }
}
