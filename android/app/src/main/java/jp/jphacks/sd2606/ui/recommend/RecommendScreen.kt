package jp.jphacks.sd2606.ui.recommend

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import jp.jphacks.sd2606.data.Answer
import jp.jphacks.sd2606.data.Question
import jp.jphacks.sd2606.data.RuleEngine
import jp.jphacks.sd2606.data.SampleData
import jp.jphacks.sd2606.data.Scored
import jp.jphacks.sd2606.data.SortOrder
import jp.jphacks.sd2606.data.Status
import jp.jphacks.sd2606.ui.AnswersDialog
import jp.jphacks.sd2606.ui.AppBottomBar
import jp.jphacks.sd2606.ui.AppUiState
import jp.jphacks.sd2606.ui.BackTopBar
import jp.jphacks.sd2606.ui.CategoryChip
import jp.jphacks.sd2606.ui.PrivacyNote
import jp.jphacks.sd2606.ui.QuestionInput
import jp.jphacks.sd2606.ui.ResultContent
import jp.jphacks.sd2606.ui.Tab
import jp.jphacks.sd2606.ui.UiResult
import jp.jphacks.sd2606.ui.completedLabel
import jp.jphacks.sd2606.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * おすすめ一覧。「必要な手続き」「関係がありそうな手続き」を、関連度順か期限順で並べる。
 * 対象外と判定したものと完了したものは、閉じたグループにまとめ、押すと開く。
 * 判定を変えうる情報が足りない手続きには「判定には生年月日が必要です」のように案内し、カードの中で入力できる。
 * 入力した値は端末に保存し、ほかの手続きの判定にも使う。
 */
@Composable
fun RecommendScreen(
    state: AppUiState,
    onAnswer: (String, Answer?) -> Unit,
    onComplete: (String, Boolean) -> Unit,
    onClearAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearDismissed: () -> Unit,
    onDismiss: (String, Boolean) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onTab: (Tab) -> Unit,
) {
    var showAnswers by rememberSaveable { mutableStateOf(false) }
    var order by rememberSaveable { mutableStateOf(SortOrder.RELEVANCE) }
    var showUnrelated by rememberSaveable { mutableStateOf(false) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            BackTopBar("あなたの手続き", onBack) {
                IconButton(onClick = { showAnswers = true }) {
                    Icon(Icons.Default.ManageAccounts, contentDescription = "保存している回答")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { AppBottomBar(Tab.PROCEDURES, onTab) },
    ) { padding ->
        ResultContent(state.catalog, onRetry, Modifier.padding(padding)) { catalog ->
            val evaluation = state.evaluation ?: return@ResultContent
            val today = remember { LocalDate.now() }
            fun open(items: List<Scored>) = RuleEngine.sort(items.filter { it.procedure.id !in state.completed }, order, today)
            val required = open(evaluation.required)
            val likely = open(evaluation.likely)
            val unrelated = open(evaluation.unrelated)
            val completed = evaluation.items.filter { it.procedure.id in state.completed }.sortedByDescending { state.completed[it.procedure.id] }

            val answerInCard: (Question, Answer?) -> Unit = { q, answer ->
                onAnswer(q.field, answer)
                if (answer != null) {
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar("${q.name}を保存しました。ほかの手続きの判定にも使います")
                    }
                }
            }
            val complete: (Scored) -> Unit = { item ->
                val id = item.procedure.id
                onComplete(id, true)
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar("「${item.procedure.title}」を完了にしました", actionLabel = "元に戻す", duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) onComplete(id, false)
                }
            }
            val dismiss: (Scored, Boolean) -> Unit = { item, on ->
                val id = item.procedure.id
                onDismiss(id, on)
                if (on) {
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val result = snackbar.showSnackbar("「${item.procedure.title}」を関係ないにしました", actionLabel = "元に戻す", duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) onDismiss(id, false)
                    }
                }
            }
            val card: @Composable (Scored, Modifier) -> Unit = { item, modifier ->
                ProcedureCard(
                    item,
                    catalog.questions,
                    state.answers,
                    answerInCard,
                    onClick = { onOpen(item.procedure.id) },
                    onComplete = { complete(item) },
                    onDismiss = { dismiss(item, it) },
                    modifier = modifier,
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding),
            ) {
                item(key = "privacy") { PrivacyNote("判定はこの端末の中で行っています。答えた内容は送信していません。") }
                if (state.offline) {
                    item(key = "offline") {
                        Text(
                            "通信できないため、前回受け取った制度データで判定しています。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                item(key = "sort") { SortSelector(order) { order = it } }

                item(key = "h-required") { SectionHeader("必要な手続き（${required.size}件）", null) }
                if (required.isEmpty()) {
                    item(key = "none") { Text("いまの回答では、必ず必要な手続きは見つかりませんでした。", style = MaterialTheme.typography.bodyMedium) }
                }
                items(required, key = { it.procedure.id }) { card(it, Modifier.animateItem()) }

                if (likely.isNotEmpty()) {
                    item(key = "h-likely") {
                        SectionHeader(
                            "関係がありそうな手続き（${likely.size}件）",
                            buildString {
                                append(if (order == SortOrder.RELEVANCE) "関係の強い順に並べています。" else "期限の近い順に並べています（期限の決まっていないものは最後）。")
                                if (likely.any { it.missing.isNotEmpty() }) append("情報を入れると、必要か対象外かが決まるものがあります（入力は任意）。")
                            },
                        )
                    }
                    items(likely, key = { it.procedure.id }) { card(it, Modifier.animateItem()) }
                }

                if (unrelated.isNotEmpty()) {
                    item(key = "h-unrelated") {
                        GroupHeader("対象外の手続き（${unrelated.size}件）", "いまの回答では対象外と判定したものです。", showUnrelated) { showUnrelated = !showUnrelated }
                    }
                    if (showUnrelated) items(unrelated, key = { it.procedure.id }) { card(it, Modifier.animateItem()) }
                }

                if (completed.isNotEmpty()) {
                    item(key = "h-completed") {
                        GroupHeader("完了した手続き（${completed.size}件）", null, showCompleted) { showCompleted = !showCompleted }
                    }
                    if (showCompleted) {
                        items(completed, key = { it.procedure.id }) { item ->
                            CompletedCard(
                                item,
                                state.completed[item.procedure.id].orEmpty(),
                                onClick = { onOpen(item.procedure.id) },
                                onUndo = { onComplete(item.procedure.id, false) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
            if (showAnswers) {
                AnswersDialog(
                    questions = catalog.questions,
                    answers = state.answers,
                    completedCount = state.completed.size,
                    dismissedCount = state.dismissed.size,
                    onAnswer = onAnswer,
                    onClearCompleted = onClearCompleted,
                    onClearDismissed = onClearDismissed,
                    onDismiss = { showAnswers = false },
                    onClear = {
                        showAnswers = false
                        onClearAll()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSelector(order: SortOrder, onChange: (SortOrder) -> Unit) {
    val labels = mapOf(SortOrder.RELEVANCE to "関連度順", SortOrder.DEADLINE to "期限順")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SortOrder.entries.forEachIndexed { i, o ->
            SegmentedButton(
                selected = order == o,
                onClick = { onChange(o) },
                shape = SegmentedButtonDefaults.itemShape(i, SortOrder.entries.size),
            ) { Text(labels.getValue(o)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, note: String?) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** 押すと開閉するグループの見出し（対象外・完了）。 */
@Composable
private fun GroupHeader(title: String, note: String?, expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "閉じる" else "開く",
            )
        }
    }
}

@Composable
private fun ProcedureCard(
    item: Scored,
    questions: List<Question>,
    answers: Map<String, Answer>,
    onAnswer: (Question, Answer?) -> Unit,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onDismiss: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val proc = item.procedure
    val missing = remember(item, questions) { item.missing.mapNotNull { RuleEngine.questionFor(it, questions) }.distinct() }
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryChip(proc.category, onClick)
                Spacer(Modifier.width(8.dp))
                Text(proc.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            proc.deadline?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = "期限", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            missing.forEach { q -> NeedInfo(q, answers[q.field]) { onAnswer(q, it) } }
            // 手続きの画面の下部と同じ並び: 左「関係ない」、右「完了にする」。ルールで対象外のものには出さない
            if (item.status != Status.UNRELATED || item.dismissed) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedButton(onClick = { onDismiss(!item.dismissed) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.DoNotDisturbOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (item.dismissed) "関係ありに戻す" else "関係ない")
                    }
                    Button(onClick = onComplete, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("完了にする")
                    }
                }
            }
        }
    }
    if (item.status == Status.UNRELATED) {
        OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) { content() }
    } else {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth()) { content() }
    }
}

/** 「判定には扶養している家族の人数が必要です」の案内。「入力する」でその場に入力欄を開き、入れた値は全体の情報として保存する。 */
@Composable
private fun NeedInfo(question: Question, answer: Answer?, onAnswer: (Answer?) -> Unit) {
    var open by rememberSaveable(question.field) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("判定には${question.name}が必要です", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { open = !open }) { Text(if (open) "閉じる" else "入力する") }
            }
            if (open) {
                Column(Modifier.padding(end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(question.text, style = MaterialTheme.typography.bodySmall)
                    QuestionInput(question, answer, onAnswer)
                    Text(
                        "入れた値は端末に保存し、ほかの手続きの判定にも使います。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedCard(item: Scored, completedOn: String, onClick: () -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = "完了", tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.procedure.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(completedLabel(completedOn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onUndo) { Text("未完了に戻す") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecommendScreenPreview() {
    val catalog = SampleData.catalog
    val state = AppUiState(
        catalog = UiResult.Success(catalog),
        answers = SampleData.answers,
        evaluation = RuleEngine.evaluate(catalog.procedures, SampleData.answers, catalog.threshold),
    )
    AppTheme {
        RecommendScreen(
            state,
            onAnswer = { _, _ -> },
            onComplete = { _, _ -> },
            onClearAll = {},
            onClearCompleted = {},
            onClearDismissed = {},
            onDismiss = { _, _ -> },
            onRetry = {},
            onBack = {},
            onOpen = {},
            onTab = {},
        )
    }
}
