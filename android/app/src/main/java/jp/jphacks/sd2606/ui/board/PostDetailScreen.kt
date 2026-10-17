package jp.jphacks.sd2606.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.jphacks.sd2606.data.Post
import jp.jphacks.sd2606.ui.BackTopBar
import jp.jphacks.sd2606.ui.ResultContent
import jp.jphacks.sd2606.ui.UiResult
import kotlinx.coroutines.launch

/** 体験談の全文。「役に立った」、通報、自分の投稿なら削除ができる。 */
@Composable
fun PostDetailScreen(vm: BoardViewModel, postId: Int, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var post by remember(postId) { mutableStateOf<UiResult<Post>>(UiResult.Loading) }
    var reload by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) } // "report" / "delete"
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(postId, reload) {
        post = UiResult.Loading
        post = vm.post(postId).fold({ UiResult.Success(it) }, { UiResult.Error(it.message.orEmpty()) })
    }
    val mine = postId in state.mine

    Scaffold(
        topBar = {
            BackTopBar("体験談", onBack) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "その他") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (mine) {
                        DropdownMenuItem(text = { Text("この投稿を削除") }, onClick = { menu = false; confirm = "delete" })
                    } else {
                        DropdownMenuItem(
                            text = { Text(if (postId in state.reported) "通報済み" else "不適切な投稿として通報") },
                            enabled = postId !in state.reported,
                            onClick = { menu = false; confirm = "report" },
                        )
                    }
                }
            }
        },
    ) { padding ->
        ResultContent(post, { reload++ }, Modifier.padding(padding)) { p ->
            val meta = (state.meta as? UiResult.Success)?.data ?: return@ResultContent
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PostCard(p, meta, state.helpfulCounts[p.id] ?: p.helpful, full = true)
                if (p.sample) {
                    Text("これは開発チームが作成したサンプルの体験談です。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (mine) {
                    Text("あなたがこの端末から投稿した体験談です。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                val voted = p.id in state.helpful
                val count = state.helpfulCounts[p.id] ?: p.helpful
                val label = "役に立った（$count）"
                val onHelpful: () -> Unit = { scope.launch { vm.toggleHelpful(p).onFailure { message = it.message } } }
                if (voted) {
                    FilledTonalButton(onClick = onHelpful) {
                        Icon(Icons.Filled.ThumbUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                } else {
                    OutlinedButton(onClick = onHelpful) {
                        Icon(Icons.Outlined.ThumbUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (action == "delete") "投稿を削除しますか？" else "通報しますか？") },
            text = {
                Text(
                    if (action == "delete") "削除すると元に戻せません。"
                    else "個人情報や不適切な内容を含む投稿を通報します。通報が一定数集まると非表示になります。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        if (action == "delete") vm.delete(postId).onSuccess { onBack() }.onFailure { message = it.message }
                        else vm.report(postId).onSuccess { message = "通報しました。ご協力ありがとうございます。" }.onFailure { message = it.message }
                    }
                }) { Text(if (action == "delete") "削除する" else "通報する") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("やめる") } },
        )
    }
}
