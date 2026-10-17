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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import jp.jphacks.sd2606.data.Answer
import jp.jphacks.sd2606.data.Question
import jp.jphacks.sd2606.data.describe
import jp.jphacks.sd2606.data.isSelected
import jp.jphacks.sd2606.data.toAnswer

/** 質問の種類に合った入力欄（日付なら日付の入力、それ以外は選択肢）。 */
@Composable
fun QuestionInput(question: Question, answer: Answer?, onAnswer: (Answer?) -> Unit) {
    if (question.kind == "date") {
        DateInput(answer as? Answer.Date, onAnswer)
    } else {
        QuestionChips(question, answer, onAnswer)
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
    footer: @Composable () -> Unit = {},
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
            footer()
        }
    }
}

/** この端末に保存している回答の一覧と消去。何を持っていて、何を送っていないかを見せる。 */
@Composable
fun AnswersDialog(questions: List<Question>, answers: Map<String, Answer>, onDismiss: () -> Unit, onClear: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("端末に保存している情報") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val answered = questions.filter { it.field in answers }
                if (answered.isEmpty()) Text("まだ何も保存していません。")
                answered.forEach { q ->
                    Column {
                        Text(q.text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(q.describe(answers.getValue(q.field)), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                PrivacyNote("サーバーには送っていません。判定はこの端末の中で行っています。")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
        dismissButton = {
            TextButton(onClick = onClear, enabled = answers.isNotEmpty()) {
                Text("すべて消去", color = MaterialTheme.colorScheme.error)
            }
        },
    )
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
