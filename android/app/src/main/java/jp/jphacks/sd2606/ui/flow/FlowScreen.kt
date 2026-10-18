package jp.jphacks.sd2606.ui.flow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.remember
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.Answer
import jp.jphacks.sd2606.data.FlowItem
import jp.jphacks.sd2606.data.Document
import androidx.compose.material.icons.outlined.Payments
import jp.jphacks.sd2606.data.AmountView
import jp.jphacks.sd2606.data.Amounts
import jp.jphacks.sd2606.data.ProcedureDef
import jp.jphacks.sd2606.data.Question
import jp.jphacks.sd2606.data.RuleEngine
import jp.jphacks.sd2606.data.SampleData
import jp.jphacks.sd2606.data.Scored
import jp.jphacks.sd2606.data.Status
import jp.jphacks.sd2606.data.StepView
import jp.jphacks.sd2606.ui.AppUiState
import jp.jphacks.sd2606.ui.BackTopBar
import jp.jphacks.sd2606.ui.ProcedureActions
import jp.jphacks.sd2606.ui.QuestionCard
import jp.jphacks.sd2606.ui.ResultContent
import jp.jphacks.sd2606.ui.UiResult
import jp.jphacks.sd2606.ui.completedLabel
import jp.jphacks.sd2606.ui.recommend.MaybeRequired
import jp.jphacks.sd2606.ui.theme.AppTheme

/**
 * 手続きの流れを縦のタイムラインで表示する画面。制度データは端末内にあるので通信しない。
 * 年齢や働き方で当てはまらないステップは出さず、詳しい手順は押すと開く。
 * 終わった手続きは完了として、関係ない手続きは「自分には関係ない」として記録できる。
 */
@Composable
fun FlowScreen(
    state: AppUiState,
    procedureId: String,
    onAnswer: (String, Answer?) -> Unit,
    onComplete: (String, Boolean) -> Unit,
    onDismiss: (String, Boolean) -> Unit,
    onConfirm: (String, Boolean) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onAsk: (String) -> Unit = {},
) {
    val proc = (state.catalog as? UiResult.Success)?.data?.procedures?.firstOrNull { it.id == procedureId }
    Scaffold(
        topBar = {
            BackTopBar(proc?.title ?: "手続きの流れ", onBack, bold = true) {
                if (proc != null) {
                    // この手続きと、あなたの回答を AI に伝えて質問する
                    TextButton(onClick = { onAsk(proc.id) }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("AIに質問")
                    }
                }
            }
        },
        bottomBar = {
            val item = proc?.let { state.evaluation?.find(it.id) }
            if (proc != null && item != null) {
                BottomActions(
                    item = item,
                    completedOn = state.completed[proc.id],
                    onComplete = { onComplete(proc.id, it) },
                    onDismiss = { onDismiss(proc.id, it) },
                    onConfirm = { onConfirm(proc.id, it) },
                )
            }
        },
    ) { padding ->
        ResultContent(state.catalog, onRetry, Modifier.padding(padding)) { catalog ->
            if (proc == null) {
                Text("手続きが見つかりませんでした。", Modifier.padding(padding).padding(16.dp))
                return@ResultContent
            }
            val item = state.evaluation?.find(proc.id)
            // フロー: 分岐を選ぶまではその先を出さず、選び終えたら最後にお金の目安を出す
            val flow = remember(proc, state.answers) { RuleEngine.flow(proc, state.answers) }
            val documents = RuleEngine.documentsOf(flow.steps)
            val amounts = remember(flow, state.answers) { Amounts.of(flow.amounts, state.answers).map { it.second } }
            val uriHandler = LocalUriHandler.current
            LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(padding)) {
                item { Header(proc) }
                item { Status(item, state, catalog.questions, onAnswer) }
                if (documents.isNotEmpty()) item(key = "documents") { Documents(documents, Modifier.animateItem()) }
                item { Text("手続きの流れ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp)) }
                if (flow.items.isEmpty()) {
                    item { Text("あなたに当てはまる手順はありません。", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp)) }
                }
                itemsIndexed(flow.items, key = { i, fi -> if (fi is FlowItem.Choice) "choice:${fi.choice.field}" else "step:$i" }) { index, fi ->
                    when (fi) {
                        is FlowItem.Step -> StepRow(
                            fi.number, fi.view, catalog.questions, state.answers,
                            isLast = index == flow.items.lastIndex,
                            modifier = Modifier.animateItem(),
                        )
                        is FlowItem.Choice -> ChoiceBlock(
                            fi,
                            onSelect = { value -> onAnswer(fi.choice.field, value?.let { Answer.Single(it) }) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (flow.decided && amounts.isNotEmpty()) item(key = "amounts") { AmountsCard(amounts, Modifier.animateItem()) }
                val notes = RuleEngine.notesFor(proc, state.answers)
                if (notes.isNotEmpty()) item(key = "notes") { NotesCard(notes.map { RuleEngine.fillIn(it.text, state.answers, catalog.questions) }, Modifier.animateItem()) }
                items(proc.links) { link ->
                    TextButton(onClick = { uriHandler.openUri(link.url) }) { Text("🔗 ${link.label}") }
                }
            }
        }
    }
}

/** フローの後の短い注意（控除を受けられる可能性など）。 */
@Composable
private fun NotesCard(notes: List<String>, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("控除を受けられるかもしれません", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            notes.forEach { Text("・$it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/**
 * 画面の下に固定するボタン（一覧のカードと同じ ProcedureActions）。
 * どれも端末に記録し、押し直すと取り消せる。記録したときは、ボタンの上にその旨を出す。
 */
@Composable
private fun BottomActions(
    item: Scored,
    completedOn: String?,
    onComplete: (Boolean) -> Unit,
    onDismiss: (Boolean) -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val note = when {
                completedOn != null -> completedLabel(completedOn)
                item.dismissed -> "「関係ない」にしました"
                item.confirmed -> "「当てはまる」にしました"
                else -> null
            }
            note?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    // 必要な手続きには「関係ない」を出さないので、「当てはまる」の取り消しはここから
                    if (item.confirmed && completedOn == null) TextButton(onClick = { onConfirm(false) }) { Text("取り消す") }
                }
            }
            ProcedureActions(item, completed = completedOn != null, onComplete = onComplete, onDismiss = onDismiss, onConfirm = { onConfirm(true) })
        }
    }
}

/**
 * この人にとって必要か（必要 / 関係がありそう / 対象外）。見出しだけを出し、理由は押すと開く。
 * 判定を変えうる情報が足りなければ、その質問を聞く。
 */
@Composable
private fun Status(item: Scored?, state: AppUiState, questions: List<Question>, onAnswer: (String, Answer?) -> Unit) {
    item ?: return
    Column(Modifier.padding(bottom = 16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val (title, color) = when (item.status) {
            Status.REQUIRED -> "あなたに必要な手続きです" to MaterialTheme.colorScheme.primaryContainer
            Status.LIKELY -> "関係がありそうな手続きです" to MaterialTheme.colorScheme.secondaryContainer
            Status.UNRELATED -> "いまの回答では対象外です" to MaterialTheme.colorScheme.surfaceVariant
        }
        StatusCard(title, color, item.reasons.map { RuleEngine.fillIn(it, state.answers, questions) })
        if (item.maybeRequiredBy.isNotEmpty()) MaybeRequired(item, state.answers)
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

/** 見出しのカード。押すと理由の箇条書きが開く（最初は閉じている）。 */
@Composable
private fun StatusCard(title: String, color: Color, reasons: List<String>) {
    var open by rememberSaveable { mutableStateOf(false) }
    Card(
        onClick = { open = !open },
        enabled = reasons.isNotEmpty(),
        colors = CardDefaults.cardColors(containerColor = color, disabledContainerColor = color),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (reasons.isNotEmpty()) {
                    Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "理由を閉じる" else "理由を開く")
                }
            }
            AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    reasons.forEach { Text("・$it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

/** 必要なものの一覧（表示するステップの分をまとめたもの）。 */
@Composable
private fun AmountsCard(amounts: List<AmountView>, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Card(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)) {
        Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("お金の目安", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (amounts.any { it.breakdown.isNotEmpty() }) {
                    TextButton(onClick = { open = !open }) { Text(if (open) "内訳を閉じる" else "計算の内訳") }
                }
            }
            amounts.forEach { a ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(a.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    a.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (open) {
                        a.breakdown.forEach { Text("・$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                    }
                }
            }
            Text("あなたの回答から計算した目安です（社会保険料控除などは含めていません）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/**
 * フローの分岐。まだ選んでいなければ選択肢を並べ（未選択のラジオボタン・名前・1文の説明）、
 * 選んだら選択済みのラジオボタンと名前、「選び直す」だけを出す。選んだ値は回答として端末に保存する。
 */
@Composable
private fun ChoiceBlock(item: FlowItem.Choice, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    val selected = item.selected
    Column(modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AltRoute, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(item.choice.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        if (selected == null) {
            item.choice.options.forEach { option ->
                OutlinedCard(onClick = { onSelect(option.value) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(end = 16.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                        RadioButton(selected = false, onClick = { onSelect(option.value) })
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(option.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(option.detail, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        } else {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = true, onClick = null)
                    Text(selected.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onSelect(null) }) { Text("選び直す") }
                }
            }
        }
    }
}

@Composable
private fun Documents(documents: List<Document>, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("必要なもの", style = MaterialTheme.typography.titleSmall)
            }
            documents.forEach { doc ->
                Text(
                    "・${doc.name}${if (doc.conditional) "（当てはまる場合）" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** 手続きの要点を「やること・期限・窓口」の3行にまとめる。 */
@Composable
private fun Header(proc: ProcedureDef) {
    Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderRow(Icons.Default.Info, "やること", proc.summary)
            proc.deadline?.let { HeaderRow(Icons.Default.Schedule, "期限", it, MaterialTheme.colorScheme.error) }
            proc.office?.let { HeaderRow(Icons.Default.Place, "窓口", it) }
        }
    }
}

@Composable
private fun HeaderRow(icon: ImageVector, label: String, text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp).padding(top = 2.dp), tint = if (color == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.primary else color)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp).padding(top = 1.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepRow(number: Int, view: StepView, questions: List<Question>, answers: Map<String, Answer>, isLast: Boolean, modifier: Modifier = Modifier) {
    val step = view.step
    var open by rememberSaveable(number, step.title) { mutableStateOf(false) }
    Row(modifier.height(IntrinsicSize.Min)) {
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
            Text(RuleEngine.fillIn(step.title, answers, questions), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (view.unknown.isNotEmpty()) {
                val names = view.unknown.mapNotNull { RuleEngine.questionFor(it, questions)?.name }.distinct().joinToString("・")
                Text(
                    "当てはまる場合のみ（${names}を入れると、当てはまるかどうかで表示を絞ります）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            // 「年収（{annual_income}）」のような箇所を、その人の回答に置き換える
            Text(RuleEngine.fillIn(step.detail, answers, questions), style = MaterialTheme.typography.bodyMedium)
            step.where?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = "場所", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            if (step.documents.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    step.documents.forEach { doc -> SuggestionChip(onClick = {}, label = { Text(doc) }) }
                }
            }
            if (step.details.isNotEmpty()) {
                TextButton(
                    onClick = { open = !open },
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.outline),
                ) {
                    Text(if (open) "詳しい手順を閉じる" else "詳しい手順")
                    Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        step.details.forEach { Text("・${RuleEngine.fillIn(it, answers, questions)}", style = MaterialTheme.typography.bodyMedium) }
                    }
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
    AppTheme {
        FlowScreen(state, SampleData.procedure.id, onAnswer = { _, _ -> }, onComplete = { _, _ -> }, onDismiss = { _, _ -> }, onConfirm = { _, _ -> }, onRetry = {}, onBack = {})
    }
}
