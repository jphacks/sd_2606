package jp.jphacks.sd2606.ui.recommend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import jp.jphacks.sd2606.data.Matched
import jp.jphacks.sd2606.data.PendingQuestion
import jp.jphacks.sd2606.data.RuleEngine
import jp.jphacks.sd2606.data.SampleData
import jp.jphacks.sd2606.ui.AnswersDialog
import jp.jphacks.sd2606.ui.AppUiState
import jp.jphacks.sd2606.ui.BackTopBar
import jp.jphacks.sd2606.ui.PrivacyNote
import jp.jphacks.sd2606.ui.QuestionCard
import jp.jphacks.sd2606.ui.ResultContent
import jp.jphacks.sd2606.ui.UiResult
import jp.jphacks.sd2606.ui.theme.AppTheme

/**
 * おすすめ一覧。判定できた手続きと、「答えると分かる手続き」（判定に足りない質問）を並べる。
 * 質問に答えるとその場で判定し直し、手続きが上の一覧に移るか、対象外として消える。
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
            BackTopBar("関係がありそうな手続き", onBack) {
                IconButton(onClick = { showAnswers = true }) {
                    Icon(Icons.Default.ManageAccounts, contentDescription = "保存している回答")
                }
            }
        },
    ) { padding ->
        ResultContent(state.catalog, onRetry, Modifier.padding(padding)) { catalog ->
            val evaluation = state.evaluation ?: return@ResultContent
            val questions = remember(evaluation, catalog) { RuleEngine.pendingQuestions(evaluation, catalog.questions) }
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

                item(key = "h-matched") { SectionTitle("あなたに必要な手続き（${evaluation.matched.size}件）") }
                if (evaluation.matched.isEmpty()) {
                    item(key = "none") { Text("いまの回答では、必要な手続きは見つかりませんでした。") }
                }
                items(evaluation.matched, key = { "m-${it.procedure.id}" }) { m ->
                    RecommendationCard(m, onClick = { onOpen(m.procedure.id) }, Modifier.animateItem())
                }

                if (questions.isNotEmpty()) {
                    item(key = "h-pending") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SectionTitle("答えると分かる手続き")
                            Text(
                                "必要かどうかを決めるのに足りない情報だけを聞いています。答えたくなければそのままで構いません。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(questions, key = { "q-${it.question.field}" }) { pq ->
                        PendingQuestionCard(pq, state.answers[pq.question.field], onAnswer, onOpen, Modifier.animateItem())
                    }
                }
            }
            if (showAnswers) {
                AnswersDialog(
                    questions = catalog.questions,
                    answers = state.answers,
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
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingQuestionCard(
    pq: PendingQuestion,
    answer: Answer?,
    onAnswer: (String, Answer?) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    QuestionCard(
        question = pq.question,
        answer = answer,
        onAnswer = { onAnswer(pq.question.field, it) },
        note = "答えると、次の手続きが必要かどうか分かります（タップで内容を見られます）",
        modifier = modifier,
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pq.procedures.forEach { proc ->
                AssistChip(
                    onClick = { onOpen(proc.id) },
                    label = { Text(proc.title) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(item: Matched, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val proc = item.procedure
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = onClick, label = { Text(Labels.category[proc.category] ?: proc.category) })
                Spacer(Modifier.width(8.dp))
                Text(proc.title, style = MaterialTheme.typography.titleMedium)
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
        evaluation = RuleEngine.evaluate(catalog.procedures, SampleData.answers),
    )
    AppTheme { RecommendScreen(state, onAnswer = { _, _ -> }, onClearAnswers = {}, onRetry = {}, onBack = {}, onOpen = {}) }
}
