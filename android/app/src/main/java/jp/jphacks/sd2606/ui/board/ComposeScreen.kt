package jp.jphacks.sd2606.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.time.YearMonth
import jp.jphacks.sd2606.data.Answer
import jp.jphacks.sd2606.data.BoardMeta
import jp.jphacks.sd2606.data.NewPost
import jp.jphacks.sd2606.data.Post
import jp.jphacks.sd2606.data.describeYearMonth
import jp.jphacks.sd2606.ui.BackTopBar
import jp.jphacks.sd2606.ui.PrivacyNote
import jp.jphacks.sd2606.ui.ResultContent
import kotlinx.coroutines.launch

private const val TITLE_MIN = 4
private const val TITLE_MAX = 60
private const val BODY_MIN = 20
private const val BODY_MAX = 2000
private const val TAGS_MAX = 3

/**
 * 体験談を書く画面。年代と立場は、端末に保存している回答（生年月日・働き方）から初期値を選ぶだけで、
 * 何を公開するかは本人が決める（生年月日そのものは送らない）。送る前に公開される内容をプレビューで見せる。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    vm: BoardViewModel,
    answers: Map<String, Answer>,
    procedureId: String?,
    onBack: () -> Unit,
    onPosted: (Post) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackTopBar("体験談を書く", onBack) }) { padding ->
        ResultContent(state.meta, vm::loadMeta, Modifier.padding(padding)) { meta ->
            var kind by rememberSaveable { mutableStateOf(meta.kinds.first().value) }
            var tags by rememberSaveable { mutableStateOf(initialTags(meta, procedureId)) }
            var title by rememberSaveable { mutableStateOf("") }
            var body by rememberSaveable { mutableStateOf("") }
            var difficulty by rememberSaveable { mutableStateOf<Int?>(null) }
            var minutes by rememberSaveable { mutableStateOf<Int?>(null) }
            var happened by rememberSaveable { mutableStateOf<String?>(null) }
            var ageBand by rememberSaveable { mutableStateOf(initialAgeBand(meta, answers)) }
            var occupation by rememberSaveable { mutableStateOf(initialOccupation(meta, answers)) }
            var sending by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            val draft = NewPost(kind, title.trim(), body.trim(), tags, ageBand, occupation, happened, difficulty, minutes)
            val problems = buildList {
                if (tags.isEmpty()) add("タグを1つ以上選んでください")
                if (draft.title.length < TITLE_MIN) add("タイトルは${TITLE_MIN}文字以上で書いてください")
                if (draft.body.length < BODY_MIN) add("本文は${BODY_MIN}文字以上で書いてください")
            }

            Column(
                Modifier
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PrivacyNote("氏名・電話番号・住所・勤務先・学校名など、あなたや他の人が特定される情報は書かないでください。")

                Section("種類") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        meta.kinds.forEachIndexed { i, k ->
                            SegmentedButton(
                                selected = kind == k.value,
                                onClick = { kind = k.value },
                                shape = SegmentedButtonDefaults.itemShape(i, meta.kinds.size),
                            ) { Text(k.label) }
                        }
                    }
                }

                Section("タグ（${TAGS_MAX}つまで）") {
                    meta.tagGroups.forEach { group ->
                        val inGroup = meta.tags.filter { it.group == group.value }
                        if (inGroup.isEmpty()) return@forEach
                        Text(group.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            inGroup.forEach { t ->
                                val selected = t.id in tags
                                FilterChip(
                                    selected = selected,
                                    enabled = selected || tags.size < TAGS_MAX,
                                    onClick = { tags = if (selected) tags - t.id else tags + t.id },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(TITLE_MAX) },
                    label = { Text("タイトル") },
                    placeholder = { Text("例: 退職後の国保加入で書類が足りなかった") },
                    singleLine = true,
                    supportingText = { Text("${title.length} / $TITLE_MAX") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it.take(BODY_MAX) },
                    label = { Text("体験談") },
                    placeholder = { Text("どこで・何を持って・どのくらいかかったか、困ったこと、次の人へのアドバイスなど") },
                    supportingText = { Text("${body.length} / $BODY_MAX（${BODY_MIN}文字以上）") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )

                Section("大変さ（任意）") { StarInput(difficulty) { difficulty = it } }

                Section("かかった時間（任意）") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        meta.durations.forEach { d ->
                            FilterChip(selected = minutes == d.value, onClick = { minutes = if (minutes == d.value) null else d.value }, label = { Text(d.label) })
                        }
                    }
                }

                Section("いつの話か（任意）") {
                    val months = remember { (0L until 36L).map { YearMonth.now().minusMonths(it).toString() } }
                    Picker(happened?.let(::describeYearMonth) ?: "選ばない", listOf<Pair<String?, String>>(null to "選ばない") + months.map { it to describeYearMonth(it) }) { happened = it }
                }

                Section("あなたについて（任意・公開されます）") {
                    Text(
                        "保存している生年月日・働き方から選んでいます。公開したくなければ「公開しない」にしてください。生年月日そのものは送りません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            Picker(
                                meta.ageBands.firstOrNull { it.value == ageBand }?.label ?: "年代: 公開しない",
                                listOf<Pair<String?, String>>(null to "公開しない") + meta.ageBands.map { it.value to it.label },
                            ) { ageBand = it }
                        }
                        Box(Modifier.weight(1f)) {
                            Picker(
                                meta.occupations.firstOrNull { it.value == occupation }?.label ?: "立場: 公開しない",
                                listOf<Pair<String?, String>>(null to "公開しない") + meta.occupations.map { it.value to it.label },
                            ) { occupation = it }
                        }
                    }
                }

                HorizontalDivider()
                Section("公開される内容") {
                    PostCard(previewOf(draft), meta, helpful = 0, full = true)
                }

                problems.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        sending = true
                        error = null
                        scope.launch {
                            vm.submit(draft).onSuccess(onPosted).onFailure { error = it.message }
                            sending = false
                        }
                    },
                    enabled = problems.isEmpty() && !sending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("この内容で公開する")
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

/** ボタンを押すと選択肢のメニューが開く（null は「選ばない」）。 */
@Composable
private fun Picker(current: String, options: List<Pair<String?, String>>, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(current, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onSelect(value) })
            }
        }
    }
}

/** 手続きの画面から開いたら、その手続きにいちばん絞り込まれたタグ（対応する手続きが少ないもの）を選んでおく。 */
private fun initialTags(meta: BoardMeta, procedureId: String?): List<String> =
    procedureId?.let { id -> meta.tagsFor(id).minByOrNull { it.procedures.size }?.id }?.let(::listOf) ?: emptyList()

private fun initialAgeBand(meta: BoardMeta, answers: Map<String, Answer>): String? =
    (answers["birth_date"] as? Answer.Date)?.let { runCatching { meta.ageBandFor(it.toLocalDate()) }.getOrNull() }

private fun initialOccupation(meta: BoardMeta, answers: Map<String, Answer>): String? {
    val chosen = (answers["occupation"] as? Answer.Multi)?.values ?: return null
    return meta.occupations.firstOrNull { it.value in chosen }?.value
}

private fun previewOf(p: NewPost) = Post(
    id = 0,
    kind = p.kind,
    title = p.title.ifEmpty { "（タイトル）" },
    body = p.body.ifEmpty { "（本文）" },
    tags = p.tags,
    ageBand = p.ageBand,
    occupation = p.occupation,
    happenedYm = p.happenedYm,
    difficulty = p.difficulty,
    minutes = p.minutes,
    createdAt = "",
)
