package jp.jphacks.sd2606.ui.recommend

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.Answer
import jp.jphacks.sd2606.data.Labels
import jp.jphacks.sd2606.data.Question
import jp.jphacks.sd2606.data.RuleEngine
import jp.jphacks.sd2606.data.SampleData
import jp.jphacks.sd2606.data.Scored
import jp.jphacks.sd2606.ui.AnswersDialog
import jp.jphacks.sd2606.ui.AppUiState
import jp.jphacks.sd2606.ui.BackTopBar
import jp.jphacks.sd2606.ui.PrivacyNote
import jp.jphacks.sd2606.ui.QuestionInput
import jp.jphacks.sd2606.ui.ResultContent
import jp.jphacks.sd2606.ui.UiResult
import jp.jphacks.sd2606.ui.theme.AppTheme

/**
 * おすすめ一覧。「必要な手続き」（必須と判定）と「関係がありそうな手続き」（必須ではないが点数がしきい値以上）に分け、
 * それぞれ点数の高い順に並べる。関係がありそうな手続きのうち、答えると必須かどうか決まるものには
 * 「判定には生年月日が必要です」のように案内し、カードの中で入力できる。
 */
@Composable
fun RecommendScreen(
    state: AppUiState,
    onAnswer: (String, Answer?) -> Unit,
    onClearAnswers: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var showAnswers by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            BackTopBar("あなたの手続き", onBack) {
                IconButton(onClick = { showAnswers = true }) {
                    Icon(Icons.Default.ManageAccounts, contentDescription = "保存している回答")
                }
            }
        },
    ) { padding ->
        ResultContent(state.catalog, onRetry, Modifier.padding(padding)) { catalog ->
            val evaluation = state.evaluation ?: return@ResultContent
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

                item(key = "h-required") { SectionHeader("必要な手続き（${evaluation.required.size}件）", null) }
                if (evaluation.required.isEmpty()) {
                    item(key = "none") { Text("いまの回答では、必ず必要な手続きは見つかりませんでした。", style = MaterialTheme.typography.bodyMedium) }
                }
                items(evaluation.required, key = { it.procedure.id }) { item ->
                    ProcedureCard(item, catalog.questions, state.answers, onAnswer, onClick = { onOpen(item.procedure.id) }, Modifier.animateItem())
                }

                if (evaluation.likely.isNotEmpty()) {
                    item(key = "h-likely") {
                        SectionHeader(
                            "関係がありそうな手続き（${evaluation.likely.size}件）",
                            buildString {
                                append("関係の強い順に並べています。条件しだいで必要になります。")
                                if (evaluation.likely.any { it.missing.isNotEmpty() }) append("情報を入れると、必要かどうか確定するものがあります（入力は任意）。")
                            },
                        )
                    }
                    items(evaluation.likely, key = { it.procedure.id }) { item ->
                        ProcedureCard(item, catalog.questions, state.answers, onAnswer, onClick = { onOpen(item.procedure.id) }, Modifier.animateItem())
                    }
                }
            }
            if (showAnswers) {
                AnswersDialog(
                    questions = catalog.questions,
                    answers = state.answers,
                    onAnswer = onAnswer,
                    onDismiss = { showAnswers = false },
                    onClear = {
                        showAnswers = false
                        onClearAnswers()
                    },
                )
            }
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

@Composable
private fun ProcedureCard(
    item: Scored,
    questions: List<Question>,
    answers: Map<String, Answer>,
    onAnswer: (String, Answer?) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val proc = item.procedure
    val missing = remember(item, questions) { item.missing.mapNotNull { RuleEngine.questionFor(it, questions) }.distinct() }
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = onClick, label = { Text(Labels.category[proc.category] ?: proc.category) })
                Spacer(Modifier.width(8.dp))
                Text(proc.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!item.required) {
                    Text(
                        "関連度 ${"%.1f".format(item.score)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            proc.deadline?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = "期限", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            item.reasons.forEach { reason ->
                Text("・$reason", style = MaterialTheme.typography.bodyMedium)
            }
            missing.forEach { q -> NeedInfo(q, answers[q.field]) { onAnswer(q.field, it) } }
        }
    }
}

/** 「判定には生年月日が必要です」の案内。「入力する」でその場に入力欄を開く。 */
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
                    QuestionInput(question, answer, onAnswer)
                }
            }
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
    AppTheme { RecommendScreen(state, onAnswer = { _, _ -> }, onClearAnswers = {}, onRetry = {}, onBack = {}, onOpen = {}) }
}
