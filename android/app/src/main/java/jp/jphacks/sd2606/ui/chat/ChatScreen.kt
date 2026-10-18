package jp.jphacks.sd2606.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.Catalog
import jp.jphacks.sd2606.data.ChatContext
import jp.jphacks.sd2606.data.ChatReply
import jp.jphacks.sd2606.data.ReplyOption
import jp.jphacks.sd2606.ui.AppBottomBar
import jp.jphacks.sd2606.ui.AppUiState
import jp.jphacks.sd2606.ui.BackTopBar
import jp.jphacks.sd2606.ui.ResultContent
import jp.jphacks.sd2606.ui.Tab

/** 下部タブ（手続きなし）で最初に出す質問の例。押すとそのまま送る。 */
private val GENERAL_SUGGESTIONS = listOf(
    "いま一番先にやるべき手続きは？",
    "私はあといくらまでバイトできる？",
    "親の扶養から外れるとどうなる？",
    "学生納付特例は使ったほうがいい？",
)

/** 手続きの画面から開いたときに最初に出す質問の例。 */
private val PROCEDURE_SUGGESTIONS = listOf(
    "この手続きは私に必要？",
    "いつまでに何をすればいい？",
    "やらないとどうなる？",
    "必要なものは？",
)

/**
 * AI への質問。あなたの回答（年齢・年収など）と、手続きの画面から開いたときはその手続きを、あらかじめ AI に伝える。
 * AI がユーザーの状態を聞き返したときは選択肢を出し、アプリの質問項目に当たるものは選ぶと回答にも保存する。
 *
 * onBack が null なら下部タブの画面（ナビゲーションバーを出す）、あれば手続きの画面から開いた画面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: AppUiState,
    procedureId: String?,
    thread: ChatThread,
    onSend: (String) -> Unit,
    onPickOption: (ChatReply, ReplyOption) -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onRetryCatalog: () -> Unit,
    onBack: (() -> Unit)? = null,
    onTab: (Tab) -> Unit = {},
) {
    val clearAction: @Composable () -> Unit = {
        if (thread.entries.isNotEmpty()) {
            IconButton(onClick = onClear) { Icon(Icons.Outlined.DeleteSweep, contentDescription = "会話を消す") }
        }
    }
    Scaffold(
        topBar = {
            if (onBack != null) BackTopBar("AIに質問", onBack, bold = true) { clearAction() }
            else TopAppBar(title = { Text("AIに質問", fontWeight = FontWeight.Bold) }, actions = { clearAction() })
        },
        bottomBar = { if (onBack == null) AppBottomBar(Tab.AI, onTab) },
    ) { padding ->
        ResultContent(state.catalog, onRetryCatalog, Modifier.padding(padding)) { catalog ->
            val context = remember(catalog, state.answers, state.evaluation, state.completed, procedureId) {
                ChatContext.build(catalog, state.answers, state.evaluation, state.completed, procedureId)
            }
            val title = procedureId?.let { id -> catalog.procedures.firstOrNull { it.id == id }?.title }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
            ) {
                Messages(catalog, context, title, thread, procedureId, onSend, onPickOption, onRetry, Modifier.weight(1f))
                HorizontalDivider()
                InputBar(enabled = !thread.loading, onSend = onSend)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Messages(
    catalog: Catalog,
    context: ChatContext,
    title: String?,
    thread: ChatThread,
    procedureId: String?,
    onSend: (String) -> Unit,
    onPickOption: (ChatReply, ReplyOption) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // 新しい会話・考え中・エラーが出たら一番下まで送る
    val count = thread.entries.size + (if (thread.loading || thread.error != null) 1 else 0)
    LaunchedEffect(count) { if (count > 0) listState.animateScrollToItem(count) }

    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "context") { ContextCard(context, title) }
        if (thread.entries.isEmpty()) {
            item(key = "suggestions") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (title != null) "「$title」について、あなたの状況に合わせて答えます。" else "税金・保険・年金の手続きについて、あなたの状況に合わせて答えます。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (if (procedureId != null) PROCEDURE_SUGGESTIONS else GENERAL_SUGGESTIONS).forEach { q ->
                            SuggestionChip(onClick = { onSend(q) }, label = { Text(q) })
                        }
                    }
                }
            }
        }
        itemsIndexed(thread.entries) { index, entry ->
            if (entry.fromUser) {
                UserBubble(entry.text)
            } else {
                val reply = entry.reply
                // 聞き返しの選択肢は、最後の返事でまだ答えていないときだけ押せる
                val pickable = reply != null && reply.isQuestion && index == thread.entries.lastIndex && !thread.loading
                AiBubble(entry.text) {
                    if (pickable && reply.options.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            reply.options.forEach { option ->
                                SuggestionChip(onClick = { onPickOption(reply, option) }, label = { Text(option.label) })
                            }
                        }
                        val saveTo = reply.field?.let { f -> catalog.questions.firstOrNull { it.field == f }?.name }
                        if (saveTo != null) {
                            Text(
                                "選ぶと「$saveTo」の回答にも保存し、手続きの判定に使います",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (thread.loading) {
            item(key = "loading") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("考えています…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        thread.error?.let { error ->
            item(key = "error") {
                Column {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRetry) { Text("もう一度送る") }
                }
            }
        }
    }
}

/** AI に伝える情報。最初は1行の要約で、押すと中身を開く。 */
@Composable
private fun ContextCard(context: ChatContext, title: String?) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.clickable { open = !open }.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("AIに伝える情報", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "閉じる" else "開く")
            }
            val summary = buildString {
                append(if (context.profile.isEmpty()) "あなたの回答はまだありません" else context.profile.joinToString("・") { it.label.substringAfter('：') })
                if (title != null) append("\nいま見ている手続き: $title")
            }
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (open) 4 else 2)
            AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    context.profile.forEach { Text("・${it.label}: ${it.value}", style = MaterialTheme.typography.bodySmall) }
                    if (context.procedures.isNotEmpty()) {
                        Text("・手続きの判定（必要・関係がありそう・対象外）${context.procedures.size}件", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "質問すると、この情報と会話がサーバーを通して AI（Claude）に送られます。サーバーには保存しません。生年月日は送らず、年齢だけを送ります。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp), modifier = Modifier.widthIn(max = 300.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun AiBubble(text: String, extra: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp).size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)) {
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
            extra()
        }
    }
}

@Composable
private fun InputBar(enabled: Boolean, onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        if (enabled && text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("質問を入力") },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = send, enabled = enabled && text.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "送る")
        }
    }
}
