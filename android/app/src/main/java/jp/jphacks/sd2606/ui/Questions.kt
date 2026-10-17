package jp.jphacks.sd2606.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import jp.jphacks.sd2606.data.Answer
import jp.jphacks.sd2606.data.Question
import jp.jphacks.sd2606.data.describe
import jp.jphacks.sd2606.data.isSelected
import jp.jphacks.sd2606.data.toAnswer

/**
 * 質問の種類に合った入力欄（日付なら日付の入力、それ以外は選択肢）。
 * live なら数値を入力のたびに反映する（並びが動かない最初の画面用）。
 */
@Composable
fun QuestionInput(question: Question, answer: Answer?, onAnswer: (Answer?) -> Unit, live: Boolean = false) {
    when (question.kind) {
        "date" -> DateInput(answer as? Answer.Date, onAnswer)
        "number" -> NumberInput(question, answer as? Answer.Number, onAnswer, live)
        else -> QuestionChips(question, answer, onAnswer)
    }
}

/**
 * 数値の入力。入力の途中で判定が変わって画面が動かないよう、「決定」（またはキーボードの完了）で反映する。
 * 空にして決定すると未回答に戻す。live なら入力のたびに反映し、「決定」ボタンは出さない。
 */
@Composable
private fun NumberInput(question: Question, answer: Answer.Number?, onAnswer: (Answer?) -> Unit, live: Boolean) {
    val saved = answer?.let { (it.value / question.scale).toString() }.orEmpty()
    var text by rememberSaveable(saved) { mutableStateOf(saved) }
    val commit = { onAnswer(text.toLongOrNull()?.let { Answer.Number(it * question.scale) }) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter(Char::isDigit).take(7)
                if (live) onAnswer(text.toLongOrNull()?.let { Answer.Number(it * question.scale) })
            },
            suffix = { Text(question.unit) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier.weight(1f),
        )
        if (!live) {
            Spacer(Modifier.width(8.dp))
            Button(onClick = commit, enabled = text != saved) { Text("決定") }
        }
    }
}

/**
 * 質問の選択肢。single / range は選び直すと置き換え、もう一度押すと取り消し。multi は個別に切り替え、
 * すべて外すと未回答に戻す（noneIfEmpty なら「どれも当てはまらない」として残す）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionChips(question: Question, answer: Answer?, onAnswer: (Answer?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        question.choices.forEach { choice ->
            val selected = question.isSelected(choice, answer)
            FilterChip(
                selected = selected,
                onClick = {
                    onAnswer(
                        if (question.kind == "multi") {
                            val current = when (answer) {
                                is Answer.Multi -> answer.values
                                is Answer.Single -> listOf(answer.value) // 1つ選択だった頃の回答を引き継ぐ
                                else -> emptyList()
                            }
                            val value = listOfNotNull(choice.value)
                            val next = if (selected) current - value else current + value
                            if (next.isEmpty() && !question.noneIfEmpty) null else Answer.Multi(next)
                        } else if (selected) {
                            null
                        } else {
                            choice.toAnswer()
                        },
                    )
                },
                label = { Text(choice.label) },
            )
        }
    }
}

/** 判定に必要になった質問を1問だけ聞くカード。note には「答えると何が分かるか」を書く。 */
@Composable
fun QuestionCard(
    question: Question,
    answer: Answer?,
    onAnswer: (Answer?) -> Unit,
    note: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(question.text, style = MaterialTheme.typography.titleSmall)
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            QuestionInput(question, answer, onAnswer)
        }
    }
}

/**
 * この端末に保存している情報の一覧。受け付ける項目をすべて並べ（未入力も含む）、項目ごとに編集・削除できる。
 * 完了の記録も端末に保存しているので、件数を見せて消せるようにする。何を持っていて、何を送っていないかを見せる。
 */
@Composable
fun AnswersDialog(
    questions: List<Question>,
    answers: Map<String, Answer>,
    completedCount: Int,
    dismissedCount: Int,
    reminderCount: Int,
    onAnswer: (String, Answer?) -> Unit,
    onClearCompleted: () -> Unit,
    onClearDismissed: () -> Unit,
    onClearReminders: () -> Unit,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("端末に保存している情報") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                questions.forEach { q ->
                    val answer = answers[q.field]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(q.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                answer?.let { q.describe(it) } ?: "未入力",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (answer == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { editing = if (editing == q.field) null else q.field }) {
                            Icon(Icons.Default.Edit, contentDescription = "${q.name}を編集")
                        }
                        IconButton(onClick = { onAnswer(q.field, null) }, enabled = answer != null) {
                            Icon(Icons.Default.Delete, contentDescription = "${q.name}を削除")
                        }
                    }
                    if (editing == q.field) {
                        Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuestionInput(q, answer, onAnswer = { onAnswer(q.field, it) })
                        }
                    }
                    HorizontalDivider()
                }
                RecordRow("完了の記録", completedCount, onClearCompleted)
                HorizontalDivider()
                RecordRow("「関係ない」にした手続き", dismissedCount, onClearDismissed)
                HorizontalDivider()
                RecordRow("オンにした通知", reminderCount, onClearReminders)
                HorizontalDivider()
                Spacer(Modifier.size(4.dp))
                PrivacyNote("サーバーには送っていません。判定はこの端末の中で行っています。")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
        dismissButton = {
            TextButton(onClick = onClear, enabled = answers.isNotEmpty() || completedCount > 0 || dismissedCount > 0 || reminderCount > 0) {
                Text("すべて消去", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

/** 端末に残している記録の件数と、消すボタン。 */
@Composable
private fun RecordRow(label: String, count: Int, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (count > 0) "${count}件" else "なし",
                style = MaterialTheme.typography.bodyLarge,
                color = if (count == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onClear, enabled = count > 0) {
            Icon(Icons.Default.Delete, contentDescription = "${label}を削除")
        }
    }
}

/** 日付の入力。ボタンを押すと日付の入力ダイアログ（カレンダーにも切り替え可）を開く。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateInput(answer: Answer.Date?, onAnswer: (Answer?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(answer?.describe() ?: "日付を入力する")
    }
    Text(
        "生年月日は年齢の計算にだけ使い、端末の外には送りません。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!open) return

    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = answer?.toLocalDate()?.toEpochMillis(),
        yearRange = (today.year - 110)..today.year,
        initialDisplayMode = DisplayMode.Input,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.toEpochMillis()
        },
    )
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onAnswer(Answer.Date(LocalDate.ofEpochDay(it / MILLIS_PER_DAY))) }
                    open = false
                },
                enabled = state.selectedDateMillis != null,
            ) { Text("決定") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("キャンセル") } },
    ) {
        DatePicker(state = state, title = { Text("生年月日", Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

// DatePicker は日付を UTC の 0 時のミリ秒で扱う
private const val MILLIS_PER_DAY = 86_400_000L

private fun LocalDate.toEpochMillis() = toEpochDay() * MILLIS_PER_DAY

/** 「回答は端末の外に出さない」ことを示す帯。 */
@Composable
fun PrivacyNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
