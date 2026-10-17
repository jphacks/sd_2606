package jp.jphacks.sd2606.ui.flow

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.Answer
import jp.jphacks.sd2606.data.ProcedureDef
import jp.jphacks.sd2606.data.Question
import jp.jphacks.sd2606.data.RuleEngine
import jp.jphacks.sd2606.data.SampleData
import jp.jphacks.sd2606.data.Scored
import jp.jphacks.sd2606.data.Status
import jp.jphacks.sd2606.data.StepView
import jp.jphacks.sd2606.ui.AppUiState
import jp.jphacks.sd2606.ui.BackTopBar
import jp.jphacks.sd2606.ui.QuestionCard
import jp.jphacks.sd2606.ui.ResultContent
import jp.jphacks.sd2606.ui.UiResult
import jp.jphacks.sd2606.ui.completedLabel
import jp.jphacks.sd2606.ui.theme.AppTheme

/**
 * 手続きの流れを縦のタイムラインで表示する画面。制度データは端末内にあるので通信しない。
 * 年齢や働き方で当てはまらない説明（ステップ）は省き、必要なものはステップをまとめて一覧にする。
 * まだ判定できていない手続きなら、上部で判定に必要な質問だけを聞く。終わった手続きは完了として記録できる。
 */
@Composable
fun FlowScreen(
    state: AppUiState,
    procedureId: String,
    onAnswer: (String, Answer?) -> Unit,
    onComplete: (String, Boolean) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val proc = (state.catalog as? UiResult.Success)?.data?.procedures?.firstOrNull { it.id == procedureId }
    Scaffold(topBar = { BackTopBar(proc?.title ?: "手続きの流れ", onBack) }) { padding ->
        ResultContent(state.catalog, onRetry, Modifier.padding(padding)) { catalog ->
            if (proc == null) {
                Text("手続きが見つかりませんでした。", Modifier.padding(padding).padding(16.dp))
                return@ResultContent
            }
            val item = state.evaluation?.find(proc.id)
            val steps = item?.steps ?: proc.steps.map { StepView(it, emptyList()) }
            val hidden = proc.steps.size - steps.size
            val uriHandler = LocalUriHandler.current
            LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(padding)) {
                item { Header(proc) }
                item { Completion(state.completed[proc.id]) { onComplete(proc.id, it) } }
                item { Status(item, state, catalog.questions, onAnswer) }
                if (item != null && item.documents.isNotEmpty()) item { Documents(item) }
                item {
                    Text(
                        if (hidden > 0) "手続きの流れ（あなたに当てはまらない説明${hidden}件を省いています）" else "手続きの流れ",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                itemsIndexed(steps) { index, view ->
                    StepRow(index + 1, view, catalog.questions, isLast = index == steps.lastIndex)
                }
                items(proc.links) { link ->
                    TextButton(onClick = { uriHandler.openUri(link.url) }) { Text("🔗 ${link.label}") }
                }
            }
        }
    }
}

/** 完了の記録。終わったら押して、完了した日を端末に残す。 */
@Composable
private fun Completion(completedOn: String?, onChange: (Boolean) -> Unit) {
    Box(Modifier.padding(bottom = 16.dp)) {
        if (completedOn != null) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(completedLabel(completedOn), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onChange(false) }) { Text("未完了に戻す") }
                }
            }
        } else {
            Button(onClick = { onChange(true) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("申請などが終わったら、完了にする")
            }
        }
    }
}

/** この人にとって必要か（必要 / 関係がありそう / 対象外）。判定を変えうる情報が足りなければ、その質問を聞く。 */
@Composable
private fun Status(item: Scored?, state: AppUiState, questions: List<Question>, onAnswer: (String, Answer?) -> Unit) {
    item ?: return
    Column(Modifier.padding(bottom = 16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (item.status) {
            Status.REQUIRED -> StatusCard("あなたに必要な手続きです", MaterialTheme.colorScheme.primaryContainer) {
                item.reasons.forEach { Text("・$it", style = MaterialTheme.typography.bodyMedium) }
            }
            Status.LIKELY -> StatusCard("関係がありそうな手続きです", MaterialTheme.colorScheme.secondaryContainer) {
                item.reasons.forEach { Text("・$it", style = MaterialTheme.typography.bodyMedium) }
                Text("条件しだいで必要になります。", style = MaterialTheme.typography.bodySmall)
            }
            Status.UNRELATED -> StatusCard("いまの回答では対象外です", MaterialTheme.colorScheme.surfaceVariant) {
                item.reasons.forEach { Text("・$it", style = MaterialTheme.typography.bodyMedium) }
                Text("参考として表示しています。", style = MaterialTheme.typography.bodySmall)
            }
        }
        item.missing.mapNotNull { RuleEngine.questionFor(it, questions) }.distinct().forEach { q ->
            QuestionCard(
                question = q,
                answer = state.answers[q.field],
                onAnswer = { onAnswer(q.field, it) },
                note = "答えると、この手続きが必要かどうか確定します。入れた値はほかの手続きの判定にも使います。",
            )
        }
    }
}

@Composable
private fun StatusCard(title: String, color: Color, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

/** 必要なものの一覧（表示するステップの分をまとめたもの）。 */
@Composable
private fun Documents(item: Scored) {
    Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("必要なもの", style = MaterialTheme.typography.titleSmall)
            }
            item.documents.forEach { doc ->
                Text(
                    "・${doc.name}${if (doc.conditional) "（当てはまる場合）" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun Header(proc: ProcedureDef) {
    Card(Modifier.padding(bottom = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(proc.summary, style = MaterialTheme.typography.bodyMedium)
            proc.deadline?.let { Text("期限: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            proc.office?.let { Text("窓口: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepRow(number: Int, view: StepView, questions: List<Question>, isLast: Boolean) {
    val step = view.step
    Row(Modifier.height(IntrinsicSize.Min)) {
        // 左側: 番号の丸と、次のステップへの線
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Text("$number", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Column(
            Modifier
                .padding(start = 8.dp, bottom = 24.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(step.title, style = MaterialTheme.typography.titleSmall)
            if (view.unknown.isNotEmpty()) {
                val names = view.unknown.mapNotNull { RuleEngine.questionFor(it, questions)?.name }.distinct().joinToString("・")
                Text(
                    "当てはまる場合のみ（${names}を入れると、当てはまるかどうかで表示を絞ります）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(step.detail, style = MaterialTheme.typography.bodyMedium)
            step.where?.let { Text("📍 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            if (step.documents.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    step.documents.forEach { doc -> SuggestionChip(onClick = {}, label = { Text(doc) }) }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FlowScreenPreview() {
    val catalog = SampleData.catalog
    val state = AppUiState(
        catalog = UiResult.Success(catalog),
        answers = SampleData.answers,
        evaluation = RuleEngine.evaluate(catalog.procedures, SampleData.answers, catalog.threshold),
    )
    AppTheme { FlowScreen(state, SampleData.procedure.id, onAnswer = { _, _ -> }, onComplete = { _, _ -> }, onRetry = {}, onBack = {}) }
}
