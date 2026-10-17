package jp.jphacks.sd2606.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.jphacks.sd2606.data.BoardMeta
import jp.jphacks.sd2606.data.Post
import jp.jphacks.sd2606.data.PostQuery
import jp.jphacks.sd2606.ui.BackTopBar
import jp.jphacks.sd2606.ui.PrivacyNote
import jp.jphacks.sd2606.ui.ResultContent
import kotlinx.coroutines.launch

/**
 * 体験談の一覧。下のタブの「体験談」では全体を、手続きの画面の「すべて見る」では procedureId の体験談だけを出す。
 * 種類・タグ・並び順で絞り込める。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostListScreen(
    vm: BoardViewModel,
    title: String,
    onOpen: (Int) -> Unit,
    onWrite: () -> Unit,
    procedureId: String? = null,
    onBack: (() -> Unit)? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { if (onBack != null) BackTopBar(title, onBack) else TopAppBar(title = { Text(title) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onWrite, icon = { Icon(Icons.Default.Edit, contentDescription = null) }, text = { Text("体験談を書く") })
        },
    ) { padding ->
        ResultContent(state.meta, vm::loadMeta, Modifier.padding(padding)) { meta ->
            var kind by rememberSaveable { mutableStateOf<String?>(null) }
            var tag by rememberSaveable { mutableStateOf<String?>(null) }
            var sort by rememberSaveable { mutableStateOf("new") }
            val query = PostQuery(procedure = procedureId, tag = tag, kind = kind, sort = sort)
            PostList(vm, meta, query, state.version, state.helpfulCounts, onOpen, Modifier.padding(padding)) {
                Filters(meta, procedureId, kind, { kind = it }, tag, { tag = it }, sort, { sort = it })
            }
        }
    }
}

@Composable
private fun PostList(
    vm: BoardViewModel,
    meta: BoardMeta,
    query: PostQuery,
    version: Int,
    helpfulCounts: Map<Int, Int>,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
    header: @Composable () -> Unit,
) {
    var items by remember(query, version) { mutableStateOf<List<Post>>(emptyList()) }
    var next by remember(query, version) { mutableStateOf<Int?>(0) }
    var loading by remember(query, version) { mutableStateOf(false) }
    var error by remember(query, version) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadMore() {
        val offset = next ?: return
        loading = true
        error = null
        vm.page(query, offset)
            .onSuccess { page ->
                items = (items + page.items).distinctBy { it.id }
                next = page.nextOffset
            }
            .onFailure { error = it.message }
        loading = false
    }
    LaunchedEffect(query, version) { loadMore() }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), // 下は「書く」ボタンの分
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { header() }
        items(items, key = { it.id }) { post ->
            PostCard(post, meta, helpfulCounts[post.id] ?: post.helpful, onClick = { onOpen(post.id) })
        }
        item(key = "footer") {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                when {
                    loading -> CircularProgressIndicator()
                    error != null -> TextButton(onClick = { scope.launch { loadMore() } }) {
                        Text("${error}（タップで再試行）", color = MaterialTheme.colorScheme.error)
                    }
                    next != null -> OutlinedButton(onClick = { scope.launch { loadMore() } }) { Text("もっと見る") }
                    items.isEmpty() -> Text("まだ体験談がありません。最初の1件を書いてみませんか？", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Filters(
    meta: BoardMeta,
    procedureId: String?,
    kind: String?,
    onKind: (String?) -> Unit,
    tag: String?,
    onTag: (String?) -> Unit,
    sort: String,
    onSort: (String) -> Unit,
) {
    val tags = if (procedureId != null) meta.tagsFor(procedureId) else meta.tags
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PrivacyNote("匿名の体験談です。制度や窓口の対応は自治体・年度で変わることがあります。")
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                item { FilterChip(selected = kind == null, onClick = { onKind(null) }, label = { Text("すべて") }) }
                items(meta.kinds, key = { it.value }) { k ->
                    FilterChip(selected = kind == k.value, onClick = { onKind(if (kind == k.value) null else k.value) }, label = { Text(k.label) })
                }
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { onSort(if (sort == "new") "helpful" else "new") }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(if (sort == "new") "新着順" else "役に立った順")
            }
        }
        if (tags.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(selected = tag == null, onClick = { onTag(null) }, label = { Text("全タグ") }) }
                items(tags, key = { it.id }) { t ->
                    FilterChip(selected = tag == t.id, onClick = { onTag(if (tag == t.id) null else t.id) }, label = { Text("#${t.label}") })
                }
            }
        }
    }
}
